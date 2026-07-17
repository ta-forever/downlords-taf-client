package com.faforever.client.wager;

import com.faforever.client.api.dto.WagerBotPnl;
import com.faforever.client.i18n.I18n;
import com.faforever.client.ladder.LadderPointsService;
import com.faforever.client.ladder.SeasonStanding;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
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
  private PlayerService playerService;
  @Mock
  private LadderPointsService ladderPointsService;
  @Mock
  private LeaderboardService leaderboardService;
  @Mock
  private Player player;

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

  private static SeasonStanding standing(int wagerNet) {
    return new SeasonStanding(5, "me", "board", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", wagerNet);
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
    // Current player is net +120 LP wagering (+150 on one board, −30 on another).
    when(player.getId()).thenReturn(5);
    when(playerService.getCurrentPlayer()).thenReturn(Optional.of(player));
    when(ladderPointsService.getStandingsForPlayerCached(5)).thenReturn(
        CompletableFuture.completedFuture(List.of(standing(150), standing(-30))));
    when(leaderboardService.getLeaderboards()).thenReturn(CompletableFuture.completedFuture(List.of()));

    instance = new WagerBotPnlController(wagerService, i18n, playerService, ladderPointsService, leaderboardService);
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

  @Test
  public void youVsModelSumsOwnWagerNet() {
    // +150 + (−30) = +120 -> the player is ahead of the model.
    verify(i18n).get("wager.bot.youAhead", 120L);
  }
}
