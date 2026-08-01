package com.faforever.client.replay;

import com.faforever.client.assetserver.LocalAssetServerService;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.fa.DemoFileInfo;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.game.Game;
import com.faforever.client.map.MapService;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.patch.GameUpdater;
import com.faforever.client.preferences.PreferencesService;
import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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

  /** How long to let in-flight asset reads finish before rewriting the install. */
  private static final int ASSET_SERVER_DRAIN_SECONDS = 5;

  private final ClientProperties clientProperties;
  private final ReplayService replayService;
  private final MapService mapService;
  private final ModService modService;
  private final GameUpdater gameUpdater;
  private final PreferencesService preferencesService;
  private final LocalAssetServerService localAssetServerService;
  private final PlatformService platformService;
  private final NotificationService notificationService;

  /**
   * Which mod version a VAULT replay needs — the one it was PLAYED on, not the latest.
   *
   * The mod's default git branch is the branch HEAD, so for anything but a brand-new replay it
   * checks out the wrong build. That is not cosmetic for the browser viewer: it resolves unit
   * types by rank within the local unit set and validates them against the demo's 0x1a table by
   * CRC, so a version whose unit COUNT differs shifts every rank past the first added unit and
   * misattributes models wholesale. Observed on an Escalation replay: demo 530 unit types vs a
   * 552-unit install, 183/530 CRCs matching, every model wrong — while the legacy replayer
   * played the same replay fine, because it pins the version from the demo.
   *
   * The pin comes from the same place the legacy path gets it
   * ({@code GameService.runWithReplay(DemoFileInfo)}): the demo's units hash, which the API
   * hands us in {@code replayMeta} and {@link Replay} exposes as demoFileInfo.
   * {@code findFeaturedModByTaDemoModHash} matches it against each FeaturedModVersion's taHash
   * and narrows the versions list to the one that matched — so versions.get(0) IS the build the
   * game was played on. Safe to call: getFeaturedMods() rebuilds its beans per call, so the
   * narrowing does not mutate shared state.
   *
   * Falls back to the branch head when the replay predates replayMeta, or when the hash matches
   * no known version — best effort beats refusing to open, and the viewer warns loudly about a
   * unit-set mismatch anyway.
   */
  private CompletableFuture<String> resolveReplayModVersion(Replay replay, FeaturedMod featuredMod) {
    String branch = featuredMod != null ? featuredMod.getGitBranch() : null;
    DemoFileInfo demoFileInfo = replay.getDemoFileInfo();
    if (demoFileInfo == null || Strings.isNullOrEmpty(demoFileInfo.getModHash())) {
      return CompletableFuture.completedFuture(branch);
    }
    return modService.findFeaturedModByTaDemoFileInfo(demoFileInfo)
        .thenApply(pinned -> {
          if (pinned == null || pinned.getVersions() == null || pinned.getVersions().isEmpty()) {
            log.info("Replay {} units hash {} matches no known version of {}; using branch {}",
                replay.getId(), demoFileInfo.getModHash(), featuredMod, branch);
            return branch;
          }
          String version = pinned.getVersions().get(0).getGitBranch();
          log.info("Replay {} pinned to mod version {} by units hash {}",
              replay.getId(), version, demoFileInfo.getModHash());
          return version;
        })
        .exceptionally(throwable -> {
          log.warn("Could not resolve the mod version for replay {}; using branch {}",
              replay.getId(), branch, throwable);
          return branch;
        });
  }

  /**
   * Run the whole watch flow with map enumeration held off.
   *
   * maptool.exe opens EVERY archive under --gamepath to enumerate maps, and Java holds Windows
   * files without FILE_SHARE_DELETE, so an enumeration overlapping a featured-mod checkout
   * fails the rename ("Could not rename ._T2ESC.ufo….tmp to T2ESC.ufo") and leaves the install
   * half-updated. MapService already has the deferral for this and GameUpdater takes one around
   * the update — but the counter is shared and OUR OWN optionalEnsureMap takes and releases one
   * too. Releasing it can drop the count to zero and immediately fire the enumeration that was
   * queued while the checkout was still running; that is exactly what the failing log shows,
   * two maptool.exe launches landing 340 ms before the checkout's rename.
   *
   * Holding one deferral across the entire flow means the nested take/release can never reach
   * zero before we are completely done with the install.
   */
  private <T> CompletableFuture<T> withMapEnumerationDeferred(
      Supplier<CompletableFuture<T>> flow) {
    mapService.addInstalledMapsUpdateDeferal();
    CompletableFuture<T> future;
    try {
      future = flow.get();
    } catch (RuntimeException e) {
      mapService.releaseInstalledMapsUpdateDeferal();
      throw e;
    }
    return future.whenComplete((result, throwable) -> mapService.releaseInstalledMapsUpdateDeferal());
  }

  /**
   * Bring the local install of a featured mod up to date before the viewer reads it.
   *
   * The browser viewer resolves every unit from the LOCAL install (unit .fbi/.3do/.cob served
   * over the asset server), and matches the demo's 0x1a unit table against it by CRC. An
   * out-of-date mod therefore does not merely look slightly wrong — the unit-table fingerprint
   * misses and units resolve to the wrong types or not at all. So the browser path needs the
   * same update step the classic replayer gets from
   * {@code GameService.runWithReplay} → {@code updateGameIfNecessary}, which it was missing.
   *
   * Version resolution mirrors {@code runWithReplay(String, Game)}: the game's own recorded mod
   * version when there is one, otherwise the mod's default git branch.
   *
   * The user's auto-update preference is honoured because it is honoured INSIDE the updater
   * (GitLfsFeaturedModUpdater: ALWAYS updates silently, ASK prompts once per mod per session,
   * NEVER skips) — deliberately not re-implemented here, so the two watch paths can't drift.
   *
   * Never fails the watch: a mod that won't update is a degraded viewing experience, not a
   * reason to refuse to open the viewer, and gameUpdater.update() has already told the user.
   */
  private CompletableFuture<Void> ensureModUpToDate(String modTechnical, String modVersion) {
    if (Strings.isNullOrEmpty(modTechnical)) {
      // TADA replays: the mod isn't known until livescene decodes the demo header, so there is
      // nothing to update against — the viewer resolves the install by unit-table fingerprint.
      return CompletableFuture.completedFuture(null);
    }
    if (!preferencesService.isGameExeValid(modTechnical)) {
      // Not installed here at all. Updating would mean a full clone off the back of a "watch"
      // click; the asset server already answers "mod not installed" and the viewer says so.
      log.info("Not updating {} for browser watch: no valid local installation", modTechnical);
      return CompletableFuture.completedFuture(null);
    }
    // STOP SERVING THE INSTALL BEFORE REWRITING IT. The update replaces the very archives this
    // server streams, and Java holds Windows files without FILE_SHARE_DELETE — one in-flight
    // range read from a viewer tab left open by an earlier watch is enough to fail the checkout
    // with "Could not rename ._T2ESC.ufo….tmp to T2ESC.ufo" and leave the mod half-updated.
    // Draining costs nothing here: openViewer() restarts the server a moment later, and any
    // tab we cut off was showing the pre-update mod anyway.
    localAssetServerService.stop(ASSET_SERVER_DRAIN_SECONDS);
    // INSTRUMENTATION, deliberately kept: everything after this point (map ensure, viewer open)
    // assumes the install has stopped changing. One failing log showed our map ensure starting
    // 144 ms into a 5.5 s checkout, which would mean this future settles before GitCheckoutTask
    // does and the viewer can open against half-written archives. The composition all the way
    // down to taskService.submitTask(...).getFuture() reads as correctly chained, so it was
    // never explained — and it has not reproduced since. These two lines settle it in one run:
    // compare "update SETTLED after N ms" against GitCheckoutTask's own "Checked out X in M ms".
    // N < M means the update was not awaited.
    long startedAt = System.currentTimeMillis();
    return modService.getFeaturedMod(modTechnical)
        .thenCompose(featuredMod -> {
          String version = modVersion != null ? modVersion : featuredMod.getGitBranch();
          log.info("Mod update START: {} -> version {}", modTechnical, version);
          return gameUpdater.update(featuredMod, version);
        })
        .handle((head, throwable) -> {
          log.info("Mod update SETTLED: {} after {} ms (head={}, failed={})",
              modTechnical, System.currentTimeMillis() - startedAt, head, throwable != null);
          if (throwable != null) {
            log.warn("Mod update failed for {} before browser watch; opening anyway",
                modTechnical, throwable);
          }
          return null;
        });
  }

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

    // Mod update BEFORE the map ensure, the same order runWithReplay uses — an update can bring
    // map archives with it. The whole flow runs with map enumeration deferred so maptool.exe
    // cannot open the archives the checkout is replacing (see withMapEnumerationDeferred).
    return withMapEnumerationDeferred(() -> ensureModUpToDate(
            game.getFeaturedMod(), game.getFeaturedModVersion())
        .thenCompose(aVoid -> mapService.optionalEnsureMap(
            game.getFeaturedMod(), game.getMapName(), game.getMapCrc(), game.getMapArchiveName(), null, null))
        .thenCompose(mapBean -> replayService.fetchWatchTicket(game))
        .thenAccept(ticket -> {
          if (ticket == null) {
            // Denied: the lobby has already notified the user. Open nothing.
            return;
          }
          openViewer(game.getId(), "live", game.getFeaturedMod(), ticket.orElse(null), null);
        }))
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

    FeaturedMod featuredMod = replay.getFeaturedMod();
    String modTechnical = featuredMod != null ? featuredMod.getTechnicalName() : null;

    return withMapEnumerationDeferred(() -> resolveReplayModVersion(replay, featuredMod)
        .thenCompose(version -> ensureModUpToDate(modTechnical, version))
        .thenCompose(aVoid -> modTechnical != null && replay.getMap() != null
            ? mapService.optionalEnsureMap(modTechnical, replay.getMap(), null, null)
            : CompletableFuture.completedFuture(null))
        .thenAccept(mapBean -> openViewer(replay.getId(), "vod", modTechnical, null, null)))
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
