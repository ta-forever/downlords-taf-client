package com.faforever.client.fa.relay.event;

import com.faforever.client.game.Game;
import lombok.Value;

/**
 * Fired to request that GameService should rehosted the given Game either immediately or as soon currentGame terminates
 */
@Value
public class RehostRequestEvent {
  private Game game;
}
