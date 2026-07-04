package com.faforever.client.wager;

/**
 * Client-side LMSR trade-cost math (WAGER_DESIGN.md §3), for the shares↔LP preview.
 *
 * <p>The cost to move shares of an outcome depends only on that outcome's current price
 * {@code p} (implied probability in [0,1]) and the market's liquidity {@code b} — the
 * absolute share inventory cancels out of {@code C(q+δ)−C(q)}. So both directions have a
 * closed form and need no server round-trip; the executed cost is confirmed in the ack.
 *
 * <pre>
 *   cost(δ shares) = b · ln(1 − p + p·e^(δ/b)) · payout        (+ buy / − sell refund)
 *   shares(LP)     = b · ln((e^(K/b) − 1 + p) / p),  K = LP/payout
 * </pre>
 */
public final class WagerMath {

  private WagerMath() {
  }

  /** LP cost to move {@code deltaShares} (+buy / −sell) of an outcome at price {@code p}. */
  public static double costLp(double price, double b, double deltaShares, int payoutLp) {
    if (b <= 0) {
      return Double.NaN;
    }
    double p = clampPrice(price);
    double shareCost = b * Math.log(1 - p + p * Math.exp(deltaShares / b));
    return shareCost * payoutLp;
  }

  /**
   * Shares obtainable for an {@code lp} budget (+buy / −sell) at price {@code p}. Returns
   * {@code NaN} if the trade is infeasible (e.g. selling more value than the outcome holds).
   */
  public static double sharesForLp(double price, double b, double lp, int payoutLp) {
    if (b <= 0 || payoutLp <= 0) {
      return Double.NaN;
    }
    double p = clampPrice(price);
    double k = lp / (double) payoutLp;              // target cost in share-units
    double inner = (Math.exp(k / b) - 1 + p) / p;
    if (inner <= 0 || Double.isNaN(inner)) {
      return Double.NaN;
    }
    return b * Math.log(inner);
  }

  private static double clampPrice(double p) {
    return Math.max(1e-6, Math.min(1 - 1e-6, p));
  }

  /**
   * The price of an outcome <em>before</em> a trade that moved it to {@code priceAfter} by
   * adding {@code deltaShares} (+buy / −sell) — the inverse used to plot the pre-trade price
   * so a chart shows the jump. Exact for a 2-outcome LMSR: the odds multiply by e^(δ/b), so
   * {@code odds_before = odds_after · e^(−δ/b)}. (An approximation for &gt;2 outcomes.)
   */
  public static double priceBefore(double priceAfter, double b, double deltaShares) {
    if (b <= 0) {
      return priceAfter;
    }
    double p = clampPrice(priceAfter);
    double oddsBefore = (p / (1 - p)) * Math.exp(-deltaShares / b);
    return oddsBefore / (1 + oddsBefore);
  }
}

