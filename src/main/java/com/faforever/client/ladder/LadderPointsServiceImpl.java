package com.faforever.client.ladder;

import com.faforever.client.FafClientApplication;
import com.faforever.client.api.FafApiAccessor;
import com.faforever.client.api.dto.GameMedal;
import com.faforever.client.api.dto.GamePlayerMetrics;
import com.faforever.client.api.dto.LadderPoints;
import com.faforever.client.api.dto.LadderPointsJournal;
import com.faforever.client.api.dto.Player;
import com.faforever.client.api.dto.PlayerMedalSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Lazy
@Service
@Slf4j
@Profile("!" + FafClientApplication.PROFILE_OFFLINE)
@RequiredArgsConstructor
public class LadderPointsServiceImpl implements LadderPointsService {

  private final FafApiAccessor fafApiAccessor;
  private final ExecutorService executorService;

  /** Inline-surface cache for featured medals (chat list / team cards). Caches the empty result
   * too so players without a featured medal aren't re-queried per cell. */
  private static final long FEATURED_MEDAL_TTL_MILLIS = 5 * 60 * 1000L;
  private final java.util.Map<Integer, CachedFeaturedMedal> featuredMedalCache = new java.util.concurrent.ConcurrentHashMap<>();

  private record CachedFeaturedMedal(Optional<FeaturedMedalDisplay> medal, long expiresAt) {}

  /** Inline-surface cache for player standings (team cards / game tooltips render rosters often). */
  private static final long STANDINGS_TTL_MILLIS = 2 * 60 * 1000L;
  private final java.util.Map<Integer, CachedStandings> standingsCache = new java.util.concurrent.ConcurrentHashMap<>();

  private record CachedStandings(List<SeasonStanding> standings, long expiresAt) {}

  /** Board-scoped standings cache ((player, league) -> standing) for the batched team-card lookup;
   * caches the "no placement" result too, so a player without a rank on the board isn't re-queried
   * per render. */
  private final java.util.Map<String, CachedBoardStanding> boardStandingCache = new java.util.concurrent.ConcurrentHashMap<>();

  private record CachedBoardStanding(Optional<SeasonStanding> standing, long expiresAt) {}

  private static String boardCacheKey(int playerId, int leagueId) {
    return playerId + ":" + leagueId;
  }

