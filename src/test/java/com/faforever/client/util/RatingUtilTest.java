package com.faforever.client.util;

import com.faforever.client.leaderboard.LeaderboardRating;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the team-card aggregate against the bug it was introduced to fix: summing each player's own
 * {@code mean - 3*deviation} lets one unplaced player invert which team the card shows as stronger,
 * because the rating engine compares teams on the sum of their <em>means</em> and applies no
 * uncertainty discount.
 */
public class RatingUtilTest {

  private static LeaderboardRating rating(float mean, float deviation) {
    return LeaderboardRating.create(mean, deviation);
  }

  @Test
  public void placementIsDetectedByDeviation() {
    // A brand-new TrueSkill rating: mean-3*deviation is 0, which is not a rating anyone should see.
    assertTrue(RatingUtil.isInPlacement(rating(1500f, 500f)));
    // A settled player. Prod p99 deviation at 20+ games is 201 and the observed maximum 220, so no
    // established player may ever be marked as in placement.
    assertFalse(RatingUtil.isInPlacement(rating(1500f, 220f)));
    assertFalse(RatingUtil.isInPlacement(rating(1500f, 100f)));
    // Boundary is inclusive.
    assertTrue(RatingUtil.isInPlacement(rating(1500f, RatingUtil.PLACEMENT_DEVIATION_THRESHOLD)));
    assertFalse(RatingUtil.isInPlacement(rating(1500f, RatingUtil.PLACEMENT_DEVIATION_THRESHOLD - 1f)));
    assertFalse(RatingUtil.isInPlacement(null));
  }

  @Test
  public void placementIsDetectedByGameCountWhereNoDeviationIsAvailable() {
    // The leaderboard table's API entries carry totalGames but no deviation. The cutoff is tuned to
    // agree with the deviation rule (94.1% identical classification on prod, 2026-09).
    assertTrue(RatingUtil.isInPlacement(0));
    assertTrue(RatingUtil.isInPlacement(1));
    assertTrue(RatingUtil.isInPlacement(RatingUtil.PLACEMENT_GAMES - 1));
    assertFalse(RatingUtil.isInPlacement(RatingUtil.PLACEMENT_GAMES));
    assertFalse(RatingUtil.isInPlacement(50));
  }

  @Test
  public void teamContributionDiscountsEveryPlayerEqually() {
    // Same mean, wildly different certainty -> same contribution. The old per-player displayed
    // rating would have differed by 1200 here.
    assertThat(RatingUtil.getTeamRatingContribution(rating(1500f, 100f)),
        is(RatingUtil.getTeamRatingContribution(rating(1500f, 500f))));
    assertThat(RatingUtil.getTeamRatingContribution(rating(1500f, 100f)), is(1200.0));
    assertThat(RatingUtil.getTeamRatingContribution(null), is(0.0));
  }

  /**
   * Game 192813 (prod, Escalation 5v5). The losing side held a mean sum of 7988 against 7163 — they
   * were the favourites — but one player was on their debut at 1500/500, so the old aggregate showed
   * 5341 against 5693 and made them look like underdogs, which is why their loss drew a larger
   * rating adjustment than a balanced game rather than a smaller one.
   */
  @Test
  public void aggregateOrdersTeamsTheWayTheRatingEngineDid() {
    List<LeaderboardRating> favourites = List.of(
        rating(1325.88f, 95.8458f),
        rating(1309.51f, 95.604f),
        rating(1911.96f, 98.5053f),
        rating(1940.94f, 91.6525f),
        rating(1500.00f, 500f));        // on debut
    List<LeaderboardRating> underdogs = List.of(
        rating(1014.50f, 99.3495f),
        rating(857.244f, 97.3424f),
        rating(2139.82f, 96.7013f),
        rating(1452.93f, 97.4569f),
        rating(1698.33f, 98.1804f));

    double favouriteMeanSum = favourites.stream().mapToDouble(LeaderboardRating::getMean).sum();
    double underdogMeanSum = underdogs.stream().mapToDouble(LeaderboardRating::getMean).sum();
    assertThat(underdogMeanSum, is(lessThan(favouriteMeanSum)));

    // The aggregate must agree with that ordering.
    assertThat(RatingUtil.getTeamRating(underdogs), is(lessThan(RatingUtil.getTeamRating(favourites))));

    // The old aggregate did not, which is the regression being pinned.
    int oldFavourites = favourites.stream().mapToInt(RatingUtil::getRating).sum();
    int oldUnderdogs = underdogs.stream().mapToInt(RatingUtil::getRating).sum();
    assertThat(oldUnderdogs, is(greaterThan(oldFavourites)));
  }

  @Test
  public void aggregateBarelyMovesForATeamOfSettledPlayers() {
    // Deviation 100 is the reference, so a settled team's aggregate should land within a few points
    // of the old sum — the change must not visibly re-scale ordinary games.
    List<LeaderboardRating> team = List.of(
        rating(1325f, 96f), rating(1309f, 95f), rating(1911f, 98f), rating(1940f, 92f), rating(1698f, 98f));
    int old = team.stream().mapToInt(RatingUtil::getRating).sum();
    assertThat(Math.abs(RatingUtil.getTeamRating(team) - old), is(lessThan(100)));
  }

  @Test
  public void anUnplacedPlayerRaisesTheirTeamsAggregateRatherThanZeroingIt() {
    List<LeaderboardRating> withDebutant = List.of(rating(1500f, 100f), rating(1500f, 500f));
    // Old behaviour contributed 0 for the debutant; now they contribute their mean.
    assertThat(RatingUtil.getTeamRating(withDebutant), is(2400));
    assertThat(withDebutant.stream().mapToInt(RatingUtil::getRating).sum(), is(1200));
  }
}
