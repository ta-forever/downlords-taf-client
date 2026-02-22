package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GalacticWarUpdateMessage extends FafServerMessage {

  List<String> galaxyNames;

  public GalacticWarUpdateMessage() {
    super(FafServerMessageType.GALACTIC_WAR_UPDATE);
  }
}
