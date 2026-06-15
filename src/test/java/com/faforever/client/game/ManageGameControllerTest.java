package com.faforever.client.game;

import com.faforever.client.i18n.I18n;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.theme.UiService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ManageGameControllerTest extends AbstractPlainJavaFxTest {

  private ManageGameController instance;
  @Mock
  private UiService uiService;
  @Mock
  private GameService gameService;
  @Mock
  private PlayerService playerService;
  @Mock
  private I18n i18n;
  @Mock
  private Game game;
  @Mock
  private ManualTeamArrangementController teamController;
  @Mock
  private ReservedPlayersEditorController reservedController;

  private ObservableList<Integer> reservedIds;

  @Before
  public void setUp() throws IOException {
    instance = new ManageGameController(uiService, gameService, playerService, i18n);

    reservedIds = FXCollections.observableArrayList(7, 8);

    when(game.getId()).thenReturn(42);
    when(game.getHost()).thenReturn("hostguy");
    when(game.getMaxPlayers()).thenReturn(8);
    when(game.getReservedPlayerIds()).thenReturn(FXCollections.observableArrayList());
    when(game.getReservedPlayers()).thenReturn(FXCollections.observableArrayList());
    when(playerService.getPlayerForUsername("hostguy")).thenReturn(Optional.<Player>empty());
    when(i18n.get(anyString())).thenReturn("");

    when(uiService.loadFxml("theme/play/manual_team_arrangement.fxml")).thenReturn(teamController);
    when(teamController.getRoot()).thenReturn(new VBox());
    when(uiService.loadFxml("theme/play/reserved_players_editor.fxml")).thenReturn(reservedController);
    when(reservedController.getRoot()).thenReturn(new VBox());
    when(reservedController.maxPlayersProperty()).thenReturn(new SimpleIntegerProperty(8));
    when(reservedController.getReservedPlayerIds()).thenReturn(reservedIds);

    loadFxml("theme/play/manage_game.fxml", param -> instance);
  }

  @Test
  public void buildsBothTabsWhenReservedEnabled() {
    when(game.isReservedSlotsEnabled()).thenReturn(true);
    runOnFxThreadAndWait(() -> instance.setGame(game));
    assertThat(instance.contentPane.getChildren().size(), is(2));
    assertThat(instance.tabBar.isVisible(), is(true));
    assertThat(instance.reservedToggle.isVisible(), is(true));
  }

  @Test
  public void buildsOnlyTeamsTabWhenReservedDisabled() {
    when(game.isReservedSlotsEnabled()).thenReturn(false);
    runOnFxThreadAndWait(() -> instance.setGame(game));
    assertThat(instance.contentPane.getChildren().size(), is(1));
    assertThat(instance.tabBar.isVisible(), is(false));
    assertThat(instance.teamsToggle.isSelected(), is(true));
  }

  @Test
  public void saveCommitsTeamsAndReserved() {
    when(game.isReservedSlotsEnabled()).thenReturn(true);
    when(teamController.commit()).thenReturn(true);
    boolean[] closed = {false};
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.setOnClose(() -> closed[0] = true);
      instance.onSave();
    });
    verify(teamController).commit();
    verify(gameService).sendReservedPlayers(eq(List.of(7, 8)));
    assertThat(closed[0], is(true));
  }

  @Test
  public void saveBlockedWhenTeamsInfeasible() {
    when(game.isReservedSlotsEnabled()).thenReturn(true);
    when(teamController.commit()).thenReturn(false);
    boolean[] closed = {false};
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.setOnClose(() -> closed[0] = true);
      instance.onSave();
    });
    verify(gameService, never()).sendReservedPlayers(org.mockito.ArgumentMatchers.any());
    assertThat(closed[0], is(false));
  }
}
