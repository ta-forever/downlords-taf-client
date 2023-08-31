package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetGamePasswordMessage extends ClientMessage {

  private String password;

  public SetGamePasswordMessage(String password) {
    super(ClientMessageType.SET_GAME_PASSWORD);
    this.password = password;
  }

}
