package com.faforever.client.rating;

import com.faforever.client.game.Game;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.player.Player;
import com.faforever.client.replay.Replay;
import javafx.util.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RatingService {
  LeaderboardRating createNewLeaderboardRating();

  /**
   * Calculates the game quality of the specified replay based in the "before" ratings its player stats.
   */
  double calculateQuality(Replay replay);

  /// metric: "kl" or "quality"
  public List<Player> getBalancedTeams(Game game);

  public List<Player> getBalancedTeams(Replay replay);

  // priority: "teams", "singles", "default" or "all
  Map<Integer, Pair<String, LeaderboardRating>> getDistilledPlayerRatings(
      List<Player> players, Set<String> teamBoards, Set<String> singleBoards, String priority);
}
