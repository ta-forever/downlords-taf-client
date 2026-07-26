package com.faforever.client.replay;

import com.faforever.client.assetserver.LocalAssetServerService;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.game.Game;
import com.faforever.client.map.MapService;
import com.faforever.client.notification.NotificationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class BrowserWatchServiceTest {

  private BrowserWatchService instance;
  private ClientProperties clientProperties;

  @Mock
  private ReplayService replayService;
  @Mock
  private MapService mapService;
  @Mock
  private LocalAssetServerService localAssetServerService;
  @Mock
  private PlatformService platformService;
  @Mock
  private NotificationService notificationService;
  @Mock
  private Game game;

  @Before
  public void setUp() {
    clientProperties = new ClientProperties();
    clientProperties.getLiveViewer().setUrlFormat("https://www.taforever.com/live/%d");

    when(game.getId()).thenReturn(42);
    when(game.getFeaturedMod()).thenReturn("tacc");
    when(mapService.optionalEnsureMap(any(), any(), any(), any(), any(), any()))
        .thenReturn(completedFuture(null));
    when(localAssetServerService.ensureRunning()).thenReturn("http://127.0.0.1:53211/AbCtoken");

    instance = new BrowserWatchService(clientProperties, replayService, mapService,
        localAssetServerService, platformService, notificationService);
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
}
