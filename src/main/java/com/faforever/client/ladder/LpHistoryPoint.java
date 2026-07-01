package com.faforever.client.ladder;

import lombok.Value;

import java.time.OffsetDateTime;

/** One point on a player's Ladder Points progression for a board: cumulative LP ({@code score})
 * right after a game at {@code instant}. Spans seasons, so a season reset shows as a drop. */
@Value
public class LpHistoryPoint {
  OffsetDateTime instant;
  int score;
}
