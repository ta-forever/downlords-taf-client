package com.faforever.client.replay;

import com.faforever.client.assetserver.LocalAssetServerService;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.game.Game;
import com.faforever.client.map.MapService;
import com.faforever.client.notification.NotificationService;
import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Opens games in the hosted browser-based 3D viewer — live games, completed vault replays, and
 * TADA replays.
 *
 * The viewer page (faf-client.live-viewer.url-format) receives everything it needs in the URL
 * fragment: the source kind, the signed watch ticket (live games only — completed replays are
 * publicly downloadable, so no ticket applies), the featured mod technical name, and the base URL
 * of the local asset server from which the page fetches the user's installed game archives.
 * Fragment, not query — fragments never leave the browser, so tickets and localhost URLs stay
 * out of webserver/proxy access logs.
 */
@Lazy
@Service
@Slf4j
@RequiredArgsConstructor
public class BrowserWatchService {

  private final ClientProperties clientProperties;
  private final ReplayService replayService;
  private final MapService mapService;
  private final LocalAssetServerService localAssetServerService;
  private final PlatformService platformService;
  private final NotificationService notificationService;

  public boolean isAvailable() {
    return !Strings.isNullOrEmpty(clientProperties.getLiveViewer().getUrlFormat());
  }

  /**
   * Watch a LIVE game: ensure the map is installed locally (the viewer loads it from the local
   * asset server), fetch a watch ticket, start the asset server, then open the viewer page.
   *
   * Deliberately no canStartReplay()-style gate: watching in a browser while a game or replay is
   * running locally is harmless. Watching your own live game is blocked server-side (the lobby
   * denies the ticket to participants).
   */
  public CompletableFuture<Void> watchInBrowser(Game game) {
    if (!isAvailable()) {
      log.warn("Browser watch requested but faf-client.live-viewer.url-format is not configured");
      return CompletableFuture.completedFuture(null);
    }

    return mapService.optionalEnsureMap(
            game.getFeaturedMod(), game.getMapName(), game.getMapCrc(), game.getMapArchiveName(), null, null)
        .thenCompose(mapBean -> replayService.fetchWatchTicket(game))
        .thenAccept(ticket -> {
          if (ticket == null) {
            // Denied: the lobby has already notified the user. Open nothing.
            return;
          }
          openViewer(game.getId(), "live", game.getFeaturedMod(), ticket.orElse(null), null);
        })
        .exceptionally(throwable -> {
          log.error("Failed to open browser viewer for game {}", game.getId(), throwable);
          notificationService.addImmediateErrorNotification(throwable, "game.watch.browserFailed");
          return null;
        });
  }

  /**
   * Watch a COMPLETED vault replay. No watch ticket: the game is over (the anti-smurf block only
   * applies to live games) and the archived .tad is publicly downloadable anyway — the livescene
   * service fetches it by game id server-side.
   */
  public CompletableFuture<Void> watchReplayInBrowser(Replay replay) {
    if (!isAvailable()) {
      log.warn("Browser watch requested but faf-client.live-viewer.url-format is not configured");
      return CompletableFuture.completedFuture(null);
    }

    String modTechnical = replay.getFeaturedMod() != null ? replay.getFeaturedMod().getTechnicalName() : null;
    CompletableFuture<?> mapEnsured = modTechnical != null && replay.getMap() != null
        ? mapService.optionalEnsureMap(modTechnical, replay.getMap(), null, null)
        : CompletableFuture.completedFuture(null);

    return mapEnsured
        .thenAccept(mapBean -> openViewer(replay.getId(), "vod", modTechnical, null, null))
        .exceptionally(throwable -> {
          log.error("Failed to open browser viewer for replay {}", replay.getId(), throwable);
          notificationService.addImmediateErrorNotification(throwable, "game.watch.browserFailed");
          return null;
        });
  }

  /**
   * Watch a TADA replay (remote only — local .tad files can't be reached by the livescene
   * service). The mod is unknown until the demo header is decoded, so no mod param is passed;
   * the viewer resolves the mod from the livescene init message (mod id + unit-table
   * fingerprint) against the asset server's installed-mods list. Game id 0 = "source described
   * entirely by the fragment".
   */
  public CompletableFuture<Void> watchTadaReplayInBrowser(String key, String tadaReplayId, String filename) {
    if (!isAvailable()) {
      log.warn("Browser watch requested but faf-client.live-viewer.url-format is not configured");
      return CompletableFuture.completedFuture(null);
    }

    try {
      openViewer(0, "tada", null, null,
          "&tadaKey=" + urlEncode(key) + "&tadaId=" + urlEncode(tadaReplayId) + "&tadaFile=" + urlEncode(filename));
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      log.error("Failed to open browser viewer for TADA replay {}", tadaReplayId, e);
      notificationService.addImmediateErrorNotification(e, "game.watch.browserFailed");
      return CompletableFuture.completedFuture(null);
    }
  }

  private void openViewer(int gameId, String source, String modTechnical, String ticket, String extraParams) {
    String assetBaseUrl = localAssetServerService.ensureRunning();

    StringBuilder url = new StringBuilder(String.format(clientProperties.getLiveViewer().getUrlFormat(), gameId));
    url.append("#source=").append(source);
    if (modTechnical != null) {
      url.append("&mod=").append(urlEncode(modTechnical));
    }
    url.append("&assets=").append(urlEncode(assetBaseUrl));
    if (ticket != null && !ticket.isBlank()) {
      url.append("&ticket=").append(urlEncode(ticket));
    }
    if (extraParams != null) {
      url.append(extraParams);
    }

    log.info("Opening browser viewer: game {} source {} (ticket: {})", gameId, source, ticket != null);
    platformService.showDocument(url.toString());
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
