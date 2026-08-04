package com.faforever.client.vault.replay;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.game.Game;
import com.faforever.client.i18n.I18n;
import com.faforever.client.replay.BrowserWatchService;
import com.faforever.client.replay.ReplayService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.util.TimeService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class WatchButtonControllerTest extends AbstractPlainJavaFxTest {

  private WatchButtonController instance;

  @Mock
  private ReplayService replayService;
  @Mock
  private BrowserWatchService browserWatchService;
  @Mock
  private TimeService timeService;
  @Mock
  private I18n i18n;
  @Mock
  private Game game;

  @Before
  public void setUp() {
    when(i18n.get("game.watch")).thenReturn("Watch");
    when(i18n.get("game.watch.inGame")).thenReturn("Watch in game");
    when(i18n.get("game.watch.inBrowser")).thenReturn("Watch in browser");

    // A game whose watch delay has already elapsed.
    when(game.getStartTime()).thenReturn(Instant.now().minusSeconds(400));
    when(game.getReplayDelaySeconds()).thenReturn(300);
    when(game.getTeams()).thenReturn(FXCollections.observableHashMap());

    instance = new WatchButtonController(replayService, browserWatchService, new ClientProperties(), timeService, i18n);
    instance.watchButton = new Button();
  }

  private void initialize() {
    runOnFxThreadAndWait(() -> instance.initialize());
  }

  /** The menu is built on click, not at initialize(), because its contents depend on live state. */
  private void openMenu() {
    runOnFxThreadAndWait(() -> instance.populateWatchMenu());
  }

  @Test
  public void menuContainsBothItemsWhenBrowserViewerConfigured() {
    when(browserWatchService.isAvailable()).thenReturn(true);
    initialize();
    openMenu();

    assertThat(instance.getWatchMenu().getItems(), hasSize(2));
  }

  @Test
  public void menuOmitsBrowserItemWhenViewerNotConfigured() {
    when(browserWatchService.isAvailable()).thenReturn(false);
    initialize();
    openMenu();

    assertThat(instance.getWatchMenu().getItems(), hasSize(1));
  }

  @Test
  public void inGameItemStartsClassicLiveReplay() {
    when(browserWatchService.isAvailable()).thenReturn(true);
    initialize();
    runOnFxThreadAndWait(() -> instance.setGame(game));
    openMenu();

    instance.getWatchMenu().getItems().get(0).getOnAction().handle(new ActionEvent());

    verify(replayService).runLiveReplay(game);
  }

  @Test
  public void browserItemOpensBrowserViewer() {
    when(browserWatchService.isAvailable()).thenReturn(true);
    initialize();
    runOnFxThreadAndWait(() -> instance.setGame(game));
    openMenu();

    instance.getWatchMenu().getItems().get(1).getOnAction().handle(new ActionEvent());

    verify(browserWatchService).watchInBrowser(game);
  }

  /**
   * A player waiting in a staging room can't launch a second TA, so the in-game flavour is not
   * offered at all — the browser one is the whole menu.
   */
  @Test
  public void menuOmitsInGameItemWhileStagingRoomOpen() {
    when(browserWatchService.isAvailable()).thenReturn(true);
    when(browserWatchService.isBrowserOnly()).thenReturn(true);
    initialize();
    runOnFxThreadAndWait(() -> instance.setGame(game));
    openMenu();

    assertThat(instance.getWatchMenu().getItems(), hasSize(1));
    assertThat(instance.getWatchMenu().getItems().get(0).getText(), is("Watch in browser"));

    instance.getWatchMenu().getItems().get(0).getOnAction().handle(new ActionEvent());
    verify(browserWatchService).watchInBrowser(game);
  }

  @Test
  public void buttonEnabledOnceDelayElapsed() {
    when(browserWatchService.isAvailable()).thenReturn(true);
    initialize();

    assertThat(instance.watchButton.isDisabled(), is(true));
    runOnFxThreadAndWait(() -> instance.setGame(game));
    assertThat(instance.watchButton.isDisabled(), is(false));
    assertThat(instance.watchButton.getText(), is("Watch"));
  }
}
