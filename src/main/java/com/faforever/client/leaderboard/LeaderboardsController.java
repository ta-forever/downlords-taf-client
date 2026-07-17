package com.faforever.client.leaderboard;

import com.faforever.client.chat.UserInfoWindowController;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.StringCell;
import com.faforever.client.i18n.I18n;
import com.faforever.client.ladder.LadderPointsService;
import com.faforever.client.ladder.LadderUiUtil;
import com.faforever.client.ladder.SeasonInfo;
import com.faforever.client.ladder.SeasonStanding;
import com.faforever.client.main.event.ShowUserReplaysEvent;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.ModService;
import com.faforever.client.remote.FafService;
import com.faforever.client.teammatchmaking.MatchmakingQueue;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.DisplayMetric;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.TimeService;
import com.faforever.client.util.Validator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static javafx.collections.FXCollections.observableList;

import javafx.collections.FXCollections;


@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class LeaderboardsController extends AbstractViewController<Node> {

  private final LeaderboardService leaderboardService;
  private final LadderPointsService ladderPointsService;
  private final com.faforever.client.ladder.LadderSocialService ladderSocialService;
  private final NotificationService notificationService;
  private final ModService modService;
  private final FafService fafService;
  private final UiService uiService;
  private final PlayerService playerService;
  private final PreferencesService preferencesService;
  private final EventBus eventBus;
  private final I18n i18n;
  private final TimeService timeService;
  public Pane leaderboardRoot;
  public TableColumn<LeaderboardEntry, Number> rankColumn;
  public TableColumn<LeaderboardEntry, String> nameColumn;
  public TableColumn<LeaderboardEntry, Number> ratingColumn;
  public TableColumn<LeaderboardEntry, Number> gamesPlayedColumn;
  public TableColumn<LeaderboardEntry, Number> winRateColumn;
  public TableColumn<LeaderboardEntry, String> allResultsColumn;
  public TableColumn<LeaderboardEntry, String> recentResultsColumn;
  public TableColumn<LeaderboardEntry, Number> streakColumn;
  public TableColumn<LeaderboardEntry, Number> bestStreakColumn;
  public TableView<LeaderboardEntry> ratingTable;
  public javafx.scene.control.Label seasonTitleLabel;
  public TableView<SeasonStanding> seasonLadderTable;
  public TableColumn<SeasonStanding, Number> seasonRankColumn;
  public TableColumn<SeasonStanding, String> seasonNameColumn;
  public TableColumn<SeasonStanding, Number> seasonPointsColumn;
  public TableColumn<SeasonStanding, Number> seasonGamesColumn;
  public TableColumn<SeasonStanding, Number> seasonMedalsColumn;
  public TableColumn<SeasonStanding, Number> seasonWinRateColumn;
  public TableColumn<SeasonStanding, String> seasonWdlColumn;
  public TableColumn<SeasonStanding, String> seasonWdl10Column;
  public TableColumn<SeasonStanding, Number> seasonStreakColumn;
  public TableColumn<SeasonStanding, Number> seasonBestStreakColumn;
  public TableColumn<SeasonStanding, Number> seasonWagerColumn;
  public ComboBox<Leaderboard> leaderboardComboBox;
  public ComboBox<com.faforever.client.ladder.SeasonInfo> seasonComboBox;
  public ComboBox<String> modComboBox;
  public javafx.scene.control.TitledPane hallOfFameTitledPane;
  public TableView<com.faforever.client.ladder.HallOfFameEntry> hallOfFameTable;
  public TableColumn<com.faforever.client.ladder.HallOfFameEntry, Number> hofRankColumn;
  public TableColumn<com.faforever.client.ladder.HallOfFameEntry, String> hofNameColumn;
  public TableColumn<com.faforever.client.ladder.HallOfFameEntry, Number> hofGoldColumn;
  public TableColumn<com.faforever.client.ladder.HallOfFameEntry, Number> hofSilverColumn;
  public TableColumn<com.faforever.client.ladder.HallOfFameEntry, Number> hofBronzeColumn;
  public TextField searchTextField;
  public Pane connectionProgressPane;
  public Pane contentPane;
  public CheckBox friendsOnlyCheckBox;
  /** Re-rendered when the global displayMetric pref flips (e.g. via the top-bar pill). Held as a
   * field so the weak listener isn't collected. */
  private javafx.beans.value.ChangeListener<com.faforever.client.preferences.DisplayMetric> displayMetricListener;

  @VisibleForTesting
  protected AutoCompletionBinding<String> usernamesAutoCompletion;

  private List<Leaderboard> allLeaderboards;
  private Map<String, String> leaderboardToModTech = new HashMap<>();
  /** player id -> medals earned in the selected season, for the Season Ladder medal column. */
  private Map<Integer, Long> seasonMedalCounts = Map.of();
  /** Remembers the season the user last picked per board for this session — so navigating away and
   * back keeps the selection, while a fresh app start still defaults to the current season. */
  private final Map<String, Integer> selectedSeasonByBoard = new HashMap<>();
  /** True while the season picker is being populated programmatically, so its onAction doesn't fire
   * a redundant reload mid-populate. */
  private boolean populatingSeasons;

  @Override
  public void initialize() {
    super.initialize();
    friendsOnlyCheckBox.setSelected(preferencesService.getPreferences().getLastLeaderboardFriendsOnlySelection());

    leaderboardService.getLeaderboards()
        .thenCombine(modService.getFeaturedMods(), (leaderboards, featuredMods) -> {
          Map<String, String> lbTechToModTech = new HashMap<>();
          leaderboards.stream()
              .filter(lb -> "global".equals(lb.getTechnicalName()))
              .forEach(lb -> lbTechToModTech.put("global", "global"));

          List<CompletableFuture<?>> queueFutures = new ArrayList<>();
          for (FeaturedMod mod : featuredMods) {
            String modTech = mod.getTechnicalName();
            queueFutures.add(
                fafService.getMatchmakerQueuesByMod(modTech).thenAccept(queues -> {
                  for (MatchmakingQueue q : queues) {
                    if (q.getLeaderboard() != null) {
                      String lbTech = q.getLeaderboard().getTechnicalName();
                      if (lbTech != null) {
                        lbTechToModTech.put(lbTech, modTech);
                      }
                    }
                  }
                })
            );
          }

          CompletableFuture.allOf(queueFutures.toArray(new CompletableFuture[0]))
              .thenRun(() -> JavaFxUtil.runLater(() ->
                  initialiseModAndLeaderboardComboBoxes(leaderboards, featuredMods, lbTechToModTech)
              ));
          return null;
        });

    rankColumn.setCellValueFactory(param -> new SimpleIntegerProperty(ratingTable.getItems().indexOf(param.getValue()) + 1));
    rankColumn.setCellFactory(param -> new StringCell<>(rank -> i18n.number(rank.intValue())));

    nameColumn.setCellValueFactory(param -> param.getValue().usernameProperty());
    nameColumn.setCellFactory(param -> new StringCell<>(name -> name));

    winRateColumn.setCellValueFactory(param -> new SimpleFloatProperty(param.getValue().getWinRate()));
    winRateColumn.setCellFactory(param -> new StringCell<>(number -> i18n.get("percentage", number.floatValue() * 100)));

    recentResultsColumn.setCellValueFactory(param -> param.getValue().recentResultsProperty());
    recentResultsColumn.setCellFactory(param -> new StringCell<>(rate -> rate));

    allResultsColumn.setCellValueFactory(param -> param.getValue().allResultsProperty());
    allResultsColumn.setCellFactory(param -> new StringCell<>(results -> results));

    streakColumn.setCellValueFactory(param -> param.getValue().streakProperty());
    streakColumn.setCellFactory(param -> new StringCell<>(streak -> i18n.number(streak.intValue())));

    bestStreakColumn.setCellValueFactory(param -> param.getValue().bestStreakProperty());
    bestStreakColumn.setCellFactory(param -> new StringCell<>(streak -> i18n.number(streak.intValue())));

    gamesPlayedColumn.setCellValueFactory(param -> param.getValue().totalGamesProperty());
    gamesPlayedColumn.setCellFactory(param -> new StringCell<>(count -> i18n.number(count.intValue())));

    ratingColumn.setCellValueFactory(param -> param.getValue().ratingProperty());
    ratingColumn.setCellFactory(param -> new StringCell<>(rating -> i18n.number(rating.intValue())));

    // Season Ladder table (LP mode). SeasonStanding is immutable, so wrap each value per row.
    seasonRankColumn.setCellValueFactory(param ->
        new SimpleIntegerProperty(seasonLadderTable.getItems().indexOf(param.getValue()) + 1));
    seasonRankColumn.setCellFactory(param -> new StringCell<>(rank -> i18n.number(rank.intValue())));
    seasonNameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getPlayerLogin()));
    seasonNameColumn.setCellFactory(param -> new StringCell<>(name -> name));
    seasonPointsColumn.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getScore()));
    seasonPointsColumn.setCellFactory(param -> new StringCell<>(lp -> i18n.number(lp.intValue())));
    seasonGamesColumn.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getGames()));
    seasonGamesColumn.setCellFactory(param -> new StringCell<>(count -> i18n.number(count.intValue())));
    seasonMedalsColumn.setCellValueFactory(param -> new SimpleIntegerProperty(
        seasonMedalCounts.getOrDefault(param.getValue().getPlayerId(), 0L).intValue()));
    seasonMedalsColumn.setCellFactory(param -> new StringCell<>(count -> i18n.number(count.intValue())));
    // Per-season result stats (design §13): same columns as Ratings, but LP-scoped and season-reset.
    seasonWinRateColumn.setCellValueFactory(param -> new SimpleFloatProperty(param.getValue().getWinRate()));
    seasonWinRateColumn.setCellFactory(param -> new StringCell<>(number -> i18n.get("percentage", number.floatValue() * 100)));
    seasonWdlColumn.setCellValueFactory(param -> new SimpleStringProperty(LadderUiUtil.winDrawLoss(param.getValue())));
    seasonWdlColumn.setCellFactory(param -> new StringCell<>(s -> s));
    seasonWdl10Column.setCellValueFactory(param -> new SimpleStringProperty(LadderUiUtil.recentWinDrawLoss(param.getValue())));
    seasonWdl10Column.setCellFactory(param -> new StringCell<>(s -> s));
    seasonStreakColumn.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getCurrentStreak()));
    seasonStreakColumn.setCellFactory(param -> new StringCell<>(streak -> i18n.number(streak.intValue())));
    seasonBestStreakColumn.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getBestStreak()));
    seasonBestStreakColumn.setCellFactory(param -> new StringCell<>(streak -> i18n.number(streak.intValue())));
    // Wager P&L (V137, Option B): the gambling-portion breakdown of score. Signed; blank at 0
    // so a non-gambler's row isn't cluttered with "0".
    seasonWagerColumn.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getWagerNet()));
    seasonWagerColumn.setCellFactory(param -> new StringCell<>(net -> {
      int n = net.intValue();
      return n == 0 ? "" : (n > 0 ? "+" : "") + i18n.number(n);
    }));

    // Hall of fame: #1/#2/#3 podium tally across completed seasons. Rank is the row position.
    hofRankColumn.setCellValueFactory(param ->
        new SimpleIntegerProperty(hallOfFameTable.getItems().indexOf(param.getValue()) + 1));
    hofRankColumn.setCellFactory(param -> new StringCell<>(rank -> i18n.number(rank.intValue())));
    hofNameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getPlayerLogin()));
    hofNameColumn.setCellFactory(param -> new StringCell<>(name -> name));
    hofGoldColumn.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getGold()));
    hofGoldColumn.setCellFactory(param -> new StringCell<>(n -> i18n.number(n.intValue())));
    hofSilverColumn.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getSilver()));
    hofSilverColumn.setCellFactory(param -> new StringCell<>(n -> i18n.number(n.intValue())));
    hofBronzeColumn.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getBronze()));
    hofBronzeColumn.setCellFactory(param -> new StringCell<>(n -> i18n.number(n.intValue())));
    setMedalColumnGraphic(hofGoldColumn, LadderUiUtil.TOURNAMENT_GOLD);
    setMedalColumnGraphic(hofSilverColumn, LadderUiUtil.TOURNAMENT_SILVER);
    setMedalColumnGraphic(hofBronzeColumn, LadderUiUtil.TOURNAMENT_BRONZE);
    hallOfFameTitledPane.managedProperty().bind(hallOfFameTitledPane.visibleProperty());

    contentPane.managedProperty().bind(contentPane.visibleProperty());
    connectionProgressPane.managedProperty().bind(connectionProgressPane.visibleProperty());
    connectionProgressPane.visibleProperty().bind(contentPane.visibleProperty().not());

    ratingTable.managedProperty().bind(ratingTable.visibleProperty());
    seasonLadderTable.managedProperty().bind(seasonLadderTable.visibleProperty());
    seasonTitleLabel.managedProperty().bind(seasonTitleLabel.visibleProperty());
    seasonComboBox.managedProperty().bind(seasonComboBox.visibleProperty());
    seasonComboBox.setConverter(seasonStringConverter());

    // The metric is driven by the global pref (flipped via the top-bar pill, design §13.1); react to
    // it instead of owning a toggle here.
    applyMode();
    displayMetricListener = (obs, oldValue, newValue) -> {
      applyMode();
      reload();
    };
    JavaFxUtil.addListener(preferencesService.getPreferences().displayMetricProperty(),
        new javafx.beans.value.WeakChangeListener<>(displayMetricListener));

    searchTextField.textProperty().addListener((observable, oldValue, newValue) -> scrollToSearch(newValue));
  }

  /** Show the table for the currently selected metric; the other is hidden + unmanaged. */
  private void applyMode() {
    boolean lpMode = isLadderPointsMode();
    seasonLadderTable.setVisible(lpMode);
    ratingTable.setVisible(!lpMode);
    if (!lpMode) {
      seasonComboBox.setVisible(false);       // the season picker + hall of fame belong to the
      hallOfFameTitledPane.setVisible(false); // Season Ladder only
      seasonTitleLabel.setVisible(false);
    }
  }

  private boolean isLadderPointsMode() {
    return preferencesService.getPreferences().getDisplayMetric() != DisplayMetric.RATINGS;
  }

  private void scrollToSearch(String newValue) {
    if (isLadderPointsMode()) {
      searchInTable(seasonLadderTable, newValue, SeasonStanding::getPlayerLogin);
    } else {
      searchInTable(ratingTable, newValue, LeaderboardEntry::getUsername);
    }
  }

  private <T> void searchInTable(TableView<T> table, String newValue, Function<T, String> nameFn) {
    if (Validator.isInt(newValue)) {
      table.scrollTo(Integer.parseInt(newValue) - 1);
      return;
    }
    String needle = newValue.toLowerCase();
    T found = table.getItems().stream()
        .filter(row -> nameFn.apply(row) != null && nameFn.apply(row).toLowerCase().startsWith(needle))
        .findFirst()
        .orElseGet(() -> table.getItems().stream()
            .filter(row -> nameFn.apply(row) != null && nameFn.apply(row).toLowerCase().contains(needle))
            .findFirst().orElse(null));
    if (found != null) {
      table.scrollTo(found);
      table.getSelectionModel().select(found);
    } else {
      table.getSelectionModel().select(null);
    }
  }

  private void initialiseModAndLeaderboardComboBoxes(List<Leaderboard> leaderboards, List<FeaturedMod> featuredMods, Map<String, String> lbTechToModTech) {
    allLeaderboards = leaderboards;
    this.leaderboardToModTech = (lbTechToModTech != null) ? lbTechToModTech : new HashMap<>();

    Map<String, String> modDisplayNames = featuredMods.stream()
        .collect(Collectors.toMap(FeaturedMod::getTechnicalName,
            fm -> fm.getDisplayName() != null ? fm.getDisplayName() : fm.getTechnicalName(),
            (a, b) -> a));

    Set<String> modTechs = leaderboards.stream()
        .map(lb -> lbTechToModTech.getOrDefault(lb.getTechnicalName(), extractModFromLeaderboard(lb.getTechnicalName())))
        .collect(Collectors.toCollection(LinkedHashSet::new));

    List<String> modList = new ArrayList<>();
    if (modTechs.contains("global")) {
      modList.add("global");
      modTechs.remove("global");
    }
    modList.addAll(modTechs);

    modComboBox.setConverter(new StringConverter<>() {
      @Override
      public String toString(String modTech) {
        if (modTech == null) {
          return "";
        }
        if ("global".equals(modTech)) {
          return "Global";
        }
        return modDisplayNames.getOrDefault(modTech, modTech);
      }

      @Override
      public String fromString(String string) {
        return null;
      }
    });

    modComboBox.setItems(FXCollections.observableArrayList(modList));

    leaderboardComboBox.setConverter(leaderboardStringConverter());

    modComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue != null) {
        preferencesService.getPreferences().setLastLeaderboardModSelection(newValue);
        preferencesService.storeInBackground();
        updateLeaderboardItemsForMod(newValue);
      }
    });

    // initial select will trigger the listener above to populate lb combo + load
    String lastMod = preferencesService.getPreferences().getLastLeaderboardModSelection();
    if (lastMod != null && modList.contains(lastMod)) {
      modComboBox.getSelectionModel().select(lastMod);
    } else if (!modList.isEmpty()) {
      modComboBox.getSelectionModel().selectFirst();
    } else {
      // fallback
      leaderboardComboBox.setItems(FXCollections.observableArrayList(leaderboards));
      selectAppropriateLeaderboard();
      onLeaderboardSelected();
    }
  }

  private String extractModFromLeaderboard(String technicalName) {
    if (technicalName == null) {
      return "";
    }
    int idx = technicalName.lastIndexOf('_');
    return idx >= 0 ? technicalName.substring(idx + 1) : technicalName;
  }

  private void updateLeaderboardItemsForMod(String modTech) {
    List<Leaderboard> filtered = allLeaderboards.stream()
        .filter(lb -> {
          String resolved = leaderboardToModTech.getOrDefault(lb.getTechnicalName(), extractModFromLeaderboard(lb.getTechnicalName()));
          return resolved.equals(modTech);
        })
        .collect(Collectors.toList());
    leaderboardComboBox.setItems(FXCollections.observableArrayList(filtered));
    selectAppropriateLeaderboard();
    onLeaderboardSelected();
  }

  // kept for fallback in other places if needed, but main filter uses the map now
  private boolean matchesMod(String technicalName, String modTech) {
    if (technicalName == null || modTech == null) {
      return false;
    }
    if ("global".equals(modTech)) {
      return "global".equals(technicalName);
    }
    return technicalName.endsWith("_" + modTech) || technicalName.equals(modTech);
  }

  private void selectAppropriateLeaderboard() {
    leaderboardComboBox.getItems().stream()
        .filter(lbe -> lbe.getTechnicalName().equals(preferencesService.getPreferences().getLastLeaderboardSelection()))
        .findAny()
        .ifPresentOrElse(
            lbe -> leaderboardComboBox.getSelectionModel().select(lbe),
            () -> leaderboardComboBox.getSelectionModel().selectFirst());
  }

  @NotNull
  private StringConverter<Leaderboard> leaderboardStringConverter() {
    return new StringConverter<>() {
      @Override
      public String toString(Leaderboard leaderboard) {
        if (leaderboard != null) {
          return i18n.get(leaderboard.getNameKey());
        } else {
          return "<none>";
        }
      }

      @Override
      public Leaderboard fromString(String string) {
        return null;
      }
    };
  }

  @NotNull
  private StringConverter<SeasonInfo> seasonStringConverter() {
    return new StringConverter<>() {
      @Override
      public String toString(SeasonInfo season) {
        if (season == null) {
          return "";
        }
        String range = i18n.get("leaderboard.season.range",
            season.getFrom() != null ? timeService.asDate(season.getFrom()) : "?",
            season.getTo() != null ? timeService.asDate(season.getTo()) : "?");
        return isCurrentSeason(season) ? i18n.get("leaderboard.season.current", range) : range;
      }

      @Override
      public SeasonInfo fromString(String string) {
        return null;
      }
    };
  }

  public void onLeaderboardSelected() {
    if (leaderboardComboBox.getValue() == null) {
      return;
    }
    preferencesService.getPreferences().setLastLeaderboardSelection(leaderboardComboBox.getValue().getTechnicalName());
    preferencesService.getPreferences().setLastLeaderboardFriendsOnlySelection(friendsOnlyCheckBox.isSelected());
    preferencesService.storeInBackground();
    reload();
  }

  /** Loads the table for the currently selected metric + board. */
  private void reload() {
    contentPane.setVisible(false);
    searchTextField.clear();
    if (usernamesAutoCompletion != null) {
      usernamesAutoCompletion.dispose();
    }
    if (leaderboardComboBox.getValue() == null) {
      return;
    }
    if (isLadderPointsMode()) {
      loadSeasonLadder();
    } else {
      loadRatings();
    }
  }

  /** Season-ladder entry point: load the board's seasons, restore/choose a selection, then load it. */
  private void loadSeasonLadder() {
    String technicalName = leaderboardComboBox.getValue().getTechnicalName();
    loadHallOfFame(technicalName);
    ladderPointsService.getSeasons(technicalName)
        .thenAccept(seasons -> JavaFxUtil.runLater(() -> {
          // Ignore a late result if the user switched back to Ratings or changed board meanwhile.
          if (!isLadderPointsMode() || leaderboardComboBox.getValue() == null
              || !technicalName.equals(leaderboardComboBox.getValue().getTechnicalName())) {
            return;
          }
          if (seasons.isEmpty()) {
            seasonComboBox.setVisible(false);
            seasonTitleLabel.setVisible(false);
            seasonComboBox.getItems().clear();
            seasonLadderTable.setItems(observableList(List.of()));
            contentPane.setVisible(true);
            return;
          }
          SeasonInfo toSelect = chooseSeason(technicalName, seasons);
          // Populate the picker without its onAction firing a second, redundant load.
          populatingSeasons = true;
          seasonComboBox.setItems(observableList(seasons));
          seasonComboBox.getSelectionModel().select(toSelect);
          seasonComboBox.setVisible(true);
          populatingSeasons = false;
          loadSeasonLadderForSelected();
        }))
        .exceptionally(throwable -> {
          JavaFxUtil.runLater(() -> contentPane.setVisible(false));
          log.warn("Error while loading seasons", throwable);
          notificationService.addImmediateErrorNotification(throwable, "leaderboard.failedToLoad");
          return null;
        });
  }

  /** Loads the board's hall of fame (podium tally over completed seasons); hidden if there are no
   * completed seasons yet. Independent of the selected season — it spans all of them. */
  private void loadHallOfFame(String technicalName) {
    ladderPointsService.getHallOfFame(technicalName)
        .thenAccept(entries -> JavaFxUtil.runLater(() -> {
          // Ignore a late result if the user switched back to Ratings or changed board meanwhile.
          if (!isLadderPointsMode() || leaderboardComboBox.getValue() == null
              || !technicalName.equals(leaderboardComboBox.getValue().getTechnicalName())) {
            return;
          }
          hallOfFameTable.setItems(observableList(entries));
          hallOfFameTitledPane.setVisible(!entries.isEmpty());
        }))
        .exceptionally(throwable -> {
          log.warn("Could not load hall of fame for {}", technicalName, throwable);
          JavaFxUtil.runLater(() -> hallOfFameTitledPane.setVisible(false));
          return null;
        });
  }

  private void setMedalColumnGraphic(TableColumn<?, ?> column, String medalCode) {
    ImageView icon = new ImageView(uiService.getThemeImage(LadderUiUtil.medalIconPath(medalCode)));
    icon.setFitWidth(16);
    icon.setFitHeight(16);
    icon.setPreserveRatio(true);
    column.setGraphic(icon);
  }

  /** Restore the season the user last picked for this board if it still exists, else default to the
   * current season (whose window contains now), else the most recent. */
  private SeasonInfo chooseSeason(String technicalName, List<SeasonInfo> seasons) {
    Integer remembered = selectedSeasonByBoard.get(technicalName);
    if (remembered != null) {
      for (SeasonInfo s : seasons) {
        if (s.getSeasonId() == remembered) {
          return s;
        }
      }
    }
    return seasons.stream().filter(this::isCurrentSeason).findFirst().orElse(seasons.get(0));
  }

  /** Title the Season Ladder with the selected season's description; hide the title if the season
   * has no description. */
  private void applySeasonTitle(SeasonInfo season) {
    String description = season != null ? season.getDescription() : null;
    if (description != null && !description.isBlank()) {
      seasonTitleLabel.setText(description);
      seasonTitleLabel.setVisible(true);
    } else {
      seasonTitleLabel.setVisible(false);
    }
  }

  private boolean isCurrentSeason(SeasonInfo s) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return s.getFrom() != null && s.getTo() != null
        && !now.isBefore(s.getFrom()) && !now.isAfter(s.getTo());
  }

  /** User picked a season — remember it for this board (for the session), then reload the ladder. */
  public void onSeasonSelected() {
    if (populatingSeasons || seasonComboBox.getValue() == null || leaderboardComboBox.getValue() == null) {
      return;
    }
    selectedSeasonByBoard.put(leaderboardComboBox.getValue().getTechnicalName(),
        seasonComboBox.getValue().getSeasonId());
    loadSeasonLadderForSelected();
  }

  /** Load + render the ladder and medal counts for the currently selected board + season. */
  private void loadSeasonLadderForSelected() {
    Leaderboard board = leaderboardComboBox.getValue();
    SeasonInfo season = seasonComboBox.getValue();
    if (board == null || season == null) {
      return;
    }
    String technicalName = board.getTechnicalName();
    String boardDisplayName = i18n.get(board.getNameKey());
    int seasonId = season.getSeasonId();
    boolean current = isCurrentSeason(season);
    applySeasonTitle(season);
    contentPane.setVisible(false);
    if (usernamesAutoCompletion != null) {
      usernamesAutoCompletion.dispose();
    }
    ladderPointsService.getSeasonLadder(technicalName, seasonId, 1000, 1)
        .thenCombine(ladderPointsService.getSeasonMedalCounts(technicalName, seasonId), (standings, medalCounts) -> {
      List<SeasonStanding> rows = friendsOnlyCheckBox.isSelected()
          ? standings.stream().filter(s -> playerService.isFriend(s.getPlayerId())).collect(Collectors.toList())
          : standings;
      JavaFxUtil.runLater(() -> {
        seasonMedalCounts = medalCounts;
        // "You passed a friend" toasts (§15.2): only meaningful for the live (current) season, and
        // diffed against the full ladder, not the friends-only view.
        if (current) {
          ladderSocialService.detectPasses(technicalName, boardDisplayName, standings);
        }
        seasonLadderTable.setItems(observableList(rows));
        usernamesAutoCompletion = TextFields.bindAutoCompletion(searchTextField,
            rows.stream().map(SeasonStanding::getPlayerLogin).filter(Objects::nonNull).collect(Collectors.toList()));
        usernamesAutoCompletion.setDelay(0);
        contentPane.setVisible(true);
      });
      return null;
    }).exceptionally(throwable -> {
      JavaFxUtil.runLater(() -> contentPane.setVisible(false));
      log.warn("Error while loading season ladder", throwable);
      notificationService.addImmediateErrorNotification(throwable, "leaderboard.failedToLoad");
      return null;
    });
  }

  private void loadRatings() {
    leaderboardService.getEntries(leaderboardComboBox.getValue()).thenAccept(leaderboardEntryBeans -> {
      if (friendsOnlyCheckBox.isSelected()) {
        leaderboardEntryBeans = leaderboardEntryBeans.stream()
            .filter(leaderboardEntry -> playerService.isFriend(leaderboardEntry.getUserId()))
            .collect(Collectors.toList());
      }
      List<LeaderboardEntry> finalLeaderboardEntryBeans = leaderboardEntryBeans;
      JavaFxUtil.runLater(() -> {
        ratingTable.setItems(observableList(finalLeaderboardEntryBeans));
        usernamesAutoCompletion = TextFields.bindAutoCompletion(
            searchTextField,
            finalLeaderboardEntryBeans.stream().map(LeaderboardEntry::getUsername).collect(Collectors.toList()))
        ;
        usernamesAutoCompletion.setDelay(0);
        contentPane.setVisible(true);
      });
    }).exceptionally(throwable -> {
      contentPane.setVisible(false);
      log.warn("Error while loading leaderboard entries", throwable);
      notificationService.addImmediateErrorNotification(throwable, "leaderboard.failedToLoad");
      return null;
    });
  }

  public Node getRoot() {
    return leaderboardRoot;
  }

  public void openContextMenu(ContextMenuEvent event) {
    int index = ratingTable.getSelectionModel().selectedIndexProperty().get();
    if (index < 0) {
      return;
    }
    showUserContextMenu(ratingTable.getItems().get(index).getUsername(), event);
  }

  public void openSeasonContextMenu(ContextMenuEvent event) {
    int index = seasonLadderTable.getSelectionModel().selectedIndexProperty().get();
    if (index < 0) {
      return;
    }
    showUserContextMenu(seasonLadderTable.getItems().get(index).getPlayerLogin(), event);
  }

  private void showUserContextMenu(String userName, ContextMenuEvent event) {
    if (userName == null) {
      return;
    }
    playerService.getPlayerByName(userName)
        .thenAccept(optionalPlayer -> {
          if (optionalPlayer.isPresent()) JavaFxUtil.runLater(() -> {
            ContextMenu contextMenu = new ContextMenu();

            MenuItem userInfoMenuItem = new MenuItem(i18n.get("chat.userContext.userInfo"));
            userInfoMenuItem.setOnAction(e -> showUserInfo(optionalPlayer.get()));
            contextMenu.getItems().add(userInfoMenuItem);

            MenuItem viewReplaysMenuItem = new MenuItem(i18n.get("chat.userContext.viewReplays"));
            viewReplaysMenuItem.setOnAction(e -> showUserReplays(optionalPlayer.get()));
            contextMenu.getItems().add(viewReplaysMenuItem);

            contextMenu.show(this.getRoot().getScene().getWindow(), event.getScreenX(), event.getScreenY());
          });
        });
  }

  public void showUserInfo(Player player) {
    UserInfoWindowController userInfoWindowController = uiService.loadFxml("theme/user_info_window.fxml");
    userInfoWindowController.setPlayer(player);
    userInfoWindowController.setOwnerWindow(this.getRoot().getScene().getWindow());
    userInfoWindowController.show();
  }

  public void showUserReplays(Player player) {
    eventBus.post(new ShowUserReplaysEvent(player.getId()));
  }

}
