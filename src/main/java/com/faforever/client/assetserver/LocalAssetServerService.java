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

  private HttpServer server;
  private String token;

  /**
   * Starts the server if it isn't running yet.
   *
   * @return the base URL (scheme, loopback host, port and token path segment, no trailing slash)
   *     the viewer should resolve asset paths against, e.g. {@code http://127.0.0.1:53211/Ab3…xZ}.
   */
  @SneakyThrows
  public synchronized String ensureRunning() {
    if (server == null) {
      byte[] tokenBytes = new byte[16];
      new SecureRandom().nextBytes(tokenBytes);
      token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

      HttpServer newServer = HttpServer.create(
          new InetSocketAddress(InetAddress.getLoopbackAddress(), clientProperties.getLiveViewer().getAssetServerPort()), 0);
      newServer.createContext("/", new AssetServerHandler(token, allowedOrigin(), preferencesService));
      newServer.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "local-asset-server");
        thread.setDaemon(true);
        return thread;
      }));
      newServer.start();
      server = newServer;
      log.info("Local asset server listening on 127.0.0.1:{}", server.getAddress().getPort());
    }
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + token;
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
