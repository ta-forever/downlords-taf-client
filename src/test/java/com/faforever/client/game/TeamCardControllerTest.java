package com.faforever.client.game;

import com.faforever.client.galacticwar.GalacticWarService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.ladder.LadderPointsService;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.replay.Replay.PlayerStats;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.theme.UiService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.control.Label;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TeamCardControllerTest extends AbstractPlainJavaFxTest {
  private TeamCardController instance;
  @Mock
  private Player player;
  @Mock
  private I18n i18n;
  @Mock
  private UiService uiService;
  @Mock
  private PlayerService playerService;
  @Mock
  private GalacticWarService galacticWarService;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private Preferences preferences;
  @Mock
  private LadderPointsService ladderPointsService;
  // Not @Mock: creating these mocks triggers static class initialization (PlayerCardTooltipController
  // builds a static Image), which needs the JavaFX toolkit. @Mock fields are created before the
  // TestFX @Before that starts the toolkit, so we instantiate them in setUp() instead.
  private PlayerCardTooltipController playerCardTooltipController;
  private RatingChangeLabelController ratingChangeLabelController;

  private ArrayList<Player> playerList;
  private ObservableMap<String, List<PlayerStats>> teams;
  private PlayerStats playerStats;

  @Before
  public void setUp() throws IOException {
    playerCardTooltipController = mock(PlayerCardTooltipController.class);
    ratingChangeLabelController = mock(RatingChangeLabelController.class);
    instance = new TeamCardController(uiService, playerService, galacticWarService, i18n, preferencesService, ladderPointsService);
    when(preferencesService.getPreferences()).thenReturn(preferences);
    playerList = new ArrayList<>();
    playerList.add(player);
    teams = FXCollections.observableHashMap();

    when(uiService.loadFxml("theme/player_card_tooltip.fxml")).thenReturn(playerCardTooltipController);
    when(uiService.loadFxml("theme/rating_change_label.fxml")).thenReturn(ratingChangeLabelController);
    when(playerCardTooltipController.getRoot()).thenReturn(new Label());
    when(ratingChangeLabelController.getRoot()).thenReturn(new Label());
    when(player.getId()).thenReturn(1);

    playerStats = PlayerStats.builder()
        .playerId(1)
        .beforeMean(1000.0)
        .beforeDeviation(0.0)
        .afterMean(1100.0)
        .afterMean(0.0)
        .score(0)
        .faction(null)
        .build();

    teams.put("2", Collections.singletonList(playerStats));

    loadFxml("theme/team_card.fxml", param -> instance);
  }

  @Test
  public void setPlayersInTeam() {
    instance.setPlayersInTeam("2", playerList, player -> LeaderboardRating.create(1000f, 0f), null, null, RatingPrecision.ROUNDED, true);
    // hidePlayerRatings=true, so the team title omits the total rating and uses the "replay.team" key.
    verify(i18n).get("replay.team", 1);
  }

  @Test
  public void showRatingChange() {
    instance.setPlayersInTeam("2", playerList, player -> LeaderboardRating.create(1000f, 0f), null, null, RatingPrecision.EXACT, true);
    instance.showRatingChange(teams);
    verify(ratingChangeLabelController).setRatingChange(playerStats);
  }

  @Test
  public void socialStatusIconsHiddenByDefault() {
    when(preferences.isShowFriendFoeInTeamCards()).thenReturn(false);
    instance.setPlayersInTeam("2", playerList, player -> LeaderboardRating.create(1000f, 0f), null, null, RatingPrecision.ROUNDED, true);
    verify(playerCardTooltipController).hideSocialStatusIcons();
  }

  @Test
  public void socialStatusIconsShownWhenEnabled() {
    when(preferences.isShowFriendFoeInTeamCards()).thenReturn(true);
    instance.setPlayersInTeam("2", playerList, player -> LeaderboardRating.create(1000f, 0f), null, null, RatingPrecision.ROUNDED, true);
    verify(playerCardTooltipController, never()).hideSocialStatusIcons();
  }

  @Test
  public void playingStatusIconFallbackAppliedWhenEnabled() {
    instance.setShowPlayingStatusIconFallback(true);
    instance.setPlayersInTeam("2", playerList, player -> LeaderboardRating.create(1000f, 0f), null, null, RatingPrecision.ROUNDED, true);
    verify(playerCardTooltipController).showPlayingStatusIconIfNoIcons();
  }

  @Test
  public void playingStatusIconFallbackOffByDefault() {
    instance.setPlayersInTeam("2", playerList, player -> LeaderboardRating.create(1000f, 0f), null, null, RatingPrecision.ROUNDED, true);
    verify(playerCardTooltipController, never()).showPlayingStatusIconIfNoIcons();
  }

}
