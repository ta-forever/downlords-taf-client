package com.faforever.client.chat;

import ch.micheljung.fxwindow.FxStage;
import com.faforever.client.api.dto.PlayerEvent;
import com.faforever.client.domain.RatingHistoryDataPoint;
import com.faforever.client.events.EventService;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.OffsetDateTimeCell;
import com.faforever.client.fx.ScatterXChart;
import com.faforever.client.fx.StringCell;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.Leaderboard;
import com.faforever.client.leaderboard.LeaderboardEntry;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.NameRecord;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.replay.Replay;
import com.faforever.client.stats.StatisticsService;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.Assert;
import com.faforever.client.util.RatingUtil;
import com.faforever.client.util.TimeService;
import com.faforever.client.vault.search.SearchController.SortConfig;
import com.faforever.client.vault.search.SearchController.SortOrder;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.faforever.client.events.EventService.EVENT_CORE_PLAYS;
import static com.faforever.client.events.EventService.EVENT_CORE_WINS;
import static com.faforever.client.events.EventService.EVENT_BUILT_AIR_UNITS;
import static com.faforever.client.events.EventService.EVENT_BUILT_LAND_UNITS;
import static com.faforever.client.events.EventService.EVENT_BUILT_NAVAL_UNITS;
import static com.faforever.client.events.EventService.EVENT_BUILT_TECH_1_UNITS;
import static com.faforever.client.events.EventService.EVENT_BUILT_TECH_2_UNITS;
import static com.faforever.client.events.EventService.EVENT_BUILT_TECH_3_UNITS;
import static com.faforever.client.events.EventService.EVENT_GOK_PLAYS;
import static com.faforever.client.events.EventService.EVENT_GOK_WINS;
import static com.faforever.client.events.EventService.EVENT_ARM_PLAYS;
import static com.faforever.client.events.EventService.EVENT_ARM_WINS;
import static javafx.collections.FXCollections.observableList;

@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
@Slf4j
@RequiredArgsConstructor
public class UserInfoWindowController implements Controller<Node> {

  private final StatisticsService statisticsService;
  private final CountryFlagService countryFlagService;
  private final EventService eventService;
  private final I18n i18n;
  private final UiService uiService;
  private final TimeService timeService;
  private final PlayerService playerService;
  private final NotificationService notificationService;
  private final LeaderboardService leaderboardService;
  private final FafService fafService;
  private final PreferencesService preferencesService;
  public TabPane tabPane;
  public PieChart gamesPlayedByLeaderboardChart;
  public PieChart gamesPlayedByModChart;
  public PieChart techBuiltChart;
  public PieChart unitsBuiltChart;
  public StackedBarChart factionsChart;
  public Label gamesPlayedLabel;
  public Label resultsBreakdownLabel;
  public Label recordScoreLabel;
  public HBox ratingsBox;
  public Label ratingsLabels;
  public Label ratingsValues;
  public NumberAxis yAxis;
  public NumberAxis xAxis;
  public ScatterXChart<Long, Integer> ratingHistoryChart;
  public VBox loadingHistoryPane;
  public ComboBox<RatingMetric> ratingMetricComboBox;
  public ComboBox<TimePeriod> timePeriodComboBox;
  public ComboBox<Leaderboard> ratingTypeComboBox;
  public Label usernameLabel;
  public Label countryLabel;
  public ImageView countryImageView;
  public Pane userInfoRoot;
  public TableView<NameRecord> nameHistoryTable;
  public TableColumn<NameRecord, OffsetDateTime> changeDateColumn;
  public TableColumn<NameRecord, String> nameColumn;

  public TableView<LeaderboardEntry> ratingTable;
  public TableColumn<LeaderboardEntry, String> ratingTableLeaderboardnameColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableGamesPlayedColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableRatingColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableWinRateColumn;
  public TableColumn<LeaderboardEntry, String> ratingTableAllResultsColumn;
  public TableColumn<LeaderboardEntry, String> ratingTableRecentResultsColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableStreakColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableBestStreakColumn;

