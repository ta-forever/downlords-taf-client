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
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.DisplayMetric;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.rating.RatingService;
import com.faforever.client.replay.ReplayService;
import com.faforever.client.util.RatingUtil;
import com.faforever.client.theme.UiService;
import javafx.beans.InvalidationListener;
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

  public VBox wagerRoot;
  public ListView<GameRow> gamesList;
  public Button refreshButton;
  public ListView<BoardLp> myLpList;
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
  private final BooleanProperty submitting = new SimpleBooleanProperty(false);

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
          setText(i18n.get("wager.boardLp", item.displayName(), item.score()));
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

    JavaFxUtil.addListener(preferencesService.getPreferences().displayMetricProperty(),
        new WeakChangeListener<>(displayMetricListener));
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    wagerService.setSettlementListener(this::onSettlement);
    reload();
  }

  @Override
  public void onHide() {
    wagerService.setSettlementListener(null);
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
    if (settlement.isWin()) {
      audioService.playAchievementUnlockedSound();
      tradeStatusLabel.setText(i18n.get("wager.youWon", settlement.payoutLp()));
      tradeStatusLabel.setStyle("-fx-text-fill: #26a65b; -fx-font-weight: bold;");
    } else if ("VOIDED".equals(settlement.status()) && settlement.payoutLp() > 0) {
      tradeStatusLabel.setText(i18n.get("wager.refunded", settlement.payoutLp()));
    }
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
      rows.add(new BoardLp(s.getLeaderboardTechnicalName(), s.getLeaderboardTechnicalName(), s.getScore()));
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
    boardLabel.setText(i18n.get("wager.boardAvailable", selectedRatingType, lp));
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
      marketStatusLabel.setText("");
      selectOutcomeForChart(null);
      return;
    }
    WagerMarketBean market = currentMarkets.get(0);   // v1: TEAM_WIN is the primary market
    outcomesTable.setItems(market.getOutcomes());
    marketStatusLabel.setText(i18n.get("wager.marketStatus", market.getMarketType(), market.getStatus()));
    if (outcomesTable.getSelectionModel().getSelectedItem() == null && !market.getOutcomes().isEmpty()) {
      outcomesTable.getSelectionModel().selectFirst();    // fires the selection listener -> chart
    }
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
    sharesField.setText(held > 0 ? String.format("%.2f", held) : "0");
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

  /** Plot the selected outcome's price (0..100) over elapsed seconds. */
  private void rebuildChart() {
    if (chartPoints.isEmpty()) {
      priceChart.getData().clear();
      return;
    }
    List<double[]> points = new ArrayList<>(chartPoints);
    // An untraded outcome has a single point — draw a flat line at its (opening) price so
    // the chart isn't empty (a lone point renders nothing with symbols off).
    if (points.size() == 1) {
      points.add(0, new double[]{points.get(0)[0] - 60, points.get(0)[1]});
    }
    points.sort(Comparator.comparingDouble(a -> a[0]));
    double t0 = points.get(0)[0];

    XYChart.Series<Number, Number> series = new XYChart.Series<>();
    series.setName(chartOutcome != null ? outcomeDisplay(chartOutcome) : "");
    for (double[] pt : points) {
      series.getData().add(new XYChart.Data<>(pt[0] - t0, pt[1] * 100));
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
