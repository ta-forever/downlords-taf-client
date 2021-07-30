package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class PingMessage extends ClientMessage {

  private Long afkSeconds;

  public PingMessage(long afkSeconds) {
    super(ClientMessageType.PING);
    this.afkSeconds = afkSeconds;
  }
}
