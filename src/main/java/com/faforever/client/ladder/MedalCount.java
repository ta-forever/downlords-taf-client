package com.faforever.client.ladder;

import lombok.Value;

import javax.annotation.Nullable;

/** A player's count of one medal code (optionally scoped to a season/board) — medal case input. */
@Value
public class MedalCount {
  String code;
  @Nullable Integer season;
  @Nullable Integer leaderboardId;
  long count;
}
