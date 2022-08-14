package com.faforever.client.main.event;

import lombok.Data;

@Data
public class ShowTadaReplayEvent  {
  public ShowTadaReplayEvent(String key, String tadaReplayId, String filename) {
    this.key = key;
    this.tadaReplayId = tadaReplayId;
    this.filename = filename;
  }

  private final String key;
  private final String tadaReplayId;
  private final String filename;
}
