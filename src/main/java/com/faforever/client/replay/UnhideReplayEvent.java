package com.faforever.client.replay;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnhideReplayEvent {

  private final Integer gameId;

  public UnhideReplayEvent(Integer gameId) {
    this.gameId = gameId;
  }
}
