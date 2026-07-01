package com.faforever.client.chat;

import ch.micheljung.fxwindow.FxStage;
import com.faforever.client.achievements.AchievementItemController;
import com.faforever.client.achievements.AchievementService;
import com.faforever.client.achievements.AchievementService.AchievementState;
import com.faforever.client.api.dto.AchievementDefinition;
import com.faforever.client.api.dto.PlayerAchievement;
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
import com.faforever.client.preferences.DisplayMetric;
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
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.faforever.client.achievements.AchievementService.AchievementState.UNLOCKED;
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
  private final AchievementService achievementService;
  private final EventService eventService;
  private final I18n i18n;
  private final UiService uiService;
  private final TimeService timeService;
  private final PlayerService playerService;
  private final NotificationService notificationService;
  private final LeaderboardService leaderboardService;
  private final com.faforever.client.leaderboard.RatingTierService ratingTierService;
  private final com.faforever.client.ladder.LadderPointsService ladderPointsService;
  private final FafService fafService;
  private final PreferencesService preferencesService;
  private final com.google.common.eventbus.EventBus eventBus;

  private final Map<String, AchievementItemController> achievementItemById = new HashMap<>();
  private final Map<String, AchievementDefinition> achievementDefinitionById = new HashMap<>();
  public Label lockedAchievementsHeaderLabel;
  public Label unlockedAchievementsHeaderLabel;
  public Pane unlockedAchievementsHeader;
  public Pane lockedAchievementsHeader;
  public ScrollPane achievementsPane;
  public ImageView mostRecentAchievementImageView;
  public Label mostRecentAchievementDescriptionLabel;
  public Label loadingProgressLabel;
  public Pane mostRecentAchievementPane;
  public Label mostRecentAchievementNameLabel;
  public Pane lockedAchievementsContainer;
  public Pane unlockedAchievementsContainer;



  public TabPane tabPane;
  public Tab medalCabinetTab;
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

  /** Re-renders the summary + stats table when the global displayMetric pref flips (e.g. the
   * in-dialog pill or the top-bar pill). Field-held so the weak listener survives. */
  private javafx.beans.value.ChangeListener<DisplayMetric> displayMetricListener;

  public TableView<com.faforever.client.ladder.SeasonStanding> seasonStatsTable;
  public TableColumn<com.faforever.client.ladder.SeasonStanding, String> seasonStatsBoardColumn;
  public TableColumn<com.faforever.client.ladder.SeasonStanding, Number> seasonStatsRankColumn;
  public TableColumn<com.faforever.client.ladder.SeasonStanding, Number> seasonStatsPointsColumn;
  public TableColumn<com.faforever.client.ladder.SeasonStanding, Number> seasonStatsGamesColumn;
  public TableColumn<com.faforever.client.ladder.SeasonStanding, Number> seasonStatsWinRateColumn;
  public TableColumn<com.faforever.client.ladder.SeasonStanding, String> seasonStatsWdlColumn;
  public TableColumn<com.faforever.client.ladder.SeasonStanding, String> seasonStatsWdl10Column;
  public TableColumn<com.faforever.client.ladder.SeasonStanding, Number> seasonStatsStreakColumn;
  public TableColumn<com.faforever.client.ladder.SeasonStanding, Number> seasonStatsBestStreakColumn;
  private final Map<String, String> boardDisplayNames = new HashMap<>();
  private List<LeaderboardEntry> lastLeaderboardEntries = Collections.emptyList();

  public TableView<LeaderboardEntry> ratingTable;
  public TableColumn<LeaderboardEntry, String> ratingTableLeaderboardnameColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableGamesPlayedColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableRatingColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableWinRateColumn;
  public TableColumn<LeaderboardEntry, String> ratingTableAllResultsColumn;
  public TableColumn<LeaderboardEntry, String> ratingTableRecentResultsColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableStreakColumn;
  public TableColumn<LeaderboardEntry, Number> ratingTableBestStreakColumn;
  public VBox ladderMedalCabinetCard;
  public VBox ladderMedalCabinet;
  public ImageView featuredMedalImageView;
  private final Map<String, VBox> medalSlotByCode = new HashMap<>();
  private String featuredMedalCode;
  private boolean ownProfile;

  private Player player;
  private Window ownerWindow;  private List<RatingHistoryDataPoint> ratingData;
  private List<com.faforever.client.ladder.LpHistoryPoint> lpData = Collections.emptyList();
  private List<Replay> replayHistory;

  private static boolean isUnlocked(PlayerAchievement playerAchievement) {
    return UNLOCKED == AchievementState.valueOf(playerAchievement.getState().name());
  }

  public void initialize() {
    JavaFxUtil.bindManagedToVisible(loadingHistoryPane, loadingProgressLabel, achievementsPane, mostRecentAchievementPane,
        unlockedAchievementsHeader, unlockedAchievementsContainer, lockedAchievementsHeader, lockedAchievementsContainer,
        ratingHistoryChart);

    unlockedAchievementsHeader.visibleProperty().bind(unlockedAchievementsContainer.visibleProperty());
    unlockedAchievementsContainer.visibleProperty().bind(Bindings.createBooleanBinding(
        () -> !unlockedAchievementsContainer.getChildren().isEmpty(), unlockedAchievementsContainer.getChildren()));

    lockedAchievementsHeader.visibleProperty().bind(lockedAchievementsContainer.visibleProperty());
    lockedAchievementsContainer.visibleProperty().bind(Bindings.createBooleanBinding(
        () -> !lockedAchievementsContainer.getChildren().isEmpty(), lockedAchievementsContainer.getChildren()));

    lockedAchievementsContainer.getChildren().addListener((InvalidationListener) observable ->
        lockedAchievementsHeaderLabel.setText(i18n.get("achievements.locked", lockedAchievementsContainer.getChildren().size()))
    );
    unlockedAchievementsContainer.getChildren().addListener((InvalidationListener) observable ->
        unlockedAchievementsHeaderLabel.setText(i18n.get("achievements.unlocked", unlockedAchievementsContainer.getChildren().size()))
    );

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

    // Animated charts defer rendering newly-set data to an animation that doesn't fire until a
    // layout/resize — which is why the graph stayed blank on first open until the window was
    // resized. Disabling animation makes the data (and axes) render immediately.
    ratingHistoryChart.setAnimated(false);
    xAxis.setAnimated(false);
    yAxis.setAnimated(false);

    leaderboardService.getLeaderboards().thenApply(leaderboards -> {
      leaderboards.forEach(lb -> boardDisplayNames.put(lb.getTechnicalName(), i18n.get(lb.getNameKey())));
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

    initializeSeasonStatsTable();
    initializeMetricToggle();

    ratingData = Collections.emptyList();
    replayHistory = Collections.emptyList();
  }

  private void initializeSeasonStatsTable() {
    ratingTable.managedProperty().bind(ratingTable.visibleProperty());
    seasonStatsTable.managedProperty().bind(seasonStatsTable.visibleProperty());

    seasonStatsBoardColumn.setCellValueFactory(p -> new SimpleStringProperty(
        boardDisplayNames.getOrDefault(p.getValue().getLeaderboardTechnicalName(),
            p.getValue().getLeaderboardTechnicalName())));
    seasonStatsBoardColumn.setCellFactory(p -> new StringCell<>(name -> name));
    seasonStatsRankColumn.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getRank()));
    seasonStatsRankColumn.setCellFactory(p -> new StringCell<>(
        rank -> rank.intValue() > 0 ? i18n.get("lp.rank.value", rank.intValue()) : "—"));
    seasonStatsPointsColumn.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getScore()));
    seasonStatsPointsColumn.setCellFactory(p -> new StringCell<>(lp -> i18n.number(lp.intValue())));
    seasonStatsGamesColumn.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getGames()));
    seasonStatsGamesColumn.setCellFactory(p -> new StringCell<>(c -> i18n.number(c.intValue())));
    seasonStatsWinRateColumn.setCellValueFactory(p -> new SimpleFloatProperty(p.getValue().getWinRate()));
    seasonStatsWinRateColumn.setCellFactory(p -> new StringCell<>(n -> i18n.get("percentage", n.floatValue() * 100)));
    seasonStatsWdlColumn.setCellValueFactory(p -> new SimpleStringProperty(
        com.faforever.client.ladder.LadderUiUtil.winDrawLoss(p.getValue())));
    seasonStatsWdlColumn.setCellFactory(p -> new StringCell<>(s -> s));
    seasonStatsWdl10Column.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getRecentResults()));
    seasonStatsWdl10Column.setCellFactory(p -> new StringCell<>(s -> s));
    seasonStatsStreakColumn.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getCurrentStreak()));
    seasonStatsStreakColumn.setCellFactory(p -> new StringCell<>(n -> i18n.number(n.intValue())));
    seasonStatsBestStreakColumn.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getBestStreak()));
    seasonStatsBestStreakColumn.setCellFactory(p -> new StringCell<>(n -> i18n.number(n.intValue())));
  }

  /** The in-dialog pill (the shared display_metric_toggle include) flips the global displayMetric
   * pref; this dialog just reacts to it, swapping the rating summary + Statistics table. */
  private void initializeMetricToggle() {
    applyMetricMode();
    displayMetricListener = (obs, oldValue, newValue) -> applyMetricMode();
    JavaFxUtil.addListener(preferencesService.getPreferences().displayMetricProperty(),
        new javafx.beans.value.WeakChangeListener<>(displayMetricListener));
  }

  private boolean isLadderPointsMode() {
    return preferencesService.getPreferences().getDisplayMetric() != DisplayMetric.RATINGS;
  }

  /** Show the table + rating summary for the selected metric, (re)loading data as needed. */
  private void applyMetricMode() {
    boolean lpMode = isLadderPointsMode();
    seasonStatsTable.setVisible(lpMode);
    ratingTable.setVisible(!lpMode);
    if (player != null) {
      updateRatingGrids(lastLeaderboardEntries);   // re-render the rating summary in the new mode
      if (lpMode) {
        loadSeasonStats();
      }
    }
  }

  private void loadSeasonStats() {
    ladderPointsService.getStandingsForPlayer(player.getId())
        .thenAccept(standings -> JavaFxUtil.runLater(() -> {
          List<com.faforever.client.ladder.SeasonStanding> rows = standings.stream()
              .sorted(Comparator.comparingInt(com.faforever.client.ladder.SeasonStanding::getGames).reversed())
              .collect(Collectors.toList());
          seasonStatsTable.setItems(observableList(rows));
        }))
        .exceptionally(throwable -> {
          log.warn("Could not load season stats for player {}", player.getId(), throwable);
          return null;
        });
  }

  public Region getRoot() {
    return userInfoRoot;
  }

  private void setAvailableAchievements(List<AchievementDefinition> achievementDefinitions) {
    ObservableList<Node> children = lockedAchievementsContainer.getChildren();
    JavaFxUtil.runLater(children::clear);

    achievementDefinitions.forEach(achievementDefinition -> {
      AchievementItemController controller = uiService.loadFxml("theme/achievement_item.fxml");
      controller.setAchievementDefinition(achievementDefinition);
      achievementDefinitionById.put(achievementDefinition.getId(), achievementDefinition);
      achievementItemById.put(achievementDefinition.getId(), controller);
      JavaFxUtil.runLater(() -> children.add(controller.getRoot()));
    });
  }

  public void setPlayer(Player player) {
    this.player = player;

    usernameLabel.setText(player.getUsername());
    countryFlagService.loadCountryFlag(player.getCountry()).ifPresent(image -> countryImageView.setImage(image));

    updateNameHistory();
    countryLabel.setText(i18n.getCountryNameLocalized(player.getCountry()));

    onRatingTypeChange();

    loadAchievements();
    eventService.getPlayerEvents(player.getId())
        .thenAccept(events -> {
          plotFactionsChart(events);
          plotUnitsByCategoriesChart(events);
          plotTechBuiltChart(events);
        })
        .exceptionally(throwable -> {
          log.warn("Could not load player events", throwable);
          notificationService.addImmediateErrorNotification(throwable, "userInfo.statistics.errorLoading");
          return null;
        });

    // The rating summary + per-board games pie are driven by leaderboard entries, not player events —
    // load them independently so a player-events failure doesn't leave the rating summary blank.
    plotGamesPlayedByLeaderboardChart();

    loadReplayHistory(100)
        .thenAccept((x) -> plotGamesPlayedByModChart());

    loadLadderMedalCabinet();
    if (isLadderPointsMode()) {
      loadSeasonStats();
    }

    // TEMP: open the dialog on the Medal Cabinet tab for now (while reviewing the new cabinet).
    tabPane.getSelectionModel().select(medalCabinetTab);
  }

  /**
   * Fills the faux display cabinet on the General tab with the player's earned LP medals
   * (career totals across seasons/boards, from {@code taf_player_medal_summary}). The v1 roster
   * (LadderUiUtil.MEDAL_CODES) always gets a slot — earned slots are lit, never-earned ones are
   * shown dimmed/locked so the case reads as a collection — and any extra earned codes are
   * appended. On the viewer's OWN profile, clicking an earned medal features it next to the
   * name (CL-7, the avatar analogue); the currently featured medal also renders in the header.
   * Hidden entirely if the summary can't be read (e.g. server without LP tables).
   */
  private void loadLadderMedalCabinet() {
    ownProfile = playerService.getCurrentPlayer().map(p -> p.getId() == player.getId()).orElse(false);
    ladderPointsService.getMedalCounts(player.getId())
        .thenCombine(ladderPointsService.getFeaturedMedal(player.getId()), AbstractMap.SimpleImmutableEntry::new)
        .thenCombine(fafService.getPlayerTournamentSummary(player.getId()).exceptionally(t -> List.of()),
            (medalsAndFeatured, tournamentSummaries) -> {
          List<com.faforever.client.ladder.MedalCount> counts = medalsAndFeatured.getKey();
          java.util.Optional<String> featured = medalsAndFeatured.getValue();

          // career view: sum each LP medal code across seasons/boards
          Map<String, Long> totalByCode = counts.stream()
              .collect(Collectors.groupingBy(com.faforever.client.ladder.MedalCount::getCode,
                  Collectors.summingLong(com.faforever.client.ladder.MedalCount::getCount)));

          // Tournament placements join the case as their own selectable medals (firsts/seconds/
          // thirds summed across mods). The grand-total rollup row (featuredMod==null) is skipped
          // to avoid double-counting the per-mod rows.
          long golds = 0, silvers = 0, bronzes = 0;
          for (com.faforever.client.api.dto.PlayerTournamentSummary s : tournamentSummaries) {
            if (s.getFeaturedMod() == null) {
              continue;
            }
            golds += s.getFirsts();
            silvers += s.getSeconds();
            bronzes += s.getThirds();
          }
          if (golds > 0) totalByCode.put(com.faforever.client.ladder.LadderUiUtil.TOURNAMENT_GOLD, golds);
          if (silvers > 0) totalByCode.put(com.faforever.client.ladder.LadderUiUtil.TOURNAMENT_SILVER, silvers);
          if (bronzes > 0) totalByCode.put(com.faforever.client.ladder.LadderUiUtil.TOURNAMENT_BRONZE, bronzes);

          // Every medal in tier order (the canonical roster).
          List<String> allKnown = com.faforever.client.ladder.LadderUiUtil.MEDAL_CLASSES.stream()
              .flatMap(mc -> mc.codes().stream()).collect(Collectors.toList());

          // Lead with EARNED (accomplishment first): known earned codes in tier order, then any
          // extra/legacy earned codes. The still-to-earn medals follow, grouped by tier, so the
          // greyed set reads as an organized ladder of goals rather than a wall.
          List<String> earned = allKnown.stream()
              .filter(c -> totalByCode.getOrDefault(c, 0L) > 0)
              .collect(Collectors.toCollection(ArrayList::new));
          totalByCode.keySet().stream()
              .filter(c -> !allKnown.contains(c) && totalByCode.get(c) > 0)
              .sorted().forEach(earned::add);

          featuredMedalCode = featured.orElse(null);
          JavaFxUtil.runLater(() -> {
            medalSlotByCode.clear();
            ladderMedalCabinet.getChildren().clear();
            addCabinetSection(i18n.get("medal.cabinet.earned"), earned, totalByCode, true);
            for (com.faforever.client.ladder.LadderUiUtil.MedalClass mc
                : com.faforever.client.ladder.LadderUiUtil.MEDAL_CLASSES) {
              List<String> locked = mc.codes().stream()
                  .filter(c -> totalByCode.getOrDefault(c, 0L) == 0).collect(Collectors.toList());
              addCabinetSection(i18n.get(mc.labelKey()), locked, totalByCode, false);
            }
            applyFeaturedHighlight();
            updateFeaturedHeaderIcon();
            ladderMedalCabinetCard.setVisible(true);
            ladderMedalCabinetCard.setManaged(true);
          });
          return null;
        })
        .exceptionally(throwable -> {
          log.warn("Could not load ladder medal cabinet for player {}", player.getId(), throwable);
          return null;
        });
  }

  /** Append a labelled cabinet section: a header followed by a wrapping row of medal slots.
   * No-op when {@code codes} is empty (so a fully-earned tier doesn't leave an empty header).
   * {@code primary} marks the "Earned" section so it can read more prominently than the
   * still-to-earn tiers. Must run on the FX thread. */
  private void addCabinetSection(String header, List<String> codes, Map<String, Long> totalByCode,
                                 boolean primary) {
    if (codes.isEmpty()) {
      return;
    }
    Label headerLabel = new Label(header);
    headerLabel.getStyleClass().add(primary ? "medal-section-earned" : "medal-section-header");
    FlowPane row = new FlowPane(14, 14);
    row.getStyleClass().add("medal-section-row");
    for (String code : codes) {
      long count = totalByCode.getOrDefault(code, 0L);
      VBox slot = buildMedalSlot(code, count);
      medalSlotByCode.put(code, slot);
      row.getChildren().add(slot);
    }
    ladderMedalCabinet.getChildren().addAll(headerLabel, row);
  }

  private VBox buildMedalSlot(String code, long count) {
    boolean earned = count > 0;
    String name = com.faforever.client.ladder.LadderUiUtil.medalDisplayName(i18n, code);

    ImageView icon = new ImageView(uiService.getThemeImage(
        com.faforever.client.ladder.LadderUiUtil.medalIconPath(code)));
    icon.setFitWidth(64);
    icon.setFitHeight(64);
    icon.setPreserveRatio(true);

    VBox slot = new VBox(2, icon);
    slot.setAlignment(javafx.geometry.Pos.TOP_CENTER);
    slot.getStyleClass().addAll("medal-slot", earned ? "earned" : "locked");

    Label nameLabel = new Label(name);
    nameLabel.getStyleClass().add("medal-name");
    nameLabel.setWrapText(true);
    nameLabel.setMaxWidth(80);
    nameLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
    slot.getChildren().add(nameLabel);

    if (earned) {
      Label countBadge = new Label(i18n.get("medal.cabinet.count", count));
      countBadge.getStyleClass().add("medal-count");
      slot.getChildren().add(countBadge);
    }

    // Own profile: an earned medal can be featured (or un-featured by clicking it again).
    String tip;
    if (ownProfile && earned) {
      slot.getStyleClass().add("selectable");
      slot.setOnMouseClicked(e -> onSelectFeaturedMedal(code));
      tip = i18n.get("medal.cabinet.tooltip.selectable", name, count);
    } else {
      tip = earned
          ? i18n.get("medal.cabinet.tooltip.earned", name, count)
          : i18n.get("medal.cabinet.tooltip.locked", name);
    }
    // Append the qualitative "how to earn it" hint (flavour, not exact thresholds), so the
    // cabinet doubles as a guide to what's there to win. Falls back to the older .description.
    String desc = i18n.getWithDefault("", "medal." + code + ".desc");
    if (desc.isEmpty()) {
      desc = i18n.getWithDefault("", "medal." + code + ".description");
    }
    if (!desc.isEmpty()) {
      tip = tip + "\n" + desc;
    }
    Tooltip.install(slot, new Tooltip(tip));
    return slot;
  }

  /** Toggle the clicked medal as the player's featured medal, persist it, and reflect it in the
   * cabinet highlight + header icon. Optimistic: the UI updates immediately; a failed write is
   * logged and rolled back on the next reload. */
  private void onSelectFeaturedMedal(String code) {
    String newCode = code.equals(featuredMedalCode) ? null : code;
    featuredMedalCode = newCode;
    applyFeaturedHighlight();
    updateFeaturedHeaderIcon();
    ladderPointsService.setFeaturedMedal(player.getId(), newCode)
        .thenRun(() -> eventBus.post(new com.faforever.client.ladder.FeaturedMedalChangedEvent(player.getId())))
        .exceptionally(throwable -> {
          log.warn("Could not set featured medal {} for player {}", newCode, player.getId(), throwable);
          return null;
        });
  }

  private void applyFeaturedHighlight() {
    medalSlotByCode.forEach((code, slot) -> {
      slot.getStyleClass().remove("featured");
      if (code.equals(featuredMedalCode)) {
        slot.getStyleClass().add("featured");
      }
    });
  }

  private void updateFeaturedHeaderIcon() {
    boolean show = featuredMedalCode != null;
    if (show) {
      featuredMedalImageView.setImage(uiService.getThemeImage(
          com.faforever.client.ladder.LadderUiUtil.medalIconPath(featuredMedalCode)));
      Tooltip.install(featuredMedalImageView, new Tooltip(
          com.faforever.client.ladder.LadderUiUtil.medalDisplayName(i18n, featuredMedalCode)));
    }
    featuredMedalImageView.setVisible(show);
    featuredMedalImageView.setManaged(show);
  }

  private void updateRatingGrids(List<LeaderboardEntry> leaderboardEntries) {
    // The displayMetric pref swaps the static gauge (LADDER_POINTS_DESIGN §13.2): Season Ladder
    // shows each board's division + LP; Skill Rating shows the absolute rating rounded to nearest 100.
    boolean lpMode = preferencesService.getPreferences().getDisplayMetric() != DisplayMetric.RATINGS;

    // Skill-Rating mode: all-time games + W-D-L from the leaderboard entries. (Season Ladder mode
    // overrides these below with the current season's totals from the LP standings.)
    if (!lpMode) {
      Integer winCount = leaderboardEntries.stream().map(LeaderboardEntry::getWonGames).reduce(0, Integer::sum);
      Integer drawCount = leaderboardEntries.stream().map(LeaderboardEntry::getDrawnGames).reduce(0, Integer::sum);
      Integer lossCount = leaderboardEntries.stream().map(LeaderboardEntry::getLostGames).reduce(0, Integer::sum);
      gamesPlayedLabel.setText(i18n.number(winCount + drawCount + lossCount));
      resultsBreakdownLabel.setText(i18n.get("userInfo.winDrawLoss", winCount, drawCount, lossCount));
    }

    // The standings fetch must never blank the summary: in Skill-Rating mode it isn't even used, and
    // if LP isn't available we still want the ratings to show. Degrade to empty standings on failure.
    CompletableFuture<List<com.faforever.client.ladder.SeasonStanding>> standingsFuture =
        ladderPointsService.getStandingsForPlayer(player.getId())
            .exceptionally(throwable -> {
              log.warn("Could not load LP standings for rating summary of player {}", player.getId(), throwable);
              return List.of();
            });
    leaderboardService.getLeaderboards()
        .thenCombine(standingsFuture, (leaderboards, standings) -> {
          StringBuilder ratingNames = new StringBuilder();
          StringBuilder ratingNumbers = new StringBuilder();
          if (lpMode) {
            if (standings.isEmpty()) {
              ratingNames.append(i18n.get("leaderboard.toggle.lp.label")).append("\n");
              ratingNumbers.append(i18n.get("lp.badge.unranked")).append("\n");
            } else {
              Map<String, String> boardNames = leaderboards.stream()
                  .collect(Collectors.toMap(Leaderboard::getTechnicalName, lb -> i18n.get(lb.getNameKey()), (a, b) -> a));
              standings.stream()
                  .sorted(Comparator.comparingInt(com.faforever.client.ladder.SeasonStanding::getGames).reversed())
                  .forEach(standing -> {
                    ratingNames.append(boardNames.getOrDefault(standing.getLeaderboardTechnicalName(),
                        standing.getLeaderboardTechnicalName())).append("\n");
                    ratingNumbers.append(com.faforever.client.ladder.LadderUiUtil.standingDisplay(i18n, standing))
                        .append("\n");
                  });
            }
          } else {
            leaderboardEntries.forEach(lbe -> {
              if (lbe != null && !lbe.getLeaderboard().getLeaderboardHidden()) {
                Leaderboard lb = lbe.getLeaderboard();
                String leaderboardName = i18n.get(lb.getNameKey());
                ratingNames.append(i18n.get("leaderboard.rating", leaderboardName)).append("\n");
                // Hysteresis-stabilised tier (§13.2.3): a steady skill tier, not game-to-game movement.
                int tier = ratingTierService.displayTier(player.getId(), lb.getTechnicalName(), (int) lbe.getRating());
                ratingNumbers.append(i18n.number(tier)).append("\n");
              }
            });
          }
          JavaFxUtil.runLater(() -> {
            ratingsLabels.setText(ratingNames.toString());
            ratingsValues.setText(ratingNumbers.toString());
            // Season Ladder mode: Games Played + W-D-L are the current season's totals (summed
            // across boards), not the all-time leaderboard tally shown in Skill-Rating mode.
            if (lpMode) {
              int seasonGames = standings.stream().mapToInt(com.faforever.client.ladder.SeasonStanding::getGames).sum();
              int seasonWins = standings.stream().mapToInt(com.faforever.client.ladder.SeasonStanding::getWins).sum();
              int seasonDraws = standings.stream().mapToInt(com.faforever.client.ladder.SeasonStanding::getDraws).sum();
              int seasonLosses = standings.stream().mapToInt(com.faforever.client.ladder.SeasonStanding::getLosses).sum();
              gamesPlayedLabel.setText(i18n.number(seasonGames));
              resultsBreakdownLabel.setText(i18n.get("userInfo.winDrawLoss", seasonWins, seasonDraws, seasonLosses));
            }
            // Same first-open paint glitch as the chart: text set asynchronously after the window's
            // first paint isn't repainted until a manual resize. Force a layout of the dialog content
            // to paint the new label text (and the games/win-loss labels) immediately.
            userInfoRoot.applyCss();
            userInfoRoot.layout();
          });
          return null;
        })
        .exceptionally(throwable -> {
          log.warn("Could not populate rating summary for player {}", player.getId(), throwable);
          return null;
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

  private void loadAchievements() {
    enterAchievementsLoadingState();
    achievementService.getAchievementDefinitions()
        .exceptionally(throwable -> {
          log.warn("Player achievements could not be loaded", throwable);
          notificationService.addImmediateErrorNotification(throwable, "userInfo.achievements.errorLoading");
          return Collections.emptyList();
        })
        .thenAccept(this::setAvailableAchievements)
        .thenCompose(aVoid -> achievementService.getPlayerAchievements(player.getId()))
        .thenAccept(playerAchievements -> {
          updatePlayerAchievements(playerAchievements);
          enterAchievementsLoadedState();
        })
        .exceptionally(throwable -> {
          log.warn("Could not display achievement definitions", throwable);
          notificationService.addImmediateErrorNotification(throwable, "userInfo.achievements.errorLDisplaying");
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

  private void enterAchievementsLoadingState() {
    loadingProgressLabel.setVisible(true);
    achievementsPane.setVisible(false);
  }

  private void updatePlayerAchievements(List<? extends PlayerAchievement> playerAchievements) {
    PlayerAchievement mostRecentPlayerAchievement = null;

    ObservableList<Node> children = unlockedAchievementsContainer.getChildren();
    JavaFxUtil.runLater(children::clear);

    for (PlayerAchievement playerAchievement : playerAchievements) {
      AchievementItemController achievementItemController = achievementItemById.get(playerAchievement.getAchievement().getId());
      achievementItemController.setPlayerAchievement(playerAchievement);

      if (isUnlocked(playerAchievement)) {
        JavaFxUtil.runLater(() -> children.add(achievementItemController.getRoot()));
        if (mostRecentPlayerAchievement == null
            || playerAchievement.getUpdateTime().compareTo(mostRecentPlayerAchievement.getUpdateTime()) > 0) {
          mostRecentPlayerAchievement = playerAchievement;
        }
      }
    }

    if (mostRecentPlayerAchievement == null) {
      mostRecentAchievementPane.setVisible(false);
    } else {
      mostRecentAchievementPane.setVisible(true);
      AchievementDefinition mostRecentAchievement = achievementDefinitionById.get(mostRecentPlayerAchievement.getAchievement().getId());
      if (mostRecentAchievement == null) {
        return;
      }
      String mostRecentAchievementName = i18n.get(mostRecentAchievement.getName());
      String mostRecentAchievementDescription = i18n.get(mostRecentAchievement.getDescription());

      JavaFxUtil.runLater(() -> {
        mostRecentAchievementNameLabel.setText(mostRecentAchievementName);
        mostRecentAchievementDescriptionLabel.setText(mostRecentAchievementDescription);
        mostRecentAchievementImageView.setImage(achievementService.getImage(mostRecentAchievement, UNLOCKED));
      });
    }
  }

  private void enterAchievementsLoadedState() {
    loadingProgressLabel.setVisible(false);
    achievementsPane.setVisible(true);
  }

  private void plotGamesPlayedByLeaderboardChart() {
    JavaFxUtil.runLater(() -> gamesPlayedByLeaderboardChart.getData().clear());
    leaderboardService.getEntriesForPlayer(player.getId()).thenAccept(leaderboardEntries -> JavaFxUtil.runLater(() -> {
      List<LeaderboardEntry> sortedEntries = leaderboardEntries.stream()
          .sorted((a,b) -> (int)(b.getRating() - a.getRating()))
          .collect(Collectors.toList());
      lastLeaderboardEntries = sortedEntries;
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
    CompletableFuture<Void> ratingFuture = statisticsService.getRatingHistory(player.getId(), leaderboard)
        .thenAccept(ratingHistory -> ratingData = ratingHistory)
        .exceptionally(throwable -> {
          // FIXME display to user
          log.warn("Statistics could not be loaded", throwable);
          return null;
        });
    // Ladder Points progression for the same board (a separate metric on the graph).
    CompletableFuture<Void> lpFuture = ladderPointsService.getLpHistory(player.getId(), leaderboard.getTechnicalName())
        .thenAccept(history -> lpData = history)
        .exceptionally(throwable -> {
          log.warn("LP history could not be loaded", throwable);
          lpData = Collections.emptyList();
          return null;
        });
    return CompletableFuture.allOf(ratingFuture, lpFuture);
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

    List<XYChart.Data<Long, Integer>> ladderPointsHistory = lpData.stream()
        .sorted(Comparator.comparing(com.faforever.client.ladder.LpHistoryPoint::getInstant))
        .filter(point -> point.getInstant().isAfter(afterDate))
        .map(point -> new Data<>(point.getInstant().toEpochSecond(), point.getScore()))
        .collect(Collectors.toList());

    RatingMetric selectedMetric = ratingMetricComboBox.getSelectionModel().getSelectedItem();
    if (selectedMetric.equals(RatingMetric.TRUESKILL)) {
      values = trueskillHistory;
    }
    else if (selectedMetric.equals(RatingMetric.STREAK)) {
      values = streakHistory;
    }
    else if (selectedMetric.equals(RatingMetric.LADDER_POINTS)) {
      values = ladderPointsHistory;
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
    // Force the (now non-animated) chart to lay out its points immediately. setData only requests a
    // layout for a later pulse, which in this dialog wasn't repositioning the points until a manual
    // resize; doing it synchronously here paints them on first open.
    ratingHistoryChart.applyCss();
    ratingHistoryChart.layout();
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
