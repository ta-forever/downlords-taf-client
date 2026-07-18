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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
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
  public ListView<GameRow> gamesList;
  public Button refreshButton;
  public ListView<BoardLp> myLpList;
  public WagerBotPnlController botPnlController;   // injected from <fx:include fx:id="botPnl">
  public Label selectedGameLabel;
  public Label boardLabel;
  public Label marketStatusLabel;
  public ImageView mapImageView;
  public Label mapNameLabel;
  public HBox teamCardsPane;
  public TableView<WagerOutcomeBean> outcomesTable;
  public TableColumn<WagerOutcomeBean, String> outcomeColumn;
  public TableColumn<WagerOutcomeBean, Number> priceColumn;
  public TextField lpField;          // buy: LP to spend (fee included)
  public Label buyPreviewLabel;      // "≈ N shares"
  public Button buyButton;
  public TextField sharesField;      // sell: shares to sell
  public Button maxButton;
  public Label sellPreviewLabel;     // "≈ N LP"
  public Button sellButton;
  public Label tradeStatusLabel;
  public LineChart<Number, Number> priceChart;
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

  // When a portfolio row asks to open its market, the outcomes load asynchronously after the
  // game is selected — stash the outcome to select and apply it once they arrive.
  private String pendingOutcomeKey;

  // price chart state (for the selected outcome)
  private WagerOutcomeBean chartOutcome;
  private ChangeListener<Number> chartPriceListener;
  private final List<double[]> chartPoints = new ArrayList<>();   // [epochSeconds, price 0..1]

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
    outcomesTable.getSelectionModel().selectedItemProperty().addListener((obs, old, outcome) -> {
      recomputeBuyPreview();
      recomputeSellPreview();
      selectOutcomeForChart(outcome);
    });

    // Prediction-market UX: BUY by amount (LP to spend, fee incl.) -> shares; SELL by share
    // count -> LP received (fee deducted). Each field drives only its own side's preview.
    lpField.textProperty().addListener((obs, old, val) -> recomputeBuyPreview());
    sharesField.textProperty().addListener((obs, old, val) -> recomputeSellPreview());

    buyButton.setOnAction(event -> buy());
    sellButton.setOnAction(event -> sell());
    maxButton.setOnAction(event -> fillMaxShares());
    refreshButton.setOnAction(event -> reload());

    // Trading is disabled until an outcome is picked; also disabled while a trade is in flight.
    BooleanBinding noOutcome = outcomesTable.getSelectionModel().selectedItemProperty().isNull();
    BooleanBinding busy = noOutcome.or(submitting);
    lpField.disableProperty().bind(busy);
    buyButton.disableProperty().bind(busy);
    sharesField.disableProperty().bind(busy);
    maxButton.disableProperty().bind(busy);
    sellButton.disableProperty().bind(busy);

    priceChart.setAnimated(false);
    priceChart.setCreateSymbols(false);
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
    teardownChart();
  }

  /** A market resolved: auto-refresh the portfolio + my LP (no manual refresh needed), and
   * cue a win if it paid out in my favour. */
  private void onSettlement(WagerService.Settlement settlement) {
    reloadPortfolio();
    reloadMyLp();
    if (botPnlController != null) {
      botPnlController.refresh();   // a settled market updates the model-maker scoreboard + you-vs-model
    }
    if (settlement.isWin()) {
      audioService.playAchievementUnlockedSound();
      tradeStatusLabel.setText(i18n.get("wager.youWon", settlement.payoutLp()));
      tradeStatusLabel.setStyle("-fx-text-fill: #26a65b; -fx-font-weight: bold;");
    } else if ("VOIDED".equals(settlement.status()) && settlement.payoutLp() > 0) {
      tradeStatusLabel.setText(i18n.get("wager.refunded", settlement.payoutLp()));
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
    selectedGameId = row.gameId();
    selectedRatingType = row.ratingType();
    selectedGameLabel.setText(row.label());
    updateBoardLabel();
    myLpList.refresh();

    buildTeamCards(row.gameId());

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
    // Any real markets update clears a prior subscribe-reject warning (e.g. participant_blocked)
    // and its styling — we're back in a normal state for the selected game.
    marketStatusLabel.setStyle("");
    if (currentMarkets == null || currentMarkets.isEmpty()) {
      outcomesTable.getItems().clear();
      marketStatusLabel.setText("");
      selectOutcomeForChart(null);
      return;
    }
    WagerMarketBean market = currentMarkets.get(0);   // v1: TEAM_WIN is the primary market
    outcomesTable.setItems(market.getOutcomes());
    marketStatusLabel.setText(i18n.get("wager.marketStatus", market.getMarketType(), market.getStatus()));
    if (outcomesTable.getSelectionModel().getSelectedItem() == null && !market.getOutcomes().isEmpty()
        && !selectPendingOutcome()) {
      outcomesTable.getSelectionModel().selectFirst();    // fires the selection listener -> chart
    }
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
    WagerMarketBean market = currentMarkets == null || currentMarkets.isEmpty() ? null : currentMarkets.get(0);
    String teamLabel = WagerLabels.outcomeLabel(
        market == null ? null : market.getMarketType(), outcome.getOutcomeKey(), outcome.getLabel());
    if (market == null) {
      return teamLabel;
    }
    Game game = findGameQuietly(market.getGameId());
    List<String> names = game == null ? null : game.getTeams().get(outcome.getOutcomeKey());
    if (names == null || names.isEmpty()) {
      return teamLabel;
    }
    String players = names.size() <= 2
        ? String.join(", ", names)
        : i18n.get("wager.etAl", names.stream()
            .max(Comparator.comparingInt(name -> ratingOf(name, game.getRatingType())))
            .orElse(names.get(0)));
    return teamLabel + ": " + players;
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

  /** Shares buyable for a total LP budget (fee included), trimmed so integer rounding never
   * pushes the actual total over the budget — "spend everything" isn't rejected for the fee. */
  private double sharesForBudget(WagerOutcomeBean outcome, WagerMarketBean market, double budget) {
    double price = outcome.getPrice();
    double b = market.getLiquidity();
    double rate = market.getFeeBps() / 10000.0;
    double shares = WagerMath.sharesForLp(price, b, budget / (1 + rate), PAYOUT);
    for (int guard = 1; guard <= 5 && !Double.isNaN(shares) && shares > 0
        && forwardTotalLp(price, b, shares, market.getFeeBps()) > budget; guard++) {
      shares = WagerMath.sharesForLp(price, b, (budget - guard) / (1 + rate), PAYOUT);
    }
    return shares;
  }

  /** Net LP received selling {@code shares} now (fee deducted). */
  private long sellProceeds(WagerOutcomeBean outcome, WagerMarketBean market, double shares) {
    long gross = -Math.round(WagerMath.costLp(outcome.getPrice(), market.getLiquidity(), -shares, PAYOUT));
    long fee = Math.round(Math.abs((double) gross) * market.getFeeBps() / 10000.0);
    return gross - fee;
  }

  private void recomputeBuyPreview() {
    WagerOutcomeBean outcome = selectedOutcome();
    double budget = parseDouble(lpField.getText());
    if (outcome == null || currentMarkets == null || currentMarkets.isEmpty()
        || Double.isNaN(budget) || budget <= 0) {
      buyPreviewLabel.setText("");
      return;
    }
    double shares = sharesForBudget(outcome, currentMarkets.get(0), budget);
    buyPreviewLabel.setText(Double.isNaN(shares) || shares <= 0 ? ""
        : i18n.get("wager.buyPreview", String.format("%.2f", shares)));
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
    double budget = parseDouble(lpField.getText());
    if (Double.isNaN(budget) || budget <= 0) {
      tradeStatusLabel.setText(i18n.get("wager.enterLp"));
      return;
    }
    double shares = sharesForBudget(outcome, currentMarkets.get(0), budget);
    if (Double.isNaN(shares) || shares <= 0) {
      tradeStatusLabel.setText(i18n.get("wager.enterLp"));
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
    if (outcome == null) {
      priceChart.getData().clear();
      return;
    }
    double now = System.currentTimeMillis() / 1000.0;
    chartPoints.add(new double[]{now, outcome.getPrice()});
    WagerMarketBean market = currentMarkets == null || currentMarkets.isEmpty() ? null : currentMarkets.get(0);
    if (market == null) {
      rebuildChart();
      return;
    }
    boolean twoOutcome = market.getOutcomes().size() == 2;

    // Derive this outcome's whole price path from the market's trade log (incl. the other
    // team's trades + reconstructed pre-trade prices) — done in the service.
    wagerService.getPriceHistory(market.getMarketId(), outcome.getOutcomeId(), market.getLiquidity(), twoOutcome, 500)
        .thenAccept(points -> JavaFxUtil.runLater(() -> {
          if (chartOutcome != outcome) {
            return;                    // selection moved on while we were loading
          }
          for (WagerService.PricePoint pt : points) {
            chartPoints.add(new double[]{pt.epochSeconds(), pt.price()});
          }
          rebuildChart();
        }))
        .exceptionally(t -> null);

    chartPriceListener = (obs, oldV, newV) -> JavaFxUtil.runLater(() -> {
      chartPoints.add(new double[]{System.currentTimeMillis() / 1000.0, newV.doubleValue()});
      rebuildChart();
    });
    outcome.priceProperty().addListener(chartPriceListener);
    rebuildChart();
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
    series.setName(chartOutcome != null ? outcomeDisplay(chartOutcome) : "");
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
    priceChart.getData().setAll(series);
  }

  private void teardownChart() {
    if (chartOutcome != null && chartPriceListener != null) {
      chartOutcome.priceProperty().removeListener(chartPriceListener);
    }
    chartOutcome = null;
    chartPriceListener = null;
    chartPoints.clear();
    if (priceChart != null) {
      priceChart.getData().clear();
    }
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
