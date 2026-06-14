package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Lobby response to a {@link RequestWatchTicketMessage}: a signed, short-lived
 * ticket the client passes to the replay server (as the {@code ticket} query
 * parameter of the {@code taflive://} URI) to authorise live watching.
 */
@Getter
@Setter
public class WatchTicketMessage extends FafServerMessage {

  private int gameId;
  private String ticket;
  private long expiresAt;
  /** True when the lobby refused to issue a ticket (e.g. the player is a participant in this game). */
  private boolean denied;

  public WatchTicketMessage() {
    super(FafServerMessageType.WATCH_TICKET);
  }
}
