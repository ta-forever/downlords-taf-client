package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Buy ({@code deltaShares > 0}) or sell ({@code < 0}) shares of an outcome (WAGER_DESIGN.md
 * §14). {@code clientRef} is an opaque id echoed back in the {@link WagerTradeAckMessage} /
 * {@link WagerTradeRejectMessage} so the client can correlate the response. The lobby injects
 * the authenticated player id — it is never taken from the client.
 */
@Getter
@Setter
public class WagerTradeMessage extends ClientMessage {

  private int gameId;
  private long marketId;
  private String outcomeKey;
  private double deltaShares;
  private String clientRef;

  public WagerTradeMessage(int gameId, long marketId, String outcomeKey, double deltaShares, String clientRef) {
    super(ClientMessageType.WAGER_TRADE);
    setTarget(MessageTarget.WAGER);
    this.gameId = gameId;
    this.marketId = marketId;
    this.outcomeKey = outcomeKey;
    this.deltaShares = deltaShares;
    this.clientRef = clientRef;
  }
}
