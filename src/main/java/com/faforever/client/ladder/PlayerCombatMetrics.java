package com.faforever.client.ladder;

import lombok.Value;

import javax.annotation.Nullable;

/** Per-player per-game combat metrics (Combat Score #1). {@code regime} says which fields are
 * trustworthy: "generative" = all; "costfree" = no destroyedValue/dvRatio. */
@Value
public class PlayerCombatMetrics {
  int playerId;
  @Nullable String playerLogin;
  String regime;
  @Nullable Integer destroyedValue;
  @Nullable Float dvRatio;
  @Nullable Integer econValue;
  @Nullable Integer taKills;
  @Nullable Integer taLosses;
  @Nullable Long dmgDealt;
  @Nullable Long lastActTick;
  @Nullable Long gameMaxTick;
  @Nullable Byte team;
}
