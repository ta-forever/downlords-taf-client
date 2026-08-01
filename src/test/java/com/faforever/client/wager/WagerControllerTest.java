package com.faforever.client.wager;

import com.faforever.client.api.dto.WagerMarket;
import com.faforever.client.audio.AudioService;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameService;
import com.faforever.client.galacticwar.GalacticWarService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.ladder.LadderPointsService;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.NavigationItem;
import com.faforever.client.main.event.ShowReplayEvent;
import com.faforever.client.map.MapService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.DisplayMetric;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.rating.RatingService;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.replay.ReplayService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.theme.UiService;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Self-contained load + behaviour test for the live wagering tab: the FXML wiring, the
 * share-count buy preview, the "the game you're watching has ended" indication (which a pure
 * spectator with no position must also see), and the game/outcome selection surviving a
 * navigate-away-and-back.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class WagerControllerTest extends AbstractPlainJavaFxTest {

  private static final int GAME_ID = 42;

  private WagerController instance;

  @Mock
  private WagerService wagerService;
  @Mock
  private I18n i18n;
  @Mock
  private GameService gameService;
  @Mock
  private LadderPointsService ladderPointsService;
  @Mock
  private PlayerService playerService;
  @Mock
  private UiService uiService;
  @Mock
  private RatingService ratingService;
  @Mock
  private GalacticWarService galacticWarService;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private Preferences preferences;
  @Mock
  private MapService mapService;
  @Mock
  private AudioService audioService;
  @Mock
  private ReplayService replayService;
  @Mock
  private LeaderboardService leaderboardService;
  @Mock
  private EventBus eventBus;
  @Mock
  private Player player;

  private WagerMarketBean market;
  private final ObservableList<WagerMarketBean> markets = FXCollections.observableArrayList();
  private final ObservableList<Game> games = FXCollections.observableArrayList();

  @Before
  public void setUp() throws IOException {
    // i18n echoes the key back, so assertions can name the exact message the UI chose.
    when(i18n.get(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    when(i18n.get(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(i18n.getWithDefault(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));

    when(preferencesService.getPreferences()).thenReturn(preferences);
    when(preferences.displayMetricProperty()).thenReturn(new SimpleObjectProperty<>(DisplayMetric.LADDER_POINTS));
    when(leaderboardService.getLeaderboards()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(gameService.getGames()).thenReturn(games);
    when(player.getId()).thenReturn(5);
    when(playerService.getCurrentPlayer()).thenReturn(Optional.of(player));
    when(ladderPointsService.getStandingsForPlayer(anyInt()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(wagerService.getMyPositions()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(wagerService.getWatchlist()).thenReturn(CompletableFuture.completedFuture(List.of(apiMarket())));
    when(wagerService.getPriceHistory(anyLong(), anyLong(), anyDouble(), anyBoolean(), anyInt()))
        .thenReturn(new CompletableFuture<>());   // never completes: no chart history in these tests

    // Mirror the real service: each subscribe replaces the list with FRESH market beans (the WS
    // snapshot rebuilds them), and unsubscribing empties it.
    when(wagerService.subscribeToGame(GAME_ID)).thenAnswer(invocation -> {
      market = newMarket();
      markets.setAll(market);
      return markets;
    });
    doAnswer(invocation -> {
      markets.clear();
      return null;
    }).when(wagerService).unsubscribe();

    WagerBotPnlController botPnl =
        new WagerBotPnlController(wagerService, i18n, leaderboardService);
    when(wagerService.getBotPnl()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(ladderPointsService.getStandingsForPlayerCached(anyInt()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    instance = new WagerController(wagerService, i18n, gameService, ladderPointsService, playerService,
        uiService, ratingService, galacticWarService, preferencesService, mapService, audioService,
        replayService, leaderboardService, eventBus);
    loadFxml("theme/wager/wager.fxml", clazz -> clazz == WagerBotPnlController.class ? botPnl : instance);
    runOnFxThreadAndWait(() -> {});
  }

  private static WagerMarketBean newMarket() {
    WagerMarketBean bean = new WagerMarketBean(7L, GAME_ID, "TEAM_WIN", null, "OPEN", 100.0, 200);
    bean.getOutcomes().add(new WagerOutcomeBean(1L, "1", "Team 1", 0.5, null));
    bean.getOutcomes().add(new WagerOutcomeBean(2L, "2", "Team 2", 0.5, null));
    return bean;
  }

  private static WagerMarket apiMarket() {
    WagerMarket m = new WagerMarket();
    m.setId("7");
    m.setGameId(GAME_ID);
    m.setMarketType("TEAM_WIN");
    m.setRatingType("Esc-team");
    m.setStatus("OPEN");
    return m;
  }

  /** Navigate to the tab, which loads the watchlist and (re)selects the remembered game. */
  private void navigateTo() {
    runOnFxThreadAndWait(() -> instance.display(new NavigateEvent(NavigationItem.WAGER)));
    runOnFxThreadAndWait(() -> {});   // flush the watchlist's runLater
  }

  /** First visit: nothing is remembered yet, so the user picks the game off the watchlist. */
  private void navigateToAndPickGame() {
    navigateTo();
    runOnFxThreadAndWait(() -> instance.gamesList.getSelectionModel().selectFirst());
  }

  /** Give the tab a real size and lay it out, so geometry assertions have something to measure
   * (the shared test scene is 1x1, where nothing is ever laid out). */
  private void layOutTab() {
    runOnFxThreadAndWait(() -> {
      VBox wagerRoot = instance.wagerRoot;
      getRoot().getChildren().setAll(wagerRoot);
      wagerRoot.resize(900, 700);
      wagerRoot.applyCss();
      wagerRoot.layout();
    });
  }

  @Test
  public void fxmlLoadsAndTradingStartsDisabled() {
    assertThat(instance.getRoot(), notNullValue());
    assertThat(instance.buySharesField, notNullValue());
    assertThat(instance.priceChart, notNullValue());
    assertThat(instance.buyButton.isDisabled(), is(true));
    assertThat(instance.sellButton.isDisabled(), is(true));
  }

  @Test
  public void selectingAGameOpensItsMarketAndEnablesTrading() {
    navigateToAndPickGame();

    assertThat(instance.gamesList.getItems().size(), is(1));
    assertThat(instance.outcomesTable.getItems().size(), is(2));
    assertThat(instance.outcomesTable.getSelectionModel().getSelectedItem(), notNullValue());
    assertThat(instance.marketStatusLabel.getText(), is("wager.marketStatus"));
    assertThat(instance.buyButton.isDisabled(), is(false));
  }

  /** Buying is entered as a share count now, and previews the LP it will cost (fee included). */
  @Test
  public void buyPreviewIsQuotedInLpForATypedShareCount() {
    navigateToAndPickGame();

    runOnFxThreadAndWait(() -> instance.buySharesField.setText("10"));
    assertThat(instance.buyPreviewLabel.getText(), is("wager.buyPreview"));

    runOnFxThreadAndWait(() -> instance.buySharesField.setText(""));
    assertThat(instance.buyPreviewLabel.getText(), is(""));
  }

  /** The chart's own legend strip is gone; the series name is drawn inside the plot area instead. */
  @Test
  public void seriesNameIsOverlaidOnTheChartInsteadOfALegendStrip() {
    navigateToAndPickGame();

    assertThat(instance.priceChart.isLegendVisible(), is(false));
    assertThat(instance.chartSeriesLabel.isVisible(), is(true));
    assertThat(instance.chartSeriesLabel.getText().isBlank(), is(false));
    // No trades yet, so the tick key hides itself rather than reserving space in the overlay.
    assertThat(instance.tradeLegend.isManaged(), is(false));
  }

  /**
   * Collapsing the other left-column sections must hand their room to "My positions" — a heavy
   * trader could only see a handful of rows and had to scroll for anything older.
   */
  @Test
  public void collapsingASectionGivesItsHeightToTheOthers() {
    navigateToAndPickGame();
    // Measure, collapse and re-measure in one pass: a pulse between them would re-autosize the
    // root back to its preferred height and the two numbers would not be comparable.
    double[] portfolioBeforeAfter = new double[3];
    runOnFxThreadAndWait(() -> {
      VBox root = instance.wagerRoot;
      getRoot().getChildren().setAll(root);
      root.resize(900, 700);
      root.applyCss();
      root.layout();
      portfolioBeforeAfter[0] = instance.portfolioPane.getHeight();

      instance.liveGamesPane.setExpanded(false);
      instance.myLpPane.setExpanded(false);
      root.resize(900, 700);
      root.layout();
      portfolioBeforeAfter[1] = instance.portfolioPane.getHeight();
      portfolioBeforeAfter[2] = instance.liveGamesPane.getHeight();
    });

    assertThat("collapsed panes kept their space: " + java.util.Arrays.toString(portfolioBeforeAfter),
        portfolioBeforeAfter[1] > portfolioBeforeAfter[0], is(true));
    // A collapsed pane must shrink to its title bar rather than holding a stretched empty box.
    assertThat(portfolioBeforeAfter[2] < portfolioBeforeAfter[0], is(true));
  }

  /** With nothing picked off the watchlist there are no outcomes to show, so the picker (and the
   * trade controls under it) are hidden — and unmanaged, so they leave no gap. */
  @Test
  public void outcomesColumnIsHiddenUntilAGameIsSelected() {
    navigateTo();
    assertThat(instance.outcomesColumn.isVisible(), is(false));
    assertThat(instance.outcomesColumn.isManaged(), is(false));
    assertThat(instance.outcomesSeparator.isVisible(), is(false));

    runOnFxThreadAndWait(() -> instance.gamesList.getSelectionModel().selectFirst());
    assertThat(instance.outcomesColumn.isVisible(), is(true));
    assertThat(instance.outcomesSeparator.isVisible(), is(true));
  }

  /** The outcomes column takes every pixel between the team cards and the right-hand edge. */
  @Test
  public void outcomesColumnFillsTheSpaceRightOfTheTeamCards() {
    navigateToAndPickGame();
    layOutTab();

    HBox row = (HBox) instance.outcomesColumn.getParent();
    assertThat("row was not laid out", row.getWidth() > 0, is(true));
    double rightEdge = row.getWidth() - row.getInsets().getRight();
    assertThat(instance.outcomesColumn.getBoundsInParent().getMaxX(), closeTo(rightEdge, 1.0));
    // ...and it starts after the team cards rather than being pushed against the edge.
    assertThat(instance.outcomesColumn.getBoundsInParent().getMinX()
        > instance.teamCardsPane.getBoundsInParent().getMaxX(), is(true));
  }

  /** The trade controls sit under the table, flush with its left edge; Sell hugs the right edge. */
  @Test
  public void tradeControlsSitBeneathTheOutcomesTable() {
    navigateToAndPickGame();
    layOutTab();

    Bounds table = instance.outcomesTable.getBoundsInParent();
    Bounds buy = instance.buySharesField.getParent().getBoundsInParent();   // the buy/sell row
    assertThat("trade row was not laid out", buy.getWidth() > 0, is(true));
    assertThat(buy.getMinY() >= table.getMaxY(), is(true));                 // beneath the table
    assertThat(buy.getMinX(), closeTo(table.getMinX(), 1.0));               // same left edge

    // Buy on the left of that row, Sell hard against its right edge.
    Bounds buyButton = instance.buyButton.getBoundsInParent();
    Bounds sellButton = instance.sellButton.getBoundsInParent();
    assertThat(buyButton.getMaxX() < sellButton.getMinX(), is(true));
    assertThat(sellButton.getMaxX(), closeTo(buy.getWidth(), 1.0));
  }

  /** The overlaid name is anchored to the line: right of the y-axis, just above the opening price. */
  @Test
  public void seriesNameSitsJustAboveTheOpeningPrice() {
    navigateToAndPickGame();
    layOutTab();

    NumberAxis yAxis = (NumberAxis) instance.priceChart.getYAxis();
    Bounds yBounds = yAxis.getBoundsInParent();
    assertThat("chart was not laid out", yBounds.getHeight() > 0, is(true));

    // Clear of the y-axis...
    assertThat(instance.chartSeriesLabel.getLayoutX() > yBounds.getMaxX(), is(true));
    // ...and sitting on top of the opening price (both outcomes open at 0.5 -> 50).
    double openingPriceY = yBounds.getMinY() + yAxis.getDisplayPosition(50.0);
    double labelBottom = instance.chartSeriesLabel.getLayoutY() + instance.chartSeriesLabel.prefHeight(-1);
    assertThat(labelBottom <= openingPriceY, is(true));
    assertThat(openingPriceY - labelBottom < 40, is(true));
  }

  /**
   * The reported gap: the server pushes {@code wager_settled} to spectators too, but it mutates
   * the market bean rather than the markets list, so nothing used to re-render for someone
   * holding no position.
   */
  @Test
  public void settlementIsShownEvenWithNoPosition() {
    navigateToAndPickGame();

    runOnFxThreadAndWait(() -> {
      market.getOutcomes().get(0).setWinner(true);
      market.getOutcomes().get(1).setWinner(false);
      market.setStatus("SETTLED");        // exactly what WagerServiceImpl.onSettled does
    });

    assertThat(instance.marketStatusLabel.getText(), is("wager.market.settled"));
    assertThat(instance.buyButton.isDisabled(), is(true));
    assertThat(instance.sellButton.isDisabled(), is(true));
  }

  /** Settling stamps the charted outcome's fate on the end of its line: tick if it won, X if not. */
  @Test
  public void settlementStampsAVerdictOnTheEndOfTheChartedLine() {
    navigateToAndPickGame();
    // The first outcome ("1") is auto-selected, so chart it as the winner.
    settle(true);

    XYChart.Data<Number, Number> end = lastChartDatum();
    assertThat(end.getNode(), instanceOf(Path.class));
    assertThat(((Path) end.getNode()).getStroke(), is(Color.web("#26a65b")));       // green tick
    // It rides on the end of the line, at the last price (both outcomes are still at 50).
    assertThat(end.getYValue().doubleValue(), is(50.0));
  }

  @Test
  public void aLosingChartedOutcomeGetsARedCross() {
    navigateToAndPickGame();
    runOnFxThreadAndWait(() -> instance.outcomesTable.getSelectionModel().select(1));   // outcome "2"
    settle(true);                                                                      // "1" wins

    assertThat(((Path) lastChartDatum().getNode()).getStroke(), is(Color.web("#cb4b16")));   // red X
  }

  /** Resolve the market the way {@code WagerServiceImpl.onSettled} does: winners, then status. */
  private void settle(boolean firstOutcomeWins) {
    runOnFxThreadAndWait(() -> {
      market.getOutcomes().get(0).setWinner(firstOutcomeWins);
      market.getOutcomes().get(1).setWinner(!firstOutcomeWins);
      market.setStatus("SETTLED");
    });
  }

  private XYChart.Data<Number, Number> lastChartDatum() {
    List<XYChart.Data<Number, Number>> data = instance.priceChart.getData().get(0).getData();
    return data.get(data.size() - 1);
  }

  /** A trade tick marks where its move STARTS, so it sits at the pre-trade price, not the result. */
  @Test
  public void tradeTicksSitAtThePreTradePrice() {
    double tradeEpoch = 1_700_000_100L;
    when(wagerService.getPriceHistory(anyLong(), anyLong(), anyDouble(), anyBoolean(), anyInt()))
        .thenReturn(CompletableFuture.completedFuture(new WagerService.PriceHistory(
            List.of(new WagerService.PricePoint(tradeEpoch - 1, 0.40),
                new WagerService.PricePoint(tradeEpoch, 0.62)),
            List.of(new WagerService.TradeMarker(tradeEpoch, 0.40, 0.62, 9, "someone", true, 5)))));

    navigateToAndPickGame();
    runOnFxThreadAndWait(() -> {});   // flush the price-history runLater

    XYChart.Data<Number, Number> tick = instance.priceChart.getData().get(0).getData().stream()
        .filter(datum -> datum.getNode() instanceof Polygon)
        .findFirst().orElseThrow();
    assertThat(tick.getYValue().doubleValue(), is(40.0));      // the foot of the step, not 62

    // The legend entry carries BOTH tick directions — a category owns its up- and down-ticks.
    HBox entry = (HBox) instance.tradeLegend.getChildren().get(0);
    HBox ticks = (HBox) entry.getChildren().get(0);
    assertThat(ticks.getChildren().size(), is(2));
    assertThat(ticks.getChildren().get(0), instanceOf(Polygon.class));
    assertThat(ticks.getChildren().get(1), instanceOf(Polygon.class));
  }

  /** Once the game is over the chart offers the obvious next step: go watch the replay. */
  @Test
  public void openReplayButtonAppearsOnlyOnceTheGameIsOver() {
    navigateToAndPickGame();
    assertThat(instance.openReplayButton.isVisible(), is(false));

    settle(true);
    assertThat(instance.openReplayButton.isVisible(), is(true));

    runOnFxThreadAndWait(() -> instance.openReplayButton.fire());
    verify(eventBus).post(new ShowReplayEvent(GAME_ID));
  }

  @Test
  public void voidedMarketIsShownAndStopsTrading() {
    navigateToAndPickGame();

    runOnFxThreadAndWait(() -> market.setStatus("VOIDED"));

    assertThat(instance.marketStatusLabel.getText(), is("wager.market.voided"));
    assertThat(instance.buyButton.isDisabled(), is(true));
  }

  /** The lobby says the game is over before the market settles; say so rather than looking live. */
  @Test
  public void gameEndingClosesTheMarketDisplay() {
    Game game = new Game();
    game.setId(GAME_ID);
    game.setStatus(GameStatus.LIVE);
    games.setAll(game);

    navigateToAndPickGame();
    assertThat(instance.buyButton.isDisabled(), is(false));

    runOnFxThreadAndWait(() -> game.setStatus(GameStatus.ENDED));

    assertThat(instance.marketStatusLabel.getText(), is("wager.market.closed"));
    assertThat(instance.buyButton.isDisabled(), is(true));
  }

  /** Leaving the tab and coming back keeps the game AND the outcome the user was looking at. */
  @Test
  public void selectedGameAndOutcomeSurviveNavigation() {
    navigateToAndPickGame();
    runOnFxThreadAndWait(() -> instance.outcomesTable.getSelectionModel().select(1));
    assertThat(instance.outcomesTable.getSelectionModel().getSelectedItem().getOutcomeKey(), is("2"));

    // Leaving the tab unsubscribes, which drops the market and with it the outcome selection.
    runOnFxThreadAndWait(() -> instance.hide());
    assertThat(instance.outcomesTable.getItems().isEmpty(), is(true));

    navigateTo();
    assertThat(instance.gamesList.getSelectionModel().getSelectedItem().gameId(), is(GAME_ID));
    assertThat(instance.outcomesTable.getSelectionModel().getSelectedItem().getOutcomeKey(), is("2"));
  }
}
