package com.faforever.client.replay;

import com.faforever.client.api.dto.Validity;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.fa.DemoFileInfo;
import com.faforever.client.fx.DefaultImageView;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.StringCell;
import com.faforever.client.galacticwar.GalacticWarService;
import com.faforever.client.game.Faction;
import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.game.RatingPrecision;
import com.faforever.client.game.TeamCardController;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.Leaderboard;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.DisplayMetric;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.rating.RatingService;
import com.faforever.client.replay.Replay.ChatMessage;
import com.faforever.client.replay.Replay.GameOption;
import com.faforever.client.replay.Replay.PlayerStats;
import com.faforever.client.reporting.ReportDialogController;
import com.faforever.client.theme.UiService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.ClipboardUtil;
import com.faforever.client.util.RatingUtil;
import com.faforever.client.util.TimeService;
import com.faforever.client.vault.review.Review;
import com.faforever.client.vault.review.ReviewService;
import com.faforever.client.vault.review.ReviewsController;
import com.faforever.commons.io.Bytes;
import com.google.common.annotations.VisibleForTesting;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.faforever.client.leaderboard.LeaderboardService.DEFAULT_RATING_TYPE;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
@RequiredArgsConstructor
public class ReplayDetailController implements Controller<Node> {

  private final TimeService timeService;
  private final I18n i18n;
  private final UiService uiService;
  private final ReplayService replayService;
  private final RatingService ratingService;
  private final MapService mapService;
  private final PlayerService playerService;
  private final ClientProperties clientProperties;
  private final NotificationService notificationService;
  private final ReviewService reviewService;
  private final UserService userService;
  private final GalacticWarService galacticWarService;
  private final PreferencesService preferencesService;
  private final com.faforever.client.ladder.LadderPointsService ladderPointsService;
  private final ArrayList<TeamCardController> teamCardControllers = new ArrayList<>();
  /** Re-renders the team cards (rating ⇄ ladder rank) when the global display-metric pill flips. */
  private ChangeListener<DisplayMetric> displayMetricListener;
  public javafx.scene.layout.VBox combatRewardsContainer;
  /** Root node of the included Season Ladder ⇄ Skill Rating pill; hidden for unranked (global /
   * hidden) boards, where neither metric is meaningful. Injected by the {@code fx:include}. */
  public Node displayMetricToggle;
  public Pane replayDetailRoot;
  public Label titleLabel;
  public Button copyButton;
  public Label dateLabel;
  public Label timeLabel;
  public Label modLabel;
  public Label durationLabel;
  public Label ratingLabel;
  public Label qualityLabel;
  public Label onMapLabel;
  public Pane teamsInfoBox;
  public Pane teamsContainer;
  public Separator ratingSeparator;
  public Pane reviewsContainer;
  public ReviewsController reviewsController;
  public Separator reviewSeparator;
  public TableView<ChatMessage> chatTable;
  public TableColumn<ChatMessage, Duration> chatGameTimeColumn;
  public TableColumn<ChatMessage, String> chatSenderColumn;
  public TableColumn<ChatMessage, String> chatMessageColumn;
  public TableView<GameOption> optionsTable;
  public TableColumn<GameOption, String> optionKeyColumn;
  public TableColumn<GameOption, String> optionValueColumn;
  public Pane moreInformationPane;
  public DefaultImageView mapThumbnailImageView;
  public Node replayAvailableContainer;
  public Button watchButton;
  public Button tadaUploadButton;
  public TextField replayIdField;
  public ScrollPane scrollPane;
  public ToggleButton viewBattleReportButton;
  public Button reportButton;
  public Label notRatedReasonLabel;
  /** "Show rating change" — reveals the legacy MMR delta per player in the team cards. Shown only
   * for a rated replay on a non-hidden (ranked) board AND while the {@code showLegacyRating}
   * cutover flag is on. */
  public Button showRatingChangeButton;
  /** Whether this replay's board is hidden (global / just-for-fun) — no MMR delta is offered then. */
  private boolean ratingBoardHidden;
  public Label ratingTypeLabel;
  public Label visibilityLabel;
  public Button unhideButton;
  private Replay replay;
  private ObservableMap<String, List<PlayerStats>> teams;
  /** Whether the battle report (Combat Score → LP + medals) has loaded non-empty data for this game;
   * the report is only shown when this is true and the toggle is on. */
  private boolean hasRewardData;

