package com.faforever.client.replay;

import com.faforever.client.assetserver.LocalAssetServerService;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.fa.DemoFileInfo;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameService;
import com.faforever.client.map.MapService;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.FeaturedModVersion;
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.patch.GameUpdater;
import com.faforever.client.preferences.PreferencesService;
import javafx.collections.FXCollections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class BrowserWatchServiceTest {

  private BrowserWatchService instance;
  private ClientProperties clientProperties;

  @Mock
  private ReplayService replayService;
  @Mock
  private GameService gameService;
  @Mock
  private MapService mapService;
  @Mock
  private LocalAssetServerService localAssetServerService;
  @Mock
  private PlatformService platformService;
  @Mock
  private com.faforever.client.fx.BrowserLauncher browserLauncher;
  @Mock
  private NotificationService notificationService;
  @Mock
  private ModService modService;
  @Mock
  private GameUpdater gameUpdater;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private Game game;
  @Mock
  private FeaturedMod taccMod;

  @Before
  public void setUp() {
    clientProperties = new ClientProperties();
    clientProperties.getLiveViewer().setUrlFormat("https://www.taforever.com/live/%d");

    when(game.getId()).thenReturn(42);
    when(game.getFeaturedMod()).thenReturn("tacc");
    when(mapService.optionalEnsureMap(any(), any(), any(), any(), any(), any()))
        .thenReturn(completedFuture(null));
    when(localAssetServerService.ensureRunning()).thenReturn("http://127.0.0.1:53211/AbCtoken");

    // installed and up to date by default; individual tests override
    when(preferencesService.isGameExeValid(anyString())).thenReturn(true);
    when(taccMod.getTechnicalName()).thenReturn("tacc");
    when(taccMod.getGitBranch()).thenReturn("main");
    when(modService.getFeaturedMod("tacc")).thenReturn(completedFuture(taccMod));
    when(gameUpdater.update(any(), any())).thenReturn(completedFuture("main"));

    instance = new BrowserWatchService(clientProperties, replayService, gameService, mapService, modService,
        gameUpdater, preferencesService, localAssetServerService, platformService, browserLauncher,
        notificationService);
  }

  @Test
  public void opensViewerWithTicketInFragment() {
    // base64-ish ticket with characters that must survive URL encoding
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.of("abc+/=")));

    instance.watchInBrowser(game).join();

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(platformService).showDocument(url.capture());
    assertThat(url.getValue(), is(
        "https://www.taforever.com/live/42"
            + "#source=live"
            + "&mod=tacc"
            + "&assets=http%3A%2F%2F127.0.0.1%3A53211%2FAbCtoken"
            + "&ticket=abc%2B%2F%3D"));
  }

  @Test
  public void ticketTimeoutFallsBackToTicketlessUrl() {
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.empty()));

    instance.watchInBrowser(game).join();

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(platformService).showDocument(url.capture());
    assertThat(url.getValue().contains("ticket="), is(false));
    assertThat(url.getValue().contains("#source=live&mod=tacc"), is(true));
  }

  @Test
  public void deniedTicketOpensNothing() {
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(null));

    instance.watchInBrowser(game).join();

    verify(platformService, never()).showDocument(anyString());
    verify(localAssetServerService, never()).ensureRunning();
  }

  @Test
  public void unconfiguredViewerDoesNothing() {
    clientProperties.getLiveViewer().setUrlFormat(null);

    instance.watchInBrowser(game).join();

    verify(mapService, never()).optionalEnsureMap(any(), any(), any(), any(), any(), any());
    verify(platformService, never()).showDocument(anyString());
  }

  @Test
  public void vodReplayOpensWithoutTicket() {
    Replay replay = org.mockito.Mockito.mock(Replay.class);
    com.faforever.client.mod.FeaturedMod featuredMod = org.mockito.Mockito.mock(com.faforever.client.mod.FeaturedMod.class);
    com.faforever.client.map.MapBean mapBean = org.mockito.Mockito.mock(com.faforever.client.map.MapBean.class);
    when(replay.getId()).thenReturn(314);
    when(replay.getFeaturedMod()).thenReturn(featuredMod);
    when(featuredMod.getTechnicalName()).thenReturn("tacc");
    when(replay.getMap()).thenReturn(mapBean);
    when(mapService.optionalEnsureMap(anyString(), any(com.faforever.client.map.MapBean.class), any(), any()))
        .thenReturn(completedFuture(mapBean));

    instance.watchReplayInBrowser(replay).join();

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(platformService).showDocument(url.capture());
    assertThat(url.getValue(), is(
        "https://www.taforever.com/live/314"
            + "#source=vod"
            + "&mod=tacc"
            + "&assets=http%3A%2F%2F127.0.0.1%3A53211%2FAbCtoken"));
    // A completed game never asks the lobby for a watch ticket.
    verify(replayService, never()).fetchWatchTicket(any());
  }

  @Test
  public void tadaReplayOpensWithFragmentSource() {
    instance.watchTadaReplayInBrowser("k1", "abc123", "my game.tad").join();

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(platformService).showDocument(url.capture());
    assertThat(url.getValue(), is(
        "https://www.taforever.com/live/0"
            + "#source=tada"
            + "&assets=http%3A%2F%2F127.0.0.1%3A53211%2FAbCtoken"
            + "&tadaKey=k1&tadaId=abc123&tadaFile=my+game.tad"));
  }

  @Test
  public void ensuresMapBeforeOpening() {
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.empty()));
    when(game.getMapName()).thenReturn("SHERWOOD");
    when(game.getMapCrc()).thenReturn("deadbeef");
    when(game.getMapArchiveName()).thenReturn("sherwood.ufo");

    instance.watchInBrowser(game).join();

    verify(mapService).optionalEnsureMap("tacc", "SHERWOOD", "deadbeef", "sherwood.ufo", null, null);
  }

  // ── mod version check / auto-update ──────────────────────────────────────────────────────
  // The viewer resolves every unit from the LOCAL install and matches the demo's unit table
  // against it by CRC, so a stale mod misses the fingerprint outright. The browser path used
  // to ensure the map and nothing else.

  @Test
  public void liveGameUpdatesModBeforeEnsuringMapAndOpening() {
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.empty()));
    when(game.getFeaturedModVersion()).thenReturn("abc123");

    instance.watchInBrowser(game).join();

    // the game's OWN recorded mod version wins over the mod's default branch
    verify(gameUpdater).update(taccMod, "abc123");
    // …and it happens first: an update can bring map archives with it
    InOrder order = inOrder(gameUpdater, mapService, platformService);
    order.verify(gameUpdater).update(any(), anyString());
    order.verify(mapService).optionalEnsureMap(any(), any(), any(), any(), any(), any());
    order.verify(platformService).showDocument(anyString());
  }

  @Test
  public void liveGameWithoutRecordedVersionFallsBackToModBranch() {
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.empty()));
    when(game.getFeaturedModVersion()).thenReturn(null);

    instance.watchInBrowser(game).join();

    verify(gameUpdater).update(taccMod, "main");
  }

  @Test
  public void vodReplayWithoutDemoMetaFallsBackToModBranch() {
    Replay replay = org.mockito.Mockito.mock(Replay.class);
    when(replay.getId()).thenReturn(314);
    when(replay.getFeaturedMod()).thenReturn(taccMod);
    when(replay.getMap()).thenReturn(null);
    when(replay.getDemoFileInfo()).thenReturn(null);   // replay predates replayMeta

    instance.watchReplayInBrowser(replay).join();

    verify(gameUpdater).update(taccMod, "main");
    verify(platformService).showDocument(anyString());
  }

  /**
   * The one that matters: an OLD replay must get the build it was played on, not the branch
   * head. Checking out the latest shifts every unit-type rank and the viewer renders the wrong
   * models — observed as "demo 530 unit types vs a 552-unit install, 183/530 crc-matched".
   */
  @Test
  public void vodReplayPinsTheVersionItsUnitsHashNames() {
    Replay replay = org.mockito.Mockito.mock(Replay.class);
    DemoFileInfo demoInfo = new DemoFileInfo(null, "[V] Urban Contention", "mapHash",
        "unitsHash530", 3, 1);
    FeaturedMod pinned = org.mockito.Mockito.mock(FeaturedMod.class);
    FeaturedModVersion playedOn = org.mockito.Mockito.mock(FeaturedModVersion.class);
    when(playedOn.getGitBranch()).thenReturn("v10.0-530units");
    when(pinned.getVersions()).thenReturn(FXCollections.observableArrayList(playedOn));
    when(modService.findFeaturedModByTaDemoFileInfo(demoInfo)).thenReturn(completedFuture(pinned));

    when(replay.getId()).thenReturn(314);
    when(replay.getFeaturedMod()).thenReturn(taccMod);
    when(replay.getMap()).thenReturn(null);
    when(replay.getDemoFileInfo()).thenReturn(demoInfo);

    instance.watchReplayInBrowser(replay).join();

    verify(gameUpdater).update(taccMod, "v10.0-530units");
  }

  /**
   * Clicking watch twice on the same replay used to drain the asset server for 5 s and run a
   * second checkout of a version already checked out — cutting every in-flight read from the
   * tab the first click opened, which on a slow install is the whole boot. A user's log showed
   * exactly that: two clicks 65 s apart, "SETTLED after 1 ms, failed=false", and the server
   * restarted in between.
   */
  @Test
  public void repeatWatchOfThePinnedVersionDoesNotTouchTheInstallAgain() {
    Replay replay = org.mockito.Mockito.mock(Replay.class);
    DemoFileInfo demoInfo = new DemoFileInfo(null, "[V] Urban Contention", "mapHash",
        "unitsHash530", 3, 1);
    FeaturedMod pinned = org.mockito.Mockito.mock(FeaturedMod.class);
    FeaturedModVersion playedOn = org.mockito.Mockito.mock(FeaturedModVersion.class);
    when(playedOn.getGitBranch()).thenReturn("v10.0-530units");
    when(pinned.getVersions()).thenReturn(FXCollections.observableArrayList(playedOn));
    when(modService.findFeaturedModByTaDemoFileInfo(demoInfo)).thenReturn(completedFuture(pinned));

    when(replay.getId()).thenReturn(314);
    when(replay.getFeaturedMod()).thenReturn(taccMod);
    when(replay.getMap()).thenReturn(null);
    when(replay.getDemoFileInfo()).thenReturn(demoInfo);

    instance.watchReplayInBrowser(replay).join();
    instance.watchReplayInBrowser(replay).join();

    verify(gameUpdater, times(1)).update(taccMod, "v10.0-530units");
    verify(localAssetServerService, times(1)).stop(anyInt());
    // …but the viewer still opens both times, on a server that kept its port and token
    verify(platformService, times(2)).showDocument(anyString());
  }

  @Test
  public void vodReplayWithUnknownUnitsHashFallsBackToBranch() {
    Replay replay = org.mockito.Mockito.mock(Replay.class);
    DemoFileInfo demoInfo = new DemoFileInfo(null, "map", "mapHash", "hashNobodyKnows", 3, 1);
    when(modService.findFeaturedModByTaDemoFileInfo(demoInfo)).thenReturn(completedFuture(null));

    when(replay.getId()).thenReturn(314);
    when(replay.getFeaturedMod()).thenReturn(taccMod);
    when(replay.getMap()).thenReturn(null);
    when(replay.getDemoFileInfo()).thenReturn(demoInfo);

    instance.watchReplayInBrowser(replay).join();

    verify(gameUpdater).update(taccMod, "main");
    verify(platformService).showDocument(anyString());
  }

  @Test
  public void uninstalledModIsNotUpdatedButStillOpens() {
    when(preferencesService.isGameExeValid("tacc")).thenReturn(false);
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.empty()));

    instance.watchInBrowser(game).join();

    // updating would mean a full clone off the back of a "watch" click
    verify(gameUpdater, never()).update(any(), any());
    verify(platformService).showDocument(anyString());
  }

  @Test
  public void failedModUpdateStillOpensTheViewer() {
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.empty()));
    CompletableFuture<String> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("git exploded"));
    when(gameUpdater.update(any(), any())).thenReturn(failed);

    instance.watchInBrowser(game).join();

    // a mod that won't update is a degraded view, not a reason to refuse to open
    verify(platformService).showDocument(anyString());
  }

  /**
   * The asset server streams the archives the update is about to replace, and Java holds
   * Windows files without FILE_SHARE_DELETE — a stale viewer tab's in-flight read fails the
   * checkout ("Could not rename ._T2ESC.ufo….tmp to T2ESC.ufo") and half-updates the mod.
   */
  @Test
  public void drainsTheAssetServerBeforeRewritingTheInstall() {
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.empty()));

    instance.watchInBrowser(game).join();

    InOrder order = inOrder(localAssetServerService, gameUpdater);
    order.verify(localAssetServerService).stop(anyInt());   // …with a real drain, not stop(0)
    order.verify(gameUpdater).update(any(), any());
    order.verify(localAssetServerService).ensureRunning();  // restarted for the new tab
  }

  @Test
  public void doesNotDrainWhenThereIsNothingToUpdate() {
    when(preferencesService.isGameExeValid("tacc")).thenReturn(false);
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.empty()));

    instance.watchInBrowser(game).join();

    verify(localAssetServerService, never()).stop(anyInt());
  }

  /**
   * maptool.exe opens every archive under --gamepath, so an enumeration overlapping the
   * checkout fails its rename. MapService's deferral is a shared COUNTER and our own
   * optionalEnsureMap takes/releases one too — releasing it mid-flow drops the count to zero
   * and fires the queued enumeration while the checkout is still running. Hold one across the
   * whole flow so the nested release can never reach zero early.
   */
  @Test
  public void mapEnumerationIsDeferredAcrossTheWholeFlow() {
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.empty()));

    instance.watchInBrowser(game).join();

    InOrder order = inOrder(mapService, gameUpdater, platformService);
    order.verify(mapService).addInstalledMapsUpdateDeferal();
    order.verify(gameUpdater).update(any(), any());
    order.verify(mapService).optionalEnsureMap(any(), any(), any(), any(), any(), any());
    order.verify(platformService).showDocument(anyString());
    order.verify(mapService).releaseInstalledMapsUpdateDeferal();
  }

  @Test
  public void mapEnumerationDeferralIsReleasedEvenWhenTheFlowFails() {
    when(replayService.fetchWatchTicket(game))
        .thenThrow(new IllegalStateException("lobby exploded"));

    instance.watchInBrowser(game).join();

    verify(mapService).addInstalledMapsUpdateDeferal();
    verify(mapService).releaseInstalledMapsUpdateDeferal();
  }

  /**
   * A staging room's install was checked out once, at host/join time; startBattleRoom() does not
   * re-run the updater. So a checkout triggered by a watch click is what would actually launch
   * when the host hits start — never do it.
   */
  @Test
  public void doesNotRewriteTheInstallWhileAStagingRoomIsOpen() {
    when(gameService.isInStagingRoom()).thenReturn(true);
    when(replayService.fetchWatchTicket(game)).thenReturn(completedFuture(Optional.empty()));

    instance.watchInBrowser(game).join();

    verify(gameUpdater, never()).update(any(), any());
    verify(localAssetServerService, never()).stop(anyInt());
    // the viewer still opens; only the update is skipped
    verify(platformService).showDocument(anyString());
  }

  @Test
  public void browserOnlyWhileStagingRoomOpen() {
    when(gameService.isInStagingRoom()).thenReturn(true);
    assertThat(instance.isBrowserOnly(), is(true));
  }

  @Test
  public void notBrowserOnlyWhenIdle() {
    when(gameService.isInStagingRoom()).thenReturn(false);
    assertThat(instance.isBrowserOnly(), is(false));
  }

  @Test
  public void notBrowserOnlyWhenViewerNotConfigured() {
    clientProperties.getLiveViewer().setUrlFormat(null);
    when(gameService.isInStagingRoom()).thenReturn(true);
    assertThat(instance.isBrowserOnly(), is(false));
  }

  @Test
  public void tadaReplayDoesNotUpdateAnyMod() {
    instance.watchTadaReplayInBrowser("k1", "abc123", "my game.tad").join();

    // the mod isn't known until livescene decodes the demo header
    verify(gameUpdater, never()).update(any(), any());
    verify(platformService).showDocument(anyString());
  }
}
