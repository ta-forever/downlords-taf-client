package com.faforever.client.wager;

/** A rejected trade (WAGER_DESIGN.md §14). {@link #getReason()} is the machine reason code
 * (participant_blocked, insufficient_lp, stake_cap_exceeded, market_closed, oversell,
 * not_eligible, service_unavailable, ...) which the UI maps to an i18n message. */
public class WagerTradeException extends RuntimeException {

  private final String reason;

  public WagerTradeException(String reason) {
    super("wager trade rejected: " + reason);
    this.reason = reason;
  }

  public String getReason() {
    return reason;
  }
}
