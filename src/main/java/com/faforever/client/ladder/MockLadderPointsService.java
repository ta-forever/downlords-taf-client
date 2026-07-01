package com.faforever.client.ladder;

import com.faforever.client.FafClientApplication;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Lazy
@Service
@Profile(FafClientApplication.PROFILE_OFFLINE)
public class MockLadderPointsService implements LadderPointsService {

  @Override
  public CompletableFuture<List<SeasonStanding>> getStandingsForPlayer(int playerId) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<SeasonStanding>> getStandingsForPlayerCached(int playerId) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<SeasonInfo>> getSeasons(String leaderboardTechnicalName) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<SeasonStanding>> getSeasonLadder(String leaderboardTechnicalName, int seasonId, int count, int page) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<HallOfFameEntry>> getHallOfFame(String leaderboardTechnicalName) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<GameLadderResult> getGameResult(int gameId) {
    return CompletableFuture.completedFuture(new GameLadderResult(gameId, List.of(), List.of(), List.of()));
  }

  @Override
  public CompletableFuture<List<LpHistoryPoint>> getLpHistory(int playerId, String leaderboardTechnicalName) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<MedalCount>> getMedalCounts(int playerId) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<java.util.Map<Integer, Long>> getSeasonMedalCounts(String leaderboardTechnicalName, int seasonId) {
    return CompletableFuture.completedFuture(java.util.Map.of());
  }

  @Override
  public CompletableFuture<List<FeaturedMedalDisplay>> getEarnedMedals(int playerId) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<Optional<String>> getFeaturedMedal(int playerId) {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  public CompletableFuture<Optional<FeaturedMedalDisplay>> getFeaturedMedalCached(int playerId) {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  public Optional<FeaturedMedalDisplay> peekFeaturedMedal(int playerId) {
    return Optional.empty();
  }

  @Override
  public CompletableFuture<Void> setFeaturedMedal(int playerId, String medalCode) {
    return CompletableFuture.completedFuture(null);
  }
}
