package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetGameMapDetailsMessage extends ClientMessage {

  private String mapName;
  private String hpiArchive;
  private String crc;

  public SetGameMapDetailsMessage(String mapName, String hpiArchive, String crc) {
    super(ClientMessageType.SET_GAME_MAP_DETAILS);
    this.mapName = mapName;
    this.hpiArchive = hpiArchive;
    this.crc = crc;
  }

}
