package com.faforever.client.rating;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.config.ClientProperties.TrueSkill;
import com.faforever.client.game.Game;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.remote.FafService;
import com.faforever.client.replay.Replay;
import com.faforever.client.replay.Replay.PlayerStats;
import jskills.GameInfo;
import jskills.Rating;
import jskills.Team;
import jskills.TrueSkillCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.faforever.client.leaderboard.LeaderboardService.DEFAULT_RATING_TYPE;

@Service
@Slf4j
public class JSkillsRatingService implements RatingService {
  private final GameInfo gameInfo;
  private final PlayerService playerService;
  private final FafService fafService;

  public JSkillsRatingService(ClientProperties clientProperties, PlayerService playerService, FafService fafService) {
    this.playerService = playerService;
    this.fafService = fafService;
    TrueSkill trueSkill = clientProperties.getTrueSkill();
    gameInfo = new GameInfo(trueSkill.getInitialMean(), trueSkill.getInitialStandardDeviation(), trueSkill.getBeta(),
        trueSkill.getDynamicFactor(), trueSkill.getDrawProbability());
  }

  @Override
  public double calculateQuality(Replay replay) {
    Collection<List<PlayerStats>> teams = replay.getTeamPlayerStats().values();
    if (teams.size() != 2) {
      return Double.NaN;
    }
    if (!teams.stream().allMatch(playerStats -> playerStats.stream().allMatch(stats -> stats.getBeforeDeviation() != null && stats.getBeforeMean() != null))) {
      return Double.NaN;
    }
    return TrueSkillCalculator.calculateMatchQuality(gameInfo, teams.stream()
        .map(players -> {
          Team team = new Team();
          players.forEach(stats -> team.addPlayer(
              new jskills.Player<>(stats.getPlayerId()), new Rating(stats.getBeforeMean(), stats.getBeforeDeviation())
          ));
          return team;
        })
        .collect(Collectors.toList()));
  }

  private static LeaderboardRating aggregateRatings(List<LeaderboardRating> leaderboardRatings) {
    double posteriorMean = 0.0;
    double posteriorPrecision = 0.0;
    int totalGames = 0;

    for (LeaderboardRating rating : leaderboardRatings) {
      double precision = rating.getNumberOfGames() / rating.getDeviation() / rating.getDeviation();
      posteriorMean += rating.getMean() * precision;
      posteriorPrecision += precision;
      totalGames += rating.getNumberOfGames();
    }

    posteriorMean /= posteriorPrecision;
    double posteriorVariance = totalGames / posteriorPrecision;

    LeaderboardRating lbr = null;
    if (totalGames > 0.0) {
      lbr = LeaderboardRating.create((float) posteriorMean, (float) Math.sqrt(posteriorVariance));
      lbr.setNumberOfGames(totalGames);
    }
    return lbr;
  }

  @Override
  public List<Player> getBalancedTeams(Game game) {

    // we must be host
    Optional<Player> host = playerService.getPlayerForUsername(game.getHost());
    if (host.isEmpty()) {
      return List.of();
    }

    // get all leaderboards for the game's mod, and sort them into teams and singles leaderboards
    Set<String> teamsLeaderboards = null;
    Set<String> singlesLeaderboards = null;
    try {
      teamsLeaderboards = fafService.getMatchmakerQueueMapPools().get().stream()
          .filter(mq -> mq.getFeaturedMod().getTechnicalName().equals(game.getFeaturedMod()))
          .filter(mq -> mq.getTeamSize() > 1)
          .map(mq -> mq.getLeaderboard().getTechnicalName())
          .collect(Collectors.toSet());

      singlesLeaderboards = fafService.getMatchmakerQueueMapPools().get().stream()
          .filter(mq -> mq.getFeaturedMod().getTechnicalName().equals(game.getFeaturedMod()))
          .filter(mq -> mq.getTeamSize() == 1)
          .map(mq -> mq.getLeaderboard().getTechnicalName())
          .collect(Collectors.toSet());

    } catch (InterruptedException e) {
    } catch (ExecutionException e) {
    }

    // get a list of al players excluding the host
    List<Player> players = game.getTeams().values().stream()
        .flatMap(Collection::stream)
        .filter(playerName -> !playerName.equals(game.getHost()))
        .map(playerService::getPlayerForUsername)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());

