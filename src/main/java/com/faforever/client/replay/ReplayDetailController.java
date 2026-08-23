package com.faforever.client.replay;

import com.faforever.client.api.dto.Validity;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.fa.DemoFileInfo;
import com.faforever.client.fx.DefaultImageView;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlatformService;
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
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.PersistentNotification;
import com.faforever.client.notification.Severity;
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
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
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
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.Assert;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private final BrowserWatchService browserWatchService;
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
  private final PlatformService platformService;
  private final com.faforever.client.ladder.LadderPointsService ladderPointsService;
  private final com.faforever.client.wager.WagerService wagerService;
  private final ModService modService;
  private final ArrayList<TeamCardController> teamCardControllers = new ArrayList<>();
  /** Re-renders the team cards (rating ⇄ ladder rank) when the global display-metric pill flips. */
  private ChangeListener<DisplayMetric> displayMetricListener;
  public javafx.scene.layout.VBox combatRewardsContainer;
  /** Collapsible "Wager market" pane: charts the eventual winner's implied probability over the
   * game. Always present; shows {@link #wagerChartPlaceholder} when the game had no priced market. */
  public javafx.scene.control.TitledPane wagerChartPane;
  public javafx.scene.chart.LineChart<Number, Number> wagerPriceChart;
  public Label wagerChartPlaceholder;
  /** Per-trader colour legend for the trade markers on {@link #wagerPriceChart}. */
  public javafx.scene.layout.FlowPane wagerChartLegend;
  /** Per-trader realised P&amp;L over the game's resolved markets (header + one row each). */
  public javafx.scene.layout.VBox wagerPnlBox;
  /** Root node of the included Season Ladder ⇄ Skill Rating pill; hidden for unranked (global /
   * hidden) boards, where neither metric is meaningful. Injected by the {@code fx:include}. */
  public Node displayMetricToggle;
  public Pane replayDetailRoot;
  public Label titleLabel;
  public Button copyButton;
  public Label dateLabel;
  public Label timeLabel;
  public Label modLabel;
  public Label modVersionLabel;
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
  public Button downloadButton;
  public TextField replayIdField;
  public ScrollPane scrollPane;
  public ToggleButton viewBattleReportButton;
  public Button reportButton;
  public Label notRatedReasonLabel;
  /** Whether this replay's board is hidden (global / just-for-fun) — no MMR delta is offered then. */
  private boolean ratingBoardHidden;
  /** Whether the legacy MMR delta is meaningful for this replay (rated, on a non-hidden board, with
   * the {@code showLegacyRating} cutover flag on) — revealed alongside the battle report by the toggle. */
  private boolean ratingChangeAvailable;
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

    // Which BUILD of that mod the game ran on, resolved from the demo's units hash. Hidden until
    // resolved, and left hidden when it can't be — see ModService.findModVersionDisplayName.
    modVersionLabel.setVisible(false);
    modVersionLabel.managedProperty().bind(modVersionLabel.visibleProperty());
    resolvedModVersion = null;
    modService.findModVersionDisplayName(replay.getDemoFileInfo())
        .thenAccept(displayName -> JavaFxUtil.runLater(() -> {
          if (this.replay != replay) {
            return;
          }
          displayName.ifPresent(modVersionLabel::setText);
          modVersionLabel.setVisible(displayName.isPresent());
          resolvedModVersion = displayName.orElse(null);
        }));

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
      // Battle report, wager market and per-player rating change all start hidden; the single toggle
      // reveals all three. Data loads regardless so toggling on always works.
      viewBattleReportButton.setSelected(false);
      // Just-for-fun (unranked / hidden-board) games and games left unrated by some invalidity carry
      // no combat rating, LP or medals, so there is nothing to report — disable the toggle.
      boolean unratedInvalid = !replay.getValidity().equals(Validity.VALID);
      viewBattleReportButton.setDisable(ratingBoardHidden || unratedInvalid);
      populateCombatRewards(replay.getId());
    } else {
      watchButton.setText(i18n.get("game.watch"));
      ratingSeparator.setVisible(false);
      reviewSeparator.setVisible(false);
      reviewsContainer.setVisible(false);
      teamsInfoBox.setVisible(false);
      replayService.enrich(replay, replay.getReplayFile());
    }

    populateWagerChart(replay.getId());
  }

  /** Charts the eventual winner's implied probability over the game (with per-trade markers),
   * plus each trader's realised P&amp;L over the game's markets, if it had any. The pane is
   * always visible (collapsed); it shows a placeholder when there was no market. */
  private void populateWagerChart(int gameId) {
    if (wagerChartPane == null) {
      return;
    }
    wagerPriceChart.getData().clear();
    wagerPriceChart.setVisible(false);
    wagerPriceChart.setManaged(false);
    if (wagerChartLegend != null) {
      wagerChartLegend.getChildren().clear();
      wagerChartLegend.setVisible(false);
      wagerChartLegend.setManaged(false);
    }
    if (wagerPnlBox != null) {
      wagerPnlBox.getChildren().clear();
      wagerPnlBox.setVisible(false);
      wagerPnlBox.setManaged(false);
    }
    wagerChartPlaceholder.setText(i18n.get("replay.wagerChart.none"));
    wagerChartPlaceholder.setVisible(true);
    wagerChartPlaceholder.setManaged(true);
    wagerService.getReplayWagerSummary(gameId)
        .thenAccept(summary -> JavaFxUtil.runLater(() -> {
          summary.chart().ifPresent(this::renderWagerChart);
          renderWagerPnl(summary.traderPnls());
        }))
        .exceptionally(throwable -> {
          log.warn("Could not load wager price history for game {}", gameId, throwable);
          return null;
        });
  }

  private void renderWagerChart(com.faforever.client.wager.WagerService.ReplayPriceChart chart) {
    List<com.faforever.client.wager.WagerService.PricePoint> points = chart.points();
    if (points.isEmpty()) {
      return;
    }
    // Anchor X=0 at game start (kickoff), not at the first trade, so a trade sits at its true
    // game-time minute (the market is flat from open until the first trade). Fall back to the first
    // point if the start time is somehow missing.
    double startEpoch = replay.getStartTime() != null
        ? replay.getStartTime().toEpochSecond()
        : points.get(0).epochSeconds();
    javafx.scene.chart.XYChart.Series<Number, Number> series = new javafx.scene.chart.XYChart.Series<>();
    series.setName(chart.outcomeLabel());

    List<javafx.scene.chart.XYChart.Data<Number, Number>> line = new java.util.ArrayList<>();
    // Leading flat segment from kickoff to the first trade at the opening price.
    double firstEpoch = points.get(0).epochSeconds();
    if (startEpoch < firstEpoch) {
      line.add(new javafx.scene.chart.XYChart.Data<>(0.0, points.get(0).price()));
    }
    // Step (step-after) plot: the price holds flat between trades and jumps instantly at each trade,
    // so only horizontal and vertical segments are drawn — never a diagonal implying continuous drift.
    // For each price change, extend the previous price horizontally to the new trade time, then step
    // vertically to the new price.
    double prevPrice = Double.NaN;
    for (com.faforever.client.wager.WagerService.PricePoint p : points) {
      // X in minutes from kickoff; Y is the winner's implied win probability [0,1].
      double time = (p.epochSeconds() - startEpoch) / 60.0;
      double price = p.price();
      if (!Double.isNaN(prevPrice) && price != prevPrice) {
        line.add(new javafx.scene.chart.XYChart.Data<>(time, prevPrice));
      }
      line.add(new javafx.scene.chart.XYChart.Data<>(time, price));
      prevPrice = price;
    }
    // Trailing flat segment to game end at the last traded price (the market's final read on the
    // winner before settlement), so the line spans the whole game rather than stopping at the last trade.
    com.faforever.client.wager.WagerService.PricePoint lastPoint = points.get(points.size() - 1);
    if (replay.getEndTime() != null) {
      double endMin = (replay.getEndTime().toEpochSecond() - startEpoch) / 60.0;
      if (endMin > (lastPoint.epochSeconds() - startEpoch) / 60.0) {
        line.add(new javafx.scene.chart.XYChart.Data<>(endMin, lastPoint.price()));
      }
    }
    // Human-trade markers: a coloured ▲/▼ per trade riding on its (already plotted) PRE-trade
    // point — the foot of the step it caused, so the arrow marks where the move starts and points
    // along it. The symbol rides a duplicate data point inserted alongside that foot, which leaves
    // the line itself unchanged (see insertStepFootMarker).
    Map<Integer, javafx.scene.paint.Color> traderColors = new java.util.HashMap<>();
    for (com.faforever.client.wager.WagerService.TradeMarker marker : chart.markers()) {
      double time = (marker.epochSeconds() - startEpoch) / 60.0;
      String tooltip = i18n.get(marker.up() ? "wager.marker.bought" : "wager.marker.sold",
          com.faforever.client.wager.WagerChartMarkers.displayName(marker),
          String.format("%.2f", marker.shares()),
          String.format("%.1f", marker.priceAfter() * 100));
      com.faforever.client.wager.WagerChartMarkers.insertStepFootMarker(line, time,
          com.faforever.client.wager.WagerChartMarkers.markerNode(marker,
              com.faforever.client.wager.WagerChartMarkers.colorFor(traderColors, marker.userId()), tooltip));
    }
    series.getData().setAll(line);
    wagerPriceChart.getData().add(series);
    if (wagerChartLegend != null) {
      com.faforever.client.wager.WagerChartMarkers.populateLegend(wagerChartLegend, chart.markers(), traderColors);
    }
    wagerChartPlaceholder.setVisible(false);
    wagerChartPlaceholder.setManaged(false);
    wagerPriceChart.setVisible(true);
    wagerPriceChart.setManaged(true);
  }

  /** One row per trader: their realised LP over all of this game's resolved markets, best-first,
   * profit green / loss red (portfolio colour conventions). Nothing resolved → box stays hidden. */
  private void renderWagerPnl(List<com.faforever.client.wager.WagerService.TraderPnl> pnls) {
    if (wagerPnlBox == null || pnls.isEmpty()) {
      return;
    }
    wagerPnlBox.getChildren().clear();
    Label header = new Label(i18n.get("replay.wagerPnl.title"));
    header.setStyle("-fx-font-weight: bold;");
    wagerPnlBox.getChildren().add(header);
    for (com.faforever.client.wager.WagerService.TraderPnl pnl : pnls) {
      Label name = new Label(pnl.userName() != null ? pnl.userName() : "#" + pnl.userId());
      Pane spacer = new Pane();
      javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
      Label value = new Label(i18n.get("replay.wagerPnl.lp", String.format("%+d", pnl.pnlLp())));
      value.setStyle("-fx-font-weight: bold; -fx-text-fill: "
          + (pnl.pnlLp() >= 0 ? "#26a65b" : "#cb4b16") + ";");
      wagerPnlBox.getChildren().add(new javafx.scene.layout.HBox(8, name, spacer, value));
    }
    wagerPnlBox.setVisible(true);
    wagerPnlBox.setManaged(true);
    // There was market activity to show, even if the winner's line itself isn't chartable.
    wagerChartPlaceholder.setVisible(false);
    wagerChartPlaceholder.setManaged(false);
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

  /** Reveals/hides the three inline expandable sections together, driven by the single toggle:
   * the battle report (Combat Score → LP + medals), the wager market pane and the per-player legacy
   * MMR delta. The battle report is only shown when it has data; the wager pane shows its own
   * "no market" placeholder when the game had none; the rating delta is only revealed when it is
   * meaningful for this replay. */
  private void updateBattleReportVisibility() {
    boolean show = viewBattleReportButton.isSelected();

    boolean showReport = show && hasRewardData;
    combatRewardsContainer.setVisible(showReport);
    combatRewardsContainer.setManaged(showReport);

    if (wagerChartPane != null) {
      wagerChartPane.setVisible(show);
      wagerChartPane.setManaged(show);
      wagerChartPane.setExpanded(show);
    }

    if (show && ratingChangeAvailable) {
      teamCardControllers.forEach(controller -> controller.showRatingChange(teams));
    } else {
      teamCardControllers.forEach(TeamCardController::hideRatingChange);
    }
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
      // Widen the card here only: this dialog reveals per-player rating-change labels, which would
      // otherwise overlap longer player names at the shared 200px default.
      controller.setCardWidth(240.0);
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
      ratingChangeAvailable = false;
      notRatedReasonLabel.setVisible(true);
      String reasonText = i18n.getWithDefault(replay.getValidity().toString(), "game.reasonNotValid", i18n.get(replay.getValidity().getI18nKey()));
      notRatedReasonLabel.setText(reasonText);
    } else if (!replayService.replayChangedRating(replay)) {
      ratingChangeAvailable = false;
      notRatedReasonLabel.setVisible(true);
      notRatedReasonLabel.setText(i18n.get("game.notRatedYet"));
    } else {
      // Rated game: offer the legacy MMR delta — but only while the cutover flag keeps the legacy
      // rating visible (Ladder Points is the hero; the delta is the familiar companion until the
      // rating moves to the combat rating service, after which there is no live delta to show).
      ratingChangeAvailable = clientProperties.isShowLegacyRating() && !ratingBoardHidden;
      notRatedReasonLabel.setVisible(false);
    }
  }

  /** Toggles the battle report (Combat Score → LP + Medals), the wager market pane and the
   * per-player legacy MMR delta together, all driven by the single "View battle report" toggle. */
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

  private ContextMenu watchMenu;

  public void onWatchButtonClicked() {
    if (!browserWatchService.isAvailable()) {
      replayService.runDownloadReplay(replay);
      return;
    }
    if (browserWatchService.isBrowserOnly()) {
      // Staging room open: the local replayer can't start (no second TA), so don't offer it —
      // just open the browser viewer. See BrowserWatchService#isBrowserOnly.
      browserWatchService.watchReplayInBrowser(replay);
      return;
    }
    // Rebuilt per click rather than cached: whether the in-game item belongs depends on live state.
    MenuItem inGame = new MenuItem(i18n.get("game.watch.inGame"));
    inGame.setOnAction(event -> replayService.runDownloadReplay(replay));
    MenuItem inBrowser = new MenuItem(i18n.get("game.watch.inBrowser"));
    inBrowser.setOnAction(event -> browserWatchService.watchReplayInBrowser(replay));
    watchMenu = new ContextMenu(inGame, inBrowser);
    watchMenu.show(watchButton, Side.BOTTOM, 0, 0);
  }

  public void onTadaUploadButtonClicked() { replayService.uploadReplayToTada(replay.getId()); }

  /**
   * Saves the vault's replay archive to wherever the user wants it, pre-named the same way the
   * server names the copy it uploads to TADA - see {@link CanonicalReplayName}. The download is a
   * zip holding the .tad, so the canonical stem takes a .zip extension and the .tad keeps its own
   * name inside the archive.
   */
  public void onDownloadButtonClicked() {
    // resolvedModVersion is whatever the units-hash lookup fired off by setReplay came back with.
    // It is normally in by the time the user reaches this button; if it is not, the name simply
    // carries no version, which is the same outcome as a version we cannot resolve at all.
    String fileName = CanonicalReplayName.stem(replay, resolvedModVersion) + ".zip";

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle(i18n.get("replay.download.chooseFile"));
    fileChooser.setInitialFileName(fileName);
    fileChooser.getExtensionFilters().add(new ExtensionFilter(i18n.get("replay.download.fileType"), "*.zip"));
    Optional.ofNullable(lastDownloadDirectory())
        .ifPresent(directory -> fileChooser.setInitialDirectory(directory.toFile()));

    Scene scene = getRoot().getScene();
    File destination = fileChooser.showSaveDialog(scene == null ? null : scene.getWindow());
    if (destination == null) {
      return;
    }
    lastDownloadDirectory = destination.toPath().getParent();

    replayService.saveReplayAs(replay.getId(), destination.toPath())
        .thenAccept(path -> JavaFxUtil.runLater(() ->
            notificationService.addNotification(new PersistentNotification(
                i18n.get("replay.download.finished", path.getFileName().toString()), Severity.INFO,
                List.of(new Action(i18n.get("replay.download.showInFolder"),
                    event -> platformService.reveal(path)))))))
        .exceptionally(throwable -> {
          Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
          if (cause instanceof FileNotFoundException) {
            log.warn("Replay {} not available on server yet", replay.getId(), cause);
            notificationService.addImmediateWarnNotification("replayNotAvailable", replay.getId());
          } else {
            log.error("Replay {} could not be downloaded", replay.getId(), cause);
            notificationService.addImmediateErrorNotification(cause, "replay.download.failed", replay.getId());
          }
          return null;
        });
  }

  /**
   * Display name of the featured-mod build this replay ran on, once
   * {@link ModService#findModVersionDisplayName} has resolved it. Null until then, and null for
   * good when the units hash matches no build we know about.
   */
  @Nullable
  private String resolvedModVersion;

  /**
   * Where the last save landed, so a run of downloads doesn't reopen in the same default every
   * time. Static because the detail dialog is prototype-scoped: a fresh controller is built for
   * each replay, so an instance field would forget between two consecutive downloads.
   */
  @Nullable
  private static Path lastDownloadDirectory;

  @Nullable
  private Path lastDownloadDirectory() {
    if (lastDownloadDirectory != null && Files.isDirectory(lastDownloadDirectory)) {
      return lastDownloadDirectory;
    }
    Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
    return Files.isDirectory(downloads) ? downloads : null;
  }

  public void copyLink() {
    String replayUrl = Replay.getReplayUrl(replay.getId(), clientProperties.getVault().getReplayDownloadUrlFormat());
    ClipboardUtil.copyToClipboard(replayUrl);
  }

  public void onUnhideButton(ActionEvent actionEvent) {
    replayService.unhideReplay(this.replay.getId());
    this.replay.setReplayHidden(false);
  }
}
