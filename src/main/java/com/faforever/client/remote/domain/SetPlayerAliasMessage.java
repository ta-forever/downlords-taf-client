package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetPlayerAliasMessage extends ClientMessage {

  private String alias;

  public SetPlayerAliasMessage(String alias) {
    super(ClientMessageType.SET_PLAYER_ALIAS);
    this.alias = alias;
  }

}