    // prepend the host
    List<Player> hostAndPlayers = new ArrayList<>(List.of(host.get()));
    hostAndPlayers.addAll(players);
    if (hostAndPlayers.size() < 1) {
      return hostAndPlayers;
    }

    // for each player+host find a suitable rating
    // most preferred: aggregate over all teams leaderboards for the mod
    // next preferred: aggregate over all singles leaderboards for the mod
    // next preferred: the global leaderboard (DEFAULT_RATING_TYPE)
    java.util.Map<Integer, javafx.util.Pair<String,LeaderboardRating>> distilledRatings = new java.util.HashMap<>();
    LeaderboardRating defaultRating = LeaderboardRating.create(
        (float) gameInfo.getInitialMean(), (float) gameInfo.getInitialStandardDeviation());
    for (int n=0; n<hostAndPlayers.size(); ++n) {
      LeaderboardRating lbr;
      if (teamsLeaderboards != null && singlesLeaderboards != null) {
        Set<String> finalTeamsLeaderboards = teamsLeaderboards;
        lbr = aggregateRatings(hostAndPlayers.get(n).getLeaderboardRatings().entrySet().stream()
            .filter(e -> finalTeamsLeaderboards.contains(e.getKey()))
            .map(Entry::getValue)
            .toList());
        distilledRatings.put(hostAndPlayers.get(n).getId(), new javafx.util.Pair("teams", lbr));
        if (lbr == null || lbr.getNumberOfGames() < 10) {
          Set<String> finalSinglesLeaderboards = singlesLeaderboards;
          lbr = aggregateRatings(hostAndPlayers.get(n).getLeaderboardRatings().entrySet().stream()
              .filter(e -> finalSinglesLeaderboards.contains(e.getKey()))
              .map(Entry::getValue)
              .toList());
          distilledRatings.put(hostAndPlayers.get(n).getId(), new javafx.util.Pair("singles", lbr));
        }
        if (lbr == null || lbr.getNumberOfGames() < 10) {
          lbr = hostAndPlayers.get(n).getLeaderboardRatings().getOrDefault(DEFAULT_RATING_TYPE, defaultRating);
          distilledRatings.put(hostAndPlayers.get(n).getId(), new javafx.util.Pair(DEFAULT_RATING_TYPE, lbr));
        }
      } else {
        lbr = hostAndPlayers.get(n).getLeaderboardRatings().getOrDefault(game.getRatingType(), defaultRating);
        distilledRatings.put(hostAndPlayers.get(n).getId(), new javafx.util.Pair("default", lbr));
      }
    }

    // search random combinations of teams to optimise balance
    List<Player> bestTeams = null;
    Double bestScore = null;
    for (int nSearch = 0; nSearch < 30; ++nSearch) {
      Collections.shuffle(players);
      hostAndPlayers = new ArrayList<>(List.of(host.get()));
      hostAndPlayers.addAll(players);

      double m1 = 0.0, m2 = 0.0, v1 = 0.0, v2 = 0.0;
      for (int n=0; n<hostAndPlayers.size(); ++n) {
        LeaderboardRating lbr = distilledRatings.get(hostAndPlayers.get(n).getId()).getValue();
        if (n % 2 == 0) {
          m1 += lbr.getMean();
          v1 += lbr.getDeviation() * lbr.getDeviation();
        } else {
          m2 += lbr.getMean();
          v2 += lbr.getDeviation() * lbr.getDeviation();
        }
      }

      // https://stats.stackexchange.com/questions/66271/kullback-leibler-divergence-of-two-normal-distributions
      double kl = 0.5 * ((m1-m2)*(m1-m2) + v1+v2) * (1.0/v1 + 1.0/v2) - 2.0;

      if (bestTeams == null || kl < bestScore) {
        bestTeams = hostAndPlayers;
        bestScore = kl;

        log.info("[getBalancedTeams] {} {}. kl={}",
            game.getRatingType(),
            String.join(",", hostAndPlayers.stream().map(p -> String.format("%s(%s:%d/%d)",
                    p.getUsername(),
                    distilledRatings.get(p.getId()).getKey(),
                    (int)distilledRatings.get(p.getId()).getValue().getMean(),
                    (int)distilledRatings.get(p.getId()).getValue().getDeviation()))
                .toList()),
            bestScore);
      }
    }
    return bestTeams;
  }

}
