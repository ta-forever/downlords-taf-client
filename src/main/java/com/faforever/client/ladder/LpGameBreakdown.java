package com.faforever.client.ladder;

import lombok.Value;

import javax.annotation.Nullable;

/** Per-game LP award + breakdown for one player (the Combat Score → LP chain, design §13.7).
 * {@code combatBase + winBonus + upsetBonus == lpAwarded} (PENALTY=0). */
@Value
public class LpGameBreakdown {
  int playerId;
  @Nullable String playerLogin;
  int gameId;
  int combatBase;
  int winBonus;
  int upsetBonus;
  int lpAwarded;
  int scoreAfter;
  @Nullable Integer destroyedValue;
  @Nullable Float dvRatio;
}
