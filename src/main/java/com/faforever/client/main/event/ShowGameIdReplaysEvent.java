package com.faforever.client.main.event;

import lombok.Getter;

import java.util.List;

/**
 * Navigates to the online replay vault and immediately filters it to a fixed
 * set of game IDs (e.g. all games played in a particular tournament match).
 */
@Getter
public class ShowGameIdReplaysEvent extends OpenOnlineReplayVaultEvent {
  private final List<Integer> gameIds;

  public ShowGameIdReplaysEvent(List<Integer> gameIds) {
    this.gameIds = gameIds;
  }
}
