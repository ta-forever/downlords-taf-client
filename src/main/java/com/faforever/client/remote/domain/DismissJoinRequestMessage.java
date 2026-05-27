package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Host dismisses a pending join-request without admitting the requester.
 * Server just clears the entry from join_requests.
 */
@Getter
@Setter
public class DismissJoinRequestMessage extends ClientMessage {

  private int playerId;

  public DismissJoinRequestMessage(int playerId) {
    super(ClientMessageType.DISMISS_JOIN_REQUEST);
    this.playerId = playerId;
  }
}
