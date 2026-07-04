package com.faforever.client.api;

import com.faforever.client.api.dto.AchievementDefinition;
import com.faforever.client.api.dto.Clan;
import com.faforever.client.api.dto.CoopMission;
import com.faforever.client.api.dto.CoopResult;
import com.faforever.client.api.dto.FeaturedModFile;
import com.faforever.client.api.dto.Game;
import com.faforever.client.api.dto.GameMedal;
import com.faforever.client.api.dto.GamePlayerMetrics;
import com.faforever.client.api.dto.GameReview;
import com.faforever.client.api.dto.LadderPoints;
import com.faforever.client.api.dto.LadderPointsJournal;
import com.faforever.client.api.dto.League;
import com.faforever.client.api.dto.LeagueSchedule;
import com.faforever.client.api.dto.Leaderboard;
import com.faforever.client.api.dto.PlayerMedalSummary;
import com.faforever.client.api.dto.LeaderboardEntry;
import com.faforever.client.api.dto.LeaderboardRatingJournal;
import com.faforever.client.api.dto.Map;
import com.faforever.client.api.dto.MapPoolAssignment;
import com.faforever.client.api.dto.MapVersion;
import com.faforever.client.api.dto.MapVersionReview;
import com.faforever.client.api.dto.MatchmakerQueue;
import com.faforever.client.api.dto.MatchmakerQueueMapPool;
import com.faforever.client.api.dto.MeResult;
import com.faforever.client.api.dto.Mod;
import com.faforever.client.api.dto.ModVersion;
import com.faforever.client.api.dto.ModVersionReview;
import com.faforever.client.api.dto.ModerationReport;
import com.faforever.client.api.dto.Player;
import com.faforever.client.api.dto.PlayerAchievement;
import com.faforever.client.api.dto.PlayerEvent;
import com.faforever.client.api.dto.PlayerTournamentSummary;
import com.faforever.client.api.dto.Tournament;
import com.faforever.client.api.dto.TutorialCategory;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.util.Tuple;
import com.faforever.client.vault.search.SearchController.SearchConfig;
import com.faforever.client.vault.search.SearchController.SortConfig;
import com.faforever.commons.io.ByteCountListener;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Provides access to the FAF REST API. Services should not access this class directly, but use {@link
 * com.faforever.client.remote.FafService} instead.
 */
public interface FafApiAccessor {

  List<PlayerAchievement> getPlayerAchievements(int playerId);

  List<PlayerEvent> getPlayerEvents(int playerId);

  List<AchievementDefinition> getAchievementDefinitions();

  AchievementDefinition getAchievementDefinition(String achievementId);

  void authorize(int playerId, String username, String password);

  String getAccessToken();

  List<Mod> getMods();

  List<com.faforever.client.api.dto.FeaturedMod> getFeaturedMods();

  List<com.faforever.client.api.dto.MapPool> getMapPools();

  List<Leaderboard> getLeaderboards();

  List<LeaderboardEntry> getAllLeaderboardEntries(String leaderboardTechnicalName);

  Tuple<List<LeaderboardEntry>, java.util.Map<String, ?>> getLeaderboardEntriesWithMeta(String leaderboardTechnicalName, int count, int page);

  List<LeaderboardEntry> getLeaderboardEntriesForPlayer(int playerId);

  List<LeaderboardRatingJournal> getRatingJournal(int playerId, int leaderboardId);

  // --- Ladder Points (LADDER_POINTS_DESIGN.md) -------------------------------
  List<League> getLpLeagues();

  List<LeagueSchedule> getLpSeasons(int leagueId);

  /** Within-board rank for a given score on a league+season: 1 + the number of players who score
   * strictly higher. Cheap (a filtered count), so it can back per-player rank display. */
  int getLadderRank(int leagueId, int seasonId, int score);

  /** A player's cumulative LP standings this player has on any board (current + past seasons). */
  List<LadderPoints> getLadderPointsForPlayer(int playerId);