  private Player player;
  private Window ownerWindow;  private List<RatingHistoryDataPoint> ratingData;
  private List<Replay> replayHistory;

  public void initialize() {
    JavaFxUtil.bindManagedToVisible(loadingHistoryPane, ratingHistoryChart);

    nameColumn.setCellValueFactory(param -> param.getValue().nameProperty());
    changeDateColumn.setCellValueFactory(param -> param.getValue().changeDateProperty());
    changeDateColumn.setCellFactory(param -> new OffsetDateTimeCell<>(timeService));

    ratingMetricComboBox.setConverter(ratingMetricStringConverter());
    ratingMetricComboBox.getItems().addAll(RatingMetric.values());
    ratingMetricComboBox.setValue(preferencesService.getPreferences().getUserInfoRatingMetric());
    ratingMetricComboBox.getSelectionModel().selectedItemProperty().addListener((obs,oldValue,newValue) -> {
      if (preferencesService.getPreferences().getUserInfoRatingMetric() != newValue) {
        preferencesService.getPreferences().setUserInfoRatingMetric(newValue);
        preferencesService.storeInBackground();
      }});

    timePeriodComboBox.setConverter(timePeriodStringConverter());
    timePeriodComboBox.getItems().addAll(TimePeriod.values());
    timePeriodComboBox.setValue(TimePeriod.ALL_TIME);

    leaderboardService.getLeaderboards().thenApply(leaderboards -> {
      JavaFxUtil.runLater(() -> {
        ratingTypeComboBox.getItems().clear();
        ratingTypeComboBox.getItems().addAll(leaderboards);
        ratingTypeComboBox.setConverter(leaderboardStringConverter());

        leaderboards.stream()
            .filter(lbe -> lbe.getTechnicalName().equals(preferencesService.getPreferences().getLastLeaderboardSelection()))
            .findAny()
            .ifPresentOrElse(
                lbe -> ratingTypeComboBox.getSelectionModel().select(lbe),
                () -> ratingTypeComboBox.getSelectionModel().selectFirst());
      });
      return null;
    });

    ratingTableLeaderboardnameColumn.setCellValueFactory(param -> new SimpleStringProperty(
        i18n.get(param.getValue().getLeaderboard().getNameKey())));
    ratingTableLeaderboardnameColumn.setCellFactory(param -> new StringCell<>(name -> name));

    ratingTableWinRateColumn.setCellValueFactory(param -> new SimpleFloatProperty(param.getValue().getWinRate()));
    ratingTableWinRateColumn.setCellFactory(param -> new StringCell<>(number -> i18n.get("percentage", number.floatValue() * 100)));

    ratingTableAllResultsColumn.setCellValueFactory(param -> param.getValue().allResultsProperty());
    ratingTableAllResultsColumn.setCellFactory(param -> new StringCell<>(results -> results));

    ratingTableRecentResultsColumn.setCellValueFactory(param -> param.getValue().recentResultsProperty());
    ratingTableRecentResultsColumn.setCellFactory(param -> new StringCell<>(rate -> rate));

    ratingTableStreakColumn.setCellValueFactory(param -> param.getValue().streakProperty());
    ratingTableStreakColumn.setCellFactory(param -> new StringCell<>(streak -> i18n.number(streak.intValue())));

    ratingTableBestStreakColumn.setCellValueFactory(param -> param.getValue().bestStreakProperty());
    ratingTableBestStreakColumn.setCellFactory(param -> new StringCell<>(streak -> i18n.number(streak.intValue())));

    ratingTableGamesPlayedColumn.setCellValueFactory(param -> param.getValue().totalGamesProperty());
    ratingTableGamesPlayedColumn.setCellFactory(param -> new StringCell<>(count -> i18n.number(count.intValue())));

    ratingTableRatingColumn.setCellValueFactory(param -> param.getValue().ratingProperty());
    ratingTableRatingColumn.setCellFactory(param -> new StringCell<>(rating -> i18n.number(rating.intValue())));

    ratingData = Collections.emptyList();
    replayHistory = Collections.emptyList();
  }