  @Override
  public CompletableFuture<List<SeasonStanding>> getStandingsForPlayer(int playerId) {
    return CompletableFuture.supplyAsync(() -> fafApiAccessor.getLadderPointsForPlayer(playerId).stream()
        .map(this::toStanding)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toList()), executorService);
  }

  @Override
  public CompletableFuture<List<SeasonStanding>> getStandingsForPlayerCached(int playerId) {
    CachedStandings cached = standingsCache.get(playerId);
    if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
      return CompletableFuture.completedFuture(cached.standings());
    }
    return getStandingsForPlayer(playerId).thenApply(standings -> {
      standingsCache.put(playerId,
          new CachedStandings(standings, System.currentTimeMillis() + STANDINGS_TTL_MILLIS));
      return standings;
    });
  }

  @Override
  public CompletableFuture<java.util.Map<Integer, SeasonStanding>> getStandingsForPlayersOnBoard(
      java.util.Collection<Integer> playerIds, String leaderboardTechnicalName) {
    if (playerIds == null || playerIds.isEmpty() || leaderboardTechnicalName == null) {
      return CompletableFuture.completedFuture(java.util.Map.of());
    }
    return CompletableFuture.supplyAsync(() -> {
      Optional<Integer> leagueIdOpt = leagueIdFor(leaderboardTechnicalName);
      if (leagueIdOpt.isEmpty()) {
        return java.util.Map.<Integer, SeasonStanding>of();
      }
      int leagueId = leagueIdOpt.get();
      long now = System.currentTimeMillis();
      java.util.Map<Integer, SeasonStanding> result = new HashMap<>();
      List<Integer> missing = new java.util.ArrayList<>();
      for (Integer id : playerIds) {
        CachedBoardStanding cached = boardStandingCache.get(boardCacheKey(id, leagueId));
        if (cached != null && cached.expiresAt() > now) {
          cached.standing().ifPresent(s -> result.put(id, s));
        } else {
          missing.add(id);
        }
      }
      if (missing.isEmpty()) {
        return result;
      }
      // One batched request for the whole roster's rows on this board; keep the latest season each
      // player holds points in (their current standing).
      java.util.Map<Integer, LadderPoints> latestByPlayer = new HashMap<>();
      for (LadderPoints lp : fafApiAccessor.getLadderPointsForPlayersOnLeague(missing, leagueId)) {
        if (lp.getPlayer() == null || lp.getSeason() == null) {
          continue;
        }
        int pid = Integer.parseInt(lp.getPlayer().getId());
        LadderPoints prev = latestByPlayer.get(pid);
        if (prev == null
            || Integer.parseInt(lp.getSeason().getId()) > Integer.parseInt(prev.getSeason().getId())) {
          latestByPlayer.put(pid, lp);
        }
      }
      // Resolve the within-board rank once per distinct (season, score) — ties share the count query.
      java.util.Map<String, Integer> rankByScore = new HashMap<>();
      for (Integer id : missing) {
        LadderPoints lp = latestByPlayer.get(id);
        Optional<SeasonStanding> standing = Optional.empty();
        if (lp != null) {
          int seasonId = Integer.parseInt(lp.getSeason().getId());
          int rank = rankByScore.computeIfAbsent(seasonId + ":" + lp.getScore(),
              k -> fafApiAccessor.getLadderRank(leagueId, seasonId, lp.getScore()));
          SeasonStanding s = standing(lp, leaderboardTechnicalName, leagueId, seasonId, rank);
          standing = Optional.of(s);
          result.put(id, s);
        }
        boardStandingCache.put(boardCacheKey(id, leagueId),
            new CachedBoardStanding(standing, now + STANDINGS_TTL_MILLIS));
      }
      return result;
    }, executorService);
  }

  @Override
  public CompletableFuture<List<SeasonInfo>> getSeasons(String leaderboardTechnicalName) {
    return CompletableFuture.supplyAsync(() -> {
      Optional<Integer> leagueId = leagueIdFor(leaderboardTechnicalName);
      if (leagueId.isEmpty()) {
        return List.<SeasonInfo>of();
      }
      OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
      // getLpSeasons is sorted newest-first; keep only seasons that have already begun (current +
      // previous), so the picker never offers a season with no games yet.
      return fafApiAccessor.getLpSeasons(leagueId.get()).stream()
          .filter(s -> s.getTimeframeFrom() != null && !now.isBefore(s.getTimeframeFrom()))
          .map(s -> new SeasonInfo(Integer.parseInt(s.getId()),
              s.getTimeframeFrom(), s.getTimeframeTo(), s.getDescription()))
          .collect(Collectors.toList());
    }, executorService);
  }

  @Override
  public CompletableFuture<List<SeasonStanding>> getSeasonLadder(String leaderboardTechnicalName, int seasonId, int count, int page) {
    return CompletableFuture.supplyAsync(() -> {
      Optional<Integer> leagueId = leagueIdFor(leaderboardTechnicalName);
      if (leagueId.isEmpty()) {
        return List.<SeasonStanding>of();
      }
      // The page is score-sorted desc, so rank is the 1-based position within the page.
      List<LadderPoints> rows = fafApiAccessor.getLadderPointsWithMeta(leagueId.get(), seasonId, count, page).getFirst();
      int base = (page - 1) * count;
      List<SeasonStanding> standings = new java.util.ArrayList<>(rows.size());
      for (int i = 0; i < rows.size(); i++) {
        standings.add(standing(rows.get(i), leaderboardTechnicalName, leagueId.get(), seasonId, base + i + 1));
      }
      return standings;
    }, executorService);
  }

  @Override
  public CompletableFuture<GameLadderResult> getGameResult(int gameId) {
    return CompletableFuture.supplyAsync(() -> {
      List<LpGameBreakdown> breakdowns = fafApiAccessor.getLadderPointsJournal(gameId).stream()
          .map(this::toBreakdown).collect(Collectors.toList());
      List<PlayerCombatMetrics> metrics = fafApiAccessor.getGamePlayerMetrics(gameId).stream()
          .map(this::toMetrics).collect(Collectors.toList());
      List<GameMedalAward> medals = fafApiAccessor.getGameMedals(gameId).stream()
          .map(this::toMedal).collect(Collectors.toList());
      return new GameLadderResult(gameId, breakdowns, metrics, medals);
    }, executorService);
  }

  @Override
  public CompletableFuture<List<LpHistoryPoint>> getLpHistory(int playerId, String leaderboardTechnicalName) {
    return CompletableFuture.supplyAsync(() -> {
      Optional<Integer> leagueId = leagueIdFor(leaderboardTechnicalName);
      if (leagueId.isEmpty()) {
        return List.<LpHistoryPoint>of();
      }
      return fafApiAccessor.getLadderPointsJournalForPlayer(playerId, leagueId.get()).stream()
          .filter(j -> j.getCreateTime() != null)
          .map(j -> new LpHistoryPoint(j.getCreateTime(), j.getScoreAfter()))
          .collect(Collectors.toList());
    }, executorService);
  }

  @Override
  public CompletableFuture<List<MedalCount>> getMedalCounts(int playerId) {
    return CompletableFuture.supplyAsync(() -> fafApiAccessor.getPlayerMedalSummary(playerId).stream()
        .map(s -> new MedalCount(s.getMedalCode(), s.getSeason(), s.getLeaderboardId(), s.getCnt()))
        .collect(Collectors.toList()), executorService);
  }

  @Override
  public CompletableFuture<java.util.Map<Integer, Long>> getSeasonMedalCounts(String leaderboardTechnicalName, int seasonId) {
    return CompletableFuture.supplyAsync(() -> fafApiAccessor.getMedalSummaryForSeason(seasonId).stream()
        .filter(s -> s.getPlayer() != null)
        .collect(Collectors.groupingBy(s -> Integer.parseInt(s.getPlayer().getId()),
            Collectors.summingLong(PlayerMedalSummary::getCnt))), executorService);
  }

  @Override
  public CompletableFuture<List<HallOfFameEntry>> getHallOfFame(String leaderboardTechnicalName) {
    return CompletableFuture.supplyAsync(() -> {
      Optional<Integer> leagueId = leagueIdFor(leaderboardTechnicalName);
      if (leagueId.isEmpty()) {
        return List.<HallOfFameEntry>of();
      }
      OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
      java.util.Map<Integer, int[]> podiums = new HashMap<>();   // playerId -> [gold, silver, bronze]
      java.util.Map<Integer, String> logins = new HashMap<>();
      for (var season : fafApiAccessor.getLpSeasons(leagueId.get())) {
        // Only completed seasons count toward the hall of fame; the live one is still being decided.
        if (season.getTimeframeTo() == null || !now.isAfter(season.getTimeframeTo())) {
          continue;
        }
        int seasonId = Integer.parseInt(season.getId());
        List<LadderPoints> top = fafApiAccessor.getLadderPointsWithMeta(leagueId.get(), seasonId, 3, 1).getFirst();
        for (int place = 0; place < top.size() && place < 3; place++) {
          LadderPoints lp = top.get(place);
          if (lp.getPlayer() == null) {
            continue;
          }
          int playerId = Integer.parseInt(lp.getPlayer().getId());
          logins.putIfAbsent(playerId, lp.getPlayer().getLogin());
          podiums.computeIfAbsent(playerId, k -> new int[3])[place]++;
        }
      }
      return podiums.entrySet().stream()
          .map(e -> new HallOfFameEntry(e.getKey(), logins.get(e.getKey()),
              e.getValue()[0], e.getValue()[1], e.getValue()[2]))
          .sorted(Comparator.comparingInt(HallOfFameEntry::getGold)
              .thenComparingInt(HallOfFameEntry::getSilver)
              .thenComparingInt(HallOfFameEntry::getBronze).reversed())
          .collect(Collectors.toList());
    }, executorService);
  }

  /** League id for a board's leaderboard technical name, if it has a league. */
  private Optional<Integer> leagueIdFor(String leaderboardTechnicalName) {
    return fafApiAccessor.getLpLeagues().stream()
        .filter(l -> l.getLeaderboard() != null
            && leaderboardTechnicalName.equals(l.getLeaderboard().getTechnicalName()))
        .findFirst()
        .map(l -> Integer.parseInt(l.getId()));
  }

  @Override
  public CompletableFuture<List<FeaturedMedalDisplay>> getEarnedMedals(int playerId) {
    return CompletableFuture.supplyAsync(() -> {
      java.util.Map<String, Long> byCode = new java.util.HashMap<>();
      for (PlayerMedalSummary s : fafApiAccessor.getPlayerMedalSummary(playerId)) {
        byCode.merge(s.getMedalCode(), s.getCnt(), Long::sum);
      }
      long gold = 0, silver = 0, bronze = 0;
      for (var s : fafApiAccessor.getPlayerTournamentSummary(playerId)) {
        if (s.getFeaturedMod() == null) {
          continue;   // skip the grand-total rollup row
        }
        gold += s.getFirsts();
        silver += s.getSeconds();
        bronze += s.getThirds();
      }
      if (gold > 0) byCode.put(LadderUiUtil.TOURNAMENT_GOLD, gold);
      if (silver > 0) byCode.put(LadderUiUtil.TOURNAMENT_SILVER, silver);
      if (bronze > 0) byCode.put(LadderUiUtil.TOURNAMENT_BRONZE, bronze);

      List<String> order = new java.util.ArrayList<>();
      order.addAll(LadderUiUtil.MEDAL_CODES);
      order.addAll(LadderUiUtil.SEASON_MEDAL_CODES);
      order.addAll(LadderUiUtil.TOURNAMENT_MEDAL_CODES);
      byCode.keySet().stream().filter(c -> !order.contains(c)).sorted().forEach(order::add);

      List<FeaturedMedalDisplay> earned = new java.util.ArrayList<>();
      for (String code : order) {
        long n = byCode.getOrDefault(code, 0L);
        if (n > 0) {
          earned.add(new FeaturedMedalDisplay(code, n));
        }
      }
      return earned;
    }, executorService);
  }

  @Override
  public CompletableFuture<Optional<String>> getFeaturedMedal(int playerId) {
    return CompletableFuture.supplyAsync(() -> fafApiAccessor.getFeaturedMedal(playerId).stream()
        .findFirst()
        .map(com.faforever.client.api.dto.PlayerFeaturedMedal::getMedalCode), executorService);
  }

  @Override
  public CompletableFuture<Optional<FeaturedMedalDisplay>> getFeaturedMedalCached(int playerId) {
    CachedFeaturedMedal cached = featuredMedalCache.get(playerId);
    if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
      return CompletableFuture.completedFuture(cached.medal());
    }
    return CompletableFuture.supplyAsync(() -> {
      Optional<FeaturedMedalDisplay> result = fafApiAccessor.getFeaturedMedal(playerId).stream()
          .findFirst()
          .map(com.faforever.client.api.dto.PlayerFeaturedMedal::getMedalCode)
          .map(code -> new FeaturedMedalDisplay(code, careerMedalCount(playerId, code)));
      featuredMedalCache.put(playerId,
          new CachedFeaturedMedal(result, System.currentTimeMillis() + FEATURED_MEDAL_TTL_MILLIS));
      return result;
    }, executorService);
  }

  @Override
  public Optional<FeaturedMedalDisplay> peekFeaturedMedal(int playerId) {
    CachedFeaturedMedal cached = featuredMedalCache.get(playerId);
    if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
      return cached.medal();
    }
    getFeaturedMedalCached(playerId);   // fire-and-forget warm so the next render has it
    return Optional.empty();
  }

  /** Career total of one medal code for a player: tournament medals from the tournament summary,
   * everything else (LP + season placement) from the medal-summary rollup. */
  private long careerMedalCount(int playerId, String code) {
    if (LadderUiUtil.TOURNAMENT_MEDAL_CODES.contains(code)) {
      long n = 0;
      for (com.faforever.client.api.dto.PlayerTournamentSummary s
          : fafApiAccessor.getPlayerTournamentSummary(playerId)) {
        if (s.getFeaturedMod() == null) {
          continue;   // skip the grand-total rollup row to avoid double counting
        }
        if (LadderUiUtil.TOURNAMENT_GOLD.equals(code)) {
          n += s.getFirsts();
        } else if (LadderUiUtil.TOURNAMENT_SILVER.equals(code)) {
          n += s.getSeconds();
        } else if (LadderUiUtil.TOURNAMENT_BRONZE.equals(code)) {
          n += s.getThirds();
        }
      }
      return n;
    }
    return fafApiAccessor.getPlayerMedalSummary(playerId).stream()
        .filter(s -> code.equals(s.getMedalCode()))
        .mapToLong(PlayerMedalSummary::getCnt).sum();
  }

  @Override
  public CompletableFuture<Void> setFeaturedMedal(int playerId, String medalCode) {
    featuredMedalCache.remove(playerId);  // own change must not be masked by a stale cache entry
    return CompletableFuture.runAsync(() -> {
      // single row per player.
      Optional<com.faforever.client.api.dto.PlayerFeaturedMedal> existing =
          fafApiAccessor.getFeaturedMedal(playerId).stream().findFirst();
      if (medalCode == null) {
        // Clear = DELETE the row. (PATCHing medal_code=null no-ops: a null field isn't serialized in
        // the JSON:API body, so the old selection would persist.)
        existing.ifPresent(m -> fafApiAccessor.deleteFeaturedMedal(m.getId()));
      } else {
        com.faforever.client.api.dto.PlayerFeaturedMedal dto =
            new com.faforever.client.api.dto.PlayerFeaturedMedal();
        dto.setMedalCode(medalCode);
        if (existing.isPresent()) {
          dto.setId(existing.get().getId());
          fafApiAccessor.updateFeaturedMedal(existing.get().getId(), dto);
        } else {
          com.faforever.client.api.dto.Player player = new com.faforever.client.api.dto.Player();
          player.setId(String.valueOf(playerId));
          dto.setPlayer(player);
          fafApiAccessor.createFeaturedMedal(dto);
        }
      }
      // Leave the cache holding the authoritative post-write value, so a read that raced the write
      // (e.g. the avatar-change repopulate) can't leave a stale entry behind.
      Optional<FeaturedMedalDisplay> result = medalCode == null
          ? Optional.empty()
          : Optional.of(new FeaturedMedalDisplay(medalCode, careerMedalCount(playerId, medalCode)));
      featuredMedalCache.put(playerId,
          new CachedFeaturedMedal(result, System.currentTimeMillis() + FEATURED_MEDAL_TTL_MILLIS));
    }, executorService);
  }

  // --- helpers --------------------------------------------------------------

  /** Builds a standing for a player's own LP row, resolving the within-board rank with a count query. */
  private SeasonStanding toStanding(LadderPoints lp) {
    if (lp.getLeague() == null || lp.getSeason() == null || lp.getLeague().getLeaderboard() == null) {
      return null;
    }
    int leagueId = Integer.parseInt(lp.getLeague().getId());
    int seasonId = Integer.parseInt(lp.getSeason().getId());
    int rank = fafApiAccessor.getLadderRank(leagueId, seasonId, lp.getScore());
    return standing(lp, lp.getLeague().getLeaderboard().getTechnicalName(), leagueId, seasonId, rank);
  }

  private SeasonStanding standing(LadderPoints lp, String technicalName, int leagueId, int seasonId, int rank) {
    Player player = lp.getPlayer();
    return new SeasonStanding(
        player != null ? Integer.parseInt(player.getId()) : 0,
        player != null ? player.getLogin() : null,
        technicalName, leagueId, seasonId, lp.getScore(), lp.getGames(), rank,
        lp.getWins(), lp.getDraws(), lp.getLosses(), lp.getCurrentStreak(), lp.getBestStreak(),
        lp.getRecentResults() != null ? lp.getRecentResults() : "");
  }

  private LpGameBreakdown toBreakdown(LadderPointsJournal j) {
    Player player = j.getPlayer();
    return new LpGameBreakdown(
        player != null ? Integer.parseInt(player.getId()) : 0,
        player != null ? player.getLogin() : null,
        j.getGameId(), j.getCombatBase(), j.getWinBonus(), j.getUpsetBonus(),
        j.getLpAwarded(), j.getScoreAfter(), j.getDestroyedValue(), j.getDvRatio());
  }

  private PlayerCombatMetrics toMetrics(GamePlayerMetrics m) {
    Player player = m.getPlayer();
    return new PlayerCombatMetrics(
        player != null ? Integer.parseInt(player.getId()) : 0,
        player != null ? player.getLogin() : null,
        m.getRegime(), m.getDestroyedValue(), m.getDvRatio(), m.getEconValue(),
        m.getTaKills(), m.getTaLosses(), m.getDmgDealt(),
        m.getLastActTick(), m.getGameMaxTick(), m.getTeam());
  }

  private GameMedalAward toMedal(GameMedal m) {
    Player player = m.getPlayer();
    return new GameMedalAward(
        player != null ? Integer.parseInt(player.getId()) : 0,
        player != null ? player.getLogin() : null,
        m.getMedalCode(), m.getValue());
  }
}
