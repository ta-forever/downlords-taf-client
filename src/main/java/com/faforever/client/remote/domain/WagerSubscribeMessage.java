package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Subscribe to a live game's wager markets + price feed (WAGER_DESIGN.md §14). The lobby
 * WagerGateway replies with a {@link WagerMarketsMessage} snapshot and then streams
 * {@link WagerPriceMessage} ticks; it refuses (participant_blocked reject) if the player
 * is in that game.
 */
@Getter
@Setter
public class WagerSubscribeMessage extends ClientMessage {

  private int gameId;

  public WagerSubscribeMessage(int gameId) {
    super(ClientMessageType.WAGER_SUBSCRIBE);
    setTarget(MessageTarget.WAGER);
    this.gameId = gameId;
  }
}