  public void initialize() {
    JavaFxUtil.addLabelContextMenus(uiService, onMapLabel, titleLabel);
    JavaFxUtil.fixScrollSpeed(scrollPane);

    mapThumbnailImageView.setDefaultImage(uiService.getThemeImage(UiService.UNKNOWN_MAP_IMAGE));

    chatGameTimeColumn.setCellValueFactory(param -> param.getValue().timeProperty());
    chatGameTimeColumn.setCellFactory(param -> new StringCell<>(timeService::asHms));

    chatSenderColumn.setCellValueFactory(param -> param.getValue().senderProperty());
    chatSenderColumn.setCellFactory(param -> new StringCell<>(String::toString));

    chatMessageColumn.setCellValueFactory(param -> param.getValue().messageProperty());
    chatMessageColumn.setCellFactory(param -> new StringCell<>(String::toString));

    optionKeyColumn.setCellValueFactory(param -> param.getValue().keyProperty());
    optionKeyColumn.setCellFactory(param -> new StringCell<>(String::toString));

    optionValueColumn.setCellValueFactory(param -> param.getValue().valueProperty());
    optionValueColumn.setCellFactory(param -> new StringCell<>(String::toString));

    JavaFxUtil.bindManagedToVisible(moreInformationPane, teamsInfoBox,
        reviewsContainer, ratingSeparator, reviewSeparator, getRoot());

    replayDetailRoot.setOnKeyPressed(keyEvent -> {
      if (keyEvent.getCode() == KeyCode.ESCAPE) {
        onCloseButtonClicked();
      }
    });

    reviewsController.getRoot().setMaxSize(Integer.MAX_VALUE, Integer.MAX_VALUE);

    copyButton.setText(i18n.get("replay.copyUrl"));

    dateLabel.setTooltip(new Tooltip(i18n.get("replay.dateTooltip")));
    timeLabel.setTooltip(new Tooltip(i18n.get("replay.timeTooltip")));
    modLabel.setTooltip(new Tooltip(i18n.get("replay.modTooltip")));
    durationLabel.setTooltip(new Tooltip(i18n.get("replay.durationTooltip")));
    ratingLabel.setTooltip(new Tooltip(i18n.get("replay.ratingTooltip")));
    ratingTypeLabel.setTooltip(new Tooltip(i18n.get("leaderboard.displayName")));
    qualityLabel.setTooltip(new Tooltip(i18n.get("replay.qualityTooltip")));
    notRatedReasonLabel.managedProperty().bind(notRatedReasonLabel.visibleProperty());
    showRatingChangeButton.managedProperty().bind(showRatingChangeButton.visibleProperty());

    // The team cards show a skill rating or a ladder rank depending on the global pill (one of which
    // is included in this dialog's header); rebuild them live when it flips.
    displayMetricListener = (obs, oldValue, newValue) -> {
      if (teams != null) {
        populateTeamsContainer();
      }
    };
    JavaFxUtil.addListener(preferencesService.getPreferences().displayMetricProperty(),
        new WeakChangeListener<>(displayMetricListener));
  }

