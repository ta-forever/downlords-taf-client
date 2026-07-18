package com.faforever.client.wager;

import com.faforever.client.api.dto.WagerMarket;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client side of live-game wagering (WAGER_DESIGN.md §11, §14). Cold reads (watchlist,
 * portfolio) go through faf-api; the live path (subscribe, price ticks, buy/sell) rides
 * the lobby WebSocket via {@link com.faforever.client.remote.FafServerAccessor}.
 */
public interface WagerService {

  /** LP paid by one winning share; prices display as 0..this. Matches WAGER_SHARE_PAYOUT_LP. */
  int SHARE_PAYOUT_LP = 100;

  /** Open/recently-closed markets across all live games (watchlist), from faf-api. */
  CompletableFuture<List<WagerMarket>> getWatchlist();

  /**
   * Subscribe to a game's markets over the lobby WS. Returns a live-updating observable
   * list of the game's markets (populated from the WS snapshot, prices kept fresh by
   * ticks). Subscribing to a new game replaces the previous subscription.
   */
  ObservableList<WagerMarketBean> subscribeToGame(int gameId);

  /** Drop the current subscription (unsubscribes on the WS and clears the market list). */
  void unsubscribe();

  /**
   * Buy ({@code deltaShares > 0}) / sell ({@code < 0}) shares of an outcome. The returned
   * future completes with the trade result, or fails with a {@link WagerTradeException}
   * carrying the reject reason.
   */
  CompletableFuture<WagerTradeResult> trade(int gameId, long marketId, String outcomeKey, double deltaShares);

  /** The current player's net positions (portfolio), from faf-api. */
  CompletableFuture<List<WagerPositionBean>> getMyPositions();

  /** The house model-maker bot's realised P&amp;L per (season, board) — the "beat the model"
   * scoreboard, from faf-api (aggregate only; the bot's live position is never exposed). */
  CompletableFuture<List<com.faforever.client.api.dto.WagerBotPnl>> getBotPnl();

  /**
   * The selected outcome's price path over time for the chart (oldest-first), derived from
   * the WHOLE market's trade log — a 2-team outcome's price also moves when the other team is
   * traded. Each trade yields the outcome's price after it, plus the reconstructed price just
   * before (so a lone trade shows its jump). {@code twoOutcome} enables the {@code 1 − p}
   * derivation for the untraded team (a &gt;2-outcome market only uses its own trades).
   */
  CompletableFuture<List<PricePoint>> getPriceHistory(long marketId, long outcomeId, double b,
                                                      boolean twoOutcome, int limit);

  /**
   * For the replay-vault detail dialog: the price path of the outcome that eventually WON a
   * game's TEAM_WIN market (the "did the market see it coming?" line). Resolves the game's
   * markets, finds the settled market's winning outcome, and returns that outcome's implied
   * probability over the whole game. Empty when the game had no market, none settled with a
   * decided winner, or the market had no trades.
   */
  CompletableFuture<Optional<ReplayPriceChart>> getWinningOutcomeHistory(int gameId);

  /**
   * Register a listener notified (on the FX thread) when a market settles or voids — so the
   * UI can auto-refresh the portfolio and cue a win without a manual refresh. Pass null to
   * clear. The service also updates the live market beans (status + winning outcomes).
   */
  void setSettlementListener(java.util.function.Consumer<Settlement> listener);

  /**
   * Register a listener notified (on the FX thread) when a subscribe is rejected — e.g.
   * {@code participant_blocked} because you're playing in the game you tried to watch (§8).
   * Such rejects carry no {@code client_ref}, so without this they'd be silently swallowed and
   * the outcomes table would just stay empty with no explanation. Pass null to clear.
   */
  void setSubscribeRejectListener(java.util.function.Consumer<SubscribeReject> listener);

  /** A rejected subscribe: the game it was for (the currently-subscribed game) + reject reason
   * (see {@link WagerTradeException} reasons, e.g. {@code participant_blocked}). */
  record SubscribeReject(int gameId, String reason) {
  }

  /** One point on an outcome's price chart: epoch seconds + implied probability [0,1]. */
  record PricePoint(double epochSeconds, double price) {
  }

  /** The winning outcome's price path for a finished game (replay-detail chart). {@code
   * outcomeLabel} names the eventual winner (e.g. "Team 1"); {@code points} is oldest-first. */
  record ReplayPriceChart(String outcomeLabel, List<PricePoint> points) {
  }

  /** A market resolution (WAGER_DESIGN.md §14). {@code payoutLp} is the current player's own
   * payout (0 if none). {@code isWin} = a settled market that paid this player. */
  record Settlement(int gameId, long marketId, String marketType, String status,
                    List<String> winningOutcomeKeys, int payoutLp) {
    public boolean isWin() {
      return "SETTLED".equals(status) && payoutLp > 0;
    }
  }
}
