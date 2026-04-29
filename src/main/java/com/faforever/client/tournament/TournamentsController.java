package com.faforever.client.tournament;


import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.game.GameService;
import com.faforever.client.update.ClientConfiguration;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.HostGameEvent;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.ShowGameIdReplaysEvent;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.TimeService;
import com.google.common.eventbus.EventBus;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class TournamentsController extends AbstractViewController<Node> {

  private final TimeService timeService;
  private final I18n i18n;
  private final TournamentService tournamentService;
  private final TournamentTeamService teamService;
  private final FafService fafService;
  private final UiService uiService;
  private final PreferencesService preferencesService;
  private final MapService mapService;
  private final EventBus eventBus;
  private final PlayerService playerService;
  private final GameService gameService;
  private final PlatformService platformService;
  // Check-in notification handling moved to TournamentCheckInNotifier
  // so it fires before the Tournaments tab is opened (this controller
  // is lazy-loaded). No NotificationService dependency needed here.
  /** Mutable container in the title row that holds the Sign Up / Withdraw
   *  / Check In buttons. Kept as a field because the buttons depend on
   *  the FULL tournament bean (participants list), and the title row is
   *  rendered before the full bean loads — we need to patch the buttons
   *  in place once the heavy fetch completes. */
  private HBox signupActionBox;
  /** Container for the team panel rendered in the detail pane (team tournaments only). */
  private VBox teamPanel;
  /** Tracked listeners so we can deregister them when the panel is rebuilt,
   *  preventing listener accumulation across tournament selections. */
  private javafx.collections.ListChangeListener<Map<String, Object>> teamsListener;
  private javafx.collections.ListChangeListener<com.faforever.client.remote.domain.TournamentTeamInviteReceivedMessage> invitesListener;
  private javafx.beans.value.ChangeListener<? super Integer> myTeamIdListener;

  /** Width (in px) of each map thumbnail in the game list. */
  private static final double THUMBNAIL_SIZE = 180d;

  /** Currently displayed tournament — re-used when a bracket click re-renders the right pane. */
  private TournamentBean currentTournament;
  /** Bumped on every displayTournamentItem call; stale async callbacks check this. */
  private long displayGeneration;
  /** All loaded tournaments, unfiltered. The mod filter operates on this list. */
  private List<TournamentBean> allTournaments = new ArrayList<>();
  /** Current mod filter — null or "All mods" entry means show all. Set by parent controller. */
  private ModFilterEntry currentModFilter;
  private TournamentsRootController rootController;
  /** Currently selected match id (0 = none / placeholder). */
  private int selectedMatchId = 0;
  /** Match id that should get the blue "this is your next game" frame in the bracket. */
  private int userNextMatchId = 0;
  /** Lookup from match id to its rendered bracket node, for selection-class toggling and scroll-into-view. */
  private final Map<Integer, Node> matchNodes = new HashMap<>();
  /** Active forfeit countdown timers — stopped when the bracket re-renders.
   *  Also keyed by match id so individual timers can be stopped on
   *  tournament_timer_stopped broadcasts. */
  private final List<javafx.animation.Timeline> activeCountdowns = new ArrayList<>();
  /** When true, all tournament timestamps are formatted in UTC instead of the user's local time. */
  private boolean displayTimesAsUtc = false;
  /** Maps player/team name → their last (deciding) match ID for medal display. */
  private Map<String, Integer> lastMatchByPlayer = java.util.Collections.emptyMap();
  private final Map<Integer, javafx.animation.Timeline> countdownByMatchId = new HashMap<>();
  /** Parallel to countdownByMatchId so timer_stopped/restarted listeners
   *  can hide/show the countdown label while a game is live without
   *  tearing down the scene-graph node. */
  private final Map<Integer, Label> countdownLabelByMatchId = new HashMap<>();
  /** Current forfeit deadline per open match. Populated when a countdown
   *  is created and patched in place on tournament_timer_restarted
   *  broadcasts so the running Timeline reads the fresh value without
   *  being rebuilt. */
  private final Map<Integer, java.util.concurrent.atomic.AtomicReference<java.time.Instant>>
      deadlineByMatchId = new HashMap<>();
  /** Match node that should be scrolled into view after the next layout pass (user's next-to-play). */
  private Node pendingScrollTarget;

  public Pane tournamentRoot;
  public ScrollPane tournamentDetailScrollPane;
  public VBox tournamentDetailContent;
  public Pane loadingIndicator;
  public Node contentPane;
  public javafx.scene.control.TitledPane inProgressPane;
  public javafx.scene.control.TitledPane upcomingPane;
  public javafx.scene.control.TitledPane completedPane;
  public ListView<TournamentBean> inProgressListView;
  public ListView<TournamentBean> upcomingListView;
  public ListView<TournamentBean> completedListView;
  public VBox gameListPane;
  public ScrollPane gameListScrollPane;
  public VBox gameListContent;

  public TournamentsController(TimeService timeService, I18n i18n, TournamentService tournamentService,
                               TournamentTeamService teamService,
                               FafService fafService, UiService uiService,
                               PreferencesService preferencesService, MapService mapService, EventBus eventBus,
                               PlayerService playerService, GameService gameService,
                               PlatformService platformService) {
    this.timeService = timeService;
    this.i18n = i18n;
    this.tournamentService = tournamentService;
    this.teamService = teamService;
    this.fafService = fafService;
    this.uiService = uiService;
    this.preferencesService = preferencesService;
    this.mapService = mapService;
    this.eventBus = eventBus;
    this.playerService = playerService;
    this.gameService = gameService;
    this.platformService = platformService;
  }

  public void onTournamentsGuideButtonPressed(ActionEvent actionEvent) {
    // Mirrors the Galactic War guide button: derive the content host from the
    // GW endpoint URL so local dev (where dfc-config points at a localhost
    // server) opens the locally-served guide, not the prod one. The GW URL
    // lands in /galactic_war/galactic_war.json; resolve "../tournaments/..."
    // to walk back up to the content root.
    try {
      ClientConfiguration.Endpoints ep = preferencesService.getClientRemoteConfiguration()
          .getEndpoints().get(0);
      String gwUrl = null;
      if (ep.getGalacticWar2() != null && !ep.getGalacticWar2().isEmpty()) {
        gwUrl = ep.getGalacticWar2().get(0);
      } else if (ep.getGalacticWar() != null) {
        gwUrl = ep.getGalacticWar().getUrl();
      }
      if (gwUrl == null || gwUrl.isBlank()) return;
      String guideUrl = new java.net.URI(gwUrl)
          .resolve("../tournaments/tournaments_guide.html").toString();
      platformService.showDocument(guideUrl);
    } catch (java.net.URISyntaxException e) {
      log.warn("Failed to resolve tournament guide URL", e);
    }
  }

  @Override
  public Node getRoot() {
    return tournamentRoot;
  }

  @Override
  public void initialize() {
    contentPane.managedProperty().bind(contentPane.visibleProperty());
    contentPane.setVisible(false);

    setupListView(inProgressListView);
    setupListView(upcomingListView);
    setupListView(completedListView);

    // Lock the right-hand "Game List" pane to its preferred width — it only
    // needs to fit a 180px map thumbnail plus its border. The middle detail
    // pane absorbs all slack when the window resizes.
    javafx.scene.control.SplitPane.setResizableWithParent(gameListPane, Boolean.FALSE);

    // Subscribe to auto-refresh events from the tournament service via FAF
    // server NoticeMessages with non-null tournament_id. The handler debounces
    // bursts so e.g. several signups in quick succession collapse into a single
    // reload, and only fires when the tournament page is actually visible
    // (avoids hammering the API with reloads while the user is on another tab).
    eventBus.register(this);

    // Hide individual forfeit countdowns while a game is live. We
    // pause the Timeline and hide the label (rather than removing the
    // scene-graph node) so the matching timer_restarted listener can
    // show it again in place — no structural churn to the bracket row.
    // Tournament check-in required: handled by TournamentCheckInNotifier
    // (a singleton bean, eagerly constructed at app start). Putting it
    // there instead of here is necessary because this controller is
    // lazy-instantiated when the user first opens the Tournaments tab —
    // a check-in nag pushed on login replay would otherwise hit a
    // client with no listener registered and be silently dropped.

    fafService.addOnMessageListener(
        com.faforever.client.remote.domain.TournamentTimerStoppedMessage.class,
        msg -> JavaFxUtil.runLater(() -> {
          if (msg.getMatchId() == null) return;
          javafx.animation.Timeline tl = countdownByMatchId.get(msg.getMatchId());
          if (tl != null) {
            tl.pause();
          }
          Label label = countdownLabelByMatchId.get(msg.getMatchId());
          if (label != null) {
            label.setVisible(false);
            // setManaged(false) also removes it from layout so the
            // bracket row doesn't leave an empty gap where the
            // countdown used to be.
            label.setManaged(false);
          }
        }));

    // Update / restart forfeit countdowns when the server broadcasts
    // a new deadline. Fired after a draw or any other inconclusive
    // game ends and the 10-minute toilet-break grace shifts the
    // deadline beyond opened_at + noshow_timeout.
    fafService.addOnMessageListener(
        com.faforever.client.remote.domain.TournamentTimerRestartedMessage.class,
        msg -> JavaFxUtil.runLater(() -> {
          if (msg.getMatchId() == null || msg.getTimesOutAt() == null) return;
          try {
            java.time.Instant newDeadline = parseServerNaiveUtc(msg.getTimesOutAt());
            java.util.concurrent.atomic.AtomicReference<java.time.Instant> ref =
                deadlineByMatchId.get(msg.getMatchId());
            if (ref != null) {
              ref.set(newDeadline);
            }
            // Also patch the in-memory MatchInfo so a bracket re-render
            // (e.g. after a subsequent refresh event) picks up the new
            // deadline even before the full refetch lands.
            if (currentTournament != null && currentTournament.getMatches() != null) {
              currentTournament.getMatches().stream()
                  .filter(mm -> mm.getMatchId() == msg.getMatchId())
                  .findFirst()
                  .ifPresent(mm -> mm.setTimesOutAt(msg.getTimesOutAt()));
            }
            // Un-hide the label that timer_stopped hid while the game
            // was live.
            Label label = countdownLabelByMatchId.get(msg.getMatchId());
            if (label != null) {
              label.setVisible(true);
              label.setManaged(true);
            }
            javafx.animation.Timeline tl = countdownByMatchId.get(msg.getMatchId());
            if (tl != null) {
              // Resume if paused by a prior timer-stopped, and make the
              // KeyFrame fire immediately so the label updates without
              // waiting up to 1 s for the next tick.
              tl.playFromStart();
            }
          } catch (Exception ignored) {}
        }));

    // NOTE: we do NOT unregister from eventBus on scene detach. The scene
    // detaches on every tab switch, not just on controller destruction.
    // Unregistering would cause RefreshTournamentsEvent to be dropped
    // while the user is playing a tournament game on a different tab,
    // which breaks countdown timer cleanup and bracket auto-refresh.
    // The duplicate-listener risk from prototype scope is acceptable
    // (worst case: double refresh on re-navigation).
  }

  /** Pending debounced reload task — cancelled when a new event arrives within the window. */
  private javafx.animation.Timeline pendingRefresh;

  @com.google.common.eventbus.Subscribe
  public void onRefreshTournamentsEvent(com.faforever.client.main.event.RefreshTournamentsEvent event) {
    JavaFxUtil.runLater(() -> {
      if (!contentPane.isVisible()) {
        // User isn't on the tournaments tab — no need to reload now; the next
        // onDisplay() will fetch fresh data anyway.
        return;
      }
      if (pendingRefresh != null) {
        pendingRefresh.stop();
      }
      final int tournamentId = event.getTournamentId();
      final boolean selectAfter = event.isSelectAfterRefresh();
      pendingRefresh = new javafx.animation.Timeline(
          new javafx.animation.KeyFrame(javafx.util.Duration.millis(500),
              e -> {
                if (tournamentId > 0) {
                  // Targeted refresh: pull just this tournament and patch it
                  // into the lists in place. Avoids the full N-row refetch +
                  // sort + setAll storm that loadTournaments() does.
                  log.debug("Auto-refreshing single tournament {} after RefreshTournamentsEvent",
                      tournamentId);
                  refreshSingleTournament(tournamentId, selectAfter);
                } else {
                  log.debug("Auto-refreshing all tournaments after RefreshTournamentsEvent (no specific id)");
                  loadTournaments();
                }
                pendingRefresh = null;
              }));
      pendingRefresh.play();
    });
  }

  /**
   * Cell height for {@link TournamentItemListCell}. The cell is a 2-row GridPane
   * (status row + title row), and the {@code .tournament-item} CSS rule adds
   * {@code -fx-padding: 20 10 20 10} → 40px of vertical padding, on top of which
   * sit two text rows. We use a generous fixed cell size so neither row gets
   * clipped: too small and the title row vanishes; too large just wastes space.
   */
  private static final double TOURNAMENT_LIST_CELL_HEIGHT = 80d;

  /**
   * Maximum number of rows a single section is allowed to grow to before its
   * inner ListView starts scrolling. With the previous unbounded prefHeight
   * binding, JavaFX VirtualFlow saw a viewport equal to the entire content
   * height and realized a Cell node for every item — so a few hundred completed
   * tournaments froze the UI as soon as the user expanded the Completed pane.
   * Capping the section height restores virtualization (only ~MAX_VISIBLE_ROWS
   * cells get realized regardless of the underlying item count).
   */
  private static final int MAX_VISIBLE_ROWS_PER_SECTION = 8;

  private void setupListView(ListView<TournamentBean> listView) {
    listView.setCellFactory(param -> new TournamentItemListCell(uiService));
    // Fixed cell size lets VirtualFlow do constant-time scroll math instead of
    // measuring every cell.
    listView.setFixedCellSize(TOURNAMENT_LIST_CELL_HEIGHT);
    // Bind prefHeight to min(items, MAX_VISIBLE_ROWS_PER_SECTION) * cellSize so
    // small sections (e.g. 1 in-progress tournament) take only the space they
    // need but large sections (hundreds of completed) cap at the visible-rows
    // limit and let the ListView's own VirtualFlow scroll internally. Without
    // the cap, VirtualFlow gets a viewport equal to the entire content size and
    // realizes a cell for every item — UI freezes proportional to count.
    listView.prefHeightProperty().bind(
        Bindings.createDoubleBinding(
            () -> Math.min(listView.getItems().size(), MAX_VISIBLE_ROWS_PER_SECTION)
                  * TOURNAMENT_LIST_CELL_HEIGHT + 2,
            listView.getItems()));
    listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null) {
        // Skip if we're re-selecting the same tournament (e.g. after setAll
        // rebuilds the list). Avoids redundant server fetches.
        if (currentTournament != null && newVal.getId() != null
            && newVal.getId().equals(currentTournament.getId())) {
          return;
        }
        // Clear selection on the other lists
        if (listView != inProgressListView) inProgressListView.getSelectionModel().clearSelection();
        if (listView != upcomingListView) upcomingListView.getSelectionModel().clearSelection();
        if (listView != completedListView) completedListView.getSelectionModel().clearSelection();
        displayTournamentItem(newVal);
        updateButtons(newVal);
      }
    });
  }

  private TournamentBean getSelectedTournament() {
    TournamentBean t = inProgressListView.getSelectionModel().getSelectedItem();
    if (t != null) return t;
    t = upcomingListView.getSelectionModel().getSelectedItem();
    if (t != null) return t;
    return completedListView.getSelectionModel().getSelectedItem();
  }

  private void onLoadingStart() {
    JavaFxUtil.runLater(() -> {
      if (loadingIndicator != null) {
        loadingIndicator.setVisible(true);
      }
    });
  }

  private void onLoadingStop() {
    JavaFxUtil.runLater(() -> {
      if (loadingIndicator != null) {
        loadingIndicator.setVisible(false);
      }
      contentPane.setVisible(true);
    });
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    if (contentPane.isVisible()) {
      return;
    }
    loadTournaments();
  }

  /**
   * Update selection state, re-render the right pane, and toggle the
   * {@code bracket-match-selected} style class on the bracket node.
   */
  private void selectMatch(int matchId) {
    int previousId = selectedMatchId;
    selectedMatchId = matchId;
    if (currentTournament != null) {
      populateGameList(currentTournament);
    }
    Node prev = matchNodes.get(previousId);
    if (prev != null) prev.getStyleClass().remove("bracket-match-selected");
    Node curr = matchNodes.get(matchId);
    if (curr != null && !curr.getStyleClass().contains("bracket-match-selected")) {
      curr.getStyleClass().add("bracket-match-selected");
    }
  }

  public void onRefreshButtonClicked(ActionEvent actionEvent) {
    // Clear currentTournament so the selection listener doesn't skip the
    // re-fetch when restoreSelection re-selects the same tournament.
    // Without this, the "same id" guard at line ~297 short-circuits and
    // the user never sees server-side changes (team edits, new signups, etc.).
    currentTournament = null;
    loadTournaments();
  }

  public void onCreateTournamentClicked(ActionEvent actionEvent) {
    showTournamentForm(TournamentFormController.Mode.CREATE, null,
        i18n.get("tournament.create.title"));
  }

  private void showEditDialog(TournamentBean tournamentBean) {
    showTournamentForm(TournamentFormController.Mode.EDIT, tournamentBean,
        i18n.get("tournament.edit"));
  }

  private void showTournamentForm(TournamentFormController.Mode mode,
                                   TournamentBean editTarget, String title) {
    TournamentFormController form = uiService.loadFxml("theme/tournaments/tournament_form.fxml");
    form.setMode(mode, editTarget);

    javafx.scene.layout.StackPane dialogParent = null;
    javafx.scene.Node n = tournamentRoot;
    while (n != null) {
      if (n instanceof javafx.scene.layout.StackPane sp) dialogParent = sp;
      n = n.getParent();
    }
    if (dialogParent == null) return;

    final com.faforever.client.ui.dialog.Dialog themedDialog = uiService.showInDialog(
        dialogParent, form.getRoot(), title);
    form.setOnDone(themedDialog::close);
  }

  /** Maps a map_visibility enum value to its localised display label. */
  private String mapVisibilityLabel(String value) {
    if (value == null) return i18n.get("tournament.mapVisibility.alwaysVisible");
    switch (value) {
      case "hidden_until_tournament_start":
        return i18n.get("tournament.mapVisibility.hiddenUntilTournamentStart");
      case "hidden_until_round_start":
        return i18n.get("tournament.mapVisibility.hiddenUntilRoundStart");
      case "always_visible":
      default:
        return i18n.get("tournament.mapVisibility.alwaysVisible");
    }
  }

  // In progress: most recently started at top (startingAt desc, nulls last)
  private static final Comparator<TournamentBean> IN_PROGRESS_COMPARATOR =
      Comparator.comparing(TournamentBean::getStartingAt,
          Comparator.nullsLast(Comparator.reverseOrder()));
  // Upcoming: next-to-start at top (startingAt asc, nulls last)
  private static final Comparator<TournamentBean> UPCOMING_COMPARATOR =
      Comparator.comparing(TournamentBean::getStartingAt,
          Comparator.nullsLast(Comparator.naturalOrder()));
  // Completed: most recently completed at top (completedAt desc, nulls last)
  // Most recently finished first. Use completedAt when available, fall back
  // to createdAt (always populated) so cancelled tournaments (which have no
  // completedAt) still sort by recency rather than landing in random order.
  private static final Comparator<TournamentBean> COMPLETED_COMPARATOR =
      Comparator.comparing(
          (TournamentBean t) -> t.getCompletedAt() != null ? t.getCompletedAt() : t.getCreatedAt(),
          Comparator.nullsLast(Comparator.reverseOrder()));

  private void loadTournaments() {
    onLoadingStart();

    // Capture currently selected tournament so we can restore selection after refresh
    TournamentBean previouslySelected = getSelectedTournament();
    String previousId = previouslySelected != null ? previouslySelected.getId() : null;

    tournamentService.getAllTournaments()
        .thenAccept(tournaments -> JavaFxUtil.runLater(() -> {
          allTournaments = new ArrayList<>(tournaments);
          applyModFilter();

          // Restore selection if the previously selected tournament still exists
          boolean restored = false;
          if (previousId != null) {
            restored = restoreSelection(inProgressListView, previousId)
                || restoreSelection(upcomingListView, previousId)
                || restoreSelection(completedListView, previousId);
          }
          if (!restored) {
            if (!inProgressListView.getItems().isEmpty()) inProgressListView.getSelectionModel().selectFirst();
            else if (!upcomingListView.getItems().isEmpty()) upcomingListView.getSelectionModel().selectFirst();
            else if (!completedListView.getItems().isEmpty()) completedListView.getSelectionModel().selectFirst();
          }
          onLoadingStop();
        })).exceptionally(throwable -> {
      log.warn("Tournaments could not be loaded", throwable);
      return null;
    });
  }

  /**
   * Targeted refresh: fetch a single tournament by id and patch it into the
   * lists in place. Used by {@link #onRefreshTournamentsEvent} so a notice
   * tagged with a specific tournament_id doesn't trigger a full {@code
   * loadTournaments()} (which re-pulls every tournament on the wire and re-
   * sorts/setAll's the entire UI). If the tournament is not found server-side
   * it's removed from the lists.
   */
  private void refreshSingleTournament(int tournamentId) {
    refreshSingleTournament(tournamentId, false);
  }

  private void refreshSingleTournament(int tournamentId, boolean selectAfter) {
    String idStr = String.valueOf(tournamentId);
    tournamentService.getTournamentById(idStr)
        .thenAccept(updated -> JavaFxUtil.runLater(() -> {
          if (updated == null) {
            // Tournament was deleted server-side
            removeFromAllLists(idStr);
            updateSectionHeaders();
            return;
          }
          upsertTournamentBean(updated, selectAfter);
          if (selectAfter) {
            selectTournamentById(idStr);
          }
        }))
        .exceptionally(throwable -> {
          // 404 is the normal signal that a tournament we had a stale
          // refresh for has been deleted on the server (e.g. a chain
          // instance got cleaned up between its refresh broadcast and
          // our fetch). Drop it from the lists quietly, same as the
          // updated==null path above. Log the stack trace only for
          // genuinely unexpected errors.
          if (isHttpNotFound(throwable)) {
            JavaFxUtil.runLater(() -> {
              removeFromAllLists(idStr);
              updateSectionHeaders();
            });
          } else {
            log.warn("Failed to refresh tournament {}", tournamentId, throwable);
          }
          return null;
        });
  }

  /** True if the throwable chain includes a 404 response from the Elide API. */
  private static boolean isHttpNotFound(Throwable throwable) {
    Throwable t = throwable;
    while (t != null) {
      if (t instanceof org.springframework.web.client.HttpClientErrorException hce
          && hce.getStatusCode() == org.springframework.http.HttpStatus.NOT_FOUND) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }

  /** Remove any item with the given id from all three section lists. */
  private void removeFromAllLists(String tournamentId) {
    inProgressListView.getItems().removeIf(t -> tournamentId.equals(t.getId()));
    upcomingListView.getItems().removeIf(t -> tournamentId.equals(t.getId()));
    completedListView.getItems().removeIf(t -> tournamentId.equals(t.getId()));
  }

  /**
   * Drop any existing copy of the bean's id from all section lists, then re-
   * insert it into the section that matches its (possibly new) status. The
   * insert is sorted via the section's comparator. Also re-renders the detail
   * pane if the upserted bean is the currently selected tournament — this is
   * how mid-series score updates and bracket advances flow into the visible
   * detail without a full reload.
   */
  private void upsertTournamentBean(TournamentBean updated, boolean skipDisplay) {
    String id = updated.getId();
    if (id == null) {
      return;
    }
    // Capture selection + currentTournament BEFORE the remove / setAll dance
    // below. removeFromAllLists() drops the bean from its ListView, which
    // makes JavaFX's SelectionModel shift selection to whatever item now
    // occupies the freed index — most visibly, the next-starting tournament
    // when the user just signed up for the current top of the upcoming list.
    // The shift fires the selection listener for the wrong tournament, which
    // sets currentTournament to that wrong bean and would defeat the
    // id-equality guard near the end of this method.
    TournamentBean previouslySelected = getSelectedTournament();
    String previousSelectedId = previouslySelected != null ? previouslySelected.getId() : null;
    String savedCurrentId = currentTournament != null ? currentTournament.getId() : null;

    // Update the unfiltered backing list
    allTournaments.removeIf(t -> id.equals(t.getId()));
    allTournaments.add(updated);
    removeFromAllLists(id);

    // Only add to visible lists if it passes the current mod filter
    boolean showAll = currentModFilter == null || currentModFilter.isAll();
    if (showAll || (currentModFilter.modName != null && currentModFilter.modName.equals(updated.getFeaturedModName()))) {
      ObservableList<TournamentBean> targetItems;
      Comparator<TournamentBean> comparator;
      switch (updated.getStatus()) {
        case RUNNING -> {
          targetItems = inProgressListView.getItems();
          comparator = IN_PROGRESS_COMPARATOR;
        }
        case FINISHED, CANCELLED -> {
          targetItems = completedListView.getItems();
          comparator = COMPLETED_COMPARATOR;
        }
        default -> {
          targetItems = upcomingListView.getItems();
          comparator = UPCOMING_COMPARATOR;
        }
      }
      List<TournamentBean> snapshot = new ArrayList<>(targetItems);
      snapshot.add(updated);
      snapshot.sort(comparator);
      targetItems.setAll(snapshot);
    }
    updateSectionHeaders();

    // Restore selection if the remove / setAll above knocked it onto a
    // different (or no) tournament. Without this, signing up for a tournament
    // would scroll/select onto the next-starting tournament instead of
    // staying on the one just signed up for.
    if (previousSelectedId != null) {
      TournamentBean nowSelected = getSelectedTournament();
      if (nowSelected == null || !previousSelectedId.equals(nowSelected.getId())) {
        selectTournamentById(previousSelectedId);
      }
    }

    // If this was the currently displayed tournament, refresh the detail pane.
    // Use savedCurrentId (captured before the upsert) because currentTournament
    // may have been transiently mutated by the selection listener during the
    // remove above. Skip when the caller is about to selectTournamentById
    // (which triggers the selection listener → displayTournamentItem) to
    // avoid redundant server fetches.
    if (!skipDisplay && savedCurrentId != null && id.equals(savedCurrentId)) {
      // Focus-preserving guard: displayTournamentItem clears
      // tournamentDetailContent and rebuilds the whole detail pane
      // (including the team panel's TextFields). If the user is
      // mid-keystroke in a text input inside the detail pane — e.g.
      // typing a team name or an invite target — destroying the node
      // loses their text and caret. Defer until focus leaves the
      // input; the next user action (tab, click, submit) will trigger
      // the pending rebuild. Only applies to server-driven refreshes
      // of the currently-displayed tournament. Explicit clicks on a
      // different tournament land in selectTournamentById → the
      // regular displayTournamentItem path, unguarded.
      runWhenNoDetailInputFocused(() -> displayTournamentItem(updated));
    }
  }

  /** Run {@code action} immediately if nothing inside the tournament
   *  detail pane currently holds keyboard focus, otherwise install a
   *  one-shot focus listener on the active text input that fires it
   *  as soon as focus moves away. See {@link #buildTeamPanel} for the
   *  rationale. */
  private void runWhenNoDetailInputFocused(Runnable action) {
    javafx.scene.Scene scene = tournamentDetailContent != null
        ? tournamentDetailContent.getScene() : null;
    javafx.scene.Node focusOwner = scene != null ? scene.getFocusOwner() : null;
    if (focusOwner instanceof javafx.scene.control.TextInputControl
        && isDescendant(tournamentDetailContent, focusOwner)) {
      focusOwner.focusedProperty().addListener(new javafx.beans.value.ChangeListener<Boolean>() {
        @Override
        public void changed(javafx.beans.value.ObservableValue<? extends Boolean> obs,
                            Boolean was, Boolean isNow) {
          if (Boolean.FALSE.equals(isNow)) {
            obs.removeListener(this);
            JavaFxUtil.runLater(action);
          }
        }
      });
      return;
    }
    action.run();
  }

  /**
   * Refresh section header text (counts) and visibility (empty sections
   * collapse out of the layout entirely so the remaining ones don't have dead
   * space above them).
   */
  private void updateSectionHeaders() {
    int inProgressCount = inProgressListView.getItems().size();
    int upcomingCount = upcomingListView.getItems().size();
    int completedCount = completedListView.getItems().size();
    inProgressPane.setText(i18n.get("tournament.section.inProgress", inProgressCount));
    upcomingPane.setText(i18n.get("tournament.section.upcoming", upcomingCount));
    completedPane.setText(i18n.get("tournament.section.completed", completedCount));
    setSectionVisible(inProgressPane, inProgressCount > 0);
    setSectionVisible(upcomingPane, upcomingCount > 0);
    setSectionVisible(completedPane, completedCount > 0);
  }

  /** Called by the parent TournamentsRootController when the shared mod filter changes. */
  public void onModFilterChanged(ModFilterEntry entry) {
    this.currentModFilter = entry;
    applyModFilter();
  }

  /** Set by the parent so this controller can request filter changes (e.g. "select all mods"). */
  public void setRootController(TournamentsRootController root) {
    this.rootController = root;
  }

  private void applyModFilter() {
    boolean showAll = currentModFilter == null || currentModFilter.isAll();
    String filterName = showAll ? null : currentModFilter.modName;

    List<TournamentBean> inProgress = new ArrayList<>();
    List<TournamentBean> upcoming = new ArrayList<>();
    List<TournamentBean> completed = new ArrayList<>();
    for (TournamentBean t : allTournaments) {
      if (!showAll && !filterName.equals(t.getFeaturedModName())) continue;
      switch (t.getStatus()) {
        case RUNNING -> inProgress.add(t);
        case FINISHED, CANCELLED -> completed.add(t);
        default -> upcoming.add(t);
      }
    }
    inProgress.sort(IN_PROGRESS_COMPARATOR);
    upcoming.sort(UPCOMING_COMPARATOR);
    completed.sort(COMPLETED_COMPARATOR);

    inProgressListView.getItems().setAll(inProgress);
    upcomingListView.getItems().setAll(upcoming);
    completedListView.getItems().setAll(completed);
    updateSectionHeaders();
  }

  private void setSectionVisible(javafx.scene.control.TitledPane pane, boolean visible) {
    pane.setVisible(visible);
    pane.setManaged(visible);
    if (visible) pane.setExpanded(true);
  }

  private void selectTournamentById(String tournamentId) {
    if (restoreSelection(inProgressListView, tournamentId)
        || restoreSelection(upcomingListView, tournamentId)
        || restoreSelection(completedListView, tournamentId)) {
      return;
    }
    // Tournament not in filtered lists — switch to "All mods" and retry.
    if (currentModFilter != null && !currentModFilter.isAll() && rootController != null) {
      rootController.selectAllMods();
      if (!restoreSelection(inProgressListView, tournamentId)
          && !restoreSelection(upcomingListView, tournamentId)) {
        restoreSelection(completedListView, tournamentId);
      }
    }
  }

  private boolean restoreSelection(ListView<TournamentBean> listView, String tournamentId) {
    for (TournamentBean t : listView.getItems()) {
      if (tournamentId.equals(t.getId())) {
        TournamentBean current = listView.getSelectionModel().getSelectedItem();
        if (current != null && tournamentId.equals(current.getId())) {
          // Already selected — don't re-fire the listener
          return true;
        }
        listView.getSelectionModel().select(t);
        return true;
      }
    }
    return false;
  }

  private void updateButtons(TournamentBean tournament) {
    // Buttons are now rendered inline in renderHeader — nothing to do here.
  }

  /** True if {@code node} sits anywhere inside the {@code ancestor}'s
   *  scene-graph subtree. Used by the team-panel focus-preserving
   *  rerender to tell "user is typing into one of my inputs" from
   *  "user is typing into something unrelated". */
  private static boolean isDescendant(javafx.scene.Node ancestor, javafx.scene.Node node) {
    for (javafx.scene.Node n = node; n != null; n = n.getParent()) {
      if (n == ancestor) return true;
    }
    return false;
  }

  /** Parse a server-naive-UTC timestamp ("yyyy-MM-dd HH:mm:ss" or ISO
   *  with offset) into an Instant. The tournament service publishes
   *  deadlines in the shorter form (no tz) and treats them as UTC;
   *  the Elide JSON:API serializes OffsetDateTime columns with an
   *  offset. Support both so a build-skew between the services
   *  doesn't break the countdown. */
  private static java.time.Instant parseServerNaiveUtc(String raw) {
    if (raw == null) return null;
    try {
      return java.time.OffsetDateTime.parse(raw).toInstant();
    } catch (java.time.format.DateTimeParseException ignored) {
      // Fall through to naive-UTC path.
    }
    return java.time.LocalDateTime
        .parse(raw.replace(' ', 'T'))
        .atZone(java.time.ZoneOffset.UTC)
        .toInstant();
  }

  public void onSignupButtonClicked(ActionEvent actionEvent) {
    TournamentBean selected = getSelectedTournament();
    if (selected != null) {
      fafService.tournamentSignup(Integer.parseInt(selected.getId()));
    }
  }

  public void onWithdrawButtonClicked(ActionEvent actionEvent) {
    TournamentBean selected = getSelectedTournament();
    if (selected != null) {
      fafService.tournamentWithdraw(Integer.parseInt(selected.getId()));
    }
  }

  @SneakyThrows
  private void displayTournamentItem(TournamentBean tournamentBean) {
    if (tournamentBean == null) {
      return;
    }

    // Render the immediately-available "header" content (title, status, settings,
    // description) from the light list-query bean, then kick off the heavy
    // getTournamentById fetch in the background. The bracket / participants
    // sections are appended once the full graph arrives. This keeps the list
    // query light (no participants / matches / planned maps / placements /
    // standings) and only pulls the bracket data when the user actually drills
    // into a row.
    currentTournament = tournamentBean;
    final long thisGeneration = ++displayGeneration;
    // No bracket data yet — clear selection state so populateGameList shows the
    // empty placeholder until the heavy fetch resolves and renderHeavySections
    // recomputes user-next.
    userNextMatchId = 0;
    selectedMatchId = 0;
    matchNodes.clear();
    pendingScrollTarget = null;
    activeCountdowns.forEach(javafx.animation.Timeline::stop);
    activeCountdowns.clear();
    countdownByMatchId.clear();
    countdownLabelByMatchId.clear();
    deadlineByMatchId.clear();
    tournamentDetailContent.getChildren().clear();
    teamPanel = null;

    // Tell the team service which tournament is active so it can fetch
    // team data and start filtering server broadcasts on this id. For
    // solo tournaments we still call setActiveTournament(0) to clear any
    // stale team state from a previously-selected team tournament.
    int tid = parseIdSafe(tournamentBean.getId());
    if (tournamentBean.getPlayersPerSide() >= 2) {
      teamService.setActiveTournament(tid);
    } else {
      teamService.setActiveTournament(0);
    }

    renderHeader(tournamentBean);
    populateGameList(tournamentBean);

    final String tournamentId = tournamentBean.getId();
    if (tournamentId == null) {
      return;
    }
    tournamentService.getTournamentById(tournamentId)
        .thenAccept(full -> JavaFxUtil.runLater(() -> {
          // Guard against stale responses: user may have clicked a different
          // tournament, or displayTournamentItem was called again (e.g. from
          // a notification-triggered refresh + select), before this fetch completed.
          if (thisGeneration != displayGeneration) {
            return;
          }
          if (currentTournament == null || !tournamentId.equals(currentTournament.getId())) {
            return;
          }
          if (full == null) {
            return;
          }
          renderHeavySections(full);
        }))
        .exceptionally(throwable -> {
          log.warn("Failed to load tournament detail for {}", tournamentId, throwable);
          return null;
        });
  }

  /** Render the always-available header bits (title, status, settings, description). */
  private void renderHeader(TournamentBean tournamentBean) {
    // Title row with inline signup/withdraw buttons
    HBox titleRow = new HBox(12);
    titleRow.setAlignment(Pos.CENTER_LEFT);
    Label title = new Label(tournamentBean.getName());
    title.getStyleClass().add("tournament-title");
    title.setWrapText(true);
    HBox.setHgrow(title, Priority.ALWAYS);
    titleRow.getChildren().add(title);

    // Empty container for the title-row action buttons (Sign Up / Withdraw /
    // Check In / Start / Edit / Cancel). updateSignupAction populates it
    // based on the bean. We add it to the title row now so layout is stable,
    // and re-populate once the full bean arrives — the list-query bean
    // used by renderHeader doesn't include the participants relationship
    // OR the createdBy relationship, so both the membership check (Sign
    // Up vs Withdraw) and the creator check (Start/Edit/Cancel) would
    // always come up empty on first render.
    signupActionBox = new HBox(8);
    signupActionBox.setAlignment(Pos.CENTER_LEFT);
    titleRow.getChildren().add(signupActionBox);
    updateSignupAction(tournamentBean);

    javafx.scene.control.CheckBox utcToggle = new javafx.scene.control.CheckBox(i18n.get("tournament.detail.showInUtc"));
    utcToggle.setSelected(displayTimesAsUtc);
    utcToggle.selectedProperty().addListener((obs, o, n) -> {
      displayTimesAsUtc = n;
      if (currentTournament != null) {
        displayTournamentItem(currentTournament);
      }
    });
    titleRow.getChildren().add(utcToggle);
    tournamentDetailContent.getChildren().add(titleRow);

    Label status = new Label(statusText(tournamentBean));
    status.getStyleClass().addAll("tournament-status", statusStyleClass(tournamentBean));
    tournamentDetailContent.getChildren().add(status);

    GridPane settings = buildSettingsGrid(tournamentBean);
    if (settings != null) {
      tournamentDetailContent.getChildren().add(settings);
    }

    String description = tournamentBean.getDescription();
    if (description != null && !description.isBlank()) {
      Label desc = new Label(description);
      desc.getStyleClass().add("description-label");
      desc.setWrapText(true);
      desc.setMaxWidth(Double.MAX_VALUE);
      tournamentDetailContent.getChildren().add(desc);
    }
    tournamentDetailContent.getChildren().add(new javafx.scene.control.Separator());
  }

  /**
   * (Re-)populate the title-row action HBox. Holds Sign Up / Withdraw /
   * Check In (membership-driven) plus the creator buttons (Start / Edit
   * / Cancel). Called from renderHeader (with the shallow list-query
   * bean) and again from renderHeavySections (with the full bean). The
   * first call covers what the light bean already knows; the second
   * corrects everything once participants and createdBy are available
   * — both relationships are excluded from the list query and would
   * otherwise read as null.
   */
  private void updateSignupAction(TournamentBean tournamentBean) {
    if (signupActionBox == null) return;
    signupActionBox.getChildren().clear();

    boolean inCheckIn = tournamentBean.getStatus() == TournamentBean.Status.CHECK_IN;
    boolean signupsAccepted = (tournamentBean.isOpenForSignup()
        && tournamentBean.getStatus() == TournamentBean.Status.OPEN_FOR_REGISTRATION)
        || inCheckIn;
    String currentLogin = playerService.getCurrentPlayer()
        .map(p -> p.getUsername()).orElse(null);

    if (signupsAccepted) {
      boolean alreadySignedUp = currentLogin != null
          && tournamentBean.getParticipantNames() != null
          && tournamentBean.getParticipantNames().contains(currentLogin);
      if (alreadySignedUp) {
        Button withdraw = new Button(i18n.get("tournament.withdraw"));
        withdraw.setOnAction(e -> {
          String id = tournamentBean.getId();
          if (id != null) fafService.tournamentWithdraw(Integer.parseInt(id));
        });
        signupActionBox.getChildren().add(withdraw);
        if (inCheckIn && !tournamentBean.getCheckedInParticipantNames().contains(currentLogin)) {
          Button checkIn = new Button(i18n.get("tournament.checkIn"));
          checkIn.getStyleClass().add("team-btn-primary");
          checkIn.setOnAction(e -> {
            String id = tournamentBean.getId();
            if (id != null) fafService.tournamentCheckIn(Integer.parseInt(id));
          });
          signupActionBox.getChildren().add(checkIn);
        }
      } else {
        Button signup = new Button(i18n.get("tournament.signup"));
        signup.getStyleClass().add("team-btn-primary");
        signup.setOnAction(e -> {
          String id = tournamentBean.getId();
          if (id != null) fafService.tournamentSignup(Integer.parseInt(id));
        });
        signupActionBox.getChildren().add(signup);
      }
    }

    // Creator actions. Lives in the same box so the second call reliably
    // re-renders these too once createdByPlayerId is populated by the
    // full fetch (the list-query bean has it as null).
    int currentPlayerId = playerService.getCurrentPlayer()
        .map(p -> p.getId()).orElse(-1);
    boolean isCreator = tournamentBean.isPlayerCreated()
        && tournamentBean.getCreatedByPlayerId() != null
        && tournamentBean.getCreatedByPlayerId() == currentPlayerId;
    if (isCreator) {
      String tid = tournamentBean.getId();
      int tidInt = tid != null ? Integer.parseInt(tid) : 0;
      String state = tournamentBean.getApiState();
      if ("pending".equals(state) || "check_in".equals(state)) {
        Button startBtn = new Button(i18n.get("tournament.start"));
        startBtn.getStyleClass().add("team-btn-primary");
        startBtn.setOnAction(e -> { if (tidInt > 0) fafService.tournamentStart(tidInt); });
        Button editBtn = new Button(i18n.get("tournament.edit"));
        editBtn.setOnAction(e -> showEditDialog(tournamentBean));
        Button cancelBtn = new Button(i18n.get("tournament.cancel"));
        cancelBtn.setOnAction(e -> { if (tidInt > 0) fafService.tournamentCancel(tidInt); });
        signupActionBox.getChildren().addAll(startBtn, editBtn, cancelBtn);
      } else if ("underway".equals(state)) {
        Button cancelBtn = new Button(i18n.get("tournament.cancel"));
        cancelBtn.setOnAction(e -> { if (tidInt > 0) fafService.tournamentCancel(tidInt); });
        signupActionBox.getChildren().add(cancelBtn);
      }
    }
  }

  /**
   * Append bracket / participants sections to the detail pane after the
   * heavy getTournamentById fetch resolves. Replaces the {@link #currentTournament}
   * reference with the full bean so populateGameList sees the matches.
   */
  private void renderHeavySections(TournamentBean fullBean) {
    currentTournament = fullBean;
    // Patch the title-row buttons now that we know participant membership.
    updateSignupAction(fullBean);
    userNextMatchId = computeUserNextMatchId(fullBean);
    selectedMatchId = userNextMatchId;
    matchNodes.clear();
    pendingScrollTarget = null;
    activeCountdowns.forEach(javafx.animation.Timeline::stop);
    activeCountdowns.clear();
    countdownByMatchId.clear();
    countdownLabelByMatchId.clear();
    deadlineByMatchId.clear();

    // Section ordering depends on tournament state:
    //   Pending:  teams → signups → bracket preview
    //   Underway/Complete: bracket → teams → signups
    // Once started, the bracket is the primary content the user cares
    // about; teams and signups become reference info.
    boolean started = "underway".equals(fullBean.getApiState())
        || "complete".equals(fullBean.getApiState());

    // We (re-)call setActiveTournament here as a defensive measure:
    // the early call in displayTournamentItem uses the light-query bean,
    // and if playersPerSide was 0 there (jsonapi-converter field-mapping
    // miss), activeTournamentId would have been set to 0 and the list
    // request would never fire. The heavy bean always has the correct
    // value because it comes from a full getOne query.
    if (fullBean.getPlayersPerSide() >= 2) {
      teamService.setActiveTournament(parseIdSafe(fullBean.getId()));
      teamPanel = buildTeamPanel(fullBean);
    }

    if (started) {
      // Bracket first
      if (fullBean.getMatches() != null && !fullBean.getMatches().isEmpty()) {
        buildBracketSections(fullBean).forEach(tournamentDetailContent.getChildren()::add);
        tournamentDetailContent.getChildren().add(new javafx.scene.control.Separator());
      }
      // Then teams
      if (teamPanel != null) {
        tournamentDetailContent.getChildren().add(teamPanel);
        tournamentDetailContent.getChildren().add(new javafx.scene.control.Separator());
      }
    } else {
      // Teams first (forming rosters is the primary action when pending)
      if (teamPanel != null) {
        tournamentDetailContent.getChildren().add(teamPanel);
        tournamentDetailContent.getChildren().add(new javafx.scene.control.Separator());
      }
      // Then bracket preview
      if (fullBean.getMatches() != null && !fullBean.getMatches().isEmpty()) {
        buildBracketSections(fullBean).forEach(tournamentDetailContent.getChildren()::add);
      }
    }

    if (fullBean.getParticipantNames() != null && !fullBean.getParticipantNames().isEmpty()) {
      tournamentDetailContent.getChildren().add(buildParticipantsView(fullBean));
    }

    if (pendingScrollTarget != null) {
      Node target = pendingScrollTarget;
      JavaFxUtil.runLater(() -> scrollNodeIntoView(target));
    }

    populateGameList(fullBean);
  }

  private String statusText(TournamentBean t) {
    TournamentBean.Status status = t.getStatus();
    if (status == TournamentBean.Status.RUNNING) return i18n.get("tournament.status.running");
    if (status == TournamentBean.Status.CHECK_IN) return i18n.get("tournament.status.checkIn");
    if (status == TournamentBean.Status.OPEN_FOR_REGISTRATION) return i18n.get("tournament.status.openForSignup");
    if (status == TournamentBean.Status.FINISHED) return i18n.get("tournament.status.complete");
    if (status == TournamentBean.Status.CANCELLED) return i18n.get("tournament.status.cancelled");
    return i18n.get("tournament.status.closed");
  }

  private String statusStyleClass(TournamentBean t) {
    TournamentBean.Status status = t.getStatus();
    if (status == TournamentBean.Status.RUNNING) return "status-underway";
    if (status == TournamentBean.Status.CHECK_IN) return "status-underway";
    if (status == TournamentBean.Status.OPEN_FOR_REGISTRATION) return "status-pending";
    return "status-complete";
  }

  /**
   * Compute the match id of the user's first "next-to-play" match — the
   * earliest open match in display order where the current player is one of
   * the two competitors. Returns 0 if there is no such match (user not a
   * participant, all matches done, or none yet open).
   */
  private int computeUserNextMatchId(TournamentBean tournament) {
    String currentPlayer = playerService.getCurrentPlayer()
        .map(p -> p.getUsername()).orElse(null);
    if (currentPlayer == null || tournament.getMatches() == null) return 0;

    List<TournamentBean.MatchInfo> sorted = new ArrayList<>(tournament.getMatches());
    sorted.sort((a, b) -> {
      int ar = a.getRound(), br = b.getRound();
      int aKey = ar > 0 ? ar : (ar == 0 ? 1000 : 1001 + Math.abs(ar));
      int bKey = br > 0 ? br : (br == 0 ? 1000 : 1001 + Math.abs(br));
      if (aKey != bKey) return aKey - bKey;
      return a.getPosition() - b.getPosition();
    });

    Integer myTeamId = teamService.getMyTeamId().get();
    for (TournamentBean.MatchInfo m : sorted) {
      if (!"open".equals(m.getState())) continue;
      // Solo: match by player name. Team: match by team id.
      if (m.getTeam1Id() > 0 || m.getTeam2Id() > 0) {
        if (myTeamId != null && (myTeamId == m.getTeam1Id() || myTeamId == m.getTeam2Id())) {
          return m.getMatchId();
        }
      } else {
        if (currentPlayer.equals(m.getPlayer1()) || currentPlayer.equals(m.getPlayer2())) {
          return m.getMatchId();
        }
      }
    }
    return 0;
  }

  /**
   * Render the right-hand "game list" pane for the currently-selected match.
   * Single-match mode: shows just the match the user clicked on (or the auto-
   * selected next-to-play). Empty placeholder if nothing is selected.
   */
  private void populateGameList(TournamentBean tournament) {
    gameListContent.getChildren().clear();

    List<TournamentBean.MatchInfo> matches = tournament.getMatches();
    // Pane is always visible — even an empty selection shows the placeholder.
    gameListPane.setVisible(true);
    gameListPane.setManaged(true);

    if (selectedMatchId <= 0 || matches == null) {
      gameListContent.getChildren().add(buildEmptyPlaceholder());
      gameListScrollPane.setVvalue(0);
      return;
    }

    TournamentBean.MatchInfo selected = null;
    for (TournamentBean.MatchInfo m : matches) {
      if (m.getMatchId() == selectedMatchId) {
        selected = m;
        break;
      }
    }
    if (selected == null) {
      gameListContent.getChildren().add(buildEmptyPlaceholder());
      gameListScrollPane.setVvalue(0);
      return;
    }

    String currentPlayer = playerService.getCurrentPlayer()
        .map(p -> p.getUsername()).orElse(null);
    // Map preview lookups need the *technical* mod name, not the display name.
    String featuredMod = tournament.getFeaturedModTechnicalName();
    boolean isUserNextMatch = (selected.getMatchId() == userNextMatchId);

    gameListContent.getChildren().add(
        buildMatchDetail(selected, currentPlayer, featuredMod, tournament, isUserNextMatch));
    gameListScrollPane.setVvalue(0);
  }

  private javafx.scene.Node buildEmptyPlaceholder() {
    javafx.scene.control.Label placeholder = new javafx.scene.control.Label(
        i18n.get("tournament.gameList.empty"));
    placeholder.setWrapText(true);
    placeholder.setMaxWidth(Double.MAX_VALUE);
    placeholder.getStyleClass().add("map-showcase-placeholder");
    placeholder.setStyle("-fx-text-fill: #888; -fx-font-style: italic; -fx-padding: 12;");
    return placeholder;
  }

  /**
   * Build the right-pane content for one match: header label, planned-map cards,
   * and conditional Create Game / View Replays buttons.
   */
  private javafx.scene.Node buildMatchDetail(TournamentBean.MatchInfo match, String currentPlayer,
                                               String featuredMod, TournamentBean tournament,
                                               boolean isUserNextMatch) {
    javafx.scene.layout.VBox section = new javafx.scene.layout.VBox(6);
    section.getStyleClass().add("map-showcase-section");
    // Centered alignment so the width-constrained map cards (which are only as
    // wide as the thumbnail + border) sit centered within the right pane.
    // Children with maxWidth = Double.MAX_VALUE (e.g. the header label and the
    // per-match View Replays button) still expand to fill width — VBox alignment
    // only repositions children narrower than the parent.
    section.setAlignment(javafx.geometry.Pos.TOP_CENTER);

    String label = labelForRole(match.getRole(), match.getRound());
    String tbd = i18n.get("tournament.matchTbd");
    String p1 = match.getPlayer1() != null ? match.getPlayer1() : tbd;
    String p2 = match.getPlayer2() != null ? match.getPlayer2() : tbd;
    javafx.scene.control.Label header = new javafx.scene.control.Label(
        label + " — " + p1 + " vs " + p2);
    header.setWrapText(true);
    header.setMaxWidth(Double.MAX_VALUE);
    header.getStyleClass().add("map-showcase-section-header");
    header.setStyle("-fx-font-weight: bold; -fx-padding: 0 0 4 0;");
    section.getChildren().add(header);

    boolean matchOpen = "open".equals(match.getState());
    boolean matchComplete = "complete".equals(match.getState());
    // A cancelled tournament leaves its match rows in "open" state,
    // so the match-level check alone would still offer "Create Game"
    // on a cancelled tournament. Gate the live-play UI (countdown +
    // Create Game) on the parent tournament actually still being live.
    String parentState = tournament != null ? tournament.getApiState() : null;
    boolean tournamentLive = !"complete".equals(parentState)
        && !"cancelled".equals(parentState);
    matchOpen = matchOpen && tournamentLive;
    // Solo: check player name. Team: check if user's team is in this match.
    boolean userIsParticipant;
    if (match.getTeam1Id() > 0 || match.getTeam2Id() > 0) {
      Integer myTeamId = teamService.getMyTeamId().get();
      userIsParticipant = myTeamId != null
          && (myTeamId == match.getTeam1Id() || myTeamId == match.getTeam2Id());
    } else {
      userIsParticipant = currentPlayer != null
          && (currentPlayer.equals(match.getPlayer1()) || currentPlayer.equals(match.getPlayer2()));
    }
    // Only decisive games advance the map plan — a draw (or any other
    // inconclusive result) means the players replay the same contested
    // game, so nextGameNumber counts wins, not total games played.
    // Mirrors the tournament service's count_decisive_match_games rule.
    int nextGameNumber = match.getPlayer1Wins() + match.getPlayer2Wins() + 1;

    boolean mapsVisible = tournament.arePlannedMapsVisible(match);
    if (mapsVisible && match.getPlannedMaps() != null && !match.getPlannedMaps().isEmpty()) {
      for (TournamentBean.PlannedMapInfo pm : match.getPlannedMaps()) {
        // The "active" highlight + Create Game button only appear when the
        // user is viewing their OWN next-to-play match AND this is the next
        // game in the series.
        boolean isNextToPlay = isUserNextMatch && matchOpen && userIsParticipant
            && pm.getGameNumber() == nextGameNumber;
        section.getChildren().add(
            buildPlannedMapCard(match, pm, featuredMod, tournament, isNextToPlay));
      }
    } else if (!mapsVisible && match.getPlannedMaps() != null && !match.getPlannedMaps().isEmpty()) {
      // Planned maps exist but are hidden by the tournament's visibility rule.
      // Show a placeholder so the user knows a map is picked — but kept secret.
      String hint = "hidden_until_round_start".equals(tournament.getMapVisibility())
          ? i18n.get("tournament.mapsHiddenUntilRoundStart")
          : i18n.get("tournament.mapsHiddenUntilTournamentStart");
      Label hidden = new Label(hint);
      hidden.getStyleClass().add("description-label");
      hidden.setWrapText(true);
      section.getChildren().add(hidden);
    } else if (isUserNextMatch && matchOpen && userIsParticipant) {
      // No planned maps (no map pool / no single map configured).
      // Show the Create Game button directly without a map card.
      section.getChildren().add(createTournamentGameButton(null, tournament, match));
    }

    // Per-match View Replays button — only when complete and we have game IDs.
    if (matchComplete && match.getPlayedGameIds() != null && !match.getPlayedGameIds().isEmpty()) {
      Button replaysButton = new Button(i18n.get("tournament.viewReplays"));
      replaysButton.setMaxWidth(Double.MAX_VALUE);
      java.util.List<Integer> playedIds = match.getPlayedGameIds();
      replaysButton.setOnAction(e -> eventBus.post(new ShowGameIdReplaysEvent(playedIds)));
      section.getChildren().add(replaysButton);
    }

    return section;
  }

  /**
   * Creates the "Create Game" button used in both map-card and no-map tournament views.
   * Disabled while the user is already in a game (mirrors CustomGamesController).
   */
  private Button createTournamentGameButton(String mapName, TournamentBean tournament,
                                            TournamentBean.MatchInfo match) {
    Button createButton = new Button(i18n.get("tournament.createGame"));
    createButton.getStyleClass().add("start-game-button");
    createButton.setMaxWidth(Double.MAX_VALUE);
    createButton.disableProperty().bind(Bindings.createBooleanBinding(
        () -> {
          Number uid = gameService.runningGameUidProperty().get();
          return uid != null && uid.longValue() > 0;
        },
        gameService.runningGameUidProperty()));
    createButton.setOnAction(e -> {
      HostGameEvent ev = new HostGameEvent(mapName);
      applyTournamentPresets(ev, tournament, match);
      eventBus.post(ev);
    });
    return createButton;
  }

  private javafx.scene.Node buildPlannedMapCard(TournamentBean.MatchInfo match,
                                                  TournamentBean.PlannedMapInfo plannedMap,
                                                  String featuredMod,
                                                  TournamentBean tournament,
                                                  boolean isNextToPlay) {
    javafx.scene.layout.VBox cardWrapper = new javafx.scene.layout.VBox(4);
    cardWrapper.getStyleClass().add("map-showcase-card-wrapper");
    cardWrapper.setAlignment(javafx.geometry.Pos.TOP_CENTER);
    // The gameListPane is locked to ~thumbnail width via SplitPane.setResizableWithParent
    // and prefWidth, so the wrapper just fills its parent here — the frame ends up
    // hugging the thumbnail naturally.
    if (isNextToPlay) {
      cardWrapper.getStyleClass().add("map-showcase-card-active");
    }

    javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
    imageView.getStyleClass().add("map-showcase-card");
    imageView.setFitWidth(THUMBNAIL_SIZE);
    imageView.setFitHeight(THUMBNAIL_SIZE);
    imageView.setPreserveRatio(true);
    String mapName = plannedMap.getMapFolderName() != null
        ? plannedMap.getMapFolderName() : plannedMap.getMapName();
    try {
      javafx.scene.image.Image preview = mapService.loadPreview(
          featuredMod, mapName, PreviewType.MINI, 10);
      if (preview != null) imageView.setImage(preview);
    } catch (Exception e) {
      log.debug("Failed to load preview for map {}", plannedMap.getMapName(), e);
    }

    // Map name now sits BELOW the thumbnail as a regular label — no overlay,
    // no gradient dimmer, since the right pane has plenty of vertical space.
    String displayName = "G" + plannedMap.getGameNumber() + " — "
        + (plannedMap.getMapName() != null ? plannedMap.getMapName() : "?");
    javafx.scene.control.Label nameLabel = new javafx.scene.control.Label(displayName);
    nameLabel.setMaxWidth(Double.MAX_VALUE);
    nameLabel.setWrapText(true);
    nameLabel.setAlignment(javafx.geometry.Pos.CENTER);
    nameLabel.getStyleClass().add("map-showcase-name");

    // Context menu: Inspect/Browse Map always; View Replay if this game number
    // has already been played in the series (BO-N: per-game replay lookup).
    javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
    javafx.scene.control.MenuItem inspectItem = new javafx.scene.control.MenuItem(
        i18n.get("tournament.inspectMap"));
    inspectItem.setOnAction(e -> eventBus.post(new HostGameEvent(mapName)));
    menu.getItems().add(inspectItem);

    java.util.List<Integer> playedIds = match.getPlayedGameIds();
    int gameIdx = plannedMap.getGameNumber() - 1;
    if (playedIds != null && gameIdx >= 0 && gameIdx < playedIds.size()) {
      Integer playedGameId = playedIds.get(gameIdx);
      if (playedGameId != null && playedGameId > 0) {
        javafx.scene.control.MenuItem viewReplayItem = new javafx.scene.control.MenuItem(
            i18n.get("tournament.viewReplays"));
        viewReplayItem.setOnAction(e ->
            eventBus.post(new ShowGameIdReplaysEvent(java.util.Collections.singletonList(playedGameId))));
        menu.getItems().add(viewReplayItem);
      }
    }
    // Either click-target opens the menu — image and label both feel "part of the card".
    javafx.event.EventHandler<javafx.scene.input.MouseEvent> showMenu =
        e -> menu.show(imageView, e.getScreenX(), e.getScreenY());
    imageView.setOnMouseClicked(showMenu);
    nameLabel.setOnMouseClicked(showMenu);

    cardWrapper.getChildren().addAll(imageView, nameLabel);

    if (isNextToPlay) {
      cardWrapper.getChildren().add(createTournamentGameButton(mapName, tournament, match));
    }

    return cardWrapper;
  }

  private void applyTournamentPresets(HostGameEvent ev, TournamentBean tournament,
                                       TournamentBean.MatchInfo match) {
    // setSelectedMod (eventually called via CreateGameController.applyHostGamePresets)
    // matches by *technical* name, not display name. Sending the display name here
    // silently fails the IllegalArgumentException-throwing lookup in ModService.
    if (tournament.getFeaturedModTechnicalName() != null) {
      ev.setPresetFeaturedMod(tournament.getFeaturedModTechnicalName());
    }
    int playersPerSide = tournament.getPlayersPerSide();
    if (playersPerSide > 0) {
      ev.setPresetMaxPlayers(playersPerSide * 2);
    }
    ev.setPresetRanked(true);
    ev.setPresetFriendsOnly(false);
    ev.setPresetEnforceRating(false);
    ev.setPresetMinRating("");
    ev.setPresetMaxRating("");
    ev.setPresetPassword("");

    String p1 = match.getPlayer1() != null ? match.getPlayer1() : "TBD";
    String p2 = match.getPlayer2() != null ? match.getPlayer2() : "TBD";
    // The host (current player) should always appear last in the title —
    // convention for TAF is that "X vs Y" puts the host on the right.
    // Swap only when the current player is player1 on the match, since
    // best-of score stays attached to player1/player2 ordering we keep
    // the score direction consistent with the displayed names.
    String currentPlayer = playerService.getCurrentPlayer()
        .map(p -> p.getUsername()).orElse(null);
    int p1Wins = match.getPlayer1Wins();
    int p2Wins = match.getPlayer2Wins();
    if (currentPlayer != null && currentPlayer.equals(p1)) {
      String tmp = p1; p1 = p2; p2 = tmp;
      int tmpW = p1Wins; p1Wins = p2Wins; p2Wins = tmpW;
    }
    int bestOf = Math.max(1, tournament.getBestOf());
    int playedCount = match.getPlayedGameIds() != null ? match.getPlayedGameIds().size() : 0;
    int nextGame = playedCount + 1;
    String title;
    if (bestOf > 1) {
      // Compact, score-prominent: "[koth] G2/3 1-0 — alice vs bob"
      title = "[" + tournament.getName() + "] G" + nextGame + "/" + bestOf + " "
          + p1Wins + "-" + p2Wins + " — " + p1 + " vs " + p2;
    } else {
      title = "[" + tournament.getName() + "] " + p1 + " vs " + p2;
    }
    ev.setPresetTransientTitle(title);
  }

  // ===== Native FX rendering of the tournament detail pane =====

  /**
   * Build the settings GridPane (label/value rows). Returns null if there are no settings to show.
   */
  private GridPane buildSettingsGrid(TournamentBean t) {
    GridPane grid = new GridPane();
    grid.getStyleClass().add("settings-grid");
    grid.setHgap(0);
    grid.setVgap(2);

    int row = 0;
    // Date/time rows — show whichever timestamps are relevant.
    java.time.OffsetDateTime scheduledAt = t.getStartingAt();
    java.time.OffsetDateTime completedAt = t.getCompletedAt();
    String apiState = t.getApiState();
    if ("pending".equals(apiState) && scheduledAt != null) {
      addSettingRow(grid, row++, i18n.get("tournament.detail.startsAt"), formatTournamentDateTime(scheduledAt));
    }
    if ("check_in".equals(apiState) && scheduledAt != null) {
      addSettingRow(grid, row++, i18n.get("tournament.detail.startsAt"), formatTournamentDateTime(scheduledAt));
    }
    // Pre-start check-in window opens at scheduled - check_in_minutes. Show
    // it on PENDING (so signups know when they need to check in) and on
    // CHECK_IN (so they can see the window already opened — useful if they
    // joined late). Once the tournament is UNDERWAY the window is moot.
    if (t.getCheckInMinutes() > 0
        && scheduledAt != null
        && ("pending".equals(apiState) || "check_in".equals(apiState))) {
      java.time.OffsetDateTime checkInOpensAt = scheduledAt.minusMinutes(t.getCheckInMinutes());
      addSettingRow(grid, row++,
          i18n.get("tournament.detail.checkInOpensAt"),
          formatTournamentDateTime(checkInOpensAt),
          "settings-highlight",
          i18n.get("tournament.detail.checkInOpensAt.tooltip"));
    }
    if ("underway".equals(apiState) && scheduledAt != null) {
      addSettingRow(grid, row++, i18n.get("tournament.detail.startedAt"), formatTournamentDateTime(scheduledAt));
    }
    if ("complete".equals(apiState)) {
      if (scheduledAt != null) addSettingRow(grid, row++, i18n.get("tournament.detail.startedAt"), formatTournamentDateTime(scheduledAt));
      if (completedAt != null) addSettingRow(grid, row++, i18n.get("tournament.detail.completedAt"), formatTournamentDateTime(completedAt));
    }
    if ("cancelled".equals(apiState) && completedAt != null) {
      addSettingRow(grid, row++, i18n.get("tournament.detail.closedAt"), formatTournamentDateTime(completedAt));
    }
    // Always show core settings — use sensible display defaults when
    // the moderator left a field unconfigured, so the player knows
    // what they're signing up for.
    int pps = t.getPlayersPerSide();
    String formatLabel = (pps > 1 ? pps + "v" + pps + " " : "")
        + (t.getTournamentType() != null
            ? t.getTournamentType().replace("_", " ")
            : "single elimination");
    addSettingRow(grid, row++, i18n.get("tournament.detail.format"), formatLabel);
    if (t.getBestOf() > 1) {
      addSettingRow(grid, row++, i18n.get("tournament.detail.bestOf"), String.valueOf(t.getBestOf()));
    }
    addSettingRow(grid, row++, i18n.get("tournament.detail.noshowTimeout"),
        i18n.get("tournament.detail.noshowMinutes",
            t.getNoshowTimeoutMinutes() > 0 ? t.getNoshowTimeoutMinutes() : 20));
    if (t.getMinRating() != null || t.getMaxRating() != null) {
      String ratingRange = (t.getMinRating() != null ? t.getMinRating().toString() : i18n.get("tournament.detail.ratingAny"))
          + " – "
          + (t.getMaxRating() != null ? t.getMaxRating().toString() : i18n.get("tournament.detail.ratingAny"));
      addSettingRow(grid, row++, i18n.get("tournament.detail.ratingRange"), ratingRange);
    }
    addSettingRow(grid, row++, i18n.get("tournament.detail.mod"),
        t.getFeaturedModName() != null ? t.getFeaturedModName() : i18n.get("tournament.detail.modAny"));
    if (t.getLeaderboardName() != null) {
      addSettingRow(grid, row++, i18n.get("tournament.detail.seedingRating"), i18n.get(t.getLeaderboardName()));
    }
    if (t.getMapPoolName() != null) {
      addSettingRow(grid, row++, i18n.get("tournament.detail.mapPool"), t.getMapPoolName());
    }
    String visibility = t.getMapVisibility();
    if (visibility != null && !"always_visible".equals(visibility)) {
      addSettingRow(grid, row++, i18n.get("tournament.detail.mapVisibility"), mapVisibilityLabel(visibility));
    }
    if ("swiss".equals(t.getTournamentType())) {
      addSettingRow(grid, row++, i18n.get("tournament.detail.swissRounds"),
          String.valueOf(t.getSwissRounds() > 0 ? t.getSwissRounds() : 3));
      if (t.getTopCut() > 0) {
        String value = "Top " + t.getTopCut();
        if (t.getTopCutFormat() != null) {
          value += " \u2192 " + t.getTopCutFormat().replace("_", " ");
        }
        addSettingRow(grid, row++, i18n.get("tournament.detail.topCut"), value);
      }
    }
    return row == 0 ? null : grid;
  }

  private void addSettingRow(GridPane grid, int row, String label, String value) {
    addSettingRow(grid, row, label, value, null, null);
  }

  /**
   * Variant that adds an extra CSS class to both the label and value
   * (useful for highlighting urgent rows like the check-in window) and
   * installs a tooltip on the value cell that explains what's at stake.
   */
  private void addSettingRow(GridPane grid, int row, String label, String value,
                             String extraStyleClass, String tooltipText) {
    Label l = new Label(label);
    l.getStyleClass().add("settings-label");
    Label v = new Label(value);
    v.getStyleClass().add("settings-value");
    v.setWrapText(true);
    if (extraStyleClass != null) {
      l.getStyleClass().add(extraStyleClass);
      v.getStyleClass().add(extraStyleClass);
    }
    if (tooltipText != null) {
      javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(tooltipText);
      tip.setShowDelay(javafx.util.Duration.millis(300));
      tip.setWrapText(true);
      tip.setMaxWidth(360);
      javafx.scene.control.Tooltip.install(l, tip);
      javafx.scene.control.Tooltip.install(v, tip);
    }
    grid.add(l, 0, row);
    grid.add(v, 1, row);
  }

  /**
   * Build the bracket section nodes (label + bracket). For Swiss tournaments
   * also prepends the standings table.
   */
  private List<Node> buildBracketSections(TournamentBean tournament) {
    List<Node> result = new ArrayList<>();
    List<TournamentBean.MatchInfo> matches = tournament.getMatches();
    Map<String, Integer> ratings = new java.util.HashMap<>(tournament.getParticipantRatings());
    // For team tournaments, enrich the ratings map with per-team aggregate
    // ratings using the same inverse-variance-weighted formula as seeding.
    // Falls back to the simple average from TournamentBean for players
    // that aren't in the local PlayerService (offline / unseen).
    if (tournament.getPlayersPerSide() >= 2) {
      enrichTeamRatings(tournament, ratings);
    }
    Map<String, String> placementAvatars = buildPlacementAvatarMap(tournament);

    // For each player with a placement, find their last (deciding) match —
    // the one where they were eliminated or won the tournament. Medals are
    // only shown on that match, not every match they appear in.
    lastMatchByPlayer = new java.util.HashMap<>();
    if (matches != null) {
      for (TournamentBean.MatchInfo m : matches) {
        if (m.isPreview() || "pending".equals(m.getState())) continue;
        for (String p : new String[]{m.getPlayer1(), m.getPlayer2()}) {
          if (p != null && placementAvatars.containsKey(p)) {
            lastMatchByPlayer.put(p, m.getMatchId());
          }
        }
      }
    }

    // Prize avatar URLs for badge display. Once the tournament is finished
    // (or cancelled), the placement avatars on the player slots themselves
    // already show who won 1st/2nd/3rd, so the per-match prize badges become
    // redundant clutter. Suppress them in terminal states.
    boolean tournamentTerminal = tournament.getStatus() == TournamentBean.Status.FINISHED
        || tournament.getStatus() == TournamentBean.Status.CANCELLED;
    String[] prizeAvatars = tournamentTerminal
        ? new String[]{null, null, null}
        : new String[]{tournament.getWinnerAvatarUrl(),
            tournament.getSecondPlaceAvatarUrl(), tournament.getThirdPlaceAvatarUrl()};

    boolean hasLosers = matches.stream().anyMatch(m -> m.getRound() < 0);
    boolean hasGrandFinal = matches.stream().anyMatch(m -> m.getRound() == 0);
    boolean isKoth = "king_of_the_hill".equals(tournament.getTournamentType());
    boolean isSwiss = "swiss".equals(tournament.getTournamentType());

    // For double elim, suppress the semifinal 3rd-place badge (semis losers go to L bracket, not 3rd)
    String[] wPrizes = (hasLosers || hasGrandFinal) ? new String[]{null, null, null} : prizeAvatars;

    if (isSwiss) {
      result.addAll(buildSwissContent(tournament, matches, ratings, placementAvatars, prizeAvatars));
    } else if (isKoth) {
      result.add(buildKothContent(matches, ratings, placementAvatars));
    } else if (hasLosers || hasGrandFinal) {
      result.add(sectionLabel("Winners Bracket"));
      result.add(buildBracketRow(matches, ratings, placementAvatars, wPrizes, r -> r > 0));
      result.add(sectionLabel("Losers Bracket"));
      result.add(buildBracketRow(matches, ratings, placementAvatars, prizeAvatars, r -> r < 0));
      if (hasGrandFinal) {
        result.add(sectionLabel("Grand Final"));
        result.add(buildBracketRow(matches, ratings, placementAvatars, prizeAvatars, r -> r == 0));
      }
    } else {
      result.add(buildBracketRow(matches, ratings, placementAvatars, prizeAvatars, r -> r > 0));
    }
    return result;
  }

  private Label sectionLabel(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("bracket-section-label");
    return l;
  }

  /**
   * Wrap the bracket HBox in a horizontal-scrolling ScrollPane and return the
   * ScrollPane. Caller adds it to the detail container.
   */
  private Node buildBracketRow(List<TournamentBean.MatchInfo> allMatches,
                                 Map<String, Integer> ratings,
                                 Map<String, String> placementAvatars,
                                 String[] prizeAvatars,
                                 java.util.function.IntPredicate roundFilter) {
    // Collect distinct rounds matching filter, sorted
    List<Integer> roundNumbers = allMatches.stream()
        .map(TournamentBean.MatchInfo::getRound)
        .filter(roundFilter::test)
        .distinct()
        .sorted((a, b) -> {
          if (a < 0 && b < 0) return Integer.compare(Math.abs(a), Math.abs(b));
          return Integer.compare(a, b);
        })
        .collect(java.util.stream.Collectors.toList());

    HBox row = new HBox();
    row.getStyleClass().add("bracket-row");

    ScrollPane scroll = new ScrollPane(row);
    scroll.getStyleClass().add("bracket-scroll");
    scroll.setFitToHeight(true);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

    List<AnchorPane> roundPanes = new ArrayList<>();
    for (int roundNum : roundNumbers) {
      List<TournamentBean.MatchInfo> roundMatches = new ArrayList<>();
      for (TournamentBean.MatchInfo m : allMatches) {
        if (m.getRound() == roundNum) roundMatches.add(m);
      }
      if (roundMatches.isEmpty()) continue;
      String role = roundMatches.get(0).getRole();
      VBox col = buildRoundColumn(labelForRole(role, roundNum), roundMatches, ratings, placementAvatars, prizeAvatars, scroll);
      row.getChildren().add(col);
      roundPanes.add((AnchorPane) col.getChildren().get(1));
    }
    if (!roundPanes.isEmpty()) {
      scheduleBracketLayout(roundPanes);
    }
    return scroll;
  }

  /**
   * Runs {@link #layoutBracketCenters} once each match has its rendered
   * height. Heights aren't reliably available until after JavaFX has applied
   * CSS and run a layout pass on the freshly-built panes, and that can take
   * one or two pulses depending on when buildBracketRow was invoked. So we
   * attach a one-shot listener to each match's heightProperty: as soon as
   * every match has a non-zero height we run the centring pass. We also
   * re-run on later height changes (countdown labels hiding, prize rows
   * appearing) so positions stay aligned.
   */
  private void scheduleBracketLayout(List<AnchorPane> roundPanes) {
    final boolean[] scheduled = {false};
    Runnable trigger = () -> {
      if (scheduled[0]) return;
      scheduled[0] = true;
      JavaFxUtil.runLater(() -> {
        scheduled[0] = false;
        layoutBracketCenters(roundPanes);
      });
    };
    for (AnchorPane p : roundPanes) {
      for (Node n : p.getChildren()) {
        if (n instanceof Region region) {
          region.heightProperty().addListener((obs, ov, nv) -> {
            if (nv != null && nv.doubleValue() > 0) trigger.run();
          });
        }
      }
    }
    // Also fire once after the initial layout pass, in case all heights are
    // already populated (subsequent navigations to the same tournament).
    JavaFxUtil.runLater(trigger);
  }

  private VBox buildRoundColumn(String roundLabel,
                                 List<TournamentBean.MatchInfo> roundMatches,
                                 Map<String, Integer> ratings,
                                 Map<String, String> placementAvatars,
                                 String[] prizeAvatars,
                                 ScrollPane bracketScroll) {
    VBox column = new VBox();
    column.getStyleClass().add("bracket-round");

    Label header = new Label(roundLabel);
    header.getStyleClass().add("bracket-round-label");
    column.getChildren().add(header);

    AnchorPane matchesPane = new AnchorPane();
    matchesPane.getStyleClass().add("bracket-round-matches");
    matchesPane.setMaxWidth(Double.MAX_VALUE);
    matchesPane.setMinHeight(0);
    for (TournamentBean.MatchInfo m : roundMatches) {
      String[] badges = badgesForRole(m.getRole(), prizeAvatars);
      Node matchNode = buildMatchNode(m, ratings, placementAvatars, badges, bracketScroll);
      AnchorPane.setLeftAnchor(matchNode, 0.0);
      AnchorPane.setRightAnchor(matchNode, 0.0);
      matchesPane.getChildren().add(matchNode);
    }
    column.getChildren().add(matchesPane);
    return column;
  }

  /**
   * Position matches inside each round column so that round-N matches vertically
   * straddle the two round-(N-1) matches whose winners feed into them. Pairing
   * is by display order: match at index i in round N has parents at indices 2i
   * and 2i+1 in round N-1. With byes (missing parents), we align with the
   * remaining parent or stack below the previous match. For losers brackets the
   * topology is more complex than display-order pairing — the result there is
   * approximate, but never worse than the old straight stacking.
   */
  private void layoutBracketCenters(List<AnchorPane> roundPanes) {
    final double SPACING = 6.0;

    // Force CSS + layout so child match heights are populated.
    for (AnchorPane p : roundPanes) {
      p.applyCss();
      p.layout();
    }

    double[] heights0 = measureHeights(roundPanes.get(0));

    // First displayed round: stack matches with uniform spacing.
    AnchorPane firstPane = roundPanes.get(0);
    double y = 0;
    for (int i = 0; i < firstPane.getChildren().size(); i++) {
      Node match = firstPane.getChildren().get(i);
      match.setLayoutY(y);
      y += heights0[i] + SPACING;
    }
    double firstBottom = Math.max(0, y - SPACING);
    firstPane.setMinHeight(firstBottom);
    firstPane.setPrefHeight(firstBottom);

    double[] prevHeights = heights0;

    // Subsequent rounds: each match centred between its two display-order parents.
    for (int r = 1; r < roundPanes.size(); r++) {
      AnchorPane prev = roundPanes.get(r - 1);
      AnchorPane curr = roundPanes.get(r);
      List<Node> prevMatches = prev.getChildren();
      List<Node> currMatches = curr.getChildren();
      double[] currHeights = measureHeights(curr);

      double cumulativeBottom = 0;
      for (int i = 0; i < currMatches.size(); i++) {
        Node match = currMatches.get(i);
        double h = currHeights[i];
        int p1Idx = 2 * i;
        int p2Idx = 2 * i + 1;
        double targetY;
        if (p1Idx < prevMatches.size() && p2Idx < prevMatches.size()) {
          double c1 = prevMatches.get(p1Idx).getLayoutY() + prevHeights[p1Idx] / 2;
          double c2 = prevMatches.get(p2Idx).getLayoutY() + prevHeights[p2Idx] / 2;
          targetY = (c1 + c2) / 2 - h / 2;
        } else if (p1Idx < prevMatches.size()) {
          targetY = prevMatches.get(p1Idx).getLayoutY() + prevHeights[p1Idx] / 2 - h / 2;
        } else {
          targetY = (i == 0) ? 0 : cumulativeBottom + SPACING;
        }
        double minY = (i == 0) ? 0 : cumulativeBottom + SPACING;
        if (targetY < minY) targetY = minY;
        match.setLayoutY(targetY);
        cumulativeBottom = targetY + h;
      }
      curr.setMinHeight(cumulativeBottom);
      curr.setPrefHeight(cumulativeBottom);
      prevHeights = currHeights;
    }
  }

  /**
   * Snapshot the rendered height of each match. By the time scheduleBracketLayout
   * fires us, each match has been through a full layout pass so getHeight()
   * is the actual on-screen height. Prefer that over prefHeight() — prefHeight
   * underestimates when CSS-driven padding/font on deeply-nested player slots
   * hasn't fully cascaded, which is exactly the bug that caused round-1
   * matches to overlap on the first attempt.
   */
  private double[] measureHeights(AnchorPane pane) {
    List<Node> kids = pane.getChildren();
    double[] heights = new double[kids.size()];
    for (int i = 0; i < kids.size(); i++) {
      Node n = kids.get(i);
      double h = 0;
      if (n instanceof Region region) {
        h = region.getHeight();
        if (h <= 0) h = region.prefHeight(-1);
      }
      if (h <= 0) h = n.getLayoutBounds().getHeight();
      if (h <= 0) h = 40;
      heights[i] = h;
    }
    return heights;
  }

  /**
   * Build a single bracket match node. Click handler calls {@link #selectMatch}.
   * Tracks in {@code matchNodes} for selection-class toggling and remembers the
   * enclosing horizontal bracket ScrollPane (used by scrollNodeIntoView for
   * horizontal centering).
   */
  private Node buildMatchNode(TournamentBean.MatchInfo m,
                                Map<String, Integer> ratings,
                                Map<String, String> placementAvatars,
                                String[] prizeBadgeUrls,
                                ScrollPane bracketScroll) {
    VBox match = new VBox();
    match.getStyleClass().add("bracket-match");
    match.setMaxWidth(Double.MAX_VALUE);

    String state = m.getState();
    if ("complete".equals(state)) match.getStyleClass().add("bracket-match-complete");
    else if ("open".equals(state)) match.getStyleClass().add("bracket-match-open");
    else if ("preview".equals(state)) match.getStyleClass().add("bracket-match-preview");
    else match.getStyleClass().add("bracket-match-pending");

    int matchId = m.getMatchId();
    if (matchId > 0 && matchId == userNextMatchId) match.getStyleClass().add("bracket-match-user-next");
    if (matchId > 0 && matchId == selectedMatchId) match.getStyleClass().add("bracket-match-selected");

    // Show the score whenever there's any progress (mid-series BO-N) or the
    // match is complete. Pure-pending matches with 0-0 don't get a score column.
    boolean showScore = "complete".equals(state) || m.getPlayer1Wins() > 0 || m.getPlayer2Wins() > 0;
    match.getChildren().add(buildPlayerSlot(m, m.getPlayer1(), ratings,
        isWinner(m, m.getPlayer1()), showScore, m.getPlayer1Wins()));
    addMedalRow(match, m.getPlayer1(), placementAvatars, m.getMatchId());
    Region divider = new Region();
    divider.getStyleClass().add("bracket-slot-divider");
    match.getChildren().add(divider);
    match.getChildren().add(buildPlayerSlot(m, m.getPlayer2(), ratings,
        isWinner(m, m.getPlayer2()), showScore, m.getPlayer2Wins()));
    addMedalRow(match, m.getPlayer2(), placementAvatars, m.getMatchId());

    // Placement medal icons are rendered inline per-slot below.

    // Noshow countdown for open matches. Skip if the parent tournament
    // was cancelled or completed — the match row stays "open" in those
    // cases, so a bare state check would happily draw a countdown on a
    // tournament nobody is playing in any more.
    String parentApiState = currentTournament != null ? currentTournament.getApiState() : null;
    boolean parentLive = !"complete".equals(parentApiState)
        && !"cancelled".equals(parentApiState);
    // The server clears times_out_at when a game goes live for this match
    // (or hasn't scheduled a timer yet). In either case there is NO
    // active forfeit deadline, so don't draw a countdown — previously
    // we fell back to opened_at + noshow_timeout here, which caused
    // "forfeit imminent" to appear while players were mid-game.
    if ("open".equals(state) && parentLive && m.getTimesOutAt() != null
        && currentTournament != null) {
      try {
        java.time.Instant initialDeadline = parseServerNaiveUtc(m.getTimesOutAt());
        final int countdownMatchId = m.getMatchId();
        // Deadline is held in a mutable reference so the
        // tournament_timer_restarted broadcast can update it in place
        // without rebuilding the Timeline.
        java.util.concurrent.atomic.AtomicReference<java.time.Instant> deadlineRef =
            deadlineByMatchId.computeIfAbsent(countdownMatchId,
                k -> new java.util.concurrent.atomic.AtomicReference<>());
        deadlineRef.set(initialDeadline);
        Label countdownLabel = new Label();
        countdownLabel.getStyleClass().add("bracket-player-rating");
        countdownLabel.setMaxWidth(Double.MAX_VALUE);
        countdownLabel.setAlignment(javafx.geometry.Pos.CENTER);
        javafx.animation.Timeline countdown = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
              // Self-stop if the match is no longer open (result reported,
              // tournament complete/cancelled, etc.). Cancelled tournaments
              // leave their match rows as "open" in the DB, so we also need
              // to check the parent tournament's state explicitly.
              if (currentTournament != null && currentTournament.getMatches() != null) {
                String tournamentState = currentTournament.getApiState();
                boolean tournamentLive = !"complete".equals(tournamentState)
                    && !"cancelled".equals(tournamentState);
                boolean stillOpen = tournamentLive && currentTournament.getMatches().stream()
                    .anyMatch(mm -> mm.getMatchId() == countdownMatchId && "open".equals(mm.getState()));
                if (!stillOpen) {
                  countdownLabel.setVisible(false);
                  countdownLabel.setManaged(false);
                  countdownLabel.setText("");
                  return;
                }
              }
              java.time.Instant currentDeadline = deadlineRef.get();
              if (currentDeadline == null) return;
              long secsLeft = java.time.Duration.between(
                  java.time.Instant.now(), currentDeadline).getSeconds();
              if (secsLeft <= 0) {
                countdownLabel.setText(i18n.get("tournament.forfeit.imminent"));
                countdownLabel.setStyle("-fx-text-fill: -bad;");
              } else {
                countdownLabel.setText(i18n.get("tournament.forfeit.countdown",
                    String.format("%d:%02d:%02d", secsLeft / 3600, (secsLeft % 3600) / 60, secsLeft % 60)));
              }
            }));
        countdown.setCycleCount(javafx.animation.Animation.INDEFINITE);
        countdown.play();
        activeCountdowns.add(countdown);
        countdownByMatchId.put(countdownMatchId, countdown);
        countdownLabelByMatchId.put(countdownMatchId, countdownLabel);
        // Fire once immediately
        long secsLeft = java.time.Duration.between(java.time.Instant.now(), initialDeadline).getSeconds();
        if (secsLeft <= 0) {
          countdownLabel.setText("Forfeit imminent");
          countdownLabel.setStyle("-fx-text-fill: -bad;");
        } else {
          countdownLabel.setText(String.format("Forfeit in %d:%02d:%02d",
              secsLeft / 3600, (secsLeft % 3600) / 60, secsLeft % 60));
        }
        match.getChildren().add(countdownLabel);
      } catch (Exception ignored) {}
    }

    if (prizeBadgeUrls != null) {
      HBox prizeRow = new HBox();
      prizeRow.getStyleClass().add("bracket-prize-row");
      boolean any = false;
      for (String url : prizeBadgeUrls) {
        if (url == null) continue;
        any = true;
        ImageView iv = new ImageView();
        try { iv.setImage(new Image(url, 16, 16, true, true, true)); } catch (Exception ignored) {}
        iv.setFitHeight(16);
        iv.setPreserveRatio(true);
        prizeRow.getChildren().add(iv);
      }
      if (any) match.getChildren().add(prizeRow);
    }

    if (matchId > 0) {
      match.setOnMouseClicked(e -> selectMatch(matchId));
      matchNodes.put(matchId, match);
      // Stash the enclosing horizontal bracket ScrollPane on the node so the
      // scroll-into-view helper can scroll BOTH the outer (vertical) and the
      // bracket-row (horizontal) ScrollPanes.
      match.getProperties().put("bracketScroll", bracketScroll);
      // If this is the user's next-to-play, remember it for post-layout scroll.
      if (matchId == userNextMatchId && pendingScrollTarget == null) {
        pendingScrollTarget = match;
      }
    }
    return match;
  }

  private void addMedalRow(VBox match, String playerName, Map<String, String> placementAvatars,
                            int currentMatchId) {
    if (playerName == null || placementAvatars == null) return;
    // Only show the medal on the player's deciding (last) match
    Integer decidingMatch = lastMatchByPlayer.get(playerName);
    if (decidingMatch == null || decidingMatch != currentMatchId) return;
    String url = placementAvatars.get(playerName);
    if (url == null) return;
    try {
      ImageView iv = new ImageView(new Image(url, 16, 16, true, true, true));
      iv.setFitHeight(16);
      iv.setPreserveRatio(true);
      HBox row = new HBox(iv);
      row.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
      row.setMaxWidth(Double.MAX_VALUE);
      match.getChildren().add(row);
    } catch (Exception ignored) {}
  }

  private HBox buildPlayerSlot(TournamentBean.MatchInfo m, String playerName,
                                 Map<String, Integer> ratings,
                                 boolean isWinner, boolean showScore, int score) {
    HBox slot = new HBox();
    slot.getStyleClass().add("bracket-slot");
    if (isWinner) slot.getStyleClass().add("bracket-slot-winner");

    Label nameLabel;
    String tooltipText = null;
    if (playerName == null) {
      nameLabel = new Label(i18n.get("tournament.matchTbd"));
      nameLabel.getStyleClass().addAll("bracket-player", "bracket-player-tbd");
    } else {
      nameLabel = new Label(playerName);
      nameLabel.getStyleClass().add("bracket-player");
      tooltipText = playerName;
    }
    HBox.setHgrow(nameLabel, Priority.ALWAYS);
    nameLabel.setMaxWidth(Double.MAX_VALUE);
    slot.getChildren().add(nameLabel);

    if (playerName != null && ratings != null) {
      Integer rating = ratings.get(playerName);
      if (rating != null) {
        Label r = new Label("(" + rating + ")");
        r.getStyleClass().add("bracket-player-rating");
        slot.getChildren().add(r);
        tooltipText = playerName + " (" + rating + ")";
      }
    }

    // No-show reputation badge — only when the player has actually missed
    // tournaments. A clean record stays unlabelled to avoid noise. Pulls
    // from the current tournament since this slot only ever renders for
    // the displayed tournament's bracket.
    TournamentBean.ReputationInfo rep = (playerName != null && currentTournament != null
        && currentTournament.getParticipantReputations() != null)
        ? currentTournament.getParticipantReputations().get(playerName) : null;
    String repBadge = formatReputationBadge(rep);
    if (repBadge != null) {
      Label repLabel = new Label(repBadge);
      repLabel.getStyleClass().addAll("bracket-player-rating", "bracket-player-reputation");
      slot.getChildren().add(repLabel);
    }

    // Tooltip with full untruncated name + rating so nothing is lost
    // when the bracket column is too narrow for long usernames. Append
    // the reputation breakdown so the bare "(N/M)" badge is decodable.
    if (tooltipText != null) {
      if (rep != null && rep.getSignupCount() > 0) {
        int shows = Math.max(0, rep.getSignupCount()
            - rep.getNoCheckInCount() - rep.getMatchForfeitCount());
        tooltipText = tooltipText + "\n" + i18n.get("tournament.participant.reputation",
            shows, rep.getSignupCount(),
            rep.getNoCheckInCount(), rep.getMatchForfeitCount());
      }
      javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(tooltipText);
      tip.setShowDelay(javafx.util.Duration.millis(300));
      javafx.scene.control.Tooltip.install(slot, tip);
    }

    if (showScore) {
      Label scoreLabel = new Label(String.valueOf(score));
      scoreLabel.getStyleClass().add("bracket-score");
      slot.getChildren().add(scoreLabel);
    }
    return slot;
  }

  private List<Node> buildSwissContent(TournamentBean tournament,
                                         List<TournamentBean.MatchInfo> matches,
                                         Map<String, Integer> ratings,
                                         Map<String, String> placementAvatars,
                                         String[] prizeAvatars) {
    List<Node> nodes = new ArrayList<>();
    final int swissRounds = tournament.getSwissRounds() > 0 ? tournament.getSwissRounds() : 3;

    List<TournamentBean.MatchInfo> swissMatches = new ArrayList<>();
    List<TournamentBean.MatchInfo> topCutMatches = new ArrayList<>();
    for (TournamentBean.MatchInfo m : matches) {
      if (m.getRound() > 0 && m.getRound() <= swissRounds) {
        swissMatches.add(m);
      } else {
        topCutMatches.add(m);
      }
    }

    // Standings table
    nodes.add(sectionLabel("Swiss Standings"));
    nodes.add(buildSwissStandingsTable(tournament, ratings));

    // Swiss round pairings (one HBox row of round columns, in a horizontal scroll)
    nodes.add(sectionLabel("Swiss Pairings"));
    nodes.add(buildBracketRow(swissMatches, ratings, placementAvatars, prizeAvatars,
        r -> r > 0 && r <= swissRounds));

    // Top cut bracket
    if (!topCutMatches.isEmpty()) {
      boolean hasLosers = topCutMatches.stream().anyMatch(m -> m.getRound() < 0);
      boolean hasGrandFinal = topCutMatches.stream().anyMatch(m -> m.getRound() == 0);
      String[] tcWPrizes = (hasLosers || hasGrandFinal) ? new String[]{null, null, null} : prizeAvatars;

      if (hasLosers || hasGrandFinal) {
        nodes.add(sectionLabel("Top Cut — Winners Bracket"));
        nodes.add(buildBracketRow(topCutMatches, ratings, placementAvatars, tcWPrizes, r -> r > swissRounds));
        nodes.add(sectionLabel("Top Cut — Losers Bracket"));
        nodes.add(buildBracketRow(topCutMatches, ratings, placementAvatars, prizeAvatars, r -> r < 0));
        if (hasGrandFinal) {
          nodes.add(sectionLabel("Grand Final"));
          nodes.add(buildBracketRow(topCutMatches, ratings, placementAvatars, prizeAvatars, r -> r == 0));
        }
      } else {
        nodes.add(sectionLabel("Top Cut"));
        nodes.add(buildBracketRow(topCutMatches, ratings, placementAvatars, prizeAvatars, r -> r > swissRounds));
      }
    }
    return nodes;
  }

  private TableView<TournamentBean.StandingInfo> buildSwissStandingsTable(TournamentBean tournament,
                                                                            Map<String, Integer> ratings) {
    TableView<TournamentBean.StandingInfo> table = new TableView<>();
    table.getStyleClass().add("swiss-standings-table");

    TableColumn<TournamentBean.StandingInfo, Integer> rankCol = new TableColumn<>("#");
    rankCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getRank()));
    rankCol.setPrefWidth(36);

    boolean isTeamTournament = tournament.getPlayersPerSide() >= 2;
    TableColumn<TournamentBean.StandingInfo, String> playerCol = new TableColumn<>(isTeamTournament ? "Team" : "Player");
    playerCol.setCellValueFactory(c -> {
      String name = c.getValue().getPlayerName();
      Integer rating = ratings != null ? ratings.get(name) : null;
      return new ReadOnlyObjectWrapper<>(rating != null ? name + " (" + rating + ")" : name);
    });
    playerCol.setPrefWidth(180);

    TableColumn<TournamentBean.StandingInfo, Integer> winsCol = new TableColumn<>("W");
    winsCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getWins()));
    winsCol.setPrefWidth(40);

    TableColumn<TournamentBean.StandingInfo, Integer> lossesCol = new TableColumn<>("L");
    lossesCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getLosses()));
    lossesCol.setPrefWidth(40);

    TableColumn<TournamentBean.StandingInfo, Integer> oppCol = new TableColumn<>("Opp. Str.");
    oppCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getOpponentStrength()));
    oppCol.setPrefWidth(72);

    TableColumn<TournamentBean.StandingInfo, Integer> winStrCol = new TableColumn<>("Win Str.");
    winStrCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getWinStrength()));
    winStrCol.setPrefWidth(72);

    table.getColumns().addAll(rankCol, playerCol, winsCol, lossesCol, oppCol, winStrCol);

    List<TournamentBean.StandingInfo> serverStandings = tournament.getStandings();
    if (serverStandings != null && !serverStandings.isEmpty()) {
      // Solo: server persists standings to tournament_standings table
      table.setItems(FXCollections.observableArrayList(serverStandings));
    } else if (isTeamTournament && tournament.getMatches() != null) {
      // Team: server can't persist team standings (FK constraint), so
      // compute client-side from match data — same algorithm as the
      // server's compute_swiss_standings.
      int swissRounds = tournament.getSwissRounds() > 0 ? tournament.getSwissRounds() : 3;
      table.setItems(FXCollections.observableArrayList(
          computeTeamSwissStandings(tournament.getMatches(), tournament.getTeamNames(), swissRounds)));
    } else {
      // Solo pre-start: show participants alphabetically with no scores
      List<TournamentBean.StandingInfo> placeholder = new ArrayList<>();
      for (String name : tournament.getParticipantNames()) {
        placeholder.add(new TournamentBean.StandingInfo(0, name, 0, 0, 0, 0));
      }
      table.setItems(FXCollections.observableArrayList(placeholder));
    }
    table.setPrefHeight(Math.min(220, 28 + table.getItems().size() * 26));
    return table;
  }

  /**
   * Compute Swiss standings for team tournaments client-side. Mirrors the
   * server's compute_swiss_standings (bracket.py) using team names from
   * MatchInfo.player1/player2 (which hold team names for team matches).
   */
  private List<TournamentBean.StandingInfo> computeTeamSwissStandings(
      List<TournamentBean.MatchInfo> allMatches, List<String> teamNames, int swissRounds) {

    // Collect all team names from either the explicit list or from match data
    java.util.Set<String> competitors = new java.util.LinkedHashSet<>();
    if (teamNames != null) competitors.addAll(teamNames);
    for (TournamentBean.MatchInfo m : allMatches) {
      if (m.getRound() > 0 && m.getRound() <= swissRounds) {
        if (m.getPlayer1() != null) competitors.add(m.getPlayer1());
        if (m.getPlayer2() != null) competitors.add(m.getPlayer2());
      }
    }

    java.util.Map<String, Integer> wins = new java.util.HashMap<>();
    java.util.Map<String, Integer> losses = new java.util.HashMap<>();
    java.util.Map<String, List<String>> opponents = new java.util.HashMap<>();
    java.util.Map<String, List<String>> beaten = new java.util.HashMap<>();
    for (String c : competitors) {
      wins.put(c, 0);
      losses.put(c, 0);
      opponents.put(c, new ArrayList<>());
      beaten.put(c, new ArrayList<>());
    }

    for (TournamentBean.MatchInfo m : allMatches) {
      if (m.getRound() <= 0 || m.getRound() > swissRounds) continue;
      if (!"complete".equals(m.getState())) continue;
      String p1 = m.getPlayer1(), p2 = m.getPlayer2(), w = m.getWinner();
      if (p1 == null || p2 == null) continue;
      opponents.computeIfAbsent(p1, k -> new ArrayList<>()).add(p2);
      opponents.computeIfAbsent(p2, k -> new ArrayList<>()).add(p1);
      if (p1.equals(w)) {
        wins.merge(p1, 1, Integer::sum);
        losses.merge(p2, 1, Integer::sum);
        beaten.computeIfAbsent(p1, k -> new ArrayList<>()).add(p2);
      } else if (p2.equals(w)) {
        wins.merge(p2, 1, Integer::sum);
        losses.merge(p1, 1, Integer::sum);
        beaten.computeIfAbsent(p2, k -> new ArrayList<>()).add(p1);
      }
    }

    // Buchholz: sum of opponents' wins. Sonneborn-Berger: sum of beaten opponents' wins.
    List<TournamentBean.StandingInfo> standings = new ArrayList<>();
    for (String c : competitors) {
      int buchholz = opponents.getOrDefault(c, List.of()).stream()
          .mapToInt(opp -> wins.getOrDefault(opp, 0)).sum();
      int sb = beaten.getOrDefault(c, List.of()).stream()
          .mapToInt(opp -> wins.getOrDefault(opp, 0)).sum();
      standings.add(new TournamentBean.StandingInfo(
          0, c, wins.getOrDefault(c, 0), losses.getOrDefault(c, 0), buchholz, sb));
    }

    // Sort: wins desc, buchholz desc, SB desc
    standings.sort(Comparator
        .comparingInt(TournamentBean.StandingInfo::getWins).reversed()
        .thenComparingInt(TournamentBean.StandingInfo::getOpponentStrength).reversed()
        .thenComparingInt(TournamentBean.StandingInfo::getWinStrength).reversed());

    // Assign ranks
    List<TournamentBean.StandingInfo> ranked = new ArrayList<>();
    for (int i = 0; i < standings.size(); i++) {
      TournamentBean.StandingInfo s = standings.get(i);
      ranked.add(new TournamentBean.StandingInfo(
          i + 1, s.getPlayerName(), s.getWins(), s.getLosses(),
          s.getOpponentStrength(), s.getWinStrength()));
    }
    return ranked;
  }

  private Node buildKothContent(List<TournamentBean.MatchInfo> matches,
                                  Map<String, Integer> ratings,
                                  Map<String, String> placementAvatars) {
    List<TournamentBean.MatchInfo> sorted = new ArrayList<>(matches);
    sorted.sort(Comparator.comparingInt(TournamentBean.MatchInfo::getRound));

    VBox column = new VBox(6);
    column.setMaxWidth(300);
    for (TournamentBean.MatchInfo m : sorted) {
      Label header = new Label(i18n.get("tournament.match.header", m.getRound()));
      header.getStyleClass().add("bracket-round-label");
      column.getChildren().add(header);
      column.getChildren().add(buildMatchNode(m, ratings, placementAvatars, null, null));
    }
    return column;
  }

  private Node buildParticipantsView(TournamentBean tournament) {
    VBox container = new VBox(4);
    container.getStyleClass().add("tournament-participants-section");

    Label header = sectionLabel(i18n.get("tournament.participants"));
    container.getChildren().add(header);

    FlowPane flow = new FlowPane();
    flow.getStyleClass().add("tournament-participants");
    flow.setHgap(16);
    flow.setVgap(2);

    Map<String, Integer> ratings = tournament.getParticipantRatings();
    boolean inCheckIn = tournament.getStatus() == TournamentBean.Status.CHECK_IN;
    java.util.Set<String> checkedIn = tournament.getCheckedInParticipantNames();
    java.util.Map<String, TournamentBean.ReputationInfo> reps = tournament.getParticipantReputations();
    for (String name : tournament.getParticipantNames()) {
      Integer rating = ratings != null ? ratings.get(name) : null;
      // Compose the visible string. Check-in tick comes first so the eye
      // catches it; rating in the middle as before; reputation badge on
      // the right so a clean (low-no-show) record stays visually quiet.
      StringBuilder text = new StringBuilder();
      if (inCheckIn) {
        text.append(checkedIn.contains(name) ? "✓ " : "· ");
      }
      text.append(name);
      if (rating != null) text.append(" (").append(rating).append(")");
      String repBadge = formatReputationBadge(reps != null ? reps.get(name) : null);
      if (repBadge != null) text.append(" ").append(repBadge);

      Label l = new Label(text.toString());
      l.getStyleClass().add("participant-entry");
      if (inCheckIn) {
        l.getStyleClass().add(checkedIn.contains(name)
            ? "participant-checked-in" : "participant-unchecked-in");
      }
      String tooltip = buildReputationTooltip(name, reps != null ? reps.get(name) : null,
          inCheckIn ? checkedIn.contains(name) : null);
      if (tooltip != null) {
        javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(tooltip);
        tip.setShowDelay(javafx.util.Duration.millis(300));
        javafx.scene.control.Tooltip.install(l, tip);
      }
      flow.getChildren().add(l);
    }
    container.getChildren().add(flow);
    return container;
  }

  /**
   * Compact "(showed/total)" badge — positive framing of the no-show
   * counter. "shows" = signups - no_check_ins - match_forfeits. Always
   * shown when the player has any signup history; a clean 5/5 is a
   * useful trust signal, not visual noise. Returns null only when
   * signup_count is zero (the player is new — nothing to say yet).
   */
  private String formatReputationBadge(TournamentBean.ReputationInfo rep) {
    if (rep == null) return null;
    int signups = rep.getSignupCount();
    if (signups <= 0) return null;
    // Clamp at 0 in case the no-show counters somehow over-count
    // (shouldn't happen but a negative ratio would be very confusing).
    int shows = Math.max(0, signups - rep.getNoCheckInCount() - rep.getMatchForfeitCount());
    return "(" + shows + "/" + signups + ")";
  }

  /**
   * Tooltip text combining check-in status (during CHECK_IN) and the
   * full no-show breakdown. Returns null when there's nothing useful to
   * show — keeps the tooltip from popping up for clean players outside
   * the check-in window.
   */
  private String buildReputationTooltip(String name, TournamentBean.ReputationInfo rep, Boolean checkedIn) {
    StringBuilder sb = new StringBuilder();
    if (checkedIn != null) {
      sb.append(checkedIn ? i18n.get("tournament.participant.checkedIn")
                          : i18n.get("tournament.participant.notCheckedIn"));
    }
    if (rep != null && rep.getSignupCount() > 0) {
      if (sb.length() > 0) sb.append('\n');
      int shows = Math.max(0, rep.getSignupCount()
          - rep.getNoCheckInCount() - rep.getMatchForfeitCount());
      sb.append(i18n.get("tournament.participant.reputation",
          shows, rep.getSignupCount(),
          rep.getNoCheckInCount(), rep.getMatchForfeitCount()));
    }
    return sb.length() > 0 ? sb.toString() : null;
  }

  /**
   * Build a map of player name → avatar URL from the server-computed placements.
   * No client-side guessing about which match decides which placement.
   */
  private Map<String, String> buildPlacementAvatarMap(TournamentBean tournament) {
    Map<String, String> map = new HashMap<>();
    Map<Integer, List<String>> placements = tournament.getPlacements();
    if (placements == null || placements.isEmpty()) return map;

    String[] avatarsByPlace = {
        null, // index 0 unused
        tournament.getWinnerAvatarUrl(),
        tournament.getSecondPlaceAvatarUrl(),
        tournament.getThirdPlaceAvatarUrl()
    };
    for (int place = 1; place <= 3; place++) {
      String url = avatarsByPlace[place];
      if (url == null) continue;
      List<String> players = placements.get(place);
      if (players != null) {
        for (String player : players) map.put(player, url);
      }
    }
    return map;
  }

  private boolean isWinner(TournamentBean.MatchInfo match, String player) {
    return player != null && match.getWinner() != null && player.equals(match.getWinner());
  }

  /**
   * Scroll a target node into view in the outer (vertical) detail ScrollPane
   * AND in the enclosing bracket-row (horizontal) ScrollPane, if any.
   * Both axes are centered. Stored "bracketScroll" property is set by buildMatchNode.
   */
  private void scrollNodeIntoView(Node target) {
    if (target == null) return;

    // Outer vertical scroll (the whole tournament detail)
    centerInScrollPane(tournamentDetailScrollPane, target, false, true);

    // Inner horizontal scroll (the bracket row)
    Object stored = target.getProperties().get("bracketScroll");
    if (stored instanceof ScrollPane) {
      centerInScrollPane((ScrollPane) stored, target, true, false);
    }
  }

  private void centerInScrollPane(ScrollPane sp, Node target, boolean horizontal, boolean vertical) {
    if (sp == null || sp.getContent() == null) return;
    Node content = sp.getContent();
    javafx.geometry.Bounds boundsInContent = content.sceneToLocal(target.localToScene(target.getBoundsInLocal()));
    javafx.geometry.Bounds viewport = sp.getViewportBounds();

    if (horizontal) {
      double contentWidth = content.getBoundsInLocal().getWidth();
      double viewportWidth = viewport.getWidth();
      if (contentWidth > viewportWidth) {
        double centeredX = boundsInContent.getMinX() + boundsInContent.getWidth() / 2 - viewportWidth / 2;
        double hvalue = Math.max(0, Math.min(1, centeredX / (contentWidth - viewportWidth)));
        sp.setHvalue(hvalue);
      } else {
        sp.setHvalue(0);
      }
    }
    if (vertical) {
      double contentHeight = content.getBoundsInLocal().getHeight();
      double viewportHeight = viewport.getHeight();
      if (contentHeight > viewportHeight) {
        double centeredY = boundsInContent.getMinY() + boundsInContent.getHeight() / 2 - viewportHeight / 2;
        double vvalue = Math.max(0, Math.min(1, centeredY / (contentHeight - viewportHeight)));
        sp.setVvalue(vvalue);
      } else {
        sp.setVvalue(0);
      }
    }
  }

  private String labelForRole(String role, int round) {
    if (role == null) return i18n.get("tournament.bracket.round", Math.abs(round));
    switch (role) {
      case "winners_round": return i18n.get("tournament.bracket.winnersRound", round);
      case "winners_semifinal": return i18n.get("tournament.bracket.winnersSemifinal");
      case "winners_final": return i18n.get("tournament.bracket.winnersFinal");
      case "losers_round": return i18n.get("tournament.bracket.losersRound", Math.abs(round));
      case "losers_final": return i18n.get("tournament.bracket.losersFinal");
      case "grand_final":
      case "top_cut_grand_final": return i18n.get("tournament.bracket.grandFinal");
      case "swiss_round": return i18n.get("tournament.bracket.round", round);
      case "top_cut_winners_round": return i18n.get("tournament.bracket.round", round);
      case "top_cut_winners_semifinal": return i18n.get("tournament.bracket.semifinal");
      case "top_cut_winners_final": return i18n.get("tournament.bracket.final");
      case "top_cut_losers_round": return i18n.get("tournament.bracket.losersRound", Math.abs(round));
      case "top_cut_losers_final": return i18n.get("tournament.bracket.losersFinal");
      case "koth_match": return i18n.get("tournament.match.header", round);
      default: return i18n.get("tournament.bracket.round", Math.abs(round));
    }
  }

  /**
   * Determines which prize badges (1st/2nd/3rd place avatars) belong on a match
   * based on the match's semantic role.
   */
  private static String[] badgesForRole(String role, String[] prizeAvatars) {
    if (role == null || prizeAvatars == null) return null;
    switch (role) {
      // Final match — produces 1st and 2nd place
      case "winners_final":  // single elim final
      case "top_cut_winners_final":
      case "grand_final":
      case "top_cut_grand_final":
        if (prizeAvatars[0] != null || prizeAvatars[1] != null) {
          return new String[]{prizeAvatars[0], prizeAvatars[1]};
        }
        return null;
      // Semifinal — losers get 3rd
      case "winners_semifinal":  // single elim semifinal
      case "top_cut_winners_semifinal":
        // Only show 3rd badge if this is single-elim (no losers bracket).
        // For double elim top cut, semifinal losers go to losers bracket.
        // We can detect this by whether the prize is shown elsewhere; safest:
        // for "winners_semifinal" in a single elim bracket only.
        // Use a heuristic: if it's a top_cut role, check below
        if (prizeAvatars[2] != null) {
          return new String[]{prizeAvatars[2]};
        }
        return null;
      // L Final — loser gets 3rd in double elimination
      case "losers_final":
      case "top_cut_losers_final":
        if (prizeAvatars[2] != null) {
          return new String[]{prizeAvatars[2]};
        }
        return null;
      default:
        return null;
    }
  }

  // ====================================================================
  // Team panel (team tournaments only)
  // ====================================================================

  private static int parseIdSafe(String id) {
    if (id == null) return 0;
    try {
      return Integer.parseInt(id);
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  /**
   * Build the team panel that lives above the bracket for team
   * tournaments. The panel shows pending invites for the local player
   * (with accept / decline buttons), the player's current team (with
   * leave + invite-by-name buttons), and a read-only list of every
   * other team. The whole panel rebuilds itself whenever
   * {@link TournamentTeamService#getTeams()} or {@code getPendingInvites()}
   * change, so server-driven updates flow through automatically.
   */
  private VBox buildTeamPanel(TournamentBean tournamentBean) {
    VBox root = new VBox(8);
    root.getStyleClass().add("tournament-team-panel");
    root.setPadding(new Insets(10, 0, 10, 0));

    int pps = tournamentBean.getPlayersPerSide();
    Label heading = new Label(i18n.get("tournament.team.header", pps, pps));
    heading.getStyleClass().add("bracket-section-label");
    root.getChildren().add(heading);

    // Container that gets cleared and re-populated on every refresh.
    VBox dynamicContent = new VBox(6);
    root.getChildren().add(dynamicContent);

    // Focus-preserving rerender: if the user is typing into any
    // TextField under this subtree when a refresh arrives, skip the
    // rebuild and defer it until they lose focus. Rebuilding destroys
    // and replaces every child node, which in JavaFX means the
    // currently-focused TextField vanishes mid-keystroke — text is
    // preserved in the backing store but the caret and IME state are
    // lost, and any partially-typed team name / invite input gets
    // snatched away from under the user. The defer-until-blur trick
    // is the lightest-weight way to keep typing uninterrupted without
    // a full save/restore dance. The next user action (tab out, click
    // another field, click a button, submit) naturally transfers focus
    // and triggers the pending rebuild.
    Runnable rerender = new Runnable() {
      @Override
      public void run() {
        javafx.scene.Scene scene = dynamicContent.getScene();
        javafx.scene.Node focusOwner = scene != null ? scene.getFocusOwner() : null;
        if (focusOwner instanceof javafx.scene.control.TextInputControl
            && isDescendant(dynamicContent, focusOwner)) {
          focusOwner.focusedProperty().addListener(new javafx.beans.value.ChangeListener<Boolean>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Boolean> obs,
                                Boolean was, Boolean isNow) {
              if (Boolean.FALSE.equals(isNow)) {
                obs.removeListener(this);
                JavaFxUtil.runLater(() -> run());
              }
            }
          });
          return;
        }
        rebuildTeamPanelContent(dynamicContent, tournamentBean);
      }
    };
    rerender.run();

    // Deregister previous listeners to prevent accumulation across
    // tournament selections. Each buildTeamPanel call replaces the old
    // listeners with fresh ones tied to the current panel's container.
    if (teamsListener != null) {
      teamService.getTeams().removeListener(teamsListener);
    }
    if (invitesListener != null) {
      teamService.getPendingInvites().removeListener(invitesListener);
    }
    if (myTeamIdListener != null) {
      teamService.getMyTeamId().removeListener(myTeamIdListener);
    }
    teamsListener = c -> JavaFxUtil.runLater(rerender);
    invitesListener = c -> JavaFxUtil.runLater(rerender);
    myTeamIdListener = (obs, oldVal, newVal) -> JavaFxUtil.runLater(rerender);
    teamService.getTeams().addListener(teamsListener);
    teamService.getPendingInvites().addListener(invitesListener);
    teamService.getMyTeamId().addListener(myTeamIdListener);

    return root;
  }

  private void rebuildTeamPanelContent(VBox container, TournamentBean tournamentBean) {
    container.getChildren().clear();

    int tournamentId = parseIdSafe(tournamentBean.getId());
    int myPlayerId = playerService.getCurrentPlayer().map(p -> p.getId()).orElse(0);
    boolean isComplete = "complete".equals(tournamentBean.getApiState())
        || "cancelled".equals(tournamentBean.getApiState());

    // ---- Pending invites for me (top of panel, hidden for complete) ----
    if (!isComplete) for (com.faforever.client.remote.domain.TournamentTeamInviteReceivedMessage invite
        : teamService.getPendingInvites()) {
      VBox card = new VBox(4);
      card.getStyleClass().add("team-invite-card");
      String inviterName = invite.getInviterName() != null ? invite.getInviterName() : "?";
      String teamName = invite.getTeamName() != null ? invite.getTeamName() : "?";
      Label text = new Label(i18n.get("tournament.team.inviteMessage", inviterName, teamName));
      text.getStyleClass().add("team-invite-text");
      text.setWrapText(true);
      HBox buttons = new HBox(8);
      buttons.setAlignment(Pos.CENTER_LEFT);
      Button accept = new Button(i18n.get("tournament.team.accept"));
      accept.getStyleClass().add("team-btn-primary");
      Button decline = new Button(i18n.get("tournament.team.decline"));
      accept.setOnAction(e -> teamService.acceptInvite(invite.getInviteId()));
      decline.setOnAction(e -> teamService.declineInvite(invite.getInviteId()));
      buttons.getChildren().addAll(accept, decline);
      card.getChildren().addAll(text, buttons);
      container.getChildren().add(card);
    }

    // ---- My team (or "create team" prompt, hidden for complete) ----
    Map<String, Object> myTeam = findTeamForPlayer(myPlayerId);
    if (myTeam == null && !isComplete) {
      VBox createCard = new VBox(6);
      createCard.getStyleClass().add("team-card");
      Label prompt = new Label(i18n.get("tournament.team.noTeamYet"));
      prompt.getStyleClass().add("team-name");
      HBox createRow = new HBox(8);
      createRow.setAlignment(Pos.CENTER_LEFT);
      javafx.scene.control.TextField nameField = new javafx.scene.control.TextField();
      nameField.setPromptText(i18n.get("tournament.team.namePlaceholder"));
      nameField.setPrefWidth(200);
      Button create = new Button(i18n.get("tournament.team.create"));
      create.getStyleClass().add("team-btn-primary");
      create.setOnAction(e -> {
        String name = nameField.getText();
        if (name != null && !name.isBlank()) {
          teamService.createTeam(tournamentId, name.trim());
        }
      });
      createRow.getChildren().addAll(nameField, create);
      createCard.getChildren().addAll(prompt, createRow);
      container.getChildren().add(createCard);
    } else if (myTeam != null) {
      container.getChildren().add(buildOwnTeamBlock(myTeam, myPlayerId, tournamentBean));
    }

    // ---- Other teams (skip our own — already shown above with controls) ----
    Integer myTeamId = teamService.getMyTeamId().get();
    boolean hasOthers = teamService.getTeams().stream()
        .anyMatch(t -> myTeamId == null || asInt(t.get("id")) != myTeamId.intValue());
    if (hasOthers) {
      container.getChildren().add(new javafx.scene.control.Separator());
      FlowPane teamsFlow = new FlowPane();
      teamsFlow.setHgap(8);
      teamsFlow.setVgap(8);
      for (Map<String, Object> team : teamService.getTeams()) {
        if (myTeamId != null && asInt(team.get("id")) == myTeamId.intValue()) {
          continue;
        }
        VBox card = buildTeamCard(team, tournamentBean);
        card.setPrefWidth(280);
        card.setMaxWidth(280);
        teamsFlow.getChildren().add(card);
      }
      container.getChildren().add(teamsFlow);
    }
  }

  private VBox buildOwnTeamBlock(Map<String, Object> myTeam, int myPlayerId, TournamentBean tournament) {
    VBox box = new VBox(6);
    box.getStyleClass().addAll("team-card", "team-card-mine");
    box.setMaxWidth(Double.MAX_VALUE);
    int teamId = asInt(myTeam.get("id"));
    int captainId = asInt(myTeam.get("captain_id"));
    String name = String.valueOf(myTeam.getOrDefault("name", "?"));
    String ratingType = tournament.getLeaderboardTechnicalName();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> members = (List<Map<String, Object>>) myTeam.get("members");
    int pps = tournament.getPlayersPerSide();
    int teamAgg = computeTeamAggregateRating(members, ratingType, pps, tournament);

    // Header row: team name + badges
    HBox header = new HBox(8);
    header.getStyleClass().add("team-card-header");
    Label nameLabel = new Label(name);
    nameLabel.getStyleClass().add("team-name");
    header.getChildren().add(nameLabel);
    Object seedObj = myTeam.get("seed");
    if (seedObj instanceof Number) {
      Label seed = new Label(i18n.get("tournament.team.seed", ((Number) seedObj).intValue()));
      seed.getStyleClass().add("team-badge");
      header.getChildren().add(seed);
    }
    if (teamAgg != 0) {
      Label rating = new Label(String.valueOf(teamAgg));
      rating.getStyleClass().add("team-rating");
      header.getChildren().add(rating);
    }
    box.getChildren().add(header);

    // Members
    if (members != null) {
      boolean inCheckIn = tournament.getStatus() == TournamentBean.Status.CHECK_IN;
      java.util.Set<String> checkedIn = tournament.getCheckedInParticipantNames();
      for (Map<String, Object> m : members) {
        HBox memberRow = new HBox(6);
        memberRow.getStyleClass().add("team-member-row");
        String memberName = String.valueOf(m.getOrDefault("player_name", "?"));
        // Mirror the participants-list convention: ✓ for checked-in, · for not.
        // Only while we're in CHECK_IN — outside that window the indicator
        // is meaningless and would just add clutter.
        String displayName = inCheckIn
            ? (checkedIn.contains(memberName) ? "✓ " : "· ") + memberName
            : memberName;
        Label memberLabel = new Label(displayName);
        memberLabel.getStyleClass().add("team-member-name");
        if (inCheckIn) {
          memberLabel.getStyleClass().add(checkedIn.contains(memberName)
              ? "participant-checked-in" : "participant-unchecked-in");
        }
        memberRow.getChildren().add(memberLabel);
        int memberPid = asInt(m.get("player_id"));
        if (memberPid == captainId) {
          Label cap = new Label(i18n.get("tournament.team.captain"));
          cap.getStyleClass().add("team-member-captain");
          memberRow.getChildren().add(cap);
        }
        int memberRating = getMemberRating(memberName, ratingType, tournament);
        if (memberRating != 0) {
          Label rLbl = new Label(String.valueOf(memberRating));
          rLbl.getStyleClass().add("team-member-rating");
          memberRow.getChildren().add(rLbl);
        }
        // Remove button visibility rules (player client only):
        //  - Can't kick the captain (transfer captaincy first)
        //  - Can't kick anyone once the tournament is underway
        //    (server enforces has-played; we hide proactively)
        //  - Can't kick yourself (use Leave instead)
        boolean isStarted = "underway".equals(tournament.getApiState())
            || "complete".equals(tournament.getApiState());
        boolean canKick = memberPid != myPlayerId
            && memberPid != 0
            && memberPid != captainId
            && !isStarted;
        if (canKick) {
          Region spacer = new Region();
          HBox.setHgrow(spacer, Priority.ALWAYS);
          Button kick = new Button(i18n.get("tournament.team.remove"));
          kick.getStyleClass().add("team-btn-danger");
          kick.setOnAction(e -> teamService.removeMember(teamId, memberPid));
          memberRow.getChildren().addAll(spacer, kick);
        }
        box.getChildren().add(memberRow);
      }
    }

    // Actions — visibility gated by tournament state:
    //   Complete:  no controls at all (read-only historical view)
    //   Underway:  invite only (substitution), no leave/disband/remove
    //   Pending:   all controls
    boolean isComplete = "complete".equals(tournament.getApiState())
        || "cancelled".equals(tournament.getApiState());
    boolean isUnderway = "underway".equals(tournament.getApiState());

    if (!isComplete) {
      HBox actions = new HBox(8);
      actions.getStyleClass().add("team-actions");

      // Invite (pending + underway — substitution is allowed mid-tournament)
      javafx.scene.control.TextField inviteField = new javafx.scene.control.TextField();
      inviteField.setPromptText(i18n.get("tournament.team.invitePlaceholder"));
      inviteField.setPrefWidth(220);
      bindInviteAutoCompletion(inviteField, teamId);
      Button invite = new Button(i18n.get("tournament.team.invite"));
      invite.getStyleClass().add("team-btn-primary");
      Runnable trySendInvite = () -> {
        String raw = inviteField.getText() == null ? "" : inviteField.getText().trim();
        if (raw.isEmpty()) return;
        String inviteeName = stripClanDecoration(raw);
        Integer pid = invitePlayerNameToId.get(inviteeName.toLowerCase(java.util.Locale.US));
        if (pid != null) {
          teamService.invitePlayer(teamId, pid);
          inviteField.clear();
          return;
        }
        fafService.queryPlayerByName(inviteeName).thenAccept(opt -> JavaFxUtil.runLater(() -> {
          if (opt.isPresent()) {
            teamService.invitePlayer(teamId, opt.get().getId());
            inviteField.clear();
          }
        }));
      };
      invite.setOnAction(e -> trySendInvite.run());
      inviteField.setOnAction(e -> trySendInvite.run());
      actions.getChildren().addAll(invite, inviteField);

      // Leave + Disband (pending only)
      if (!isUnderway) {
        Button leave = new Button(i18n.get("tournament.team.leave"));
        leave.setOnAction(e -> teamService.leaveTeam(teamId));
        actions.getChildren().add(leave);

        Button disband = new Button(i18n.get("tournament.team.disband"));
        disband.getStyleClass().add("team-btn-danger");
        disband.setOnAction(e -> teamService.disbandTeam(teamId));
        actions.getChildren().add(disband);
      }

      box.getChildren().add(actions);
    }
    return box;
  }

  /**
   * Persistent lower-cased name → id map for the invite field's autocomplete.
   * Updated as suggestion lookups complete; read at invite-send time so the
   * chosen display name resolves to the right id without a second round-trip.
   * Cleared and repopulated each time the team panel is rebuilt — the panel
   * is short-lived so unbounded growth isn't a concern in practice.
   */
  private final java.util.Map<String, Integer> invitePlayerNameToId =
      new java.util.concurrent.ConcurrentHashMap<>();
  /** Lower-cased name → display-case name. Lets us show "Foo_Bar" in the
   *  popup even for offline players where PlayerService doesn't know them. */
  private final java.util.Map<String, String> invitePlayerDisplayName =
      new java.util.concurrent.ConcurrentHashMap<>();
  /** Lower-cased name → clan tag (already lower-cased), or absent if the
   *  player has no clan. Used by the suggestion filter for clan-tag
   *  matching, mirroring how chat users perceive (but doesn't actually
   *  do — see ChannelTabController) clan-aware autocomplete. */
  private final java.util.Map<String, String> invitePlayerClanLower =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** In-flight debounce timer for the api search; cancelled on each keystroke. */
  private javafx.animation.PauseTransition inviteSearchDebounce;

  private void bindInviteAutoCompletion(javafx.scene.control.TextField field, int teamId) {
    invitePlayerNameToId.clear();
    invitePlayerDisplayName.clear();
    invitePlayerClanLower.clear();

    // Pre-seed with currently-online players. Names, ids, AND clan tags
    // all come from PlayerService's in-memory cache so the popup is
    // useful immediately, before any api round-trip. Offline players
    // get added later as the debounced api search returns results.
    for (String name : playerService.getPlayerNames()) {
      playerService.getPlayerForUsername(name).ifPresent(p -> {
        String lc = name.toLowerCase(java.util.Locale.US);
        invitePlayerNameToId.put(lc, p.getId());
        invitePlayerDisplayName.put(lc, name);
        String clan = p.getClan();
        if (clan != null && !clan.isBlank()) {
          invitePlayerClanLower.put(lc, clan.toLowerCase(java.util.Locale.US));
        }
      });
    }

    org.controlsfx.control.textfield.AutoCompletionBinding<String> binding =
        org.controlsfx.control.textfield.TextFields.bindAutoCompletion(field, request -> {
          String text = request.getUserText();
          if (text == null || text.isBlank()) {
            return java.util.Collections.emptyList();
          }
          String lc = text.toLowerCase(java.util.Locale.US);
          // Synchronous: filter the local name pool. We accept a player
          // when ANY of these match:
          //   1. Login startsWith the prefix
          //   2. Any underscore-split part of the login startsWith the prefix
          //      (mirrors the chat client's behaviour, so e.g. typing
          //      "Axle" matches "TAFR_Axle")
          //   3. The player's clan tag startsWith the prefix
          //      (genuine clan-tag awareness — chat conflates this with
          //      the underscore split, but we look at the actual
          //      Player.clan field, so it works for clans whose tag
          //      doesn't appear in the login string)
          return invitePlayerNameToId.keySet().stream()
              .filter(name -> matchesInvitePrefix(name, lc))
              .sorted()
              .limit(20)
              .map(this::displayWithClan)
              .collect(java.util.stream.Collectors.toList());
        });
    binding.setDelay(0);
    binding.setVisibleRowCount(10);

    // Async: when the user has typed at least 2 chars, debounce 500ms
    // and fire a faf-api search for offline players. 500ms is the
    // common autocomplete sweet spot — fast enough that the popup
    // feels reactive, slow enough that someone typing at 2-3 chars/sec
    // doesn't fire 4 wasted api calls before they finish a name.
    // Results merge into invitePlayerNameToId; we then re-trigger the
    // popup so new entries appear without the user having to type again.
    field.textProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal == null || newVal.length() < 2) return;
      if (inviteSearchDebounce != null) inviteSearchDebounce.stop();
      inviteSearchDebounce = new javafx.animation.PauseTransition(
          javafx.util.Duration.millis(500));
      inviteSearchDebounce.setOnFinished(ev -> {
        String prefix = newVal.trim();
        fafService.findPlayersByLoginPrefix(prefix, 15).thenAccept(players ->
            JavaFxUtil.runLater(() -> {
              boolean changed = false;
              for (com.faforever.client.api.dto.Player p : players) {
                String key = p.getLogin().toLowerCase(java.util.Locale.US);
                Integer existing = invitePlayerNameToId.get(key);
                int newId = Integer.parseInt(p.getId());
                if (existing == null || existing != newId) {
                  invitePlayerNameToId.put(key, newId);
                  invitePlayerDisplayName.put(key, p.getLogin());
                  changed = true;
                }
              }
              // Re-trigger the popup if new names came in AND the field
              // still contains the same prefix that prompted the search.
              if (changed && prefix.equals(field.getText() == null ? "" : field.getText().trim())) {
                binding.setUserInput(field.getText());
              }
            })).exceptionally(t -> {
              log.debug("findPlayersByLoginPrefix failed for {}", prefix, t);
              return null;
            });
      });
      inviteSearchDebounce.play();
    });
  }

  /** Returns the display-case version of a lowercased name from the
   *  per-panel cache, falling back to the lowercased form. */
  private String displayCaseFor(String lcName) {
    String cached = invitePlayerDisplayName.get(lcName);
    return cached != null ? cached : lcName;
  }

  /** Decorates the display name with clan tag like "[TAFR] Axle" when
   *  available. The name→id resolution at invite-send time strips this
   *  decoration via stripClanDecoration. */
  private String displayWithClan(String lcName) {
    String name = displayCaseFor(lcName);
    String clanLower = invitePlayerClanLower.get(lcName);
    if (clanLower == null) {
      return name;
    }
    // We don't store the original-case clan tag (only lowercased), so
    // upper-case it for display since clan tags are conventionally caps.
    return "[" + clanLower.toUpperCase(java.util.Locale.US) + "] " + name;
  }

  /** Inverse of displayWithClan — extracts just the player name from
   *  the decorated form. */
  private static String stripClanDecoration(String decorated) {
    if (decorated == null) return "";
    int rb = decorated.indexOf("] ");
    if (decorated.startsWith("[") && rb > 0) {
      return decorated.substring(rb + 2).trim();
    }
    return decorated.trim();
  }

  /** Get the displayed rating for a player by name. Tries the local
   *  PlayerService first (live mean/deviation from the lobby), then
   *  falls back to the tournament's participant signup rating. Returns
   *  0 if neither source has a value. */
  private int getMemberRating(String playerName, String ratingType,
                               TournamentBean tournament) {
    if (playerName == null) return 0;
    if (ratingType != null) {
      int live = playerService.getPlayerForUsername(playerName)
          .map(p -> com.faforever.client.util.RatingUtil.getLeaderboardRating(p, ratingType))
          .orElse(0);
      if (live != 0) return live;
    }
    // Fallback: signup-time rating stored on the participant
    if (tournament != null && tournament.getParticipantRatings() != null) {
      Integer stored = tournament.getParticipantRatings().get(playerName);
      if (stored != null) return stored;
    }
    return 0;
  }

  /**
   * Enrich the ratings map with per-team aggregate ratings for team tournaments.
   * Uses the team service's cached team data (populated when the tournament is
   * selected) and the principled inverse-variance-weighted aggregate via
   * PlayerService. For teams whose members aren't locally known (offline),
   * the simple average from TournamentBean (signup ratings) is already in the
   * map as a fallback.
   */
  private void enrichTeamRatings(TournamentBean tournament, Map<String, Integer> ratings) {
    String ratingType = tournament.getLeaderboardTechnicalName();
    int pps = tournament.getPlayersPerSide();
    for (Map<String, Object> team : teamService.getTeams()) {
      String teamName = (String) team.get("name");
      if (teamName == null) continue;
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> members = (List<Map<String, Object>>) team.get("members");
      if (members == null || members.isEmpty()) continue;
      int agg = computeTeamAggregateRating(members, ratingType, pps, tournament);
      if (agg > 0) {
        ratings.put(teamName, agg);
      }
    }
  }

  /** Compute the aggregate displayed rating for a team from its member list,
   *  using only the top N rated members where N = players_per_side.
   *  Tries the principled inverse-variance-weighted formula (via PlayerService)
   *  for members the client has seen. Falls back to averaging signup-time
   *  participant ratings from the tournament bean for members that aren't
   *  locally known. Returns 0 if no rated members at all. */
  private int computeTeamAggregateRating(List<Map<String, Object>> members,
                                          String ratingType, int playersPerSide,
                                          TournamentBean tournament) {
    if (members == null || members.isEmpty()) return 0;

    // Try principled aggregation from live PlayerService data
    List<com.faforever.client.leaderboard.LeaderboardRating> liveRatings = new ArrayList<>();
    if (ratingType != null) {
      for (Map<String, Object> m : members) {
        String memberName = String.valueOf(m.getOrDefault("player_name", ""));
        playerService.getPlayerForUsername(memberName).ifPresent(p -> {
          com.faforever.client.leaderboard.LeaderboardRating lr = p.getLeaderboardRatings().get(ratingType);
          if (lr != null) liveRatings.add(lr);
        });
      }
    }
    if (!liveRatings.isEmpty()) {
      liveRatings.sort((a, b) -> Integer.compare(
          com.faforever.client.util.RatingUtil.getRating(b),
          com.faforever.client.util.RatingUtil.getRating(a)));
      int n = Math.max(1, playersPerSide);
      List<com.faforever.client.leaderboard.LeaderboardRating> top =
          liveRatings.size() > n ? liveRatings.subList(0, n) : liveRatings;
      com.faforever.client.leaderboard.LeaderboardRating agg =
          com.faforever.client.util.RatingUtil.getAggregateRating(top);
      if (agg != null) return com.faforever.client.util.RatingUtil.getRating(agg);
    }

    // Fallback: average the signup-time participant ratings
    if (tournament != null && tournament.getParticipantRatings() != null) {
      int sum = 0, count = 0;
      for (Map<String, Object> m : members) {
        String memberName = String.valueOf(m.getOrDefault("player_name", ""));
        Integer r = tournament.getParticipantRatings().get(memberName);
        if (r != null) { sum += r; count++; }
      }
      if (count > 0) return sum / count;
    }
    return 0;
  }

  private Label dateLabel(String label, java.time.temporal.TemporalAccessor dateTime) {
    Label l = new Label(label + ": " + formatTournamentDateTime(dateTime));
    l.getStyleClass().add("description-label");
    return l;
  }

  /** Format a tournament timestamp respecting the user's UTC/local toggle on the detail pane. */
  private String formatTournamentDateTime(java.time.temporal.TemporalAccessor dateTime) {
    return displayTimesAsUtc
        ? timeService.asDateTime(dateTime, java.time.ZoneOffset.UTC)
        : timeService.asDateTime(dateTime);
  }

  private boolean matchesInvitePrefix(String lcName, String lcPrefix) {
    // Direct prefix match
    if (lcName.startsWith(lcPrefix)) {
      return true;
    }
    // Clan-tag match (real clan field, not part of the login)
    String lcClan = invitePlayerClanLower.get(lcName);
    if (lcClan != null && lcClan.startsWith(lcPrefix)) {
      return true;
    }
    // Underscore-split parts: matches chat behaviour where typing
    // "Axle" finds "TAFR_Axle". Skip if the prefix itself contains an
    // underscore — then the user clearly wants the whole-name match.
    if (!lcPrefix.contains("_")) {
      for (String part : lcName.split("_")) {
        if (part.startsWith(lcPrefix)) {
          return true;
        }
      }
    }
    return false;
  }

  private VBox buildTeamCard(Map<String, Object> team, TournamentBean tournament) {
    VBox card = new VBox(4);
    card.getStyleClass().add("team-card");
    int captainId = asInt(team.get("captain_id"));
    String name = String.valueOf(team.getOrDefault("name", "?"));
    String ratingType = tournament.getLeaderboardTechnicalName();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> members = (List<Map<String, Object>>) team.get("members");

    int pps = tournament.getPlayersPerSide();
    int teamAgg = computeTeamAggregateRating(members, ratingType, pps, tournament);

    // Header
    HBox header = new HBox(8);
    header.getStyleClass().add("team-card-header");
    Label nameLabel = new Label(name);
    nameLabel.getStyleClass().add("team-name");
    header.getChildren().add(nameLabel);
    Object seedObj = team.get("seed");
    if (seedObj instanceof Number) {
      Label seed = new Label(i18n.get("tournament.team.seed", ((Number) seedObj).intValue()));
      seed.getStyleClass().add("team-badge");
      header.getChildren().add(seed);
    }
    if (teamAgg != 0) {
      Label rating = new Label(String.valueOf(teamAgg));
      rating.getStyleClass().add("team-rating");
      header.getChildren().add(rating);
    }
    card.getChildren().add(header);

    // Members
    if (members != null) {
      boolean inCheckIn = tournament.getStatus() == TournamentBean.Status.CHECK_IN;
      java.util.Set<String> checkedIn = tournament.getCheckedInParticipantNames();
      for (Map<String, Object> m : members) {
        HBox memberRow = new HBox(6);
        memberRow.getStyleClass().add("team-member-row");
        String memberName = String.valueOf(m.getOrDefault("player_name", "?"));
        // Mirror the participants-list convention: ✓ for checked-in, · for not.
        // Only while we're in CHECK_IN — outside that window the indicator
        // is meaningless and would just add clutter.
        String displayName = inCheckIn
            ? (checkedIn.contains(memberName) ? "✓ " : "· ") + memberName
            : memberName;
        Label memberLabel = new Label(displayName);
        memberLabel.getStyleClass().add("team-member-name");
        if (inCheckIn) {
          memberLabel.getStyleClass().add(checkedIn.contains(memberName)
              ? "participant-checked-in" : "participant-unchecked-in");
        }
        memberRow.getChildren().add(memberLabel);
        int memberPid = asInt(m.get("player_id"));
        if (memberPid == captainId) {
          Label cap = new Label(i18n.get("tournament.team.captain"));
          cap.getStyleClass().add("team-member-captain");
          memberRow.getChildren().add(cap);
        }
        int memberRating = getMemberRating(memberName, ratingType, tournament);
        if (memberRating != 0) {
          Label rLbl = new Label(String.valueOf(memberRating));
          rLbl.getStyleClass().add("team-member-rating");
          memberRow.getChildren().add(rLbl);
        }
        card.getChildren().add(memberRow);
      }
    } else {
      Label empty = new Label(i18n.get("tournament.team.noMembers"));
      empty.getStyleClass().add("team-member-rating");
      card.getChildren().add(empty);
    }
    return card;
  }

  private Map<String, Object> findTeamForPlayer(int playerId) {
    // Primary path: trust the server-supplied my_team_id (computed from
    // tournament_team_member, which is the source of truth). This avoids
    // any membership-scan / number-coercion fragility on the client.
    Integer myTeamId = teamService.getMyTeamId().get();
    if (myTeamId != null) {
      for (Map<String, Object> team : teamService.getTeams()) {
        if (asInt(team.get("id")) == myTeamId.intValue()) {
          return team;
        }
      }
    }
    // Fallback: scan member dicts. Kept so the panel still works if a
    // future server response forgets to set my_team_id.
    if (playerId == 0) return null;
    for (Map<String, Object> team : teamService.getTeams()) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> members = (List<Map<String, Object>>) team.get("members");
      if (members == null) continue;
      for (Map<String, Object> m : members) {
        if (asInt(m.get("player_id")) == playerId) {
          return team;
        }
      }
    }
    return null;
  }

  /** Robust int coercion for values inside Gson-parsed Map<String, Object>:
   *  Number (Integer, Long, Double from raw JSON), String, or null. */
  private static int asInt(Object o) {
    if (o instanceof Number) return ((Number) o).intValue();
    if (o instanceof String) {
      try { return Integer.parseInt((String) o); }
      catch (NumberFormatException ignored) { return 0; }
    }
    return 0;
  }

  private static boolean objectsEqual(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    if (a instanceof Number && b instanceof Number) {
      return ((Number) a).intValue() == ((Number) b).intValue();
    }
    return a.equals(b);
  }

}
