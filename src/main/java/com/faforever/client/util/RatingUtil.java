package com.faforever.client.util;

import com.faforever.client.domain.RatingHistoryDataPoint;
import com.faforever.client.leaderboard.Leaderboard;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.player.Player;

import java.util.List;
import java.util.Optional;

public final class RatingUtil {

  /**
   * Deviation at or above which a player has not yet been placed, so {@code mean - 3*deviation} is
   * not a meaningful number to show them.
   * <p>
   * Half the TrueSkill initial deviation (500). Measured against prod (2026-09, all TAF ladders),
   * this corresponds to roughly the first 8 rated games: median deviation is 429 after 1 game, 328
   * after 3-4, 268 after 5-9, and 179 after 10-19. It sits clear of established players — with 20+
   * games the p99 deviation is 201 and the observed maximum 220 — so no settled player is ever
   * marked as being in placement.
   */
  public static final float PLACEMENT_DEVIATION_THRESHOLD = 250f;

  /**
   * The deviation used to discount every player equally when aggregating a team rating; see
   * {@link #getTeamRatingContribution}. This is the median deviation of an established player
   * (measured 98.3 across all TAF ladders, 2026-09), so for a team of settled players the aggregate
   * is near-identical to the old per-player {@code sum(mean - 3*deviation)}.
   */
  private static final float TEAM_AGGREGATE_REFERENCE_DEVIATION = 100f;

  private RatingUtil() {
    // Utility class
  }

  /**
   * True when the player has too few rated games for their displayed rating to mean anything. Such
   * a player should be shown a placement marker rather than {@link #getRating}, which for a fresh
   * 1500/500 rating is 0 — read by everyone as "this player is terrible" rather than "unknown".
   */
  public static boolean isInPlacement(LeaderboardRating leaderboardRating) {
    return leaderboardRating != null && leaderboardRating.getDeviation() >= PLACEMENT_DEVIATION_THRESHOLD;
  }

  /**
   * Game count below which a player is treated as still in placement, for the surfaces that carry a
   * game count but not a deviation (the leaderboard table's API entries).
   * <p>
   * Chosen to agree with {@link #PLACEMENT_DEVIATION_THRESHOLD}: measured over 2,298 prod ratings
   * (2026-09) {@code totalGames < 8} classifies 94.1% of players identically to
   * {@code deviation >= 250}, the closest of any cutoff.
   */
  public static final int PLACEMENT_GAMES = 8;

  /**
   * True when the player has too few rated games for their displayed rating to mean anything. Use
   * the {@link #isInPlacement(LeaderboardRating) deviation overload} wherever a deviation is
   * available — it is what actually drives the number. This game-count form exists for the
   * leaderboard table, whose API entries carry {@code totalGames} but no deviation.
   */
  public static boolean isInPlacement(int totalGames) {
    return totalGames < PLACEMENT_GAMES;
  }

  /**
   * One player's contribution to a team aggregate: the mean discounted by a <em>fixed</em> reference
   * deviation instead of the player's own.
   * <p>
   * Summing each player's displayed {@code mean - 3*deviation} is the wrong aggregate, because the
   * rating engine compares teams on the sum of their <em>means</em> and applies no uncertainty
   * discount at all. A single unplaced player (deviation 500) subtracts 1500 from their team's
   * total and flips the comparison: measured over 28,360 rated prod games (2026-09), the old
   * aggregate ordered the two teams opposite to the engine on 18.0% of all games and on 53.6% of
   * games containing a debutant — worse than a coin flip. Discounting every player by the same
   * amount keeps the aggregate an affine function of the team's mean sum, which reproduces the
   * engine's ordering exactly (0.00% disagreement over the same corpus) while leaving a team of
   * settled players reading much as it did before.
   * <p>
   * Note this deliberately does <em>not</em> add variances ({@code sum(mu) - 3*sqrt(sum(sigma^2))}).
   * That is the correct conservative estimate of a team's strength, but it still disagreed with the
   * engine on 16.0% of games, because the engine is not being conservative when it rates.
   */
  public static double getTeamRatingContribution(LeaderboardRating leaderboardRating) {
    if (leaderboardRating == null) {
      return 0.0;
    }
    return leaderboardRating.getMean() - 3.0 * TEAM_AGGREGATE_REFERENCE_DEVIATION;
  }

  /** The aggregate rating shown for a whole team. See {@link #getTeamRatingContribution}. */
  public static int getTeamRating(List<LeaderboardRating> leaderboardRatings) {
    double total = 0.0;
    for (LeaderboardRating rating : leaderboardRatings) {
      total += getTeamRatingContribution(rating);
    }
    return (int) total;
  }

  public static int roundRatingToNextLowest100(double rating) {
    double ratingToBeRounded = rating < 0 ? rating - 100 : rating;
    return (int) (ratingToBeRounded / 100) * 100;
  }

  public static Integer getRoundedLeaderboardRating(Player player, String ratingType) {
    return getRoundedRating(getLeaderboardRating(player, ratingType));
  }

  public static Integer getRoundedLeaderboardRating(Player player, Leaderboard leaderboard) {
    return getRoundedLeaderboardRating(player, leaderboard.getTechnicalName());
  }

  public static int getRoundedRating(int rating) {
    return (rating + 50) / 100 * 100;
  }

  public static Integer getLeaderboardRating(Player player, String ratingType) {
    return Optional.of(player.getLeaderboardRatings())
        .map(rating -> rating.get(ratingType))
        .map(RatingUtil::getRating)
        .orElse(0);
  }

  public static Integer getLeaderboardRating(Player player, Leaderboard leaderboard) {
    return getLeaderboardRating(player, leaderboard.getTechnicalName());
  }

  public static int getRating(LeaderboardRating leaderboardRating) {
    return (int) (leaderboardRating.getMean() - 3f * leaderboardRating.getDeviation());
  }

  public static LeaderboardRating getAggregateRating(List<LeaderboardRating> leaderboardRatings) {
    double posteriorMean = 0.0;
    double posteriorPrecision = 0.0;
    int totalGames = 0;

    for (LeaderboardRating rating : leaderboardRatings) {
      double precision = 1.0 / rating.getDeviation() / rating.getDeviation();
      posteriorMean += rating.getMean() * precision;
      posteriorPrecision += precision;
      totalGames += rating.getNumberOfGames();
    }

    posteriorMean /= posteriorPrecision;
    double posteriorVariance = 1.0 / posteriorPrecision;

    if (totalGames > 0) {
      LeaderboardRating lbr = LeaderboardRating.create((float) posteriorMean, (float) Math.sqrt(posteriorVariance));
      lbr.setNumberOfGames(totalGames);
      return lbr;
    }
    else {
      return null;
    }
  }

  public static int getRating(double ratingMean, double ratingDeviation) {
    return (int) (ratingMean - 3f * ratingDeviation);
  }

  public static int getRating(RatingHistoryDataPoint datapoint) {
    return getRating(datapoint.getMean(), datapoint.getDeviation());
  }

  public static int getRating(Rating rating) {
    return getRating(rating.getMean(), rating.getDeviation());
  }
}