  public Region getRoot() {
    return userInfoRoot;
  }

  public void setPlayer(Player player) {
    this.player = player;

    usernameLabel.setText(player.getUsername());
    countryFlagService.loadCountryFlag(player.getCountry()).ifPresent(image -> countryImageView.setImage(image));

    updateNameHistory();
    countryLabel.setText(i18n.getCountryNameLocalized(player.getCountry()));

    onRatingTypeChange();

    eventService.getPlayerEvents(player.getId())
        .thenAccept(events -> plotGamesPlayedByLeaderboardChart())
        .exceptionally(throwable -> {
          log.warn("Could not load player events", throwable);
          notificationService.addImmediateErrorNotification(throwable, "userInfo.statistics.errorLoading");
          return null;
        });

    loadReplayHistory(100)
        .thenAccept((x) -> plotGamesPlayedByModChart());
  }

  private void updateRatingGrids(List<LeaderboardEntry> leaderboardEntries) {
    Integer winCount = leaderboardEntries.stream().map(LeaderboardEntry::getWonGames).reduce(0, Integer::sum);
    Integer drawCount = leaderboardEntries.stream().map(LeaderboardEntry::getDrawnGames).reduce(0, Integer::sum);
    Integer lossCount = leaderboardEntries.stream().map(LeaderboardEntry::getLostGames).reduce(0, Integer::sum);
    int gameCount = winCount + drawCount + lossCount;
    gamesPlayedLabel.setText(i18n.number(gameCount));
    resultsBreakdownLabel.setText(i18n.get("userInfo.winDrawLoss", winCount, drawCount, lossCount));

    leaderboardService.getLeaderboards().thenAccept(leaderboards -> {
      StringBuilder ratingNames = new StringBuilder();
      StringBuilder ratingNumbers = new StringBuilder();
      leaderboardEntries.forEach(lbe -> {
        if (lbe != null && !lbe.getLeaderboard().getLeaderboardHidden()) {
          Leaderboard lb = lbe.getLeaderboard();
          String leaderboardName = i18n.get(lb.getNameKey());
          ratingNames.append(i18n.get("leaderboard.rating", leaderboardName)).append("\n");
          ratingNumbers.append(i18n.number((int)lbe.getRating())).append("\n");
        }
      });
      JavaFxUtil.runLater(() -> {
        ratingsLabels.setText(ratingNames.toString());
        ratingsValues.setText(ratingNumbers.toString());
      });
    });
  }

  private void updateNameHistory() {
    playerService.getPlayersByIds(Collections.singletonList(player.getId()))
        .thenAccept(players -> nameHistoryTable.setItems(players.get(0).getNames()))
        .exceptionally(throwable -> {
          log.warn("Could not load player name history", throwable);
          notificationService.addImmediateErrorNotification(throwable, "userInfo.nameHistory.errorLoading");
          return null;
        });
  }

  private void plotFactionsChart(Map<String, PlayerEvent> playerEvents) {
    int corePlays = playerEvents.containsKey(EVENT_CORE_PLAYS) ? playerEvents.get(EVENT_CORE_PLAYS).getCurrentCount() : 0;
    int gokPlays = playerEvents.containsKey(EVENT_GOK_PLAYS) ? playerEvents.get(EVENT_GOK_PLAYS).getCurrentCount() : 0;
    int armPlays = playerEvents.containsKey(EVENT_ARM_PLAYS) ? playerEvents.get(EVENT_ARM_PLAYS).getCurrentCount() : 0;

    int coreWins = playerEvents.containsKey(EVENT_CORE_WINS) ? playerEvents.get(EVENT_CORE_WINS).getCurrentCount() : 0;
    int gokWins = playerEvents.containsKey(EVENT_GOK_WINS) ? playerEvents.get(EVENT_GOK_WINS).getCurrentCount() : 0;
    int armWins = playerEvents.containsKey(EVENT_ARM_WINS) ? playerEvents.get(EVENT_ARM_WINS).getCurrentCount() : 0;

    XYChart.Series<String, Integer> winsSeries = new XYChart.Series<>();
    winsSeries.setName(i18n.get("userInfo.wins"));
    winsSeries.getData().add(new XYChart.Data<>("Core", coreWins));
    winsSeries.getData().add(new XYChart.Data<>("GoK", gokWins));
    winsSeries.getData().add(new XYChart.Data<>("Arm", armWins));

    XYChart.Series<String, Integer> lossSeries = new XYChart.Series<>();
    lossSeries.setName(i18n.get("userInfo.losses"));
    lossSeries.getData().add(new XYChart.Data<>("Core", corePlays - coreWins));
    lossSeries.getData().add(new XYChart.Data<>("GoK", gokPlays - gokWins));
    lossSeries.getData().add(new XYChart.Data<>("Arm", armPlays - armWins));

    JavaFxUtil.runLater(() -> factionsChart.getData().addAll(winsSeries, lossSeries));
  }

