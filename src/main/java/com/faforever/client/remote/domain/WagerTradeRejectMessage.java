package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/** A rejected trade/subscribe (WAGER_DESIGN.md §14). {@code reason} is one of
 * participant_blocked, insufficient_lp, stake_cap_exceeded, market_closed, oversell,
 * not_eligible, service_unavailable, bad_request, rejected. */
@Getter
@Setter
public class WagerTradeRejectMessage extends FafServerMessage {

  private String clientRef;
  private String reason;

  public WagerTradeRejectMessage() {
    super(FafServerMessageType.WAGER_TRADE_REJECT);
  }
}
