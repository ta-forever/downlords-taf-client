package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SetReservedPlayersMessage extends ClientMessage {

  private List<Integer> playerIds;

  public SetReservedPlayersMessage(List<Integer> playerIds) {
    super(ClientMessageType.SET_RESERVED_PLAYERS);
    this.playerIds = playerIds;
  }
}
