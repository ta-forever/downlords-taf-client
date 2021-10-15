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
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.NameRecord;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
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
import javafx.beans.property.SimpleIntegerProperty;
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

import static com.faforever.client.events.EventService.EVENT_AEON_PLAYS;
import static com.faforever.client.events.EventService.EVENT_AEON_WINS;
import static com.faforever.client.events.EventService.EVENT_BUILT_AIR_UNITS;
import static com.faforever.client.events.EventService.EVENT_BUILT_LAND_UNITS;
import static com.faforever.client.events.EventService.EVENT_BUILT_NAVAL_UNITS;
import static com.faforever.client.events.EventService.EVENT_BUILT_TECH_1_UNITS;
import static com.faforever.client.events.EventService.EVENT_BUILT_TECH_2_UNITS;
import static com.faforever.client.events.EventService.EVENT_BUILT_TECH_3_UNITS;
import static com.faforever.client.events.EventService.EVENT_CYBRAN_PLAYS;
import static com.faforever.client.events.EventService.EVENT_CYBRAN_WINS;
import static com.faforever.client.events.EventService.EVENT_SERAPHIM_PLAYS;
import static com.faforever.client.events.EventService.EVENT_SERAPHIM_WINS;
import static com.faforever.client.events.EventService.EVENT_UEF_PLAYS;
import static com.faforever.client.events.EventService.EVENT_UEF_WINS;
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
  public TabPane tabPane;
  public PieChart gamesPlayedByLeaderboardChart;
  public PieChart gamesPlayedByModChart;
  public PieChart techBuiltChart;
  public PieChart unitsBuiltChart;
  public StackedBarChart factionsChart;
  public Label gamesPlayedLabel;
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
  public TableColumn<LeaderboardEntry, Number> ratingTableRecentWinRateColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableStreakColumn;

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
    ratingMetricComboBox.setValue(RatingMetric.TRUESKILL);

    timePeriodComboBox.setConverter(timePeriodStringConverter());
    timePeriodComboBox.getItems().addAll(TimePeriod.values());
    timePeriodComboBox.setValue(TimePeriod.ALL_TIME);

    leaderboardService.getLeaderboards().thenApply(leaderboards -> {
      JavaFxUtil.runLater(() -> {
        ratingTypeComboBox.getItems().clear();
        ratingTypeComboBox.getItems().addAll(leaderboards);
        ratingTypeComboBox.setConverter(leaderboardStringConverter());
        ratingTypeComboBox.getSelectionModel().selectFirst();
      });
      return null;
    });

    ratingTableLeaderboardnameColumn.setCellValueFactory(param -> new SimpleStringProperty(
        i18n.getWithDefault(param.getValue().getLeaderboard().getTechnicalName(), param.getValue().getLeaderboard().getNameKey())));
    ratingTableLeaderboardnameColumn.setCellFactory(param -> new StringCell<>(name -> name));

    ratingTableWinRateColumn.setCellValueFactory(param -> new SimpleFloatProperty(param.getValue().getWinRate()));
    ratingTableWinRateColumn.setCellFactory(param -> new StringCell<>(number -> i18n.get("percentage", number.floatValue() * 100)));

    ratingTableRecentWinRateColumn.setCellValueFactory(param -> new SimpleFloatProperty(param.getValue().getRecentWinRate()));
    ratingTableRecentWinRateColumn.setCellFactory(param -> new StringCell<>(number -> i18n.get("percentage", number.floatValue() * 100)));

    ratingTableStreakColumn.setCellValueFactory(param -> param.getValue().streakProperty());
    ratingTableStreakColumn.setCellFactory(param -> new StringCell<>(streak -> i18n.number(streak.intValue())));

    ratingTableGamesPlayedColumn.setCellValueFactory(param -> param.getValue().gamesPlayedProperty());
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
    Integer gameCount = leaderboardEntries.stream()
        .map(lbe -> lbe.getGamesPlayed())
        .reduce(0, Integer::sum);
    gamesPlayedLabel.setText(i18n.number(gameCount));

    leaderboardService.getLeaderboards().thenAccept(leaderboards -> {
      StringBuilder ratingNames = new StringBuilder();
      StringBuilder ratingNumbers = new StringBuilder();
      leaderboardEntries.forEach(lbe -> {
        if (lbe != null) {
          Leaderboard lb = lbe.getLeaderboard();
          String leaderboardName = i18n.getWithDefault(lb.getTechnicalName(), lb.getNameKey());
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
    int aeonPlays = playerEvents.containsKey(EVENT_AEON_PLAYS) ? playerEvents.get(EVENT_AEON_PLAYS).getCurrentCount() : 0;
    int cybranPlays = playerEvents.containsKey(EVENT_CYBRAN_PLAYS) ? playerEvents.get(EVENT_CYBRAN_PLAYS).getCurrentCount() : 0;
    int uefPlays = playerEvents.containsKey(EVENT_UEF_PLAYS) ? playerEvents.get(EVENT_UEF_PLAYS).getCurrentCount() : 0;
    int seraphimPlays = playerEvents.containsKey(EVENT_SERAPHIM_PLAYS) ? playerEvents.get(EVENT_SERAPHIM_PLAYS).getCurrentCount() : 0;

    int aeonWins = playerEvents.containsKey(EVENT_AEON_WINS) ? playerEvents.get(EVENT_AEON_WINS).getCurrentCount() : 0;
    int cybranWins = playerEvents.containsKey(EVENT_CYBRAN_WINS) ? playerEvents.get(EVENT_CYBRAN_WINS).getCurrentCount() : 0;
    int uefWins = playerEvents.containsKey(EVENT_UEF_WINS) ? playerEvents.get(EVENT_UEF_WINS).getCurrentCount() : 0;
    int seraphimWins = playerEvents.containsKey(EVENT_SERAPHIM_WINS) ? playerEvents.get(EVENT_SERAPHIM_WINS).getCurrentCount() : 0;

    XYChart.Series<String, Integer> winsSeries = new XYChart.Series<>();
    winsSeries.setName(i18n.get("userInfo.wins"));
    winsSeries.getData().add(new XYChart.Data<>("Aeon", aeonWins));
    winsSeries.getData().add(new XYChart.Data<>("Cybran", cybranWins));
    winsSeries.getData().add(new XYChart.Data<>("UEF", uefWins));
    winsSeries.getData().add(new XYChart.Data<>("Seraphim", seraphimWins));

    XYChart.Series<String, Integer> lossSeries = new XYChart.Series<>();
    lossSeries.setName(i18n.get("userInfo.losses"));
    lossSeries.getData().add(new XYChart.Data<>("Aeon", aeonPlays - aeonWins));
    lossSeries.getData().add(new XYChart.Data<>("Cybran", cybranPlays - cybranWins));
    lossSeries.getData().add(new XYChart.Data<>("UEF", uefPlays - uefWins));
    lossSeries.getData().add(new XYChart.Data<>("Seraphim", seraphimPlays - seraphimWins));

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
      ratingTable.setItems(observableList(leaderboardEntries));
      updateRatingGrids(leaderboardEntries);
      leaderboardEntries.forEach(leaderboardEntry ->
            gamesPlayedByLeaderboardChart.getData().add(new PieChart.Data(
                i18n.getWithDefault(leaderboardEntry.getLeaderboard().getTechnicalName(), leaderboardEntry.getLeaderboard().getNameKey()),
                leaderboardEntry.getGamesPlayed())));

    })).exceptionally(throwable -> {
      log.warn("Leaderboard entry could not be read for player: " + player.getUsername(), throwable);
      return null;
    });
  }

  public void onRatingTypeChange() {
    if (ratingTypeComboBox.getValue() != null) {
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
    Double meanPrevious = null;
    int streak = 0;
    for (RatingHistoryDataPoint dataPoint: dataPoints) {
      Double mean = dataPoint.getMean();
      if (meanPrevious != null) {
        if (streak >= 0 && mean > meanPrevious) {
          ++streak;
        }
        else if (streak >= 0 && mean < meanPrevious) {
          streak = -1;
        }
        else if (streak <= 0 && mean > meanPrevious) {
          streak = 1;
        }
        else if (streak <= 0 && mean < meanPrevious) {
          --streak;
        }
      }
      meanPrevious = mean;
      values.add(new Data<>(dataPoint.getInstant().toEpochSecond(), streak));
    }
    return values;
  }

  public void plotPlayerRatingGraph() {
    JavaFxUtil.assertApplicationThread();
    OffsetDateTime afterDate = OffsetDateTime.of(timePeriodComboBox.getValue().getDate(), ZoneOffset.UTC);
    List<XYChart.Data<Long, Integer>> values = List.of();

    if (ratingMetricComboBox.getSelectionModel().getSelectedItem().equals(RatingMetric.TRUESKILL)) {
      values = ratingData.stream().sorted(Comparator.comparing(RatingHistoryDataPoint::getInstant))
          .filter(dataPoint -> dataPoint.getInstant().isAfter(afterDate))
          .map(dataPoint -> new Data<>(dataPoint.getInstant().toEpochSecond(), RatingUtil.getRating(dataPoint)))
          .collect(Collectors.toList());
    }
    else if (ratingMetricComboBox.getSelectionModel().getSelectedItem().equals(RatingMetric.STREAK)) {
      values = getStreakCount(ratingData.stream().sorted(Comparator.comparing(RatingHistoryDataPoint::getInstant))
          .filter(dataPoint -> dataPoint.getInstant().isAfter(afterDate))
          .sorted((a,b) -> (int)(a.getInstant().toEpochSecond() - b.getInstant().toEpochSecond()))
          .collect(Collectors.toList()));
    }

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
        return i18n.getWithDefault(leaderboard.getTechnicalName(), leaderboard.getNameKey());
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