  public void setReplay(Replay replay) {
    this.replay = replay;
    replayAvailableContainer.setDisable(false);

    replayIdField.setText(i18n.get("game.idFormat", replay.getId()));
    if (playerService.isFoe(replay.getHostId())) {
      titleLabel.setText(String.format("%s's Game",
          replay.getTeams().values().stream().findFirst().get().stream().findFirst().get()));
    }
    else {
      titleLabel.textProperty().bind(replay.titleProperty());
    }
    dateLabel.setText(timeService.asDate(replay.getStartTime()));
    timeLabel.setText(timeService.asShortTime(replay.getStartTime()));

    visibilityLabel.visibleProperty().bind(replay.replayHiddenProperty());
    visibilityLabel.managedProperty().bind(visibilityLabel.visibleProperty());

    unhideButton.visibleProperty().bind(replay.replayHiddenProperty().and(
        replay.hostIdProperty().isEqualTo(userService.getUserId())));
    unhideButton.managedProperty().bind(unhideButton.visibleProperty());

    replayIdField.visibleProperty().bind(replay.replayHiddenProperty().not());
    replayIdField.managedProperty().bind(replayIdField.visibleProperty());

    copyButton.visibleProperty().bind(replay.replayHiddenProperty().not());
    copyButton.managedProperty().bind(copyButton.visibleProperty());

    tadaUploadButton.visibleProperty().bind(Bindings.createBooleanBinding(
        () -> replayService.uploadReplayToTadaPermitted(replay) && !replay.getReplayHidden(),
        replay.replayHiddenProperty()));
    tadaUploadButton.managedProperty().bind(tadaUploadButton.visibleProperty());

    Optional<MapBean> optionalMap = Optional.ofNullable(replay.getMap());
    Optional<DemoFileInfo> optionalDemoFileInfo = Optional.ofNullable(replay.getDemoFileInfo());
    if (optionalMap.isPresent()) {
      MapBean map = optionalMap.get();
      Image image = mapService.loadPreview(KnownFeaturedMod.DEFAULT.getTechnicalName(), map.getMapName(), PreviewType.MINI, 10);
      mapThumbnailImageView.setBackgroundLoadingImage(image);
      onMapLabel.setText(i18n.get("game.onMapFormat", map.getMapName()));
    } else if (optionalDemoFileInfo.isPresent() ) {
      Image image = mapService.loadPreview(KnownFeaturedMod.DEFAULT.getTechnicalName(), replay.getDemoFileInfo().getMapName(), PreviewType.MINI, 10);
      mapThumbnailImageView.setBackgroundLoadingImage(image);
      onMapLabel.setText(i18n.get("game.onMapFormat", replay.getDemoFileInfo().getMapName()));
    } else {
      mapThumbnailImageView.setBackgroundLoadingImage(null);
      onMapLabel.setText(i18n.get("game.onUnknownMap"));
    }

    OffsetDateTime endTime = replay.getEndTime();
    if (endTime != null) {
      durationLabel.setText(timeService.shortDuration(Duration.between(replay.getStartTime(), endTime)));
    } else {
      durationLabel.setVisible(false);
    }

    modLabel.setText(
        Optional.ofNullable(replay.getFeaturedMod())
            .map(FeaturedMod::getDisplayName)
            .orElseGet(() -> i18n.get("unknown"))
    );

    double gameQuality = ratingService.calculateQuality(replay);
    if (!Double.isNaN(gameQuality)) {
      qualityLabel.setText(i18n.get("percentage", Math.round(gameQuality * 100)));
    } else {
      qualityLabel.setText(i18n.get("gameQuality.undefined"));
    }

    replay.getTeamPlayerStats().values().stream()
        .flatMapToInt(playerStats -> playerStats.stream().filter(stats -> stats.getBeforeMean() != null && stats.getBeforeDeviation() != null)
            .mapToInt(stats -> RatingUtil.getRating(stats.getBeforeMean(), stats.getBeforeDeviation())))
        .average()
        .ifPresentOrElse(averageRating -> ratingLabel.setText(i18n.number((int) averageRating)),
            () -> ratingLabel.setText("-"));

    ratingLabel.setVisible(false);
    ratingLabel.managedProperty().bind(ratingLabel.visibleProperty());
    ratingTypeLabel.setText("-");
    // No board (or a hidden / global "just for fun" board) → neither rating nor ladder rank is
    // meaningful, so don't offer the metric pill. Defaults hidden; shown only for a ranked board.
    displayMetricToggle.setManaged(false);
    displayMetricToggle.setVisible(false);
    replay.getTeamPlayerStats().values().stream().findAny()
        .flatMap(playerStatsList -> playerStatsList.stream().findAny())
        .flatMap(playerStats -> Optional.ofNullable(playerStats.getLeaderboard()))
        .ifPresent(leaderboard -> {
          ratingTypeLabel.setText(i18n.get(leaderboard.getNameKey()));
          ratingTypeLabel.setVisible(!DEFAULT_RATING_TYPE.equals(leaderboard.getTechnicalName()));
          boolean hidden = leaderboard.getLeaderboardHidden();
          ratingBoardHidden = hidden;
          ratingLabel.setVisible(!hidden);
          displayMetricToggle.setVisible(!hidden);
          displayMetricToggle.setManaged(!hidden);
        });

    if (replay.getReplayFile() == null) {
      if (replay.getReplayAvailable()) {
        replayService.getSize(replay.getId())
            .thenAccept(replaySize -> JavaFxUtil.runLater(() -> {
              String humanReadableSize = Bytes.formatSize(replaySize, i18n.getUserSpecificLocale());
              watchButton.setText(i18n.get("game.watchButtonFormat", humanReadableSize));
            }));
      } else {
        if (replay.getStartTime().isBefore(OffsetDateTime.now().minusDays(1))) {
          watchButton.setText(i18n.get("game.replayFileMissing"));
        } else {
          watchButton.setText(i18n.get("game.replayNotAvailable"));
        }
        replayAvailableContainer.setDisable(true);
      }
      Optional<Player> currentPlayer = playerService.getCurrentPlayer();
      Assert.state(currentPlayer.isPresent(), "No user is logged in");

      reviewsController.setOnSendReviewListener(this::onSendReview);
      reviewsController.setOnDeleteReviewListener(this::onDeleteReview);
      reviewsController.setReviews(replay.getReviews());
      reviewsController.setOwnReview(replay.getReviews().stream()
          .filter(review -> review.getPlayer().equals(currentPlayer.get()))
          .findFirst());

      // Game Options + Chat (moreInformationPane) stay hidden.
      teams = replay.getTeamPlayerStats();
      populateTeamsContainer();
      // The battle report starts open for participants, closed for spectators; the toggle reveals it
      // either way. Data loads regardless so toggling on always works.
      viewBattleReportButton.setSelected(isCurrentPlayerParticipant());
      populateCombatRewards(replay.getId());
    } else {
      watchButton.setText(i18n.get("game.watch"));
      showRatingChangeButton.setVisible(false);
      ratingSeparator.setVisible(false);
      reviewSeparator.setVisible(false);
      reviewsContainer.setVisible(false);
      teamsInfoBox.setVisible(false);
      replayService.enrich(replay, replay.getReplayFile());
    }
  }

