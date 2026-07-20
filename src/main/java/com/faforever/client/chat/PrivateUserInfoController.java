package com.faforever.client.chat;

import com.faforever.client.achievements.AchievementService;
import com.faforever.client.api.dto.AchievementState;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameDetailController;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.Leaderboard;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.player.Player;
import com.faforever.client.preferences.DisplayMetric;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.util.IdenticonUtil;
import com.faforever.client.util.RatingUtil;
import com.google.common.eventbus.EventBus;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class PrivateUserInfoController implements Controller<Node> {
  private final I18n i18n;
  private final AchievementService achievementService;
  private final LeaderboardService leaderboardService;
  private final com.faforever.client.leaderboard.RatingTierService ratingTierService;
  private final com.faforever.client.ladder.LadderPointsService ladderPointsService;
  private final PreferencesService preferencesService;
  private final EventBus eventBus;
  private final ChatUserService chatUserService;
  public ImageView userImageView;
  public Label usernameLabel;
  public ImageView countryImageView;
  public Label countryLabel;
  public javafx.scene.layout.VBox ratingsSection;
  public Label ratingsHeaderLabel;
  public javafx.scene.layout.GridPane ratingsGrid;
  public Label gamesPlayedLabel;
  public GameDetailController gameDetailController;
  public Pane gameDetailWrapper;
  public Label unlockedAchievementsLabel;
  public Node privateUserInfoRoot;
  public Label gamesPlayedLabelLabel;
  public Label unlockedAchievementsLabelLabel;
  private ChatChannelUser chatUser;
  /** Kept so the mode pill (Season Ladder ⇄ Skill Rating) can re-render the ratings section live. */
  private Player displayedPlayer;
  private ChangeListener<DisplayMetric> displayMetricListener;

  public PrivateUserInfoController(I18n i18n, AchievementService achievementService, LeaderboardService leaderboardService,
                                   com.faforever.client.leaderboard.RatingTierService ratingTierService,
                                   com.faforever.client.ladder.LadderPointsService ladderPointsService,
                                   PreferencesService preferencesService,
                                   EventBus eventBus, ChatUserService chatUserService) {
    this.i18n = i18n;
    this.achievementService = achievementService;
    this.leaderboardService = leaderboardService;
    this.ratingTierService = ratingTierService;
    this.ladderPointsService = ladderPointsService;
    this.preferencesService = preferencesService;
    this.eventBus = eventBus;
    this.chatUserService = chatUserService;
  }

  @Override
  public Node getRoot() {
    return privateUserInfoRoot;
  }

  public void initialize() {
    JavaFxUtil.bindManagedToVisible(
        gameDetailWrapper,
        countryLabel,
        gamesPlayedLabel,
        unlockedAchievementsLabel,
        ratingsSection,
        gamesPlayedLabelLabel,
        unlockedAchievementsLabelLabel
    );

    // Follow the global mode pill (LADDER_POINTS_DESIGN §13.1): flipping it anywhere re-renders
    // this section between absolute rating and #rank / LP without reopening the tab.
    displayMetricListener = (obs, oldValue, newValue) -> {
      if (displayedPlayer != null) {
        loadReceiverRatingInformation(displayedPlayer);
      }
    };
    JavaFxUtil.addListener(preferencesService.getPreferences().displayMetricProperty(),
        new WeakChangeListener<>(displayMetricListener));

    onPlayerGameChanged(null);
  }

  public void setChatUser(@NotNull ChatChannelUser chatUser) {
    this.chatUser = chatUser;
    this.chatUser.setDisplayed(true);
    this.chatUser.getPlayer().ifPresentOrElse(this::displayPlayerInfo, () -> {
      this.chatUser.playerProperty().addListener((observable, oldValue, newValue) -> {
        if (newValue != null) {
          displayPlayerInfo(newValue);
        }
      });
      displayChatUserInfo();
    });
    JavaFxUtil.bind(usernameLabel.textProperty(), this.chatUser.usernameProperty());
    JavaFxUtil.bind(countryImageView.imageProperty(), this.chatUser.countryFlagProperty());
    JavaFxUtil.bind(countryLabel.textProperty(), this.chatUser.countryNameProperty());
  }

  private void displayChatUserInfo() {
    onPlayerGameChanged(null);
    setPlayerInfoVisible(false);
  }

  private void setPlayerInfoVisible(boolean visible) {
    userImageView.setVisible(visible);
    countryLabel.setVisible(visible);
    ratingsSection.setVisible(visible);
    gamesPlayedLabel.setVisible(visible);
    gamesPlayedLabelLabel.setVisible(visible);
    unlockedAchievementsLabel.setVisible(visible);
    unlockedAchievementsLabelLabel.setVisible(visible);
  }

  ChangeListener<Game> gameChangeListener;
  ChangeListener<ObservableMap<String,LeaderboardRating>> ratingChangeListener;
  private void displayPlayerInfo(Player player) {
    chatUserService.associatePlayerToChatUser(chatUser, player);
    displayedPlayer = player;
    setPlayerInfoVisible(true);

    userImageView.setImage(IdenticonUtil.createIdenticon(player.getId()));
    userImageView.setVisible(true);

    ratingChangeListener = (obs,oldValue,newValue) -> loadReceiverRatingInformation(player);
    player.leaderboardRatingMapProperty().addListener(new WeakChangeListener<>(ratingChangeListener));
    loadReceiverRatingInformation(player);

    gameChangeListener = (obs, oldValue, newValue) -> onPlayerGameChanged(newValue);
    player.gameProperty().addListener(new WeakChangeListener<>(gameChangeListener));
    onPlayerGameChanged(player.getGame());

    JavaFxUtil.bind(gamesPlayedLabel.textProperty(), player.numberOfGamesProperty().asString());

    populateUnlockedAchievementsLabel(player);
  }

  private CompletableFuture<CompletableFuture<Void>> populateUnlockedAchievementsLabel(Player player) {
    return achievementService.getAchievementDefinitions()
        .thenApply(achievementDefinitions -> {
          int totalAchievements = achievementDefinitions.size();
          return achievementService.getPlayerAchievements(player.getId())
              .thenAccept(playerAchievements -> {
                long unlockedAchievements = playerAchievements.stream()
                    .filter(playerAchievement -> playerAchievement.getState() == AchievementState.UNLOCKED)
                    .count();

                JavaFxUtil.runLater(() -> unlockedAchievementsLabel.setText(
                    i18n.get("chat.privateMessage.achievements.unlockedFormat", unlockedAchievements, totalAchievements))
                );
              })
              .exceptionally(throwable -> {
                log.warn("Could not load achievements for player '" + player.getId(), throwable);
                return null;
              });
        });
  }

  private void onPlayerGameChanged(Game newGame) {
    gameDetailController.setGame(newGame);
    gameDetailWrapper.setVisible(newGame != null);
  }

  private void loadReceiverRatingInformation(Player player) {
    // The displayMetric pref swaps only the static gauge (LADDER_POINTS_DESIGN §13.2): Season
    // Ladder shows division + LP; Skill Rating shows the absolute rating rounded to nearest 100.
    boolean lpMode = preferencesService.getPreferences().getDisplayMetric() != DisplayMetric.RATINGS;
    leaderboardService.getLeaderboards()
        .thenCombine(ladderPointsService.getStandingsForPlayer(player.getId()), (leaderboards, standings) -> {
          java.util.List<Map.Entry<String, String>> rows = lpMode
              ? seasonLadderRows(leaderboards, standings)
              : skillRatingRows(player, leaderboards);
          JavaFxUtil.runLater(() -> {
            ratingsHeaderLabel.setText(i18n.get(lpMode
                ? "chat.privateMessage.ladderPoints.header" : "chat.privateMessage.ratings.header"));
            renderRatingRows(rows);
          });
          return null;
        });
  }

  /** One grid row per board: wrapping name in column 0, right-aligned value pinned to its own
   * width in column 1, so the value can never wrap or drift off the name's baseline. */
  private void renderRatingRows(java.util.List<Map.Entry<String, String>> rows) {
    ratingsGrid.getChildren().clear();
    int rowIndex = 0;
    for (Map.Entry<String, String> row : rows) {
      Label name = new Label(row.getKey());
      name.setWrapText(true);
      Label value = new Label(row.getValue());
      value.setWrapText(false);
      value.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
      value.getStyleClass().add("private-chat-user-info-data");
      javafx.scene.layout.GridPane.setValignment(name, javafx.geometry.VPos.TOP);
      javafx.scene.layout.GridPane.setValignment(value, javafx.geometry.VPos.TOP);
      ratingsGrid.add(name, 0, rowIndex);
      ratingsGrid.add(value, 1, rowIndex);
      rowIndex++;
    }
  }

  /** Season Ladder gauge (§13.3): each board's rank + LP, most-played first. Cold start (no
   * standings) shows a single "Unranked" line (§13.5) - never a fabricated value. */
  private java.util.List<Map.Entry<String, String>> seasonLadderRows(
      java.util.List<Leaderboard> leaderboards,
      java.util.List<com.faforever.client.ladder.SeasonStanding> standings) {
    java.util.List<Map.Entry<String, String>> rows = new java.util.ArrayList<>();
    if (standings.isEmpty()) {
      rows.add(Map.entry(i18n.get("leaderboard.toggle.lp.label"), i18n.get("lp.badge.unranked")));
      return rows;
    }
    Map<String, String> boardNames = leaderboards.stream()
        .collect(Collectors.toMap(Leaderboard::getTechnicalName, lb -> i18n.get(lb.getNameKey()), (a, b) -> a));
    standings.stream()
        .sorted(Comparator.comparingInt(com.faforever.client.ladder.SeasonStanding::getGames).reversed())
        .forEach(standing -> rows.add(Map.entry(
            boardNames.getOrDefault(standing.getLeaderboardTechnicalName(),
                standing.getLeaderboardTechnicalName()),
            com.faforever.client.ladder.LadderUiUtil.standingDisplay(i18n, standing))));
    return rows;
  }

  /** Skill Rating gauge (§13.2): absolute rating per board, rounded to nearest 100 so it reads as
   * a stable skill tier rather than game-to-game movement. The board name carries no " Rating"
   * suffix - the section header already says so. */
  private java.util.List<Map.Entry<String, String>> skillRatingRows(Player player,
                                                                    java.util.List<Leaderboard> leaderboards) {
    java.util.List<Map.Entry<String, String>> rows = new java.util.ArrayList<>();
    leaderboards.forEach(leaderboard -> {
      LeaderboardRating leaderboardRating = player.getLeaderboardRatings().get(leaderboard.getTechnicalName());
      if (leaderboardRating != null) {
        // Hysteresis-stabilised tier (§13.2.3) so the number reads as a steady skill tier.
        int tier = ratingTierService.displayTier(player.getId(), leaderboard.getTechnicalName(),
            RatingUtil.getLeaderboardRating(player, leaderboard));
        rows.add(Map.entry(i18n.get(leaderboard.getNameKey()), i18n.number(tier)));
      }
    });
    return rows;
  }
}

