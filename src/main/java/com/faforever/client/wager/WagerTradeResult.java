package com.faforever.client.wager;

/** Outcome of a successful trade (WAGER_DESIGN.md §14 ack). {@code newScore} is the
 * trader's LP balance after the trade; {@code positionShares} their new net holding. */
public record WagerTradeResult(
    long marketId,
    String outcomeKey,
    double deltaShares,
    int lpCost,
    int feeLp,
    double priceAfter,
    double positionShares,
    Integer newScore) {
}
