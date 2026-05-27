package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Sent by a non-reserved player who was denied entry to a reserved-slots
 * game, asking the host to admit them. The host sees this surfaced as a
 * persistent notification via the host_game_state push.
 */
@Getter
@Setter
public class RequestGameAccessMessage extends ClientMessage {

  private int gameId;

  public RequestGameAccessMessage(int gameId) {
    super(ClientMessageType.REQUEST_GAME_ACCESS);
    this.gameId = gameId;
  }
}