  /** LP rows for a set of players on one league only (all seasons they hold points in), for the
   * batched team-card rank lookup — one request for a whole roster instead of a per-player fetch,
   * scoped to the game's board. Includes player/league/leaderboard/season. */
  List<LadderPoints> getLadderPointsForPlayersOnLeague(java.util.Collection<Integer> playerIds, int leagueId);

  /** Top-N season standings for a board's league + season, ordered by score desc. */
  Tuple<List<LadderPoints>, java.util.Map<String, ?>> getLadderPointsWithMeta(int leagueId, int seasonId, int count, int page);

  /** Per-game LP award + breakdown for a single game (replay card / Combat Score → LP chain). */
  List<LadderPointsJournal> getLadderPointsJournal(int gameId);

  /** A player's LP journal entries for one league (board), oldest-first, for the LP progression
   * graph. Spans seasons (score resets show as drops at season boundaries). */
  List<LadderPointsJournal> getLadderPointsJournalForPlayer(int playerId, int leagueId);

  // --- live-game wagering (read-only; wager_service/WAGER_DESIGN.md §11) ---

  /** Open (+ recently closed) wager markets across all live games, with outcomes — the watchlist. */
  List<com.faforever.client.api.dto.WagerMarket> getOpenWagerMarkets();

  /** All wager markets for one game, with outcomes (cold snapshot before the WS feed attaches). */
  List<com.faforever.client.api.dto.WagerMarket> getWagerMarketsForGame(int gameId);

  /** A player's net wager positions (portfolio), with outcome + market. */
  List<com.faforever.client.api.dto.WagerPosition> getWagerPositionsForPlayer(int playerId);

  /** Recent trades for a whole market (both outcomes), oldest-first — the price-chart source
   * (a 2-team outcome's price also moves when the OTHER team is traded). */
  List<com.faforever.client.api.dto.WagerTrade> getWagerTradesForMarket(long marketId, int count);

  /** Per-player combat metrics for a single game (replay detail #1). */
  List<GamePlayerMetrics> getGamePlayerMetrics(int gameId);

  /** Medals awarded in a single game (replay detail #3). */
  List<GameMedal> getGameMedals(int gameId);

  /** A player's seasonal medal counts (medal case). */
  List<PlayerMedalSummary> getPlayerMedalSummary(int playerId);

  /** All players' medal counts for one season (league_schedule id), for the Season Ladder medal
   * column. One bulk call, aggregated client-side. */
  List<PlayerMedalSummary> getMedalSummaryForSeason(int seasonId);

  /** The medal a player chose to display next to their name (CL-7); empty if none set. */
  List<com.faforever.client.api.dto.PlayerFeaturedMedal> getFeaturedMedal(int playerId);

  /** Create the caller's featured-medal row (first selection). Owner-only (server-enforced). */
  com.faforever.client.api.dto.PlayerFeaturedMedal createFeaturedMedal(com.faforever.client.api.dto.PlayerFeaturedMedal featuredMedal);

  /** Update the caller's featured-medal row (change selection). Owner-only (server-enforced). */
  void updateFeaturedMedal(String id, com.faforever.client.api.dto.PlayerFeaturedMedal featuredMedal);

  /** Delete the caller's featured-medal row (clear selection). Owner-only (server-enforced). */
  void deleteFeaturedMedal(String id);

  Tuple<List<Map>, java.util.Map<String, ?>> getMapsByIdWithMeta(List<Integer> mapIdList, int count, int page);

  Tuple<List<Map>, java.util.Map<String, ?>> getMostPlayedMapsWithMeta(int count, int page);

  Tuple<List<Map>, java.util.Map<String, ?>> getHighestRatedMapsWithMeta(int count, int page);

  Tuple<List<Map>, java.util.Map<String, ?>> getNewestMapsWithMeta(int count, int page);

  List<Game> getLastGamesOnMap(int playerId, String mapVersionId, int count);

  void uploadMod(Path file, ByteCountListener listener);

  void uploadMap(Path file, boolean isRanked, List<java.util.Map<String,String>> mapDetails, ByteCountListener listener) throws IOException;

  void uploadGameLogs(Path file, String context, int id, ByteCountListener listener);

  List<CoopMission> getCoopMissions();

