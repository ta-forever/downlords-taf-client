package com.faforever.client.rating;

import com.faforever.client.game.Game;
import com.faforever.client.player.Player;
import com.faforever.client.replay.Replay;

import java.util.List;

public interface RatingService {
  /**
   * Calculates the game quality of the specified replay based in the "before" ratings its player stats.
   */
  double calculateQuality(Replay replay);

  public List<Player> getBalancedTeams(Game game);
}