  @VisibleForTesting
  void onDeleteReview(Review review) {
    reviewService.deleteGameReview(review)
        .thenRun(() -> JavaFxUtil.runLater(() -> {
          replay.getReviews().remove(review);
          reviewsController.setOwnReview(Optional.empty());
        }))
        .exceptionally(throwable -> {
          log.warn("Review could not be saved", throwable);
          notificationService.addImmediateErrorNotification(throwable, "review.delete.error");
          return null;
        });
  }

  @VisibleForTesting
  void onSendReview(Review review) {
    boolean isNew = review.getId() == null;
    Player player = playerService.getCurrentPlayer()
        .orElseThrow(() -> new IllegalStateException("No current player is available"));
    review.setPlayer(player);
    reviewService.saveGameReview(review, replay.getId())
        .thenRun(() -> {
          if (isNew) {
            replay.getReviews().add(review);
          }
          reviewsController.setOwnReview(Optional.of(review));
        })
        .exceptionally(throwable -> {
          log.warn("Review could not be saved", throwable);
          notificationService.addImmediateErrorNotification(throwable, "review.save.error");
          return null;
        });
  }

  /** Loads the battle-report data (Combat Score → LP + medals) for this game; visibility is then
   * governed by the toggle via {@link #updateBattleReportVisibility()}. */
  private void populateCombatRewards(int gameId) {
    if (combatRewardsContainer == null) {
      return;
    }
    hasRewardData = false;
    combatRewardsContainer.getChildren().clear();
    updateBattleReportVisibility();
    ladderPointsService.getGameResult(gameId).thenAccept(result -> JavaFxUtil.runLater(() -> {
      if (result == null || result.isEmpty()) {
        hasRewardData = false;
        updateBattleReportVisibility();
        return;
      }
      combatRewardsContainer.getChildren().setAll(
          com.faforever.client.ladder.GameRewardsView.render(i18n, uiService, result));
      hasRewardData = true;
      updateBattleReportVisibility();
    }));
  }

