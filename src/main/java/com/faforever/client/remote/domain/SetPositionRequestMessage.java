package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Player -&gt; server: this player's start-position role request (position
 * preselection). {@link #position} is a role index 0..4 — a role is a pair of
 * mirrored map start positions, one per team (role r = map positions
 * 2r+1/2r+2) — or {@code null} to clear the request. The server stores requests
 * in arrival order and rebroadcasts them in GAME_INFO so the host's client can
 * honour them (first-come-first-served on same-role ties) and every client can
 * badge them.
 */
@Getter
@Setter
public class SetPositionRequestMessage extends ClientMessage {

  private Integer position;

  public SetPositionRequestMessage(Integer position) {
    super(ClientMessageType.SET_POSITION_REQUEST);
    this.position = position;
  }
}
