package com.faforever.client.leaderboard;

import org.junit.Test;

import static com.faforever.client.leaderboard.RatingTierService.BUCKET;
import static com.faforever.client.leaderboard.RatingTierService.MARGIN;
import static com.faforever.client.leaderboard.RatingTierService.hysteresisBucket;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RatingTierServiceTest {

  @Test
  public void firstSightRoundsToNearestBucket() {
    assertThat(hysteresisBucket(1540, null, BUCKET, MARGIN), is(1500));
    assertThat(hysteresisBucket(1560, null, BUCKET, MARGIN), is(1600));
    assertThat(hysteresisBucket(1550, null, BUCKET, MARGIN), is(1600)); // .5 rounds up
  }

  @Test
  public void negativeRatingsRoundCorrectly() {
    assertThat(hysteresisBucket(-120, null, BUCKET, MARGIN), is(-100));
    assertThat(hysteresisBucket(-40, null, BUCKET, MARGIN), is(0));
  }

  @Test
  public void straddlingABoundaryDoesNotFlicker() {
    // Displaying 1500; small moves around the 1550 boundary must NOT re-tier.
    assertThat(hysteresisBucket(1560, 1500, BUCKET, MARGIN), is(1500));
    assertThat(hysteresisBucket(1574, 1500, BUCKET, MARGIN), is(1500)); // 1500 + 50 + 25 boundary
    assertThat(hysteresisBucket(1540, 1500, BUCKET, MARGIN), is(1500));
  }

  @Test
  public void aSustainedMoveBeyondTheMarginReTiers() {
    // Past the upper hysteresis band (1575) it snaps to the new nearest bucket.
    assertThat(hysteresisBucket(1576, 1500, BUCKET, MARGIN), is(1600));
    // Symmetrically downward, past the lower band (1425).
    assertThat(hysteresisBucket(1420, 1500, BUCKET, MARGIN), is(1400));
  }

  @Test
  public void serviceRemembersTierPerContextAndDampsMovement() {
    RatingTierService service = new RatingTierService();
    assertThat(service.displayTier(7, "ladder1v1", 1540), is(1500)); // first sight -> nearest
    assertThat(service.displayTier(7, "ladder1v1", 1560), is(1500)); // damped: stays in tier
    assertThat(service.displayTier(7, "ladder1v1", 1700), is(1700)); // big move -> re-tier
    // A different board for the same player tracks independently.
    assertThat(service.displayTier(7, "ladder2v2", 1240), is(1200));
  }
}
