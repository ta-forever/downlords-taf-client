package com.faforever.client.ladder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Read access to the Ladder Points reward system (LADDER_POINTS_DESIGN.md), plus the
 * owner-writable featured-medal selection (CL-7). */
public interface LadderPointsService {

  /** A player's cumulative LP standings across all boards/seasons they have played, with the
   * division resolved from each season's thresholds. */
  CompletableFuture<List<SeasonStanding>> getStandingsForPlayer(int playerId);

  /** Like {@link #getStandingsForPlayer(int)} but short-TTL cached for inline surfaces (team cards,
   * game tooltips) that render whole rosters repeatedly. */
  CompletableFuture<List<SeasonStanding>> getStandingsForPlayerCached(int playerId);

  /** Batched, board-scoped standings for a whole team card: one query for the given players on the
   * one board (the game's rating type) instead of a per-player fetch, resolving only that exact
   * board's rank. Returns {@code playerId -> standing} for the players that have a placement there
   * (players with none are simply absent). Short-TTL cached per (player, board). */
  CompletableFuture<java.util.Map<Integer, SeasonStanding>> getStandingsForPlayersOnBoard(
      java.util.Collection<Integer> playerIds, String leaderboardTechnicalName);

  /** All started (current + previous) seasons for a board, newest first, for the season picker.
   * Empty when the board has no league or no scheduled season. */
  CompletableFuture<List<SeasonInfo>> getSeasons(String leaderboardTechnicalName);

  /** The season ladder for a specific season of a board — ranked rows (score desc). {@code count}/
   * {@code page} paginate. */
  CompletableFuture<List<SeasonStanding>> getSeasonLadder(String leaderboardTechnicalName, int seasonId, int count, int page);

  /** Podium tally (#1/#2/#3 finish counts per player) across all of a board's completed seasons,
   * for the Season Ladder hall of fame. Sorted gold-then-silver-then-bronze, descending. */
  CompletableFuture<List<HallOfFameEntry>> getHallOfFame(String leaderboardTechnicalName);

  /** The per-game LP/metrics/medals bundle for a finished game (score screen + replay detail). */
  CompletableFuture<GameLadderResult> getGameResult(int gameId);

  /** A player's Ladder Points progression on a board (cumulative LP over time, oldest-first), for
   * the User Info graph. Empty when the board has no LP league. */
  CompletableFuture<List<LpHistoryPoint>> getLpHistory(int playerId, String leaderboardTechnicalName);

  /** A player's seasonal medal counts, summed to all-time per medal code (medal case). */
  CompletableFuture<List<MedalCount>> getMedalCounts(int playerId);

  /** Total medals earned per player on a specific season (player id -> count), for the Season
   * Ladder medal column. One bulk fetch, aggregated. */
  CompletableFuture<java.util.Map<Integer, Long>> getSeasonMedalCounts(String leaderboardTechnicalName, int seasonId);

  /** The player's earned medals (career, count &gt; 0) across all types — LP, season placement and
   * tournament — for the avatar picker. Ordered by the canonical roster, code + multiplicity. */
  CompletableFuture<List<FeaturedMedalDisplay>> getEarnedMedals(int playerId);

  /** The medal code a player chose to display as their avatar (CL-7), or empty if none. */
  CompletableFuture<Optional<String>> getFeaturedMedal(int playerId);

  /** The player's chosen display medal + its career multiplicity, short-TTL cached for the chat
   * surfaces that render the same players many times (caches the empty result too). */
  CompletableFuture<Optional<FeaturedMedalDisplay>> getFeaturedMedalCached(int playerId);

  /** Synchronous read of the cached display medal for render paths that can't await (the chat
   * message WebView); returns empty and warms the cache in the background on a miss. */
  Optional<FeaturedMedalDisplay> peekFeaturedMedal(int playerId);

  /** Whether {@link #peekFeaturedMedal} just answered from a live cache entry, as opposed to
   * missing and kicking off a warm. Callers need this to tell "this player has no medal" (nothing
   * left to do) from "not resolved yet" (worth an async follow-up) — {@code peekFeaturedMedal}
   * returns empty for both. */
  boolean isFeaturedMedalCached(int playerId);

  /** Set (or clear, when {@code medalCode} is null) the caller's featured medal. Upserts the
   * single per-player row; owner-only (server-enforced). */
  CompletableFuture<Void> setFeaturedMedal(int playerId, String medalCode);
}