  private void plotUnitsByCategoriesChart(Map<String, PlayerEvent> playerEvents) {
    int airBuilt = playerEvents.containsKey(EVENT_BUILT_AIR_UNITS) ? playerEvents.get(EVENT_BUILT_AIR_UNITS).getCurrentCount() : 0;
    int landBuilt = playerEvents.containsKey(EVENT_BUILT_LAND_UNITS) ? playerEvents.get(EVENT_BUILT_LAND_UNITS).getCurrentCount() : 0;
    int navalBuilt = playerEvents.containsKey(EVENT_BUILT_NAVAL_UNITS) ? playerEvents.get(EVENT_BUILT_NAVAL_UNITS).getCurrentCount() : 0;

    JavaFxUtil.runLater(() -> unitsBuiltChart.setData(FXCollections.observableArrayList(
        new PieChart.Data(i18n.get("stats.air"), airBuilt),
        new PieChart.Data(i18n.get("stats.land"), landBuilt),
        new PieChart.Data(i18n.get("stats.naval"), navalBuilt)
    )));
  }

  private void plotTechBuiltChart(Map<String, PlayerEvent> playerEvents) {
    int tech1Built = playerEvents.containsKey(EVENT_BUILT_TECH_1_UNITS) ? playerEvents.get(EVENT_BUILT_TECH_1_UNITS).getCurrentCount() : 0;
    int tech2Built = playerEvents.containsKey(EVENT_BUILT_TECH_2_UNITS) ? playerEvents.get(EVENT_BUILT_TECH_2_UNITS).getCurrentCount() : 0;
    int tech3Built = playerEvents.containsKey(EVENT_BUILT_TECH_3_UNITS) ? playerEvents.get(EVENT_BUILT_TECH_3_UNITS).getCurrentCount() : 0;

    JavaFxUtil.runLater(() -> techBuiltChart.setData(FXCollections.observableArrayList(
        new PieChart.Data(i18n.get("stats.tech1"), tech1Built),
        new PieChart.Data(i18n.get("stats.tech2"), tech2Built),
        new PieChart.Data(i18n.get("stats.tech3"), tech3Built)
    )));
  }

  private void plotGamesPlayedByModChart() {
    if (this.replayHistory != null) {
      JavaFxUtil.runLater(() -> this.replayHistory.stream()
          .map(replay -> replay.getFeaturedMod().getDisplayName())
          .collect(Collectors.groupingBy(name -> name, Collectors.counting()))
          .forEach((name, playCount) -> gamesPlayedByModChart.getData().add(new PieChart.Data(
            name, playCount)))
      );
    }
  }

