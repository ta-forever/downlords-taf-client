package com.faforever.client.ladder;

import lombok.Value;

import javax.annotation.Nullable;

/** A player's cumulative LP standing on one board for one season, with their within-board rank.
 * Used both for a player's own standings and for ladder rows. */
@Value
public class SeasonStanding {
  int playerId;
  @Nullable String playerLogin;
  String leaderboardTechnicalName;
  int leagueId;
  int seasonId;
  int score;
  int games;
  /** Within-board rank this season (1 = top); 0 = unranked / unknown. */
  int rank;
  // --- per-season result stats (V138), for the Season Ladder columns (design §13) ---
  int wins;
  int draws;
  int losses;
  /** Signed running streak: positive = win streak, negative = loss streak. */
  int currentStreak;
  /** Longest win streak this season. */
  int bestStreak;
  /** Last <=10 results, oldest-first, chars W/D/L. */
  String recentResults;
  /** Cumulative wager P&amp;L already folded into {@link #score} (V137, Option B); the
   * gambling-portion breakdown for the "Wager P&amp;L" column. score - wagerNet = game-earned LP. */
  int wagerNet;

  /** Season win rate in [0,1]; 0 when no decided games. */
  public float getWinRate() {
    int decided = wins + draws + losses;
    return decided == 0 ? 0f : (float) wins / decided;
  }
}
