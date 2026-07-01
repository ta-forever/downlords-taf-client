package com.faforever.client.ladder;

import lombok.Value;

import javax.annotation.Nullable;

/** A player's podium tally across a board's completed seasons — how many times they finished #1
 * ({@code gold}), #2 ({@code silver}) and #3 ({@code bronze}). Drives the Season Ladder hall of fame. */
@Value
public class HallOfFameEntry {
  int playerId;
  @Nullable String playerLogin;
  int gold;
  int silver;
  int bronze;
}
