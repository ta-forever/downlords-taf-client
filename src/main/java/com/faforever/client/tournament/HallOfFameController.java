package com.faforever.client.tournament;

import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.RefreshTournamentsEvent;
import com.faforever.client.main.event.ShowGameIdReplaysEvent;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.ModService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.theme.UiService;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tournament Hall of Fame — a TableView of every player who has participated
 * in (or placed in) a completed tournament, sorted by golds → silvers →
 * bronzes → participations → name. Filterable by featured mod (or all mods),
 * searchable by player login. Persistent mod filter via Preferences.
 *
 * <p>Lives inside the Tournaments page as a sub-tab. Backed by the
 * {@code playerTournamentSummary} JSON:API endpoint (which itself reads from
 * the {@code player_tournament_summary} SQL view).
 *
 * <p>Auto-refreshes on {@link RefreshTournamentsEvent} so finishing a
 * tournament updates the Hall of Fame without a manual refresh, debounced
 * 500ms to coalesce bursts.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class HallOfFameController extends AbstractViewController<Node> {

  /** Visual size of medal/cookie icons inside cells. */
  private static final double ICON_SIZE = 22.0;
  /** Debounce window for auto-refresh after RefreshTournamentsEvent. */
  private static final int AUTO_REFRESH_DEBOUNCE_MS = 500;

  /** PseudoClass that marks the row containing the local player. */
  private static final PseudoClass CURRENT_PLAYER_PSEUDO_CLASS = PseudoClass.getPseudoClass("current-player");

  private final TournamentService tournamentService;
  private final PlayerService playerService;
  private final PreferencesService preferencesService;
  private final UiService uiService;
  private final EventBus eventBus;
  private final I18n i18n;

  // FXML-injected
  public VBox hallOfFameRoot;
  public TextField searchField;
  public TableView<HallOfFameEntryBean> hallOfFameTable;
  public Label emptyStateLabel;

  /**
   * Master copy of the most recent fetch, in canonical rank order. Plain
   * (non-Observable) — the table doesn't see this directly. The search box
   * filters from here into {@link #tableEntries}.
   */
  private final List<HallOfFameEntryBean> rankedEntries = new ArrayList<>();

  /**
   * What the TableView actually sees. A plain {@link ObservableList} so
   * JavaFX TableView can sort it in place via {@code FXCollections.sort}
   * when the user clicks a column header — no SortedList wrapper, no
   * comparator binding, no info-log warnings about missing bindings.
   * Updated wholesale by {@link #applyFilter}.
   */
  private final ObservableList<HallOfFameEntryBean> tableEntries = FXCollections.observableArrayList();

  /** The Rank column — kept as a field so loadHallOfFame can re-install it
   * into the table's sortOrder after each fetch (default sort). */
  private TableColumn<HallOfFameEntryBean, Number> rankColumn;

  /** Cached medal/cookie images so cells don't reload them every time. */
  private Image goldMedal;
  private Image silverMedal;
  private Image bronzeMedal;
  private Image cookie;

  /** Pending debounced refresh task. */
  private javafx.animation.Timeline pendingRefresh;
  /** True once at least one fetch has completed (so onDisplay doesn't re-fetch on every tab switch). */
  private boolean hasLoadedOnce;

  public HallOfFameController(TournamentService tournamentService,
                              PlayerService playerService,
                              PreferencesService preferencesService,
                              UiService uiService,
                              EventBus eventBus,
                              I18n i18n) {
    this.tournamentService = tournamentService;
    this.playerService = playerService;
    this.preferencesService = preferencesService;
    this.uiService = uiService;
    this.eventBus = eventBus;
    this.i18n = i18n;
  }

  @Override
  public Node getRoot() {
    return hallOfFameRoot;
  }

  @Override
  public void initialize() {
    loadIcons();
    setupTable();
    setupSearchField();
    setupContextMenu();
    eventBus.register(this);
  }

  private void loadIcons() {
    // Loaded once at controller construction; reused as the Image instance for
    // every cell (JavaFX shares Image data, so each ImageView is cheap).
    // Paths must be theme-relative (i.e. include the leading "theme/") so
    // UiService.getThemeFile can both look for an external override in the
    // user's theme directory and fall back to the classpath resource.
    goldMedal = uiService.getThemeImage("theme/images/hall_of_fame/gold_medal.png");
    silverMedal = uiService.getThemeImage("theme/images/hall_of_fame/silver_medal.png");
    bronzeMedal = uiService.getThemeImage("theme/images/hall_of_fame/bronze_medal.png");
    cookie = uiService.getThemeImage("theme/images/hall_of_fame/cookie.png");
  }

  // ===== Table setup =====

  private void setupTable() {
    // Plain ObservableList — TableView handles column-header sorting in
    // place via FXCollections.sort. No SortedList / FilteredList wrappers,
    // no comparator binding. The search box reaches into rankedEntries
    // and rebuilds tableEntries via setAll (see applyFilter).
    hallOfFameTable.setItems(tableEntries);

    // Empty-state visibility flips whenever the visible row count changes.
    tableEntries.addListener((javafx.collections.ListChangeListener<HallOfFameEntryBean>) c -> updateEmptyState());

    // Rank column — reads the stable rank stored on the bean. Stays put
    // when the user clicks a different column header. After the columns
    // are built, loadHallOfFame() installs this column into the table's
    // sortOrder so the table opens in rank-ascending order by default.
    TableColumn<HallOfFameEntryBean, Number> rankCol = new TableColumn<>(i18n.get("hallOfFame.column.rank"));
    rankCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getRank()));
    rankCol.setComparator(Comparator.<Number>comparingInt(Number::intValue));
    rankCol.setSortType(TableColumn.SortType.ASCENDING);
    rankCol.setPrefWidth(50);
    rankCol.setStyle("-fx-alignment: CENTER;");
    hallOfFameTable.getColumns().add(rankCol);
    this.rankColumn = rankCol;

    // Player name column.
    TableColumn<HallOfFameEntryBean, String> playerCol = new TableColumn<>(i18n.get("hallOfFame.column.player"));
    playerCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getPlayerLogin()));
    playerCol.setComparator(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    playerCol.setPrefWidth(180);
    hallOfFameTable.getColumns().add(playerCol);

    // Medal columns + cookie column. Each header has the icon as its
    // graphic and a short text label ("Gold", "Silver", "Bronze",
    // "Played"); cells render just the count.
    hallOfFameTable.getColumns().add(makeIconCountColumn(
        "hallOfFame.column.firsts", "hallOfFame.column.firsts.tooltip",
        goldMedal, HallOfFameEntryBean::getFirsts));
    hallOfFameTable.getColumns().add(makeIconCountColumn(
        "hallOfFame.column.seconds", "hallOfFame.column.seconds.tooltip",
        silverMedal, HallOfFameEntryBean::getSeconds));
    hallOfFameTable.getColumns().add(makeIconCountColumn(
        "hallOfFame.column.thirds", "hallOfFame.column.thirds.tooltip",
        bronzeMedal, HallOfFameEntryBean::getThirds));
    hallOfFameTable.getColumns().add(makeIconCountColumn(
        "hallOfFame.column.participations", "hallOfFame.column.participations.tooltip",
        cookie, HallOfFameEntryBean::getParticipations));

    // Highlight the row containing the local player so they can find
    // themselves at a glance. The :current-player pseudo-class is matched
    // by a CSS rule in style.css.
    hallOfFameTable.setRowFactory(tv -> new TableRow<>() {
      @Override
      protected void updateItem(HallOfFameEntryBean item, boolean empty) {
        super.updateItem(item, empty);
        boolean isCurrent = !empty && item != null && isCurrentPlayer(item);
        pseudoClassStateChanged(CURRENT_PLAYER_PSEUDO_CLASS, isCurrent);
      }
    });
  }

  /**
   * Build a TableColumn whose header shows a medal/cookie icon next to a
   * short text label, and whose cells render just the count (no per-row
   * icon — that turns out to be visually noisy and the user asked for
   * icons in headers only). Sortable; clicking the header sorts by count
   * descending.
   */
  private TableColumn<HallOfFameEntryBean, Number> makeIconCountColumn(
      String headerTextKey, String tooltipKey, Image icon,
      java.util.function.ToIntFunction<HallOfFameEntryBean> getter) {
    TableColumn<HallOfFameEntryBean, Number> col = new TableColumn<>(i18n.get(headerTextKey));
    // Icon as the column's graphic — JavaFX renders the column header as a
    // Label internally, with both text and graphic shown side-by-side.
    // Default contentDisplay is LEFT, so the icon ends up to the left of
    // the text, which is what we want for "🥇 Gold" / "🥈 Silver" / etc.
    ImageView headerIcon = new ImageView(icon);
    headerIcon.setFitHeight(ICON_SIZE);
    headerIcon.setFitWidth(ICON_SIZE);
    headerIcon.setPreserveRatio(true);
    col.setGraphic(headerIcon);
    // Tooltip for the longer description ("Gold medals (1st place finishes)").
    javafx.scene.control.Tooltip.install(headerIcon, new javafx.scene.control.Tooltip(i18n.get(tooltipKey)));

    col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(getter.applyAsInt(cd.getValue())));
    col.setCellFactory(c -> {
      TableCell<HallOfFameEntryBean, Number> cell = new TableCell<>() {
        @Override
        protected void updateItem(Number value, boolean empty) {
          super.updateItem(value, empty);
          if (empty || value == null) {
            setText(null);
            setStyle("");
            return;
          }
          int count = value.intValue();
          setText(String.valueOf(count));
          // Dim zero counts so the eye skips over them.
          setStyle(count == 0 ? "-fx-text-fill: -fx-text-base-color; -fx-opacity: 0.45;" : "");
        }
      };
      // Centre the count text in the cell. The column-level
      // "-fx-alignment: CENTER" doesn't propagate to a custom cellFactory
      // — TableCell defaults to CENTER_LEFT and overrides the column style
      // unless we set it explicitly here.
      cell.setAlignment(javafx.geometry.Pos.CENTER);
      return cell;
    });
    col.setPrefWidth(80);
    col.setStyle("-fx-alignment: CENTER;");
    // Default sort direction = descending (more medals first), matching the
    // canonical DEFAULT_COMPARATOR ordering used to assign rank.
    col.setSortType(TableColumn.SortType.DESCENDING);
    col.setComparator(Comparator.<Number>comparingInt(Number::intValue));
    return col;
  }

  // ===== Mod filter (driven by parent TournamentsRootController) =====

  private ModFilterEntry currentModFilter;

  /** Called by the parent controller when the shared mod filter changes. */
  public void onModFilterChanged(ModFilterEntry entry) {
    this.currentModFilter = entry;
    Integer modId = entry != null ? entry.modId : null;
    loadHallOfFame(modId);
  }

  // ===== Search box =====

  private void setupSearchField() {
    searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter());
  }

  /**
   * Rebuild {@link #tableEntries} from {@link #rankedEntries}, applying the
   * current search box predicate. Called on search-keystroke and after
   * every fetch. Replaces the entire list rather than mutating in place,
   * so TableView re-applies the active column sort cleanly.
   */
  private void applyFilter() {
    String query = searchField.getText() == null
        ? "" : searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
    if (query.isEmpty()) {
      tableEntries.setAll(rankedEntries);
    } else {
      List<HallOfFameEntryBean> filtered = new ArrayList<>();
      for (HallOfFameEntryBean entry : rankedEntries) {
        if (entry.getPlayerLogin() != null
            && entry.getPlayerLogin().toLowerCase(java.util.Locale.ROOT).contains(query)) {
          filtered.add(entry);
        }
      }
      tableEntries.setAll(filtered);
    }
  }

  // ===== Context menu =====

  private void setupContextMenu() {
    ContextMenu contextMenu = new ContextMenu();

    MenuItem replaysItem = new MenuItem(i18n.get("hallOfFame.contextMenu.viewReplays"));
    replaysItem.setOnAction(e -> {
      HallOfFameEntryBean selected = hallOfFameTable.getSelectionModel().getSelectedItem();
      if (selected != null) {
        openReplaysFor(selected);
      }
    });

    MenuItem userInfoItem = new MenuItem(i18n.get("hallOfFame.contextMenu.userInfo"));
    userInfoItem.setOnAction(e -> {
      HallOfFameEntryBean selected = hallOfFameTable.getSelectionModel().getSelectedItem();
      if (selected != null) {
        openUserInfoFor(selected);
      }
    });

    contextMenu.getItems().addAll(replaysItem, userInfoItem);
    hallOfFameTable.setContextMenu(contextMenu);
  }

  /**
   * Fetch the player's tournament game IDs (filtered by current mod
   * selection) and open them in the existing replay vault via
   * {@link ShowGameIdReplaysEvent}. The currently-selected mod filter is
   * passed through so "show replays for player X under mod Y" returns the
   * scoped subset.
   */
  private void openReplaysFor(HallOfFameEntryBean entry) {
    Integer modId = currentModFilterId();
    tournamentService.getPlayerTournamentGameIds(entry.getPlayerId(), modId)
        .thenAccept(gameIds -> JavaFxUtil.runLater(() -> {
          if (gameIds == null || gameIds.isEmpty()) {
            log.debug("No tournament game ids for player {} mod {}", entry.getPlayerId(), modId);
            return;
          }
          eventBus.post(new ShowGameIdReplaysEvent(gameIds));
        }))
        .exceptionally(throwable -> {
          log.warn("Failed to fetch tournament game ids for player {} mod {}", entry.getPlayerId(), modId, throwable);
          return null;
        });
  }

  /** Resolve the player by login then open the existing user info dialog. */
  private void openUserInfoFor(HallOfFameEntryBean entry) {
    if (entry.getPlayerLogin() == null) return;
    playerService.getPlayerByName(entry.getPlayerLogin())
        .thenAccept(optionalPlayer -> {
          if (optionalPlayer.isEmpty()) {
            log.debug("User info: no player found for login {}", entry.getPlayerLogin());
            return;
          }
          JavaFxUtil.runLater(() -> {
            com.faforever.client.chat.UserInfoWindowController controller =
                uiService.loadFxml("theme/user_info_window.fxml");
            controller.setPlayer(optionalPlayer.get());
            controller.setOwnerWindow(getRoot().getScene().getWindow());
            controller.show();
          });
        })
        .exceptionally(throwable -> {
          log.warn("Failed to look up player {}", entry.getPlayerLogin(), throwable);
          return null;
        });
  }

  // ===== Data fetch =====

  /** FXML-bound handler for the Refresh button. */
  public void onRefresh() {
    loadHallOfFame(currentModFilterId());
  }

  private void loadHallOfFame(Integer featuredModId) {
    tournamentService.getHallOfFame(featuredModId)
        .thenAccept(rows -> JavaFxUtil.runLater(() -> {
          // Sort the freshly-fetched rows by the canonical multi-column
          // comparator and assign each one a stable 1-based rank. The rank
          // gets shown in the Rank column and is used for the default
          // table sort below; it stays put when the user re-sorts by
          // gold/silver/bronze/etc.
          List<HallOfFameEntryBean> ranked = new ArrayList<>(rows != null ? rows : Collections.emptyList());
          ranked.sort(HallOfFameEntryBean.DEFAULT_COMPARATOR);
          int position = 1;
          for (HallOfFameEntryBean bean : ranked) {
            bean.setRank(position++);
          }
          rankedEntries.clear();
          rankedEntries.addAll(ranked);

          // Reset the table sort to "Rank ascending" on every fetch so a
          // mod-filter change doesn't carry over the user's previous column
          // sort. Re-installing the rank column in sortOrder triggers
          // TableView's built-in sort. We clear sortOrder *before* setAll
          // so JavaFX doesn't waste a sort pass on the unsorted setAll
          // followed by another on the sortOrder.add.
          hallOfFameTable.getSortOrder().clear();
          applyFilter();
          if (rankColumn != null) {
            hallOfFameTable.getSortOrder().add(rankColumn);
          }
          updateEmptyState();
          hasLoadedOnce = true;
        }))
        .exceptionally(throwable -> {
          log.warn("Failed to load Hall of Fame for mod {}", featuredModId, throwable);
          return null;
        });
  }

  private Integer currentModFilterId() {
    return currentModFilter != null ? currentModFilter.modId : null;
  }

  private void updateEmptyState() {
    boolean empty = tableEntries.isEmpty();
    emptyStateLabel.setVisible(empty);
    emptyStateLabel.setManaged(empty);
  }

  private boolean isCurrentPlayer(HallOfFameEntryBean entry) {
    return playerService.getCurrentPlayer()
        .map(p -> p.getId() == entry.getPlayerId())
        .orElse(false);
  }

  // ===== Auto-refresh =====

  @Subscribe
  public void onRefreshTournamentsEvent(RefreshTournamentsEvent event) {
    JavaFxUtil.runLater(() -> {
      // Don't bother refreshing if the user has never visited this tab —
      // the next onDisplay will load fresh data anyway.
      if (!hasLoadedOnce) return;
      if (pendingRefresh != null) {
        pendingRefresh.stop();
      }
      pendingRefresh = new javafx.animation.Timeline(
          new javafx.animation.KeyFrame(javafx.util.Duration.millis(AUTO_REFRESH_DEBOUNCE_MS),
              e -> {
                log.debug("Auto-refreshing Hall of Fame after RefreshTournamentsEvent");
                loadHallOfFame(currentModFilterId());
                pendingRefresh = null;
              }));
      pendingRefresh.play();
    });
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    // First time the tab is shown, the mod combo's initial fetch will load
    // data; subsequent shows do nothing (data persists). The auto-refresh
    // hook keeps it fresh.
  }

  /** Combo box entry: a featured mod (or "All mods" with modId=null). */
}
