package com.faforever.client.wager;

import com.faforever.client.api.dto.WagerBotPnl;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Self-contained load + aggregation test for the "House model-maker" scoreboard card. */
@RunWith(MockitoJUnitRunner.Silent.class)
public class WagerBotPnlControllerTest extends AbstractPlainJavaFxTest {

  private WagerBotPnlController instance;
  @Mock
  private WagerService wagerService;
  @Mock
  private I18n i18n;
  @Mock
  private LeaderboardService leaderboardService;

  private static WagerBotPnl row(String id, String board, int markets, int wins, long staked, long pnl) {
    WagerBotPnl r = new WagerBotPnl();
    r.setId(id);
    r.setRatingType(board);
    r.setMarkets(markets);
    r.setWins(wins);
    r.setStakedLp(staked);
    r.setPnlLp(pnl);
    return r;
  }

  /** A settled position: {@code costLp} staked, and it either won (shares * 100 LP) or lost. */
  private static WagerPositionBean settled(int costLp, double shares, boolean won) {
    return new WagerPositionBean(1L, 1, "1", "Team 1", shares, costLp, 0.5, "SETTLED", won, 100.0, 200);
  }

  /** An OPEN position must be ignored: its stake is spent but unresolved. */
  private static WagerPositionBean open(int costLp, double shares) {
    return new WagerPositionBean(2L, 2, "1", "Team 1", shares, costLp, 0.5, "OPEN", null, 100.0, 200);
  }

  @Before
  public void setUp() throws IOException {
    when(i18n.get(anyString())).thenReturn("");
    when(i18n.get(anyString(), any())).thenReturn("");
    // Two seasons of the same board (must sum) + a second, losing board.
    when(wagerService.getBotPnl()).thenReturn(CompletableFuture.completedFuture(List.of(
        row("1-9", "Esc-team", 10, 6, 1000, 200),
        row("2-9", "Esc-team", 5, 3, 500, 100),
        row("1-3", "ProTA-1v1", 8, 5, 800, -50))));
    // Settled: staked 100 -> 3 winning shares = 300 LP back (+200), and staked 100 -> lost (-100).
    // Net +100 on 200 staked = +50.0% ROI, against the model's 250/2300 = +10.9%.
    when(wagerService.getMyPositions()).thenReturn(CompletableFuture.completedFuture(List.of(
        settled(100, 3, true), settled(100, 2, false), open(500, 5))));
    when(leaderboardService.getLeaderboards()).thenReturn(CompletableFuture.completedFuture(List.of()));

    instance = new WagerBotPnlController(wagerService, i18n, leaderboardService);
    loadFxml("theme/wager/wager_bot_pnl.fxml", clazz -> instance);
    runOnFxThreadAndWait(() -> {});   // flush the runLater that populates the table + you-line
  }

  @Test
  public void aggregatesPerBoardAcrossSeasons() {
    List<WagerBotPnlController.BoardRow> rows = instance.botPnlTable.getItems();
    assertThat(rows.size(), is(2));

    // TreeMap order -> Esc-team first. Its two seasons summed.
    WagerBotPnlController.BoardRow esc = rows.get(0);
    assertThat(esc.board(), is("Esc-team"));
    assertThat(esc.markets(), is(15));
    assertThat(esc.wins(), is(9));
    assertThat(esc.staked(), is(1500L));
    assertThat(esc.pnl(), is(300L));
    assertThat(esc.hitRate(), is(9.0 / 15.0));
    assertThat(esc.roiPct(), is(20.0));

    // Second board carried through with a negative P&L (crowd beating the model there).
    WagerBotPnlController.BoardRow pro = rows.get(1);
    assertThat(pro.board(), is("ProTA-1v1"));
    assertThat(pro.pnl(), is(-50L));
  }

  /** Walker's ask: the "you vs the model" line has to carry BOTH ROIs, or "ahead" says nothing. */
  @Test
  public void youVsModelQuotesBothRois() {
    // Only the two SETTLED positions count: +100 LP on 200 staked = +50.0%.
    // Model total: (200 + 100 - 50) = 250 on (1000 + 500 + 800) = 2300 staked = +10.9%.
    verify(i18n).get("wager.bot.youVsModel", "+100", 200L, "+50.0%", "+10.9%");
  }

  /** Beating the model is called out in green; the raw LP number alone never said so. */
  @Test
  public void beatingTheModelIsHighlighted() {
    assertThat(instance.youLabel.getStyle().contains("#26a65b"), is(true));
  }
}
