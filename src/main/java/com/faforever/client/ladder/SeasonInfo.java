package com.faforever.client.ladder;

import lombok.Value;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;

/** The active season window for a leaderboard, shown above the Season Ladder so players know which
 * season the standings belong to and when it resets. */
@Value
public class SeasonInfo {
  int seasonId;
  @Nullable OffsetDateTime from;
  @Nullable OffsetDateTime to;
  @Nullable String description;
}
