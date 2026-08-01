package com.faforever.client.wager;

import com.faforever.client.api.dto.WagerMarket;
import com.faforever.client.audio.AudioService;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameService;
import com.faforever.client.game.TeamCardController;
import com.faforever.client.galacticwar.GalacticWarService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.ladder.LadderPointsService;
import com.faforever.client.ladder.SeasonStanding;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.ShowReplayEvent;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.DisplayMetric;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.rating.RatingService;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.replay.ReplayService;
import com.faforever.client.util.RatingUtil;
import com.faforever.client.theme.UiService;
import com.google.common.eventbus.EventBus;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Live-game wagering tab (WAGER_DESIGN.md §11): a watchlist of live games + my LP per board;
 * for the selected game its team cards (names + ratings/ranks per the Season Ladder / Skill
 * pill + aggregate, kept live), a shares↔LP dual-input trade panel, a candlestick price
 * chart for the selected outcome, and my portfolio.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class WagerController extends AbstractViewController<Node> {

  private static final int PAYOUT = WagerService.SHARE_PAYOUT_LP;

  private final WagerService wagerService;
  private final I18n i18n;
  private final GameService gameService;
  private final LadderPointsService ladderPointsService;
  private final PlayerService playerService;
  private final UiService uiService;
  private final RatingService ratingService;
  private final GalacticWarService galacticWarService;
  private final PreferencesService preferencesService;
  private final MapService mapService;
  private final AudioService audioService;
  private final ReplayService replayService;
  private final LeaderboardService leaderboardService;
  private final EventBus eventBus;

  public VBox wagerRoot;
  public TitledPane liveGamesPane;
  public TitledPane myLpPane;
  public TitledPane botTitledPane;
  public TitledPane portfolioPane;
  public ListView<GameRow> gamesList;
  public Button refreshButton;
  public ListView<BoardLp> myLpList;
  public WagerBotPnlController botPnlController;   // injected from <fx:include fx:id="botPnl">
  public Label boardLabel;
  public Label marketStatusLabel;
  public ImageView mapImageView;
  public Label mapNameLabel;
  public HBox teamCardsPane;
  public Separator outcomesSeparator;
  public VBox outcomesColumn;        // outcomes table + the trade controls beneath it
  public TableView<WagerOutcomeBean> outcomesTable;
  public TableColumn<WagerOutcomeBean, String> outcomeColumn;
  public TableColumn<WagerOutcomeBean, Number> priceColumn;
  public TextField buySharesField;   // buy: shares to buy
  public Label buyPreviewLabel;      // "≈ N LP"
  public Button buyButton;
  public TextField sharesField;      // sell: shares to sell
  public Button maxButton;
  public Label sellPreviewLabel;     // "≈ N LP"
  public Button sellButton;
  public Label tradeStatusLabel;
  public LineChart<Number, Number> priceChart;
  public Pane chartOverlay;          // sits on top of the chart; children positioned to the axes
  public Label chartSeriesLabel;     // series name, overlaid top-left (the chart legend is off)
  public Button openReplayButton;    // centred on the chart once the game is over
  public HBox tradeLegend;           // mine/others colour key for the trade ticks, bottom-left
  public TableView<WagerPositionBean> portfolioTable;
  public TableColumn<WagerPositionBean, String> posGameColumn;
  public TableColumn<WagerPositionBean, WagerPositionBean> posStatusColumn;
  public TableColumn<WagerPositionBean, String> posMarketColumn;
  public TableColumn<WagerPositionBean, Number> posSharesColumn;
  public TableColumn<WagerPositionBean, Number> posCostColumn;
  public TableColumn<WagerPositionBean, Number> posValueColumn;
  public TableColumn<WagerPositionBean, Number> posPnlColumn;

  private ObservableList<WagerMarketBean> currentMarkets;
  private final InvalidationListener marketsListener = obs -> onMarketsChanged();
  private String selectedRatingType;
  private int selectedGameId = -1;
  private final Map<String, Integer> myLpByBoard = new HashMap<>();
  /** Leaderboard technical name → human-readable display name, so the wager tab never surfaces a
   * raw technical name. Populated once from {@link LeaderboardService}; falls back to the technical
   * name only until it loads (or if a board is unknown). */
  private final Map<String, String> boardDisplayNames = new HashMap<>();
  private final BooleanProperty submitting = new SimpleBooleanProperty(false);
  /** True whenever the selected market can't be traded — no market, the game is over, or the
   * market has closed/settled/voided. Drives both the header status label and the trade controls. */
  private final BooleanProperty marketNotTradeable = new SimpleBooleanProperty(true);
  /** False until the user picks a game off the watchlist; the outcome picker (and its divider) stay
   * hidden until then rather than showing an empty table with nothing to say. */
  private final BooleanProperty gameSelected = new SimpleBooleanProperty(false);

  // Game-over plumbing. The server only pushes `wager_settled` (it never announces the OPEN ->
  // CLOSED flip), and that message mutates the market bean in place rather than the markets list,
  // so nothing re-renders off the list listener. We therefore watch two things directly: the
  // market's own status property, and the selected game's status (which reaches ENDED as soon as
  // the lobby says so — usually before settlement lands).
  private WagerMarketBean statusBoundMarket;
  private final ChangeListener<String> marketStatusListener =
      (obs, old, status) -> JavaFxUtil.runLater(this::updateMarketStatus);
  private Game endWatchedGame;
  private final ChangeListener<GameStatus> gameStatusListener =
      (obs, old, status) -> JavaFxUtil.runLater(this::updateMarketStatus);

  // When a portfolio row asks to open its market, the outcomes load asynchronously after the
  // game is selected — stash the outcome to select and apply it once they arrive.
  private String pendingOutcomeKey;
  // Last outcome the user picked for the selected game, so navigating away from the tab and back
  // restores both the game and the outcome (as long as the game is still on the watchlist).
  private String lastSelectedOutcomeKey;

  // price chart state (for the selected outcome)
  private WagerOutcomeBean chartOutcome;
  private ChangeListener<Number> chartPriceListener;
  private final List<double[]> chartPoints = new ArrayList<>();   // [epochSeconds, price 0..1]
  // Human-trade markers on the chart (from the trade log; the bot is filtered service-side).
  private final List<WagerService.TradeMarker> chartMarkers = new ArrayList<>();
  /** The first plotted price (0..100) of the current series — the vertical anchor for the overlaid
   * series-name label, so it sits with the line rather than in a corner. */
  private double chartOpeningPrice;
  /** Margin between the overlay panels and the plot edges / the price line. */
  private static final double OVERLAY_GAP = 8;
  /** Live price ticks are anonymous (the WS deliberately never says WHO traded — bot masking),
   * so new-trade markers only exist in the trade log; re-fetch it on ticks, at most this often. */
  private static final long MARKER_REFRESH_MS = 10_000;
  private long lastHistoryFetchMs;

  // Rebuild the team cards when the global Season-Ladder / Skill-Rating pill flips (the card
  // bakes the metric at build time; held strongly so the weak listener isn't collected).
  private final ChangeListener<DisplayMetric> displayMetricListener =
      (obs, oldValue, newValue) -> {
        if (selectedGameId != -1) {
          buildTeamCards(selectedGameId);
        }
      };

  @Override
  public Node getRoot() {
    return wagerRoot;
  }

  @Override
  public void initialize() {
    outcomeColumn.setCellValueFactory(param -> new SimpleStringProperty(outcomeDisplay(param.getValue())));
    priceColumn.setCellValueFactory(param -> {
      SimpleDoubleProperty p = new SimpleDoubleProperty();
      p.bind(param.getValue().priceProperty().multiply(100));
      return p;
    });
    priceColumn.setCellFactory(numberFormat("%.2f"));

    posGameColumn.setCellValueFactory(param -> new SimpleStringProperty(gameLabel(param.getValue().getGameId())));
    posStatusColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
    posStatusColumn.setCellFactory(column -> new TableCell<>() {
      @Override
      protected void updateItem(WagerPositionBean pos, boolean empty) {
        super.updateItem(pos, empty);
        if (empty || pos == null) {
          setText(null);
          setStyle("");
          return;
        }
        String status = pos.getMarketStatus();
        String key;
        String color;
        if ("OPEN".equals(status)) {
          key = "wager.status.live";
          color = "#26a65b";              // green — tradeable / game live
        } else if ("CLOSED".equals(status)) {
          key = "wager.status.closing";
          color = "#c9a227";              // amber — game ended, settling
        } else if ("VOIDED".equals(status)) {
          key = "wager.status.void";
          color = "-fx-text-base-color";
        } else if (Boolean.TRUE.equals(pos.getWinner())) {
          key = "wager.status.won";
          color = "#26a65b";
        } else {
          key = "wager.status.lost";
          color = "#cb4b16";              // red
        }
        setText(i18n.get(key));
        setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
      }
    });
    posMarketColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getLabel()));
    posSharesColumn.setCellValueFactory(param -> new SimpleDoubleProperty(param.getValue().getShares()));
    posSharesColumn.setCellFactory(numberFormat("%.2f"));
    posCostColumn.setCellValueFactory(param -> new SimpleDoubleProperty(param.getValue().getCostLp()));
    posCostColumn.setCellFactory(numberFormat("%.0f"));
    posValueColumn.setCellValueFactory(param -> new SimpleDoubleProperty(param.getValue().markToMarket(PAYOUT)));
    posValueColumn.setCellFactory(numberFormat("%.0f"));
    posPnlColumn.setCellValueFactory(param -> new SimpleDoubleProperty(param.getValue().pnl(PAYOUT)));
    posPnlColumn.setCellFactory(numberFormat("%+.0f"));

    myLpList.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(BoardLp item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setStyle("");
        } else {
          setText(i18n.get("wager.boardLp", boardDisplayName(item.technicalName()), item.score()));
          boolean highlight = item.technicalName().equals(selectedRatingType);
          setStyle(highlight ? "-fx-font-weight: bold; -fx-text-fill: -fx-accent;" : "");
        }
      }
    });

    gamesList.setCellFactory(list -> makeGameCell());
    gamesList.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
      if (row != null) {
        onSelectGame(row);
      }
    });
    // Clicking one of my positions for a still-live game jumps to that game + outcome so I can
    // see its price chart and trade it (WAGER_DESIGN.md §11). A row click handler (as well as
    // the selection listener) so re-clicking the already-selected row still jumps.
    portfolioTable.getSelectionModel().selectedItemProperty().addListener((obs, old, pos) -> {
      if (pos != null) {
        onSelectPosition(pos);
      }
    });
    portfolioTable.setRowFactory(table -> {
      javafx.scene.control.TableRow<WagerPositionBean> tableRow = new javafx.scene.control.TableRow<>();
      tableRow.setOnMouseClicked(event -> {
        if (tableRow.getItem() != null) {
          onSelectPosition(tableRow.getItem());
        }
      });
      MenuItem openReplay = new MenuItem(i18n.get("wager.position.openReplay"));
      openReplay.setOnAction(event -> {
        WagerPositionBean pos = tableRow.getItem();
        if (pos != null) {
          openReplayInVault(pos.getGameId());
        }
      });
      // A still-live game has no replay yet; disable the item for those (rows are reused, so react
      // to the row's item changing as the table scrolls/refreshes).
      tableRow.itemProperty().addListener((obs, old, pos) ->
          openReplay.setDisable(pos == null || pos.isLive()));
      ContextMenu contextMenu = new ContextMenu(openReplay);
      tableRow.contextMenuProperty().bind(
          Bindings.when(tableRow.emptyProperty()).then((ContextMenu) null).otherwise(contextMenu));
      return tableRow;
    });
    // Left column: only the OPEN sections share the height, so collapsing one really does hand its
    // room to the others (see the note in the FXML).
    growWhenExpanded(liveGamesPane, myLpPane, botTitledPane, portfolioPane);

    // Hide the whole outcomes column (picker + its trade controls) and its divider until a game is
    // picked — unmanaged too, so the row collapses instead of leaving a gap where they would be.
    outcomesColumn.visibleProperty().bind(gameSelected);
    outcomesColumn.managedProperty().bind(gameSelected);
    outcomesSeparator.visibleProperty().bind(gameSelected);
    outcomesSeparator.managedProperty().bind(gameSelected);

    outcomesTable.getSelectionModel().selectedItemProperty().addListener((obs, old, outcome) -> {
      if (outcome != null) {
        lastSelectedOutcomeKey = outcome.getOutcomeKey();   // remembered across tab navigation
      }
      recomputeBuyPreview();
      recomputeSellPreview();
      selectOutcomeForChart(outcome);
    });

    // Both sides are entered as a share count: BUY shares -> LP cost (fee incl.), SELL shares ->
    // LP received (fee deducted). Each field drives only its own side's preview.
    buySharesField.textProperty().addListener((obs, old, val) -> recomputeBuyPreview());
    sharesField.textProperty().addListener((obs, old, val) -> recomputeSellPreview());

    buyButton.setOnAction(event -> buy());
    sellButton.setOnAction(event -> sell());
    maxButton.setOnAction(event -> fillMaxShares());
    refreshButton.setOnAction(event -> reload());
    // The vault handles a replay that isn't published yet, so this needs no "is it ready" check.
    openReplayButton.setOnAction(event -> openReplayInVault(selectedGameId));

    // Trading is disabled until an outcome is picked, while a trade is in flight, and once the
    // game is over / the market is no longer open (the server would only reject those anyway).
    BooleanBinding noOutcome = outcomesTable.getSelectionModel().selectedItemProperty().isNull();
    BooleanBinding busy = noOutcome.or(submitting).or(marketNotTradeable);
    buySharesField.disableProperty().bind(busy);
    buyButton.disableProperty().bind(busy);
    sharesField.disableProperty().bind(busy);
    maxButton.disableProperty().bind(busy);
    sellButton.disableProperty().bind(busy);

    priceChart.setAnimated(false);
    priceChart.setCreateSymbols(false);
    // Re-anchor the overlaid series name / trade key whenever the plot geometry or either label's
    // own size changes (axis tick labels resize the axes, and the series name changes with the
    // selected outcome). Setting layoutX/Y doesn't alter layoutBounds, so this can't loop.
    InvalidationListener reposition = obs -> positionChartOverlay();
    priceChart.getXAxis().boundsInParentProperty().addListener(reposition);
    priceChart.getYAxis().boundsInParentProperty().addListener(reposition);
    chartSeriesLabel.layoutBoundsProperty().addListener(reposition);
    tradeLegend.layoutBoundsProperty().addListener(reposition);
    // Mixed-unit game-time axis: raw x is seconds from kickoff; label it in seconds up to 5 min,
    // then in m:ss (games run 10-30 min, where a bare second count is unreadable).
    if (priceChart.getXAxis() instanceof NumberAxis xAxis) {
      xAxis.setTickLabelFormatter(new StringConverter<>() {
        @Override
        public String toString(Number value) {
          double s = value.doubleValue();
          if (s < 300) {
            return String.format("%.0fs", s);
          }
          int total = (int) Math.round(s);
          return String.format("%d:%02d", total / 60, total % 60);
        }

        @Override
        public Number fromString(String string) {
          return 0;
        }
      });
    }

    JavaFxUtil.addListener(preferencesService.getPreferences().displayMetricProperty(),
        new WeakChangeListener<>(displayMetricListener));

    loadBoardDisplayNames();
  }

  /**
   * Let each collapsible section claim the column's spare height, but only while it is expanded.
   * Two things are needed, and the section is useless without both:
   * <ul>
   *   <li>an unbounded max height — a {@link TitledPane} defaults to a max height of its PREFERRED
   *       height, so {@code VBox.vgrow} alone does nothing and collapsing a section just leaves
   *       dead space at the bottom of the column;</li>
   *   <li>a grow priority that follows {@code expanded} — otherwise, now that the panes can
   *       stretch, a collapsed one would stretch too and hold a big empty box behind its title.</li>
   * </ul>
   * Together they mean collapsing a section genuinely hands its room to whatever is still open
   * (collapse the other three and "My positions" fills the entire column).
   */
  private static void growWhenExpanded(TitledPane... panes) {
    for (TitledPane pane : panes) {
      pane.setMaxHeight(Double.MAX_VALUE);
      VBox.setVgrow(pane, pane.isExpanded() ? Priority.ALWAYS : Priority.NEVER);
      pane.expandedProperty().addListener((obs, was, expanded) ->
          VBox.setVgrow(pane, expanded ? Priority.ALWAYS : Priority.NEVER));
    }
  }

  /** Fetch the leaderboards once and cache technical name → display name, then refresh anything
   * already rendered with a raw technical name. */
  private void loadBoardDisplayNames() {
    leaderboardService.getLeaderboards()
        .thenAccept(leaderboards -> JavaFxUtil.runLater(() -> {
          leaderboards.forEach(lb -> boardDisplayNames.put(lb.getTechnicalName(), i18n.get(lb.getNameKey())));
          myLpList.refresh();
          updateBoardLabel();
        }))
        .exceptionally(throwable -> {
          log.warn("Could not load leaderboard display names for wager tab", throwable);
          return null;
        });
  }

  /** Human-readable board name for a technical name, never the raw technical name if we can help it. */
  private String boardDisplayName(String technicalName) {
    if (technicalName == null) {
      return "";
    }
    return boardDisplayNames.getOrDefault(technicalName, technicalName);
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    wagerService.setSettlementListener(this::onSettlement);
    wagerService.setSubscribeRejectListener(this::onSubscribeReject);
    reload();
    if (botPnlController != null) {
      botPnlController.refresh();   // re-navigating to the view picks up any P&L computed while away
    }
  }

  @Override
  public void onHide() {
    wagerService.setSettlementListener(null);
    wagerService.setSubscribeRejectListener(null);
    wagerService.unsubscribe();
    if (currentMarkets != null) {
      currentMarkets.removeListener(marketsListener);
      currentMarkets = null;
    }
    bindMarketStatus(null);
    if (endWatchedGame != null) {
      endWatchedGame.statusProperty().removeListener(gameStatusListener);
      endWatchedGame = null;
    }
    teardownChart();
  }

  /** A market resolved: auto-refresh the portfolio + my LP (no manual refresh needed), re-render
   * the market lifecycle, and cue a win if it paid out in my favour. */
  private void onSettlement(WagerService.Settlement settlement) {
    reloadPortfolio();
    reloadMyLp();
    if (botPnlController != null) {
      botPnlController.refresh();   // a settled market updates the model-maker scoreboard + you-vs-model
    }
    boolean viewing = settlement.gameId() == selectedGameId;
    if (viewing) {
      updateMarketStatus();   // header label + trade controls; the bean's status is already set
    }
    if (settlement.isWin()) {
      audioService.playAchievementUnlockedSound();
      tradeStatusLabel.setText(i18n.get("wager.youWon", settlement.payoutLp()));
      tradeStatusLabel.setStyle("-fx-text-fill: #26a65b; -fx-font-weight: bold;");
    } else if ("VOIDED".equals(settlement.status()) && settlement.payoutLp() > 0) {
      tradeStatusLabel.setText(i18n.get("wager.refunded", settlement.payoutLp()));
      tradeStatusLabel.setStyle("");
    } else if (viewing) {
      // Pure spectator (or a losing position): still say plainly that the game we're looking at
      // is over and how it resolved — otherwise the tab just silently stops updating.
      WagerMarketBean market = currentMarket();
      tradeStatusLabel.setText("VOIDED".equals(settlement.status()) || market == null
          ? i18n.get("wager.market.voided")
          : i18n.get("wager.gameOverResult", winningOutcomeLabel(market)));
      tradeStatusLabel.setStyle("-fx-font-weight: bold;");
    }
  }

  /** A subscribe was rejected (e.g. {@code participant_blocked} — you're in the game you tried to
   * watch, §8). Explain the empty outcomes in the market-status label rather than leaving a blank
   * table; ignore rejects for a game we've since navigated away from. */
  private void onSubscribeReject(WagerService.SubscribeReject reject) {
    if (reject.gameId() != selectedGameId) {
      return;
    }
    outcomesTable.getItems().clear();
    selectOutcomeForChart(null);
    marketStatusLabel.setText(i18n.getWithDefault(reject.reason(), "wager.reason." + reject.reason()));
    marketStatusLabel.setStyle("-fx-text-fill: #c9a227; -fx-font-weight: bold;");   // amber warning
  }

  private void reload() {
    reloadWatchlist();
    reloadMyLp();
    reloadPortfolio();
  }

  private void reloadWatchlist() {
    wagerService.getWatchlist()
        .thenAccept(markets -> JavaFxUtil.runLater(() -> populateWatchlist(markets)))
        .exceptionally(throwable -> {
          log.warn("Could not load wager watchlist", throwable);
          return null;
        });
  }

  private void populateWatchlist(List<WagerMarket> markets) {
    Map<Integer, WagerMarket> byGame = new TreeMap<>();
    for (WagerMarket m : markets) {
      byGame.putIfAbsent(m.getGameId(), m);
    }
    List<GameRow> rows = byGame.values().stream()
        .map(m -> new GameRow(m.getGameId(), m.getRatingType(), gameLabel(m.getGameId())))
        .toList();
    gamesList.getItems().setAll(rows);
    restoreSelection(rows);
  }

  /** Re-select the game (and, once its outcomes arrive, the outcome) the user was last looking at.
   * {@link #onHide()} unsubscribes, so coming back to the tab always needs a fresh select to
   * re-subscribe — hence the explicit clear-then-select rather than relying on the list keeping
   * its selection. A game that has since dropped off the watchlist simply isn't restored. */
  private void restoreSelection(List<GameRow> rows) {
    if (selectedGameId == -1) {
      return;
    }
    GameRow row = rows.stream().filter(r -> r.gameId() == selectedGameId).findFirst().orElse(null);
    if (row == null) {
      return;
    }
    pendingOutcomeKey = lastSelectedOutcomeKey;
    gamesList.getSelectionModel().clearSelection();
    gamesList.getSelectionModel().select(row);   // triggers onSelectGame -> subscribe -> outcomes
  }

  /** Unified game label used by the watchlist AND the portfolio: "#id title (host: host)",
   * falling back to "#id title" (no host) or "Game #id" (game no longer live/known). */
  private String gameLabel(int gameId) {
    Game game = findGameQuietly(gameId);
    if (game == null || game.getTitle() == null) {
      return i18n.get("wager.gameFallback", gameId);
    }
    return game.getHost() != null
        ? i18n.get("wager.gameRow", gameId, game.getTitle(), game.getHost())
        : i18n.get("wager.gameNoHost", gameId, game.getTitle());
  }

  /** Look up a game by id WITHOUT the "can't find … in gameInfoBean map" warning that
   * {@code gameService.getByUid} logs — positions are usually for finished games not in
   * the live map, and the cell factories re-query on every render. */
  private Game findGameQuietly(int gameId) {
    for (Game game : gameService.getGames()) {
      if (game.getId() == gameId) {
        return game;
      }
    }
    return null;
  }

  /** A cell factory that formats a numeric column via {@link String#format}. */
  private static <S> Callback<TableColumn<S, Number>, TableCell<S, Number>> numberFormat(String fmt) {
    return column -> new TableCell<>() {
      @Override
      protected void updateItem(Number value, boolean empty) {
        super.updateItem(value, empty);
        setText(empty || value == null ? null : String.format(fmt, value.doubleValue()));
      }
    };
  }

  private void reloadMyLp() {
    int playerId = playerService.getCurrentPlayer().map(p -> p.getId()).orElse(0);
    if (playerId == 0) {
      return;
    }
    ladderPointsService.getStandingsForPlayer(playerId)
        .thenAccept(standings -> JavaFxUtil.runLater(() -> populateMyLp(standings)))
        .exceptionally(throwable -> {
          log.warn("Could not load my ladder points", throwable);
          return null;
        });
  }

  private void populateMyLp(List<SeasonStanding> standings) {
    myLpByBoard.clear();
    List<BoardLp> rows = new ArrayList<>();
    for (SeasonStanding s : standings) {
      myLpByBoard.put(s.getLeaderboardTechnicalName(), s.getScore());
      rows.add(new BoardLp(s.getLeaderboardTechnicalName(),
          boardDisplayName(s.getLeaderboardTechnicalName()), s.getScore()));
    }
    myLpList.getItems().setAll(rows);
    updateBoardLabel();
  }

  private void onSelectGame(GameRow row) {
    if (selectedGameId != row.gameId()) {
      lastSelectedOutcomeKey = null;    // a different game: don't carry the old game's outcome over
    }
    selectedGameId = row.gameId();
    selectedRatingType = row.ratingType();
    gameSelected.set(true);
    updateBoardLabel();
    myLpList.refresh();

    buildTeamCards(row.gameId());
    watchGameForEnd(row.gameId());

    if (currentMarkets != null) {
      currentMarkets.removeListener(marketsListener);
    }
    currentMarkets = wagerService.subscribeToGame(row.gameId());
    currentMarkets.addListener(marketsListener);
    onMarketsChanged();
  }

  private void updateBoardLabel() {
    if (selectedRatingType == null) {
      boardLabel.setText("");
      return;
    }
    int lp = myLpByBoard.getOrDefault(selectedRatingType, 0);
    boardLabel.setText(i18n.get("wager.boardAvailable", boardDisplayName(selectedRatingType), lp));
  }

  private void buildTeamCards(int gameId) {
    teamCardsPane.getChildren().clear();
    Game game = findGameQuietly(gameId);
    if (game == null) {
      mapNameLabel.setText("");
      mapImageView.setImage(null);
      return;
    }
    mapNameLabel.setText(game.getMapName());
    try {
      mapImageView.setImage(mapService.loadPreview(
          game.getFeaturedMod(), game.getMapName(), PreviewType.MINI, 10));
    } catch (Exception e) {
      log.warn("Could not load map preview for {}", game.getMapName(), e);
      mapImageView.setImage(null);
    }
    try {
      TeamCardController.createAndAdd(game.getTeams(), game.getRatingType(),
          playerService, uiService, ratingService, galacticWarService,
          teamCardsPane, false, game.getGalacticWarPlanetName());
    } catch (Exception e) {
      log.warn("Could not build team cards for game {}", gameId, e);
    }
  }

  private void onMarketsChanged() {
    if (currentMarkets == null || currentMarkets.isEmpty()) {
      outcomesTable.getItems().clear();
      bindMarketStatus(null);
      // Any real markets update clears a prior subscribe-reject warning (e.g. participant_blocked)
      // and its styling — we're back in a normal state for the selected game.
      updateMarketStatus();
      selectOutcomeForChart(null);
      return;
    }
    WagerMarketBean market = currentMarkets.get(0);   // v1: TEAM_WIN is the primary market
    outcomesTable.setItems(market.getOutcomes());
    bindMarketStatus(market);
    updateMarketStatus();
    if (outcomesTable.getSelectionModel().getSelectedItem() == null && !market.getOutcomes().isEmpty()
        && !selectPendingOutcome()) {
      outcomesTable.getSelectionModel().selectFirst();    // fires the selection listener -> chart
    }
  }

  private WagerMarketBean currentMarket() {
    return currentMarkets == null || currentMarkets.isEmpty() ? null : currentMarkets.get(0);
  }

  /** Follow the selected market's status property so a settlement that arrives while we're looking
   * at the game re-renders — it mutates the bean, not the markets list, so nothing else would. */
  private void bindMarketStatus(WagerMarketBean market) {
    if (statusBoundMarket != null) {
      statusBoundMarket.statusProperty().removeListener(marketStatusListener);
    }
    statusBoundMarket = market;
    if (market != null) {
      market.statusProperty().addListener(marketStatusListener);
    }
  }

  /** Follow the selected game's own status, which reaches ENDED as soon as the lobby says the game
   * is over — typically a little before the market settles, and the only signal at all when the
   * result is held unresolved (the market sits CLOSED server-side with nothing pushed to us). */
  private void watchGameForEnd(int gameId) {
    if (endWatchedGame != null) {
      endWatchedGame.statusProperty().removeListener(gameStatusListener);
    }
    endWatchedGame = findGameQuietly(gameId);
    if (endWatchedGame != null) {
      endWatchedGame.statusProperty().addListener(gameStatusListener);
    }
  }

  /** True once the lobby has told us the selected game is over. A game we never saw in the live
   * list is NOT treated as ended — only an observed transition counts. */
  private boolean isSelectedGameOver() {
    return endWatchedGame != null && endWatchedGame.getStatus() == GameStatus.ENDED;
  }

  /**
   * Render the market's lifecycle into the header status label (and gate trading on it), so a
   * spectator with no position still sees that the game they're watching has finished:
   * open → "TEAM_WIN — OPEN", game over / closed → amber "settling", settled → green "X won",
   * voided → "stakes refunded".
   */
  private void updateMarketStatus() {
    WagerMarketBean market = currentMarket();
    if (market == null) {
      marketStatusLabel.setText("");
      marketStatusLabel.setStyle("");
      marketNotTradeable.set(true);
      openReplayButton.setVisible(false);
      return;
    }
    String status = market.getStatus();
    // "Go watch it" is only offered once the game is over — the whole point of the button.
    openReplayButton.setVisible(!"OPEN".equals(status) || isSelectedGameOver());
    if ("SETTLED".equals(status)) {
      marketStatusLabel.setText(i18n.get("wager.market.settled", winningOutcomeLabel(market)));
      marketStatusLabel.setStyle("-fx-text-fill: #26a65b; -fx-font-weight: bold;");   // green
      marketNotTradeable.set(true);
      if (chartOutcome != null && market.getOutcomes().contains(chartOutcome)) {
        rebuildChart();     // the charted outcome now knows if it won — stamp the verdict on it
      }
    } else if ("VOIDED".equals(status)) {
      marketStatusLabel.setText(i18n.get("wager.market.voided"));
      marketStatusLabel.setStyle("-fx-font-weight: bold;");
      marketNotTradeable.set(true);
    } else if ("CLOSED".equals(status) || isSelectedGameOver()) {
      marketStatusLabel.setText(i18n.get("wager.market.closed"));
      marketStatusLabel.setStyle("-fx-text-fill: #c9a227; -fx-font-weight: bold;");   // amber
      marketNotTradeable.set(true);
    } else {
      marketStatusLabel.setText(i18n.get("wager.marketStatus", market.getMarketType(), status));
      marketStatusLabel.setStyle("");
      marketNotTradeable.set(false);
    }
    outcomesTable.refresh();   // pick up the won/lost suffix on the outcome labels
  }

  /** Short label ("Team 1") of the outcome that won a settled market. */
  private String winningOutcomeLabel(WagerMarketBean market) {
    return market.getOutcomes().stream()
        .filter(outcome -> Boolean.TRUE.equals(outcome.getWinner()))
        .findFirst()
        .map(outcome -> WagerLabels.outcomeLabel(market.getMarketType(), outcome.getOutcomeKey(),
            outcome.getLabel()))
        .orElse(i18n.get("wager.market.unknownWinner"));
  }

  /** Jump to a portfolio position's market: select its game in the watchlist (loads the team
   * cards + market) then its outcome (drives the price chart). Only for still-live games whose
   * market is still on the watchlist; resolved/absent positions are left untouched. */
  /** Right-click a portfolio row -> jump to the online Replay vault and open that game's replay
   * there, following the app's standard replay-detail conventions (rather than spinning up a
   * bespoke modal window). The vault handles the "not finished / not published yet" case. */
  private void openReplayInVault(int gameId) {
    eventBus.post(new ShowReplayEvent(gameId));
  }

  private void onSelectPosition(WagerPositionBean pos) {
    if (!pos.isLive()) {
      return;
    }
    GameRow row = gamesList.getItems().stream()
        .filter(r -> r.gameId() == pos.getGameId())
        .findFirst().orElse(null);
    if (row == null) {
      return;
    }
    pendingOutcomeKey = pos.getOutcomeKey();
    if (gamesList.getSelectionModel().getSelectedItem() == row) {
      selectPendingOutcome();     // already the selected game — outcomes are loaded, apply now
    } else {
      gamesList.getSelectionModel().select(row);   // triggers onSelectGame -> onMarketsChanged
    }
  }

  /** Apply a pending {@link #pendingOutcomeKey} once its outcome is present in the table.
   * @return true if the pending outcome was found and selected. */
  private boolean selectPendingOutcome() {
    if (pendingOutcomeKey == null) {
      return false;
    }
    WagerOutcomeBean match = outcomesTable.getItems().stream()
        .filter(outcome -> pendingOutcomeKey.equals(outcome.getOutcomeKey()))
        .findFirst().orElse(null);
    if (match == null) {
      return false;               // outcomes not loaded yet — retry on the next markets change
    }
    pendingOutcomeKey = null;
    outcomesTable.getSelectionModel().select(match);   // fires the selection listener -> chart
    return true;
  }

  // --- shares <-> LP dual input --------------------------------------------

  private WagerOutcomeBean selectedOutcome() {
    return outcomesTable.getSelectionModel().getSelectedItem();
  }

  /** Team-aligned outcome label (see {@link WagerLabels}), plus the significant players on
   * that team: all of them for 1v1/2v2, else the highest-rated + "et al" for 3v3+. */
  private String outcomeDisplay(WagerOutcomeBean outcome) {
    WagerMarketBean market = currentMarket();
    String teamLabel = WagerLabels.outcomeLabel(
        market == null ? null : market.getMarketType(), outcome.getOutcomeKey(), outcome.getLabel());
    if (market == null) {
      return teamLabel;
    }
    Game game = findGameQuietly(market.getGameId());
    List<String> names = game == null ? null : game.getTeams().get(outcome.getOutcomeKey());
    if (names != null && !names.isEmpty()) {
      String players = names.size() <= 2
          ? String.join(", ", names)
          : i18n.get("wager.etAl", names.stream()
              .max(Comparator.comparingInt(name -> ratingOf(name, game.getRatingType())))
              .orElse(names.get(0)));
      teamLabel = teamLabel + ": " + players;
    }
    // Once the market has settled, say on the row itself which team won — the whole point of the
    // tab, and the clearest "this game is over" cue for someone who never held a position.
    if ("SETTLED".equals(market.getStatus()) && outcome.getWinner() != null) {
      teamLabel = teamLabel + " " + i18n.get(outcome.getWinner() ? "wager.outcome.won" : "wager.outcome.lost");
    }
    return teamLabel;
  }

  /** A player's rating for a board (for picking the "most significant" player); 0 if unknown. */
  private int ratingOf(String username, String ratingType) {
    return playerService.getPlayerForUsername(username).map(p -> {
      var rating = p.getLeaderboardRatings().get(ratingType);
      Integer value = rating == null ? null : RatingUtil.getRating(rating);
      return value == null ? 0 : value;
    }).orElse(0);
  }

  /** The total LP a buy of {@code shares} will actually cost, fee included (server rounding). */
  private long forwardTotalLp(double price, double b, double shares, int feeBps) {
    long cost = Math.round(WagerMath.costLp(price, b, shares, PAYOUT));
    long fee = Math.round(Math.abs((double) cost) * feeBps / 10000.0);
    return cost + fee;
  }

  /** Net LP received selling {@code shares} now (fee deducted). */
  private long sellProceeds(WagerOutcomeBean outcome, WagerMarketBean market, double shares) {
    long gross = -Math.round(WagerMath.costLp(outcome.getPrice(), market.getLiquidity(), -shares, PAYOUT));
    long fee = Math.round(Math.abs((double) gross) * market.getFeeBps() / 10000.0);
    return gross - fee;
  }

  private void recomputeBuyPreview() {
    WagerOutcomeBean outcome = selectedOutcome();
    double shares = parseDouble(buySharesField.getText());
    if (outcome == null || currentMarkets == null || currentMarkets.isEmpty()
        || Double.isNaN(shares) || shares <= 0) {
      buyPreviewLabel.setText("");
      return;
    }
    WagerMarketBean market = currentMarkets.get(0);
    buyPreviewLabel.setText(i18n.get("wager.buyPreview",
        forwardTotalLp(outcome.getPrice(), market.getLiquidity(), shares, market.getFeeBps())));
  }

  private void recomputeSellPreview() {
    WagerOutcomeBean outcome = selectedOutcome();
    double shares = parseDouble(sharesField.getText());
    if (outcome == null || currentMarkets == null || currentMarkets.isEmpty()
        || Double.isNaN(shares) || shares <= 0) {
      sellPreviewLabel.setText("");
      return;
    }
    sellPreviewLabel.setText(i18n.get("wager.sellPreview", sellProceeds(outcome, currentMarkets.get(0), shares)));
  }

  private void buy() {
    WagerOutcomeBean outcome = selectedOutcome();
    if (outcome == null || currentMarkets == null || currentMarkets.isEmpty()) {
      return;
    }
    double shares = parseDouble(buySharesField.getText());
    if (Double.isNaN(shares) || shares <= 0) {
      tradeStatusLabel.setText(i18n.get("wager.enterShares"));
      return;
    }
    submitTrade(outcome, +shares);
  }

  private void sell() {
    WagerOutcomeBean outcome = selectedOutcome();
    if (outcome == null || currentMarkets == null || currentMarkets.isEmpty()) {
      return;
    }
    double shares = parseDouble(sharesField.getText());
    if (Double.isNaN(shares) || shares <= 0) {
      tradeStatusLabel.setText(i18n.get("wager.enterShares"));
      return;
    }
    submitTrade(outcome, -shares);
  }

  private void submitTrade(WagerOutcomeBean outcome, double signedShares) {
    WagerMarketBean market = currentMarkets.get(0);
    submitting.set(true);
    tradeStatusLabel.setText(i18n.get("wager.submitting"));
    wagerService.trade(market.getGameId(), market.getMarketId(), outcome.getOutcomeKey(), signedShares)
        .whenComplete((result, error) -> JavaFxUtil.runLater(() -> {
          submitting.set(false);
          if (error != null) {
            tradeStatusLabel.setText(i18n.get("wager.tradeRejected", rejectReason(error)));
          } else {
            tradeStatusLabel.setText(i18n.get("wager.tradeOk",
                result.lpCost(), result.feeLp(), String.format("%.2f", result.priceAfter() * 100)));
            reloadPortfolio();
            reloadMyLp();
          }
        }));
  }

  /** Fill the sell field with my current holding in the selected outcome. */
  private void fillMaxShares() {
    WagerOutcomeBean outcome = selectedOutcome();
    if (outcome == null || currentMarkets == null || currentMarkets.isEmpty()) {
      return;
    }
    long marketId = currentMarkets.get(0).getMarketId();
    double held = portfolioTable.getItems().stream()
        .filter(pos -> pos.getMarketId() == marketId && outcome.getOutcomeKey().equals(pos.getOutcomeKey()))
        .mapToDouble(WagerPositionBean::getShares)
        .findFirst().orElse(0);
    // Round DOWN to the field's 2-dp precision: String.format rounds half-up, so a holding
    // like 9.876 would fill "9.88" — a hair MORE than held — and the server rejects the sell
    // as an oversell (markets.py: held_shares + delta_shares < -1e-9). Flooring guarantees
    // the amount never exceeds the holding, so "max" sells the whole position cleanly.
    double sellable = Math.floor(held * 100.0) / 100.0;
    sharesField.setText(sellable > 0 ? String.format("%.2f", sellable) : "0");
  }

  /** A games-list cell with a right-click "Watch live replay" menu targeting that row. */
  private javafx.scene.control.ListCell<GameRow> makeGameCell() {
    javafx.scene.control.ListCell<GameRow> cell = new javafx.scene.control.ListCell<>() {
      @Override
      protected void updateItem(GameRow item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.label());
      }
    };
    ContextMenu menu = new ContextMenu();
    MenuItem watch = new MenuItem(i18n.get("wager.watchLive"));
    watch.setOnAction(event -> {
      if (cell.getItem() != null) {
        Game game = findGameQuietly(cell.getItem().gameId());
        if (game != null) {
          replayService.runLiveReplay(game);
        }
      }
    });
    menu.getItems().add(watch);
    cell.emptyProperty().addListener((obs, was, isEmpty) -> cell.setContextMenu(isEmpty ? null : menu));
    return cell;
  }

  private String rejectReason(Throwable error) {
    Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
        ? error.getCause() : error;
    if (cause instanceof WagerTradeException wte) {
      return i18n.getWithDefault(wte.getReason(), "wager.reason." + wte.getReason());
    }
    return cause.getClass().getSimpleName();
  }

  private static double parseDouble(String text) {
    try {
      return text == null || text.isBlank() ? Double.NaN : Double.parseDouble(text.trim());
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  private void reloadPortfolio() {
    wagerService.getMyPositions()
        .thenAccept(positions -> JavaFxUtil.runLater(() -> {
          List<WagerPositionBean> sorted = new ArrayList<>(positions);
          // Most recent first: newer markets have higher ids (proxy for trade recency).
          sorted.sort(Comparator.comparingLong(WagerPositionBean::getMarketId).reversed());
          portfolioTable.getItems().setAll(sorted);
        }))
        .exceptionally(throwable -> {
          log.warn("Could not load wager portfolio", throwable);
          return null;
        });
  }

  // --- price chart (selected outcome) --------------------------------------

  private void selectOutcomeForChart(WagerOutcomeBean outcome) {
    if (chartOutcome != null && chartPriceListener != null) {
      chartOutcome.priceProperty().removeListener(chartPriceListener);
    }
    chartOutcome = outcome;
    chartPoints.clear();
    chartMarkers.clear();
    if (outcome == null) {
      priceChart.getData().clear();
      setChartSeriesLabel(null);
      updateTradeLegend();
      return;
    }
    double now = System.currentTimeMillis() / 1000.0;
    chartPoints.add(new double[]{now, outcome.getPrice()});
    WagerMarketBean market = currentMarkets == null || currentMarkets.isEmpty() ? null : currentMarkets.get(0);
    if (market == null) {
      rebuildChart();
      return;
    }
    loadChartHistory(outcome, market);

    chartPriceListener = (obs, oldV, newV) -> JavaFxUtil.runLater(() -> {
      chartPoints.add(new double[]{System.currentTimeMillis() / 1000.0, newV.doubleValue()});
      rebuildChart();
      // The tick doesn't say who traded; refresh the trade-log-derived markers (debounced) so
      // the new trade grows a marker once the log has it.
      WagerMarketBean m = currentMarkets == null || currentMarkets.isEmpty() ? null : currentMarkets.get(0);
      if (m != null && System.currentTimeMillis() - lastHistoryFetchMs > MARKER_REFRESH_MS) {
        loadChartHistory(outcome, m);
      }
    });
    outcome.priceProperty().addListener(chartPriceListener);
    rebuildChart();
  }

  /** (Re)load the selected outcome's whole price path + human-trade markers from the market's
   * trade log (incl. the other team's trades + reconstructed pre-trade prices — done in the
   * service), replacing the accumulated chart state on arrival. */
  private void loadChartHistory(WagerOutcomeBean outcome, WagerMarketBean market) {
    lastHistoryFetchMs = System.currentTimeMillis();
    boolean twoOutcome = market.getOutcomes().size() == 2;
    wagerService.getPriceHistory(market.getMarketId(), outcome.getOutcomeId(), market.getLiquidity(), twoOutcome, 500)
        .thenAccept(history -> JavaFxUtil.runLater(() -> {
          if (chartOutcome != outcome) {
            return;                    // selection moved on while we were loading
          }
          chartPoints.clear();
          for (WagerService.PricePoint pt : history.points()) {
            chartPoints.add(new double[]{pt.epochSeconds(), pt.price()});
          }
          if (chartPoints.isEmpty()) {
            // untraded market — keep a current-price point so the flat line still renders
            chartPoints.add(new double[]{System.currentTimeMillis() / 1000.0, outcome.getPrice()});
          }
          chartMarkers.clear();
          chartMarkers.addAll(history.markers());
          rebuildChart();
        }))
        .exceptionally(t -> null);
  }

  /** Game-start epoch (seconds) for the selected live game, or NaN if unknown — the x-axis
   * anchor so a trade sits at its true game-time position (kickoff = 0), not at the first trade. */
  private double chartAnchorEpoch() {
    Game game = findGameQuietly(selectedGameId);
    if (game != null && game.getStartTime() != null) {
      return game.getStartTime().getEpochSecond();
    }
    return Double.NaN;
  }

  /** Plot the selected outcome's price (0..100) over game-time seconds (0 = kickoff). */
  private void rebuildChart() {
    if (chartPoints.isEmpty()) {
      priceChart.getData().clear();
      setChartSeriesLabel(null);
      return;
    }
    List<double[]> points = new ArrayList<>(chartPoints);
    points.sort(Comparator.comparingDouble(a -> a[0]));

    // Leading flat segment from kickoff to the first observed price: the market is flat from open
    // until the first trade, so without this a trade at game-time 11 min collapses onto t=0 and the
    // whole trading burst looks like it happened in the first few seconds.
    double anchor = chartAnchorEpoch();
    double firstEpoch = points.get(0)[0];
    if (!Double.isNaN(anchor) && anchor < firstEpoch) {
      points.add(0, new double[]{anchor, points.get(0)[1]});
    } else if (points.size() == 1) {
      // no kickoff known and a lone (untraded) point — short flat stub so the line renders
      points.add(0, new double[]{firstEpoch - 60, points.get(0)[1]});
    }
    // Trailing flat segment to "now" at the last price: the game is still live, so extend the line
    // to the current game-time instead of stopping at the last trade.
    double now = System.currentTimeMillis() / 1000.0;
    double[] last = points.get(points.size() - 1);
    if (now > last[0]) {
      points.add(new double[]{now, last[1]});
    }
    double t0 = points.get(0)[0];

    XYChart.Series<Number, Number> series = new XYChart.Series<>();
    String seriesName = chartOutcome != null ? outcomeDisplay(chartOutcome) : "";
    series.setName(seriesName);
    chartOpeningPrice = points.get(0)[1] * 100;        // vertical anchor for the overlaid name
    setChartSeriesLabel(seriesName);   // the chart's own legend is off; we draw it in the plot area
    // Step (step-after) plot: the price is constant between trades and jumps instantly at each trade,
    // so we only ever want horizontal and vertical segments — never a diagonal that implies the price
    // drifted continuously. For each price change we first extend the previous price horizontally to
    // the new trade time, then step vertically to the new price.
    double prevPrice = Double.NaN;
    for (double[] pt : points) {
      double time = pt[0] - t0;
      double price = pt[1] * 100;
      if (!Double.isNaN(prevPrice) && price != prevPrice) {
        series.getData().add(new XYChart.Data<>(time, prevPrice));
      }
      series.getData().add(new XYChart.Data<>(time, price));
      prevPrice = price;
    }
    // Human-trade markers: a coloured ▲/▼ per trade, riding on its (already plotted) PRE-trade
    // point — the foot of the step it caused, so the arrow marks where the move starts and points
    // along it. A duplicate data point carrying only the symbol node, so the line is unchanged.
    // On the LIVE chart the traders are anonymised down to two categories (mine / everyone
    // else's), so an open market never leaks who is on the other side of a price move.
    int me = currentUserId();
    for (WagerService.TradeMarker marker : chartMarkers) {
      double time = marker.epochSeconds() - t0;
      if (time < 0) {
        continue;
      }
      boolean mine = marker.userId() == me;
      XYChart.Data<Number, Number> data = new XYChart.Data<>(time, marker.priceBefore() * 100);
      data.setNode(WagerChartMarkers.markerNode(marker,
          WagerChartMarkers.anonymousColor(mine), markerTooltip(marker, mine)));
      series.getData().add(data);
    }
    addVerdictMarker(series, points, t0);
    priceChart.getData().setAll(series);
    updateTradeLegend();
  }

  /** Once the market has settled, stamp the verdict on the end of the charted line: a green tick
   * if the charted outcome won, a red X if it lost. (The chart sorts its data by x, so appending
   * this duplicate of the final point leaves the line itself untouched.) */
  private void addVerdictMarker(XYChart.Series<Number, Number> series, List<double[]> points, double t0) {
    WagerMarketBean market = currentMarket();
    if (chartOutcome == null || market == null || !"SETTLED".equals(market.getStatus())
        || chartOutcome.getWinner() == null) {
      return;
    }
    boolean won = chartOutcome.getWinner();
    double[] endPoint = points.get(points.size() - 1);
    XYChart.Data<Number, Number> verdict = new XYChart.Data<>(endPoint[0] - t0, endPoint[1] * 100);
    String outcomeName = WagerLabels.outcomeLabel(market.getMarketType(), chartOutcome.getOutcomeKey(),
        chartOutcome.getLabel());
    verdict.setNode(WagerChartMarkers.verdictNode(won,
        i18n.get(won ? "wager.chart.won" : "wager.chart.lost", outcomeName)));
    series.getData().add(verdict);
  }

  /** Tooltip for one trade marker on the live chart: buy/sell (relative to the displayed outcome),
   * how many shares, and the price it left the displayed outcome at (0..100). Deliberately says
   * only "you" or "another player" — naming the trader would defeat the anonymisation. */
  private String markerTooltip(WagerService.TradeMarker marker, boolean mine) {
    String key = mine
        ? (marker.up() ? "wager.marker.youBought" : "wager.marker.youSold")
        : (marker.up() ? "wager.marker.otherBought" : "wager.marker.otherSold");
    return i18n.get(key,
        String.format("%.2f", marker.shares()),
        String.format("%.1f", marker.priceAfter() * 100));
  }

  /** My user id, or -1 when not logged in (so no marker is ever mistaken for mine). */
  private int currentUserId() {
    return playerService.getCurrentPlayer().map(player -> player.getId()).orElse(-1);
  }

  /** Show (or hide, when there's no series) the overlaid series name in the plot area. */
  private void setChartSeriesLabel(String text) {
    if (chartSeriesLabel == null) {
      return;
    }
    boolean any = text != null && !text.isBlank();
    chartSeriesLabel.setText(any ? text : "");
    chartSeriesLabel.setVisible(any);
    chartSeriesLabel.setManaged(any);
    positionChartOverlay();
  }

  /**
   * Anchor the two overlay panels to the plot area: the series name sits just above the line's
   * OPENING price and a little right of the y-axis, so it reads as a label on the price line
   * rather than as free-floating chart furniture; the trade key sits bottom-left of the plot.
   * Both are clamped inside the plot so an opening price near 0 or 100 can't push them out.
   */
  private void positionChartOverlay() {
    if (chartOverlay == null || !(priceChart.getYAxis() instanceof NumberAxis yAxis)) {
      return;
    }
    Bounds yBounds = yAxis.getBoundsInParent();
    Bounds xBounds = priceChart.getXAxis().getBoundsInParent();
    if (yBounds.getHeight() <= 0) {
      return;                       // not laid out yet; the bounds listeners will call us back
    }
    double left = yBounds.getMaxX() + OVERLAY_GAP;      // small margin right of the y-axis
    double plotTop = yBounds.getMinY();
    double plotBottom = xBounds.getMinY() > 0 ? xBounds.getMinY() : yBounds.getMaxY();

    double labelHeight = chartSeriesLabel.prefHeight(-1);
    // getDisplayPosition is measured from the top of the y-axis, which spans the plot vertically.
    double priceY = plotTop + yAxis.getDisplayPosition(chartOpeningPrice);
    double labelY = priceY - labelHeight - OVERLAY_GAP;
    chartSeriesLabel.setLayoutX(left);
    chartSeriesLabel.setLayoutY(clamp(labelY, plotTop, plotBottom - labelHeight));

    double legendHeight = tradeLegend.prefHeight(-1);
    tradeLegend.setLayoutX(left);
    tradeLegend.setLayoutY(clamp(plotBottom - legendHeight - OVERLAY_GAP, plotTop, plotBottom - legendHeight));
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(value, max));
  }

  private void updateTradeLegend() {
    if (tradeLegend != null) {
      WagerChartMarkers.populateAnonymousLegend(tradeLegend, chartMarkers, currentUserId(),
          i18n.get("wager.legend.myTrades"), i18n.get("wager.legend.otherTrades"));
      positionChartOverlay();
    }
  }

  private void teardownChart() {
    if (chartOutcome != null && chartPriceListener != null) {
      chartOutcome.priceProperty().removeListener(chartPriceListener);
    }
    chartOutcome = null;
    chartPriceListener = null;
    chartPoints.clear();
    chartMarkers.clear();
    if (priceChart != null) {
      priceChart.getData().clear();
    }
    setChartSeriesLabel(null);
    updateTradeLegend();
  }

  /** A watchlist row: one live game, its board (rating_type), and a display label. */
  public record GameRow(int gameId, String ratingType, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  /** My cumulative LP on one board this season. */
  public record BoardLp(String technicalName, String displayName, int score) {
  }
}