  List<CoopResult> getCoopLeaderboard(String missionId, int numberOfPlayers);

  void changePassword(String username, String currentPasswordHash, String newPasswordHash) throws IOException;

  ModVersion getModVersion(String uid);

  List<FeaturedModFile> getFeaturedModFiles(FeaturedMod featuredMod, Integer version);

  Tuple<List<Game>, java.util.Map<String, ?>> getNewestReplaysWithMeta(int count, int page);

  Tuple<List<Game>, java.util.Map<String, ?>> getHighestRatedReplaysWithMeta(int count, int page);

  Tuple<List<Game>, java.util.Map<String, ?>> findReplaysByQueryWithMeta(String query, int maxResults, int page, SortConfig sortConfig);

  Optional<MapVersion> findMapByTaDemoMapHash(String taDemoMapHash);

  List<MapVersion> findMapsByName(String mapDisplayName, int count, boolean includeHidden);

  List<Player> getPlayersByIds(Collection<Integer> playerIds);

  Optional<Player> queryPlayerByName(String playerName);

  /** Wildcard player search by login prefix. Used by the team-tournament
   *  invite UI to autocomplete offline player names. Limit caps the result
   *  set so we don't pull thousands of "player1234" hits. */
  List<Player> findPlayersByLoginPrefix(String prefix, int limit);

  List<com.faforever.client.api.dto.TournamentTeam> getTournamentTeams(int tournamentId);

  List<com.faforever.client.api.dto.TournamentTeamInvite> getPendingInvitesForPlayer(int playerId);

  GameReview createGameReview(GameReview review);

  void updateGameReview(GameReview review);

  ModVersionReview createModVersionReview(ModVersionReview review);

  void updateModVersionReview(ModVersionReview review);

  MapVersionReview createMapVersionReview(MapVersionReview review);

  void updateMapVersionReview(MapVersionReview review);

  void deleteGameReview(String id);

  List<TutorialCategory> getTutorialCategories();

  void updateReplay(String id, Game game);

  Optional<Clan> getClanByTag(String tag);

  Tuple<List<Map>, java.util.Map<String, ?>> findMapsByQueryWithMeta(SearchConfig searchConfig, int count, int page);

  Optional<MapVersion> findMapVersionById(String id);

  void deleteMapVersionReview(String id);

  void deleteModVersionReview(String id);

  Optional<Game> findReplayById(int id);

  Tuple<List<Mod>, java.util.Map<String, ?>> findModsByQueryWithMeta(SearchConfig query, int maxResults, int page);

  List<MatchmakerQueueMapPool> getMatchmakerQueueMapPools();

  List<MapPoolAssignment> getMatchmakerPoolMaps(int matchmakerQueueId, float rating);

  List<Map> getAllRankedMaps();

  Optional<MatchmakerQueue> getMatchmakerQueue(String technicalName);

  List<MatchmakerQueue> getMatchmakerQueuesByMod(String modTechnicalName);

  List<Tournament> getAllTournaments();

  Tournament getTournamentById(String id);

  /**
   * Hall of Fame: aggregated tournament achievements per player. If
   * {@code featuredModId} is null, returns the across-all-mods rollup rows
   * (one per player). If non-null, returns per-player rows for that one mod.
   */
  List<PlayerTournamentSummary> getHallOfFame(Integer featuredModId);

  /** Per-player tournament summary: all (player, mod) rows for one player. */
  List<PlayerTournamentSummary> getPlayerTournamentSummary(int playerId);

  /**
   * Returns the list of game IDs the player has played in completed
   * tournaments, optionally restricted to a featured mod. Used by the Hall
   * of Fame "View Replays" context menu.
   */
  List<Integer> getPlayerTournamentGameIds(int playerId, Integer featuredModId);

  List<ModerationReport> getPlayerModerationReports(int playerId);

  void postModerationReport(com.faforever.client.reporting.ModerationReport report);

  Tuple<List<MapVersion>, java.util.Map<String, ?>> getOwnedMapsWithMeta(int playerId, int loadMoreCount, int page);

  void updateMapVersion(String id, MapVersion mapVersion);

  MeResult getOwnPlayer();
}