  /** Shows the battle report only when the toggle is on and there is data to show. The toggle is
   * disabled entirely when the game has no battle report / awarded medals to reveal. */
  private void updateBattleReportVisibility() {
    viewBattleReportButton.setDisable(!hasRewardData);
    boolean show = viewBattleReportButton.isSelected() && hasRewardData;
    combatRewardsContainer.setVisible(show);
    combatRewardsContainer.setManaged(show);
  }

  private boolean isCurrentPlayerParticipant() {
    Optional<Player> currentPlayer = playerService.getCurrentPlayer();
    if (currentPlayer.isEmpty() || teams == null) {
      return false;
    }
    int myId = currentPlayer.get().getId();
    return teams.values().stream().flatMap(List::stream).anyMatch(stats -> stats.getPlayerId() == myId);
  }

  private void populateTeamsContainer() {
    teamsContainer.getChildren().clear();
    teamCardControllers.clear();
    configureRatingControls();
    Map<Integer, PlayerStats> statsByPlayerId = teams.values().stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toMap(PlayerStats::getPlayerId, Function.identity()));

    JavaFxUtil.runLater(() -> teams.forEach((team, value) -> {
      List<Integer> playerIds = value.stream()
          .map(PlayerStats::getPlayerId)
          .collect(Collectors.toList());


      TeamCardController controller = uiService.loadFxml("theme/team_card.fxml");
      // Replay rosters often have no faction/GW icon and no country flag; fall back to a "playing"
      // status icon so the leading-icon column stays aligned instead of names sitting flush-left.
      controller.setShowPlayingStatusIconFallback(true);
      // Fix the LP-mode ladder rank to this game's board (all players share it), so a player with no
      // placement on it shows no rank rather than a most-played-elsewhere fallback — otherwise two
      // players' #1 ranks from different boards can both surface on the one card.
      statsByPlayerId.values().stream()
          .map(PlayerStats::getLeaderboard)
          .filter(Objects::nonNull)
          .map(Leaderboard::getTechnicalName)
          .findFirst()
          .ifPresent(controller::setRatingType);
      teamCardControllers.add(controller);

      Function<Player, LeaderboardRating> playerRatingFunction = player -> getPlayerLeaderboardRating(player, statsByPlayerId);
      Function<Player, Faction> playerFactionFunction = player -> getPlayerFaction(player, statsByPlayerId);
      Boolean hidePlayerRatings = statsByPlayerId.values().stream()
          .map(PlayerStats::getLeaderboard)
          .anyMatch(lb -> lb == null || lb.getLeaderboardHidden());
      boolean hasGwData = statsByPlayerId.values().stream()
          .anyMatch(ps -> ps.getGwFaction() != null);
      Function<Player, Image> gwMedalProvider = hasGwData
          ? player -> {
              PlayerStats ps = statsByPlayerId.get(player.getId());
              if (ps != null && ps.getGwFaction() != null && ps.getGwRank() != null) {
                return galacticWarService.getMedalImage(ps.getGwFaction(), ps.getGwRank());
              }
              return null;
            }
          : null;

      playerService.getPlayersByIds(playerIds)
          .thenAccept(players ->
              controller.setPlayersInTeam(team, players, playerRatingFunction, playerFactionFunction, gwMedalProvider, RatingPrecision.ROUNDED,
                  hidePlayerRatings)
          );

      teamsContainer.getChildren().add(controller.getRoot());
    }));
  }

  @VisibleForTesting
  Faction getPlayerFaction(Player player, Map<Integer, PlayerStats> statsByPlayerId) {
    return statsByPlayerId.get(player.getId()).getFaction();
  }

  @VisibleForTesting
  Integer getPlayerRating(Player player, Map<Integer, PlayerStats> statsByPlayerId) {
    PlayerStats playerStats = statsByPlayerId.get(player.getId());
    if (playerStats.getBeforeDeviation() != null && playerStats.getBeforeMean() != null) {
      return RatingUtil.getRating(playerStats.getBeforeMean(), playerStats.getBeforeDeviation());
    } else {
      return null;
    }
  }

  @VisibleForTesting
  LeaderboardRating getPlayerLeaderboardRating(Player player, Map<Integer, PlayerStats> statsByPlayerId) {
    PlayerStats playerStats = statsByPlayerId.get(player.getId());
    if (playerStats.getBeforeDeviation() != null && playerStats.getBeforeMean() != null) {
      return LeaderboardRating.create(playerStats.getBeforeMean().floatValue(), playerStats.getBeforeDeviation().floatValue());
    } else {
      return ratingService.createNewLeaderboardRating();
    }
  }

  private void configureRatingControls() {
    if (!replay.getValidity().equals(Validity.VALID)) {
      showRatingChangeButton.setVisible(false);
      notRatedReasonLabel.setVisible(true);
      String reasonText = i18n.getWithDefault(replay.getValidity().toString(), "game.reasonNotValid", i18n.get(replay.getValidity().getI18nKey()));
      notRatedReasonLabel.setText(reasonText);
    } else if (!replayService.replayChangedRating(replay)) {
      showRatingChangeButton.setVisible(false);
      notRatedReasonLabel.setVisible(true);
      notRatedReasonLabel.setText(i18n.get("game.notRatedYet"));
    } else {
      // Rated game: offer the legacy MMR delta — but only while the cutover flag keeps the legacy
      // rating visible (Ladder Points is the hero; the delta is the familiar companion until the
      // rating moves to the combat rating service, after which there is no live delta to show).
      showRatingChangeButton.setVisible(clientProperties.isShowLegacyRating() && !ratingBoardHidden);
      showRatingChangeButton.setDisable(false);
      notRatedReasonLabel.setVisible(false);
    }
  }

  /** Reveal each player's legacy MMR change in the team cards (one-shot; then disables the button). */
  public void showRatingChange() {
    teamCardControllers.forEach(teamCardController -> teamCardController.showRatingChange(teams));
    showRatingChangeButton.setDisable(true);
  }

  /** Toggles the Combat Score → Ladder Points + Medals Earned sections shown inline. */
  public void onViewBattleReport() {
    updateBattleReportVisibility();
  }

  public void onReport() {
    ReportDialogController reportDialogController = uiService.loadFxml("theme/reporting/report_dialog.fxml");
    reportDialogController.setGame(replay);
    Scene scene = getRoot().getScene();
    if (scene != null) {
      reportDialogController.setOwnerWindow(scene.getWindow());
    }
    reportDialogController.show();
  }

  @Override
  public Node getRoot() {
    return replayDetailRoot;
  }

  public void onCloseButtonClicked() {
    getRoot().setVisible(false);
  }

  public void onDimmerClicked() {
    onCloseButtonClicked();
  }

  public void onContentPaneClicked(MouseEvent event) {
    event.consume();
  }

  public void onWatchButtonClicked() {
    replayService.runDownloadReplay(replay);
  }

  public void onTadaUploadButtonClicked() { replayService.uploadReplayToTada(replay.getId()); }

  public void copyLink() {
    String replayUrl = Replay.getReplayUrl(replay.getId(), clientProperties.getVault().getReplayDownloadUrlFormat());
    ClipboardUtil.copyToClipboard(replayUrl);
  }

  public void onUnhideButton(ActionEvent actionEvent) {
    replayService.unhideReplay(this.replay.getId());
    this.replay.setReplayHidden(false);
  }
}
