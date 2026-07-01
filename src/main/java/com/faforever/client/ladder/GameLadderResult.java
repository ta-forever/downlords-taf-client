package com.faforever.client.ladder;

import lombok.Value;

import java.util.List;

/** Everything LP-related for one finished game — the per-game reward bundle shown on the
 * post-game score screen and replay detail. */
@Value
public class GameLadderResult {
  int gameId;
  List<LpGameBreakdown> breakdowns;
  List<PlayerCombatMetrics> metrics;
  List<GameMedalAward> medals;

  public boolean isEmpty() {
    return breakdowns.isEmpty() && metrics.isEmpty() && medals.isEmpty();
  }
}
