package com.faforever.client.leaderboard;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a raw skill rating ({@code mean - 3*sigma}) into the stable display tier shown wherever a
 * player identity appears in RATINGS mode (LADDER_POINTS_DESIGN.md §13.2.3).
 *
 * <p>Two transforms stack:
 * <ol>
 *   <li><b>Bucketing</b> — round to the nearest {@link #BUCKET} (100) so the number reads as a
 *       skill <i>tier</i>, not a game-to-game delta (LP owns all movement).</li>
 *   <li><b>Hysteresis</b> — once a context is displaying a bucket, the raw rating must cross the
 *       bucket boundary by {@link #MARGIN} before it re-buckets. A player straddling 1550 then
 *       stops flickering 1500&harr;1600 game to game; only a genuine, sustained move re-tiers.</li>
 * </ol>
 *
 * <p>The remembered bucket is keyed per (player, rating type) and shared process-wide, so every
 * inline surface (chat list, team cards, user info) shows the <i>same</i> tier for a player at any
 * instant. State is session-scoped and intentionally not persisted — hysteresis only needs to
 * damp movement within a viewing session.
 */
@Lazy
@Service
public class RatingTierService {

  /** Display bucket width. Measured: width 100 preserves board discrimination (central 80% spans
   * 11-13 buckets) while erasing per-game movement (§13.2.3). */
  static final int BUCKET = 100;

  /** Hysteresis half-band beyond the natural boundary. Median per-game |delta mean| is ~10-13, so a
   * quarter-bucket margin comfortably exceeds a typical game yet still lets a real move re-tier. */
  static final int MARGIN = 25;

  private final Map<String, Integer> lastBucketByContext = new ConcurrentHashMap<>();

  /**
   * The display tier for {@code rawRating} in the given context, applying bucketing + hysteresis
   * against the last tier this context displayed. Updates the remembered tier as a side effect.
   *
   * @param playerId   the player whose rating is shown
   * @param ratingType the leaderboard technical name (distinct boards re-tier independently)
   * @param rawRating  the raw {@code mean - 3*sigma} rating
   */
  public int displayTier(int playerId, String ratingType, int rawRating) {
    String key = playerId + ":" + ratingType;
    return lastBucketByContext.compute(key, (k, prev) -> hysteresisBucket(rawRating, prev, BUCKET, MARGIN));
  }

  /** Forget all remembered tiers (e.g. on logout). */
  public void clear() {
    lastBucketByContext.clear();
  }

  /**
   * Pure hysteresis bucketing. With no previous bucket, returns the nearest bucket. Otherwise keeps
   * {@code prevBucket} while {@code raw} stays within {@code +/-(bucket/2 + margin)} of it, and only
   * then snaps to the nearest bucket of the new value.
   */
  static int hysteresisBucket(int raw, Integer prevBucket, int bucket, int margin) {
    int nearest = (int) (Math.round(raw / (double) bucket) * bucket);
    if (prevBucket == null) {
      return nearest;
    }
    int lower = prevBucket - bucket / 2 - margin;
    int upper = prevBucket + bucket / 2 + margin;
    if (raw >= lower && raw <= upper) {
      return prevBucket;
    }
    return nearest;
  }
}
