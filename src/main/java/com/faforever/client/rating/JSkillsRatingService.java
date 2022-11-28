package com.faforever.client.rating;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.config.ClientProperties.TrueSkill;
import com.faforever.client.game.Game;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JSkillsRatingService implements RatingService {
  private final GameInfo gameInfo;
  private final PlayerService playerService;

  public JSkillsRatingService(ClientProperties clientProperties, PlayerService playerService) {
    this.playerService = playerService;
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

  @Override
  public List<Player> getBalancedTeams(Game game) {

    Optional<Player> host = playerService.getPlayerForUsername(game.getHost());
    if (host.isEmpty()) {
      return List.of();
    }

    List<Player> players = game.getTeams().values().stream()
        .flatMap(Collection::stream)
        .filter(playerName -> !playerName.equals(game.getHost()))
        .map(playerService::getPlayerForUsername)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());

    List<Player> bestTeams = null;
    Double bestScore = null;
    for (int nSearch = 0; nSearch < 30; ++nSearch) {
      Collections.shuffle(players);
      List<Player> hostAndPlayers = new ArrayList<>(List.of(host.get()));
      hostAndPlayers.addAll(players);

      if (hostAndPlayers.size() < 3) {
        return hostAndPlayers;
      }

      LeaderboardRating defaultRating = LeaderboardRating.create(
          (float) gameInfo.getInitialMean(), (float) gameInfo.getInitialStandardDeviation());
      String leaderboard = game.getRatingType();

      double m1 = 0.0, m2 = 0.0, v1 = 0.0, v2 = 0.0;
      for (int n=0; n<hostAndPlayers.size(); ++n) {
        LeaderboardRating lbr = hostAndPlayers.get(n).getLeaderboardRatings().getOrDefault(leaderboard, defaultRating);
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

        log.info("[getBalancedTeams] {}. kl={}",
            String.join(",", hostAndPlayers.stream().map(p -> String.format("%s(%d/%d)", p.getUsername(),
                (int)p.getLeaderboardRatings().getOrDefault(leaderboard, defaultRating).getMean(),
                (int)p.getLeaderboardRatings().getOrDefault(leaderboard, defaultRating).getDeviation()))
                .toList()),
            bestScore);
      }
    }
    return bestTeams;
  }

}
