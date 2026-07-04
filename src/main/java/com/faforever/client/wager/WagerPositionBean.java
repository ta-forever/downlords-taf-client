package com.faforever.client.wager;

/**
 * A trader's net holding in one outcome, for the portfolio (WAGER_DESIGN.md §11).
 *
 * <p>The "value" of an OPEN position is the honest LMSR **liquidation value** — the LP you'd
 * actually receive selling all your shares now, net of the sell fee — NOT {@code shares *
 * price * payout}. The naive form overstates: buying moved the price up, so at the new
 * marginal price your shares "look" worth more, but selling them pushes the price back down
 * (you can't unwind size at the marginal price). Right after a buy the liquidation value is
 * ≈ cost − round-trip fees, so P&amp;L ≈ −fees, which is the truth (no phantom profit).
 */
public class WagerPositionBean {

  private final long marketId;
  private final int gameId;
  private final String outcomeKey;
  private final String label;
  private final double shares;
  private final int costLp;
  private final double lastPrice;      // implied probability [0,1], 0 if unknown
  private final String marketStatus;
  private final Boolean winner;
  private final double liquidity;      // LMSR b of the market (for the liquidation value)
  private final int feeBps;

  public WagerPositionBean(long marketId, int gameId, String outcomeKey, String label, double shares,
                           int costLp, double lastPrice, String marketStatus, Boolean winner,
                           double liquidity, int feeBps) {
    this.marketId = marketId;
    this.gameId = gameId;
    this.outcomeKey = outcomeKey;
    this.label = label;
    this.shares = shares;
    this.costLp = costLp;
    this.lastPrice = lastPrice;
    this.marketStatus = marketStatus;
    this.winner = winner;
    this.liquidity = liquidity;
    this.feeBps = feeBps;
  }

  public long getMarketId() {
    return marketId;
  }

  public String getOutcomeKey() {
    return outcomeKey;
  }

  public int getGameId() {
    return gameId;
  }

  public String getLabel() {
    return label;
  }

  public double getShares() {
    return shares;
  }

  public int getCostLp() {
    return costLp;
  }

  public double getLastPrice() {
    return lastPrice;
  }

  public String getMarketStatus() {
    return marketStatus;
  }

  public Boolean getWinner() {
    return winner;
  }

  /** True while the market is still tradeable (game live). */
  public boolean isLive() {
    return "OPEN".equals(marketStatus);
  }

  public boolean isResolved() {
    return "SETTLED".equals(marketStatus) || "VOIDED".equals(marketStatus);
  }

  /** LP value of the position now (see class doc). Settled: shares*payout if won else 0;
   * voided: refunded at cost; open/closed: honest LMSR liquidation value. */
  public double markToMarket(int payoutUnitLp) {
    if ("SETTLED".equals(marketStatus)) {
      return Boolean.TRUE.equals(winner) ? shares * payoutUnitLp : 0d;
    }
    if ("VOIDED".equals(marketStatus)) {
      return costLp;                 // refunded at cost
    }
    if (shares <= 0 || liquidity <= 0) {
      return Math.max(0, shares * lastPrice * payoutUnitLp);   // fallback if no b
    }
    // What you'd receive selling all shares now: a sell moves the price against you.
    double sellCost = WagerMath.costLp(lastPrice, liquidity, -shares, payoutUnitLp); // <= 0 (refund)
    if (Double.isNaN(sellCost)) {
      return shares * lastPrice * payoutUnitLp;
    }
    double grossRefund = -sellCost;
    double fee = Math.round(Math.abs(grossRefund) * feeBps / 10000.0);
    return Math.max(0, grossRefund - fee);
  }

  /** Estimated P&amp;L = value − cost basis. Right after a buy this is ≈ −fees, not a phantom
   * profit. For a settled position it's the realized win/loss. */
  public double pnl(int payoutUnitLp) {
    return markToMarket(payoutUnitLp) - costLp;
  }
}
