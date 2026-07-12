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

  /**
   * Balance the game's teams honouring both the host's manual pins and the
   * players' start-position preselections. A start-position pair straddles both
   * teams, so any pair requested by two players forces those two players onto
   * opposite teams. Host pins take precedence: a preselection that would
   * contradict the pins is dropped rather than allowed to override them. Balance
   * is optimised over the remaining freedom (edge orientation + free players).
   *
   * @param pinnedTeamByPlayerId host pins (player id -&gt; team 0/1), may be null/empty
   * @param oppositeTeamPairs    each entry is a two-element {@code [playerIdA, playerIdB]}
   *                             that must land on opposite teams
   * @return the interleaved start-position order, or the best available fallback
   *     if the combined constraints are infeasible
   */
  public List<Player> getBalancedTeams(Game game, Map<Integer, Integer> pinnedTeamByPlayerId,
                                       List<int[]> oppositeTeamPairs);

  public List<Player> getBalancedTeams(Replay replay);

  // priority: "teams", "singles", "default" or "all
  Map<Integer, Pair<String, LeaderboardRating>> getDistilledPlayerRatings(
      List<Player> players, Set<String> teamBoards, Set<String> singleBoards, String priority);
}
