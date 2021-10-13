package com.faforever.client.main.event;

import lombok.Data;

@Data
public class ShowTadaReplayEvent  {
  public ShowTadaReplayEvent(String tadaReplayId) {
    this.tadaReplayId = tadaReplayId;
  }

  private final String tadaReplayId;
}
