package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/** Stop receiving a game's wager price feed (WAGER_DESIGN.md §14). */
@Getter
@Setter
public class WagerUnsubscribeMessage extends ClientMessage {

  private int gameId;

  public WagerUnsubscribeMessage(int gameId) {
    super(ClientMessageType.WAGER_UNSUBSCRIBE);
    setTarget(MessageTarget.WAGER);
    this.gameId = gameId;
  }
}
