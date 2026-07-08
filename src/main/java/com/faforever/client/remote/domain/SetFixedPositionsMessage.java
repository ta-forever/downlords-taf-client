package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Host -&gt; server: toggle start-position preselection for this game. While
 * {@code enabled} is {@code false} the game uses TA's traditional random start
 * positions and the position picker is hidden on every client; while
 * {@code true} players may request a preferred start-position pair. Host-only;
 * the server rebroadcasts the flag in GAME_INFO.
 */
@Getter
@Setter
public class SetFixedPositionsMessage extends ClientMessage {

  private boolean enabled;

  public SetFixedPositionsMessage(boolean enabled) {
    super(ClientMessageType.SET_FIXED_POSITIONS);
    this.enabled = enabled;
  }
}
