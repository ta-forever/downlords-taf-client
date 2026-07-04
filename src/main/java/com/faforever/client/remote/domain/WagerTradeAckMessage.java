package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/** Confirms an executed buy/sell (WAGER_DESIGN.md §14). {@code newScore} is the trader's LP
 * balance after the trade; {@code positionShares} their new net holding in the outcome. */
@Getter
@Setter
public class WagerTradeAckMessage extends FafServerMessage {

  private String clientRef;
  private long marketId;
  private String outcomeKey;
  private double deltaShares;
  private int lpCost;
  private int feeLp;
  private double priceAfter;
  private double positionShares;
  private Integer newScore;

  public WagerTradeAckMessage() {
    super(FafServerMessageType.WAGER_TRADE_ACK);
  }
}
