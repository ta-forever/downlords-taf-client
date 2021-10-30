package com.faforever.client.tada.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadToTadaEvent {

  private final Integer replayId;

  public UploadToTadaEvent(Integer replayId) {
    this.replayId = replayId;
  }
}