  private void plotGamesPlayedByLeaderboardChart() {
    JavaFxUtil.runLater(() -> gamesPlayedByLeaderboardChart.getData().clear());
    leaderboardService.getEntriesForPlayer(player.getId()).thenAccept(leaderboardEntries -> JavaFxUtil.runLater(() -> {
      List<LeaderboardEntry> sortedEntries = leaderboardEntries.stream()
          .sorted((a,b) -> (int)(b.getRating() - a.getRating()))
          .collect(Collectors.toList());
      updateRatingGrids(sortedEntries);
      sortedEntries.forEach(leaderboardEntry ->
            gamesPlayedByLeaderboardChart.getData().add(new PieChart.Data(
                i18n.get(leaderboardEntry.getLeaderboard().getNameKey()),
                leaderboardEntry.getWonGames())));

      sortedEntries = sortedEntries.stream()
          .filter(lbe -> !lbe.getLeaderboard().getLeaderboardHidden())
              .toList();
      ratingTable.setItems(observableList(sortedEntries));

    })).exceptionally(throwable -> {
      log.warn("Leaderboard entry could not be read for player: " + player.getUsername(), throwable);
      return null;
    });
  }

  public void onRatingTypeChange() {
    if (ratingTypeComboBox.getValue() != null) {

      preferencesService.getPreferences().setLastLeaderboardSelection(ratingTypeComboBox.getValue().getTechnicalName());
      preferencesService.storeInBackground();

      ratingHistoryChart.setVisible(false);
      loadingHistoryPane.setVisible(true);
      loadStatistics(ratingTypeComboBox.getValue()).thenRun(() -> JavaFxUtil.runLater(this::plotPlayerRatingGraph));
    }
  }

  private CompletableFuture<Void> loadStatistics(Leaderboard leaderboard) {
    return statisticsService.getRatingHistory(player.getId(), leaderboard)
        .thenAccept(ratingHistory -> ratingData = ratingHistory)
        .exceptionally(throwable -> {
          // FIXME display to user
          log.warn("Statistics could not be loaded", throwable);
          return null;
        });
  }

  private CompletableFuture<Void> loadReplayHistory(int maxResults) {
    return fafService.findReplaysByQueryWithPageCount(
        String.format("(playerStats.player.login==\"%s\")", this.player.getUsername()),
        maxResults, 1, new SortConfig("startTime", SortOrder.DESC))
        .thenAccept(replayHistory -> this.replayHistory = replayHistory.getFirst())
        .exceptionally(throwable -> {
          // FIXME display to user
          log.warn("Replays could not be loaded", throwable);
          return null;
        });
  }

  private List<XYChart.Data<Long, Integer>> getStreakCount(List<RatingHistoryDataPoint> dataPoints) {
    List<XYChart.Data<Long, Integer>> values = new ArrayList<>();
    Integer previousScore = null;
    int streak = 0;
    for (RatingHistoryDataPoint dataPoint: dataPoints) {
      Integer score = (int)dataPoint.getScore();
      if (previousScore != null) {
        if (streak * score >= 0) {
          streak += score;
        }
        else {
          streak = score;
        }
      }
      else {
        streak = score;
      }
      previousScore = score;
      values.add(new Data<>(dataPoint.getInstant().toEpochSecond(), streak));
    }
    return values;
  }

