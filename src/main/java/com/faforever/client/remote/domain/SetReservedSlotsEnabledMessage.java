package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetReservedSlotsEnabledMessage extends ClientMessage {

  private boolean enabled;

  public SetReservedSlotsEnabledMessage(boolean enabled) {
    super(ClientMessageType.SET_RESERVED_SLOTS_ENABLED);
    this.enabled = enabled;
  }
}
