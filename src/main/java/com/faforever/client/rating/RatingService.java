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

  /**
   * Balance the game's teams with some players pinned to a team by the host.
   *
   * @param pinnedTeamByPlayerId player id -&gt; team index (0 or 1) for pinned
   *     players; everyone else is balanced freely around them
   * @return the interleaved start-position order, or an empty list if the pins
   *     make an equal-size split impossible
   */
  public List<Player> getBalancedTeams(Game game, Map<Integer, Integer> pinnedTeamByPlayerId);

  public List<Player> getBalancedTeams(Replay replay);

  // priority: "teams", "singles", "default" or "all
  Map<Integer, Pair<String, LeaderboardRating>> getDistilledPlayerRatings(
      List<Player> players, Set<String> teamBoards, Set<String> singleBoards, String priority);
}