  public void plotPlayerRatingGraph() {
    JavaFxUtil.assertApplicationThread();
    OffsetDateTime afterDate = OffsetDateTime.of(timePeriodComboBox.getValue().getDate(), ZoneOffset.UTC);
    List<XYChart.Data<Long, Integer>> values = List.of();

    List<XYChart.Data<Long, Integer>> trueskillHistory = ratingData.stream().sorted(Comparator.comparing(RatingHistoryDataPoint::getInstant))
        .filter(dataPoint -> dataPoint.getInstant().isAfter(afterDate))
        .map(dataPoint -> new Data<>(dataPoint.getInstant().toEpochSecond(), RatingUtil.getRating(dataPoint)))
        .collect(Collectors.toList());

    List<XYChart.Data<Long, Integer>> streakHistory = getStreakCount(ratingData.stream().sorted(Comparator.comparing(RatingHistoryDataPoint::getInstant))
        .filter(dataPoint -> dataPoint.getInstant().isAfter(afterDate))
        .sorted((a,b) -> (int)(a.getInstant().toEpochSecond() - b.getInstant().toEpochSecond()))
        .collect(Collectors.toList()));

    if (ratingMetricComboBox.getSelectionModel().getSelectedItem().equals(RatingMetric.TRUESKILL)) {
      values = trueskillHistory;
    }
    else if (ratingMetricComboBox.getSelectionModel().getSelectedItem().equals(RatingMetric.STREAK)) {
      values = streakHistory;
    }

    int recordLowScore = values.stream()
        .mapToInt(datapoint -> datapoint.getYValue())
        .min()
        .orElse(0);
    int recordHighScore = values.stream()
        .mapToInt(datapoint -> datapoint.getYValue())
        .max()
        .orElse(0);
    recordScoreLabel.setText(i18n.get("userInfo.recordScore", recordHighScore, recordLowScore));

    xAxis.setTickLabelFormatter(ratingLabelFormatter());
    if (values.size() > 0) {
      xAxis.setLowerBound(values.get(0).getXValue());
      xAxis.setUpperBound(values.get(values.size() - 1).getXValue());
    }
    xAxis.setTickUnit((xAxis.getUpperBound() - xAxis.getLowerBound()) / 10);

    XYChart.Series<Long, Integer> series = new XYChart.Series<>(observableList(values));
    series.setName(i18n.get("userInfo.ratingOverTime"));
    ratingHistoryChart.setData(FXCollections.observableList(Collections.singletonList(series)));
    ratingHistoryChart.clearMarkers();
    if (!values.isEmpty()) {
      Integer latestValue = values.get(values.size()-1).getYValue();
      ratingHistoryChart.addHorizontalValueMarker(
          new XYChart.Data<>(0L, latestValue), 4,
          latestValue >= 0
              ? "-fx-stroke: -good;"
              : "-fx-stroke: -bad;");
      ratingHistoryChart.addAnnotationValueMarker(
          new XYChart.Data<>(values.get(0).getXValue(), latestValue), String.format("%d", latestValue),
          latestValue >= 0
              ? "-fx-stroke: -good;"
              : "-fx-stroke: -bad;");
    }
    loadingHistoryPane.setVisible(false);
    ratingHistoryChart.setVisible(true);
  }

  @NotNull
  private StringConverter<Leaderboard> leaderboardStringConverter() {
    return new StringConverter<>() {
      @Override
      public String toString(Leaderboard leaderboard) {
        return i18n.get(leaderboard.getNameKey());
      }

      @Override
      public Leaderboard fromString(String string) {
        return null;
      }
    };
  }

  @NotNull
  private StringConverter<TimePeriod> timePeriodStringConverter() {
    return new StringConverter<>() {
      @Override
      public String toString(TimePeriod period) {
        return i18n.get(period.getI18nKey());
      }

      @Override
      public TimePeriod fromString(String string) {
        return null;
      }
    };
  }


  @NotNull
  private StringConverter<RatingMetric> ratingMetricStringConverter() {
    return new StringConverter<>() {
      @Override
      public String toString(RatingMetric metric) {
        return i18n.get(metric.getI18nKey());
      }

      @Override
      public RatingMetric fromString(String string) {
        return null;
      }
    };
  }

  @NotNull
  private StringConverter<Number> ratingLabelFormatter() {
    return new StringConverter<>() {
      @Override
      public String toString(Number object) {
        long number = object.longValue();
        return timeService.asDate(Instant.ofEpochSecond(number));
      }

      @Override
      public Number fromString(String string) {
        return null;
      }
    };
  }

  public void show() {
    Assert.checkNullIllegalState(ownerWindow, "ownerWindow must be set");

    FxStage fxStage = FxStage.create(userInfoRoot)
        .initOwner(ownerWindow)
        .initModality(Modality.WINDOW_MODAL)
        .withSceneFactory(uiService::createScene)
        .allowMinimize(false)
        .apply();

    Stage stage = fxStage.getStage();
    stage.showingProperty().addListener((observable, oldValue, newValue) -> {
      if (!newValue) {
        userInfoRoot.getChildren().clear();
      }
    });
    stage.show();
  }

  public void setOwnerWindow(Window ownerWindow) {
    this.ownerWindow = ownerWindow;
  }

}
