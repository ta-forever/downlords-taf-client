package com.faforever.client.game;

import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.player.Player;
import com.faforever.client.rating.RatingService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.theme.UiService;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ManualTeamArrangementControllerTest extends AbstractPlainJavaFxTest {

  private ManualTeamArrangementController instance;
  @Mock
  private UiService uiService;
  @Mock
  private RatingService ratingService;
  @Mock
  private GameService gameService;
  @Mock
  private I18n i18n;
  @Mock
  private Game game;

  private List<Player> roster;

  @Before
  public void setUp() throws IOException {
    instance = new ManualTeamArrangementController(uiService, ratingService, gameService, i18n);

    roster = new ArrayList<>();
    for (int i = 1; i <= 4; i++) {
      Player p = new Player(new com.faforever.client.remote.domain.Player());
      p.setId(i);
      p.setUsername("p" + i);
      roster.add(p);
    }

    when(game.getId()).thenReturn(42);
    when(game.getRatingType()).thenReturn("global");
    when(game.getTeams()).thenReturn(FXCollections.observableHashMap());
    when(ratingService.getBalancedTeams(game)).thenReturn(roster);
    // Any constrained solve here is "feasible" — returns the roster order.
    when(ratingService.getBalancedTeams(eq(game), any())).thenReturn(roster);
    when(ratingService.createNewLeaderboardRating()).thenReturn(LeaderboardRating.create(1000f, 100f));
    when(gameService.getManualTeams(42)).thenReturn(null);
    when(i18n.get(anyString())).thenReturn("");
    when(i18n.get(anyString(), any(), any())).thenReturn("");
    when(i18n.get(anyString(), any(), any(), any())).thenReturn("");
    when(i18n.get(anyString(), any(), any(), any(), any())).thenReturn("");

    when(uiService.loadFxml("theme/player_card_tooltip.fxml")).thenAnswer(invocation -> {
      PlayerCardTooltipController card = Mockito.mock(PlayerCardTooltipController.class);
      when(card.getRoot()).thenReturn(new Label());
      return card;
    });

    loadFxml("theme/play/manual_team_arrangement.fxml", param -> instance);
  }

  @Test
  public void allPlayersStartUnassigned() {
    runOnFxThreadAndWait(() -> instance.setGame(game));
    assertThat(instance.team0Box.getChildren().size(), is(0));
    assertThat(instance.team1Box.getChildren().size(), is(0));
    assertThat(instance.poolBox.getChildren().size(), is(4));
  }

  @Test
  public void pinningMovesPlayerOutOfPool() {
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.assignPlayer(roster.get(0), 0);
      instance.assignPlayer(roster.get(1), 1);
    });
    assertThat(instance.team0Box.getChildren().size(), is(1));
    assertThat(instance.team1Box.getChildren().size(), is(1));
    assertThat(instance.poolBox.getChildren().size(), is(2));
  }

  @Test
  public void commitStoresOnlyPinnedPlayers() {
    final boolean[] committed = new boolean[1];
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.assignPlayer(roster.get(0), 0);
      instance.assignPlayer(roster.get(1), 1);
      committed[0] = instance.commit();
    });

    assertThat(committed[0], is(true));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<Integer, Integer>> captor = ArgumentCaptor.forClass(Map.class);
    verify(gameService).setManualTeams(eq(game), captor.capture());
    Map<Integer, Integer> pins = captor.getValue();
    assertThat(pins.size(), is(2));
    assertThat(pins.get(1), is(0));
    assertThat(pins.get(2), is(1));
  }

  @Test
  public void seedsExistingPinsFromGameService() {
    when(gameService.getManualTeams(42)).thenReturn(Map.of(1, 0, 3, 1));
    runOnFxThreadAndWait(() -> instance.setGame(game));
    assertThat(instance.team0Box.getChildren().size(), is(1));
    assertThat(instance.team1Box.getChildren().size(), is(1));
    assertThat(instance.poolBox.getChildren().size(), is(2));
  }

  @Test
  public void commitAlwaysStoresEvenWhenLopsided() {
    // Pins are soft now: Save never rejects, the balancer relaxes at calc time.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<Integer, Integer>> captor = ArgumentCaptor.forClass(Map.class);
    final boolean[] committed = new boolean[]{false};
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.assignPlayer(roster.get(0), 0);
      instance.assignPlayer(roster.get(1), 0);
      instance.assignPlayer(roster.get(2), 0);   // 3 of 4 pinned to Team 1
      committed[0] = instance.commit();
    });
    assertThat(committed[0], is(true));
    verify(gameService).setManualTeams(eq(game), captor.capture());
    assertThat(captor.getValue().size(), is(3));
  }

  @Test
  public void hostIsAlwaysPinnedToTeam1() {
    when(game.getHost()).thenReturn("p1");
    boolean[] committed = {false};
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      committed[0] = instance.commit();
    });
    assertThat(instance.team0Box.getChildren().size(), is(1));
    assertThat(instance.poolBox.getChildren().size(), is(3));
    assertThat(committed[0], is(true));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<Integer, Integer>> captor = ArgumentCaptor.forClass(Map.class);
    verify(gameService).setManualTeams(eq(game), captor.capture());
    assertThat(captor.getValue().get(1), is(0));
  }

  @Test
  public void hostCannotBeMovedOutOfTeam1() {
    when(game.getHost()).thenReturn("p1");
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.assignPlayer(roster.get(0), 2);   // try to drop the host into the pool
      instance.assignPlayer(roster.get(0), 1);   // ...or Team 2
    });
    assertThat(instance.team0Box.getChildren().size(), is(1));
    assertThat(instance.team1Box.getChildren().size(), is(0));
  }

  @Test
  public void commitSendsMaxPlayersWhenChangedWhileStaging() {
    when(game.getStatus()).thenReturn(com.faforever.client.remote.domain.GameStatus.STAGING);
    when(game.getMaxPlayers()).thenReturn(8);
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.maxPlayersSpinner.getValueFactory().setValue(6);
      instance.commit();
    });
    verify(gameService).setMaxPlayersForStagingGame(6);
  }

  @Test
  public void commitDoesNotSendMaxPlayersWhenUnchanged() {
    when(game.getStatus()).thenReturn(com.faforever.client.remote.domain.GameStatus.STAGING);
    when(game.getMaxPlayers()).thenReturn(8);
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.commit();
    });
    verify(gameService, never()).setMaxPlayersForStagingGame(org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  public void autoFillMovesPoolPlayersIntoBalancedTeams() {
    // Mock balancer returns roster interleaved: p1,p3 -> Team 1; p2,p4 -> Team 2.
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);   // all 4 start unassigned (no host stubbed)
      instance.onAutoFill();
    });
    assertThat(instance.poolBox.getChildren().size(), is(0));
    assertThat(instance.team0Box.getChildren().size(), is(2));
    assertThat(instance.team1Box.getChildren().size(), is(2));
  }

  @Test
  public void autoFillAdoptsBalancerResultAndEmptiesPool() {
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.assignPlayer(roster.get(0), 0);   // pin p1 to Team 1
      instance.onAutoFill();
    });
    assertThat(instance.poolBox.getChildren().size(), is(0));
    assertThat(instance.team0Box.getChildren().size() + instance.team1Box.getChildren().size(), is(4));
  }

  @Test
  public void autoFillRebalancesUnevenPins() {
    // Host pins 3 of 4 players onto Team 1 (uneven). Auto-fill must adopt the
    // balancer's recommendation, unpinning enough to make the teams even.
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.assignPlayer(roster.get(0), 0);
      instance.assignPlayer(roster.get(1), 0);
      instance.assignPlayer(roster.get(2), 0);   // 3 on Team 1, 1 unassigned
      instance.onAutoFill();
    });
    assertThat(instance.poolBox.getChildren().size(), is(0));
    // Balancer result (mock returns the 4-player roster interleaved) -> 2 / 2.
    assertThat(instance.team0Box.getChildren().size(), is(2));
    assertThat(instance.team1Box.getChildren().size(), is(2));
  }

  @Test
  public void reconcileLeavesManualPlacementsWhenRosterUnchanged() {
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.assignPlayer(roster.get(0), 0);   // host-less here; move p1 to Team 1
      instance.reconcileRoster();                 // same roster -> must not disturb
    });
    assertThat(instance.team0Box.getChildren().size(), is(1));
    assertThat(instance.poolBox.getChildren().size(), is(3));
  }

  @Test
  public void reconcileDropsDepartedPlayer() {
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);                 // 4 players, all in pool
      // p4 leaves the game.
      when(ratingService.getBalancedTeams(game)).thenReturn(roster.subList(0, 3));
      instance.reconcileRoster();
    });
    assertThat(instance.poolBox.getChildren().size(), is(3));
  }

  @Test
  public void reconcileAddsNewJoinerToPool() {
    Player p5 = new Player(new com.faforever.client.remote.domain.Player());
    p5.setId(5);
    p5.setUsername("p5");
    List<Player> grown = new ArrayList<>(roster);
    grown.add(p5);
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      when(ratingService.getBalancedTeams(game)).thenReturn(grown);
      instance.reconcileRoster();
    });
    assertThat(instance.poolBox.getChildren().size(), is(5));
  }

  @Test
  public void reconcilePreservesPinnedPlayers() {
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.assignPlayer(roster.get(0), 0);   // pin p1 to Team 1
      // p4 leaves; p1 must stay pinned.
      when(ratingService.getBalancedTeams(game)).thenReturn(roster.subList(0, 3));
      instance.reconcileRoster();
    });
    assertThat(instance.team0Box.getChildren().size(), is(1));
    assertThat(instance.poolBox.getChildren().size(), is(2));
  }

  @Test
  public void clearMovesEveryoneBackToPool() {
    runOnFxThreadAndWait(() -> {
      instance.setGame(game);
      instance.assignPlayer(roster.get(0), 0);
      instance.assignPlayer(roster.get(1), 1);
      instance.onClear();
    });
    assertThat(instance.team0Box.getChildren().size(), is(0));
    assertThat(instance.team1Box.getChildren().size(), is(0));
    assertThat(instance.poolBox.getChildren().size(), is(4));
  }
}
