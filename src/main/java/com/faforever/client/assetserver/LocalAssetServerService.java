package com.faforever.client.assetserver;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.preferences.PreferencesService;
import com.sun.net.httpserver.HttpServer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Executors;

/**
 * Serves the user's locally-installed game archives to the browser-based live viewer
 * ("Contract A"). The viewer page (hosted on the TAF website) fetches unit models, textures,
 * COB scripts and maps from here — game assets never pass through TAF servers.
 *
 * Access control: bound to the loopback interface only, and every request path must carry a
 * random per-session token ({@code /<token>/...}) that is only ever disclosed inside the URL
 * fragment handed to the browser. CORS is pinned to the website origin. Together these keep
 * arbitrary websites and other local processes from enumerating or reading game files.
 */
@Lazy
@Service
@Slf4j
@RequiredArgsConstructor
public class LocalAssetServerService implements DisposableBean {

  private final ClientProperties clientProperties;
  private final PreferencesService preferencesService;

  /**
   * Handler threads. The JDK's {@link HttpServer} occupies one for a whole exchange, body
   * included, so this is the number of requests that can be IN FLIGHT — not the number the
   * server can hold open. It used to be 4, which the viewer overruns trivially: it opens
   * every archive in the install concurrently and each directory parse is dozens of ranged
   * reads. Worse, a thread stays blocked writing to a socket whose browser tab has gone
   * away until TCP gives up, so a couple of crashed tabs could wedge the whole server and
   * the next request would sit in the accept queue with no response at all — which the
   * viewer, having no timeout, waited on forever.
   */
  private static final int HANDLER_THREADS = 16;

  private HttpServer server;
  private String token;
  /**
   * Port and token are REMEMBERED ACROSS RESTARTS, and that is the point of holding them
   * here rather than minting them per start. The viewer's base URL contains both, and it is
   * baked into a browser tab we have already opened; a mod update stops this server and
   * starts it again a moment later, so minting fresh ones silently invalidated every tab
   * the user still had open. Re-binding the same port with the same token turns that restart
   * into a blip the viewer's retry rides straight over.
   */
  private int lastPort;

  /**
   * Starts the server if it isn't running yet.
   *
   * @return the base URL (scheme, loopback host, port and token path segment, no trailing slash)
   *     the viewer should resolve asset paths against, e.g. {@code http://127.0.0.1:53211/Ab3…xZ}.
   */
  @SneakyThrows
  public synchronized String ensureRunning() {
    if (server == null) {
      if (token == null) {
        byte[] tokenBytes = new byte[16];
        new SecureRandom().nextBytes(tokenBytes);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
      }

      HttpServer newServer = bind(preferredPort());
      if (newServer == null) {
        // Something else took the port while we were stopped. Any tab holding the old URL is
        // lost either way, so fall back to a fresh ephemeral one rather than refusing to run.
        log.info("Local asset server could not re-bind port {}, taking a new one", lastPort);
        newServer = bind(clientProperties.getLiveViewer().getAssetServerPort());
        if (newServer == null) {
          throw new IOException("could not bind the local asset server to the loopback address");
        }
      }
      newServer.createContext("/", new AssetServerHandler(token, allowedOrigin(), preferencesService));
      newServer.setExecutor(Executors.newFixedThreadPool(HANDLER_THREADS, runnable -> {
        Thread thread = new Thread(runnable, "local-asset-server");
        thread.setDaemon(true);
        return thread;
      }));
      newServer.start();
      server = newServer;
      lastPort = server.getAddress().getPort();
      log.info("Local asset server listening on 127.0.0.1:{}", server.getAddress().getPort());
    }
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + token;
  }

  /** The port a restart should try to reclaim: the one we were last listening on. */
  private int preferredPort() {
    return lastPort != 0 ? lastPort : clientProperties.getLiveViewer().getAssetServerPort();
  }

  /** Bind the loopback address, or null when the port is taken. */
  private HttpServer bind(int port) {
    try {
      return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    } catch (IOException e) {
      return null;
    }
  }

  /** The origin (scheme://host[:port]) of the website hosting the viewer page. */
  private String allowedOrigin() {
    URI uri = URI.create(clientProperties.getWebsite().getBaseUrl());
    return uri.getPort() == -1
        ? uri.getScheme() + "://" + uri.getHost()
        : uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
  }

  @Override
  public void destroy() {
    stop();
  }

  public synchronized void stop() {
    stop(0);
  }

  /**
   * Stop the server, waiting up to {@code drainSeconds} for in-flight exchanges to finish.
   *
   * THE DRAIN IS THE POINT, and it exists for one reason: this server streams the game's own
   * archives straight off disk, and a featured-mod update REPLACES those files. Java opens
   * files on Windows without FILE_SHARE_DELETE, so a single in-flight range read of, say,
   * T2ESC.ufo makes jgit's checkout fail with "Could not rename …tmp to T2ESC.ufo" and leaves
   * the install half-updated. A stale viewer tab left open from an earlier watch is enough to
   * do it — and it reads a LOT: the unit index alone pulls every FBI in the mod.
   *
   * stop(0) closes the listening socket but does NOT wait for handlers already inside
   * serveFile(), so it does not release the file handles that matter. Callers about to touch
   * the install must pass a real delay.
   */
  public synchronized void stop(int drainSeconds) {
    if (server != null) {
      server.stop(drainSeconds);
      server = null;
      log.info("Local asset server stopped (drained up to {}s)", drainSeconds);
    }
  }
}
