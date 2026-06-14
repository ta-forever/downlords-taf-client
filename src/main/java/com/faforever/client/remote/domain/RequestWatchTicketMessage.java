package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Sent to the lobby to obtain a signed ticket authorising the player to watch
 * the live replay of {@code gameId}. The lobby records the request server-side
 * and either returns a {@link WatchTicketMessage} or refuses (e.g. the player is
 * a participant in that game).
 */
@Getter
@Setter
public class RequestWatchTicketMessage extends ClientMessage {

  private int gameId;

  public RequestWatchTicketMessage(int gameId) {
    super(ClientMessageType.REQUEST_WATCH_TICKET);
    this.gameId = gameId;
  }
}
