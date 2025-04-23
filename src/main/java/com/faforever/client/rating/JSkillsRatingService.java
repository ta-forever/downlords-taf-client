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
import com.faforever.client.util.RatingUtil;
import jskills.GameInfo;
import jskills.Rating;
import jskills.Team;
import jskills.TrueSkillCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.faforever.client.leaderboard.LeaderboardService.DEFAULT_RATING_TYPE;

@Service
@Slf4j
public class JSkillsRatingService implements RatingService {
  private final GameInfo gameInfo;
  private final PlayerService playerService;
  private final FafService fafService;
  private final String autoBalanceMetric = "kl";

  public JSkillsRatingService(ClientProperties clientProperties, PlayerService playerService, FafService fafService) {
    this.playerService = playerService;
    this.fafService = fafService;
    TrueSkill trueSkill = clientProperties.getTrueSkill();
    gameInfo = new GameInfo(trueSkill.getInitialMean(), trueSkill.getInitialStandardDeviation(), trueSkill.getBeta(),
        trueSkill.getDynamicFactor(), trueSkill.getDrawProbability());
  }

  @Override
  public LeaderboardRating createNewLeaderboardRating() {
    return LeaderboardRating.create((float)gameInfo.getInitialMean(), (float)gameInfo.getInitialStandardDeviation());
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
              new jskills.Player<>(stats.getPlayerId()), new Rating(
                  stats.getBeforeMean() == null ? gameInfo.getInitialMean() : stats.getBeforeMean(),
                  stats.getBeforeDeviation() == null ? gameInfo.getInitialStandardDeviation() : stats.getAfterDeviation()
              )));
          return team;
        })
        .collect(Collectors.toList()));
  }

  @Override
  public List<Player> getBalancedTeams(Game game) {
    Optional<Player> host = playerService.getPlayerForUsername(game.getHost());
    if (host.isEmpty()) return List.of();

    Set<String> teamsLeaderboards = getLeaderboards(game.getFeaturedMod(), true);
    Set<String> singlesLeaderboards = getLeaderboards(game.getFeaturedMod(), false);

    Map<?, List<String>> currentTeams = game.getTeams();
    currentTeams.entrySet().removeIf(entry -> entry.getKey().equals("-1"));

    List<Player> players = getNonHostPlayers(currentTeams, game.getHost());
    boolean isHostWatching = game.getTeams().getOrDefault("-1", List.of()).contains(game.getHost());

    List<Player> hostAndPlayers = new ArrayList<>();
    if (!isHostWatching) hostAndPlayers.add(host.get());
    hostAndPlayers.addAll(players);
    if (hostAndPlayers.isEmpty()) return hostAndPlayers;

    Map<Integer, javafx.util.Pair<String, LeaderboardRating>> playerRatings =
        getDistilledPlayerRatings(hostAndPlayers, teamsLeaderboards, singlesLeaderboards, "teams");

    return balancePlayers(hostAndPlayers, playerRatings);
  }

  @Override
  public List<Player> getBalancedTeams(Replay replay) {
    List<Player> players = replay.getTeamPlayerStats().values().stream()
        .flatMap(List::stream)
        .map(stats -> {
          Player player = new Player(new com.faforever.client.remote.domain.Player());
          player.setId(stats.getPlayerId());
          player.setUsername(String.valueOf(stats.getPlayerId()));
          return player;
        })
        .collect(Collectors.toList());

    Map<Integer, javafx.util.Pair<String, LeaderboardRating>> distilledRatings = replay.getTeamPlayerStats().values().stream()
        .flatMap(List::stream)
        .collect(Collectors.toMap(
            PlayerStats::getPlayerId,
            stats -> new javafx.util.Pair<>("replay",
                LeaderboardRating.create(
                    stats.getBeforeMean() == null ? (float)gameInfo.getInitialMean() : stats.getBeforeMean().floatValue(),
                    stats.getBeforeDeviation() == null ? (float)gameInfo.getInitialStandardDeviation() : stats.getBeforeDeviation().floatValue()
            ))));

    return balancePlayers(players, distilledRatings);
  }

  private List<Player> balancePlayers(List<Player> allPlayers,
                                      Map<Integer, javafx.util.Pair<String, LeaderboardRating>> distilledRatings) {
    List<Player> firstPlayersPartnersPool = allPlayers.subList(1, allPlayers.size());
    int numTeammates = firstPlayersPartnersPool.size() / 2;
    List<List<Player>> combinations = generateCombinations(firstPlayersPartnersPool, numTeammates);

    List<Player> bestTeams = null;
    Double bestScore = null;

    for (List<Player> teammates : combinations) {
      //Collections.shuffle(teammates);
      List<Player> team1 = new ArrayList<>(List.of(allPlayers.get(0)));
      team1.addAll(teammates);

      Set<Integer> team1Ids = team1.stream().map(Player::getId).collect(Collectors.toSet());
      List<Player> team2 = allPlayers.stream().filter(p -> !team1Ids.contains(p.getId())).collect(Collectors.toList());
      //Collections.shuffle(team2);

      double score = computeScore(team1, team2, distilledRatings);

      if (bestTeams == null || score < bestScore) {
        bestScore = score;
        bestTeams = interleave(team1, team2);
      }
    }

    logTeamRatings(bestTeams, distilledRatings);
    return bestTeams;
  }

  private Set<String> getLeaderboards(String mod, boolean isTeam) {
    try {
      return fafService.getMatchmakerQueueMapPools().get().stream()
          .filter(mq -> mq.getFeaturedMod().getTechnicalName().equals(mod))
          .filter(mq -> (isTeam ? mq.getTeamSize() > 1 : mq.getTeamSize() == 1))
          .map(mq -> mq.getLeaderboard().getTechnicalName())
          .collect(Collectors.toSet());
    } catch (InterruptedException | ExecutionException e) {
      return Set.of();
    }
  }

  private List<Player> getNonHostPlayers(Map<?, List<String>> teams, String hostUsername) {
    return teams.values().stream()
        .flatMap(Collection::stream)
        .filter(name -> !name.equals(hostUsername))
        .map(playerService::getPlayerForUsername)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  // priority: "teams", "singles", "default" or "all
  @Override
  public Map<Integer, javafx.util.Pair<String, LeaderboardRating>> getDistilledPlayerRatings(
      List<Player> players, Set<String> teamBoards, Set<String> singleBoards, String priority) {

    LeaderboardRating defaultRating = LeaderboardRating.create(
        (float) gameInfo.getInitialMean(), (float) gameInfo.getInitialStandardDeviation());

    // for each player+host find a suitable rating
    // most preferred: aggregate over all teams leaderboards for the mod
    // next preferred: aggregate over all singles leaderboards for the mod
    // next preferred: the global leaderboard (DEFAULT_RATING_TYPE)
    // next preferred: aggregate over all leaderboards

    return players.stream().collect(Collectors.toMap(
        Player::getId, player -> {
          LeaderboardRating lbrTeam = aggregateRatings(player.getLeaderboardRatings().entrySet().stream()
              .filter(e -> teamBoards.contains(e.getKey()))
              .map(Map.Entry::getValue)
              .toList());

          LeaderboardRating lbrSingle = aggregateRatings(player.getLeaderboardRatings().entrySet().stream()
              .filter(e -> singleBoards.contains(e.getKey()))
              .map(Map.Entry::getValue)
              .toList());

          LeaderboardRating lbrDefault = player.getLeaderboardRatings().getOrDefault(DEFAULT_RATING_TYPE, defaultRating);

          LeaderboardRating lbrEverything = aggregateRatings(player.getLeaderboardRatings().values().stream()
              .toList());

          List<javafx.util.Pair<String,LeaderboardRating>> lbrList = null;
          if ("teams".equals(priority)) {
            lbrList = List.of(
                new javafx.util.Pair<>("teams", lbrTeam),
                new javafx.util.Pair<>("singles", lbrSingle),
                new javafx.util.Pair<>("default", lbrDefault),
                new javafx.util.Pair<>("all", lbrEverything));
          }
          else if ("singles".equals(priority)) {
            lbrList = List.of(
                new javafx.util.Pair<>("singles", lbrSingle),
                new javafx.util.Pair<>("teams", lbrTeam),
                new javafx.util.Pair<>("default", lbrDefault),
                new javafx.util.Pair<>("all", lbrEverything));
          }
          else if ("default".equals(priority)) {
            lbrList = List.of(
                new javafx.util.Pair<>("default", lbrDefault),
                new javafx.util.Pair<>("teams", lbrTeam),
                new javafx.util.Pair<>("singles", lbrSingle),
                new javafx.util.Pair<>("all", lbrEverything));
          }
          else  if ("all".equals(priority)){
            lbrList = List.of(
                new javafx.util.Pair<>("all", lbrEverything),
                new javafx.util.Pair<>("default", lbrDefault),
                new javafx.util.Pair<>("teams", lbrTeam),
                new javafx.util.Pair<>("singles", lbrSingle));
          }

          assert lbrList != null;
          if (lbrList.get(0).getValue().getNumberOfGames() >= 10) {
            return lbrList.get(0);
          }
          if (lbrList.get(1).getValue().getNumberOfGames() >= 10) {
            return lbrList.get(1);
          }
          if (lbrList.get(2).getValue().getNumberOfGames() >= 10) {
            return lbrList.get(2);
          }
          return lbrList.get(3);
        }
    ));
  }

  private double computeScore(List<Player> team1, List<Player> team2,
                              Map<Integer, javafx.util.Pair<String, LeaderboardRating>> ratings) {
    double score = switch (autoBalanceMetric) {
      case "kl" -> computeKLDivergence(team1, team2, ratings);
      case "wasserstein" -> computeWasserstein(team1, team2, ratings);
      case "trueskill" -> computeTrueSkillQuality(team1, team2, ratings);
      default -> computeKLDivergence(team1, team2, ratings);
    };
    return score;
  }

  private double computeKLDivergence(List<Player> team1, List<Player> team2, Map<Integer, javafx.util.Pair<String, LeaderboardRating>> ratings) {
    double m1 = 0, m2 = 0, v1 = 0, v2 = 0;
    for (Player p : team1) {
      LeaderboardRating r = ratings.get(p.getId()).getValue();
      m1 += r.getMean();
      v1 += r.getDeviation() * r.getDeviation();
    }
    for (Player p : team2) {
      LeaderboardRating r = ratings.get(p.getId()).getValue();
      m2 += r.getMean();
      v2 += r.getDeviation() * r.getDeviation();
    }
    return 0.5 * ((m1 - m2) * (m1 - m2) + v1 + v2) * (1.0 / v1 + 1.0 / v2) - 2.0;
  }

  private double computeWasserstein(List<Player> team1, List<Player> team2, Map<Integer, javafx.util.Pair<String, LeaderboardRating>> ratings) {
    double m1 = 0, m2 = 0, d1 = 0, d2 = 0;
    for (Player p : team1) {
      LeaderboardRating r = ratings.get(p.getId()).getValue();
      m1 += r.getMean();
      d1 += r.getDeviation();
    }
    for (Player p : team2) {
      LeaderboardRating r = ratings.get(p.getId()).getValue();
      m2 += r.getMean();
      d2 += r.getDeviation();
    }
    return Math.sqrt((m1 - m2) * (m1 - m2) + (d1 - d2) * (d1 - d2));
  }

  private double computeTrueSkillQuality(List<Player> team1, List<Player> team2, Map<Integer, javafx.util.Pair<String, LeaderboardRating>> ratings) {
    jskills.Team jsTeam1 = new jskills.Team();
    for (Player p : team1) {
      LeaderboardRating r = ratings.get(p.getId()).getValue();
      jsTeam1.addPlayer(new jskills.Player<>(p.getId()), new Rating(r.getMean(), r.getDeviation()));
    }
    jskills.Team jsTeam2 = new jskills.Team();
    for (Player p : team2) {
      LeaderboardRating r = ratings.get(p.getId()).getValue();
      jsTeam2.addPlayer(new jskills.Player<>(p.getId()), new Rating(r.getMean(), r.getDeviation()));
    }
    return TrueSkillCalculator.calculateMatchQuality(gameInfo, List.of(jsTeam1, jsTeam2));
  }

  private List<Player> interleave(List<Player> a, List<Player> b) {
    List<Player> result = new ArrayList<>();
    for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
      if (i < a.size()) result.add(a.get(i));
      if (i < b.size()) result.add(b.get(i));
    }
    return result;
  }

  private void logTeamRatings(List<Player> bestTeams,
                              Map<Integer, javafx.util.Pair<String, LeaderboardRating>> ratings) {
    for (int parity = 0; parity < 2; ++parity) {
      int finalParity = parity;
      List<Player> subset = IntStream.range(0, bestTeams.size())
          .filter(i -> i % 2 == finalParity)
          .mapToObj(bestTeams::get).toList();

      double sumMean = subset.stream()
          .mapToDouble(p -> ratings.get(p.getId()).getValue().getMean()).sum();

      double sumVar = subset.stream()
          .mapToDouble(p -> {
            double d = ratings.get(p.getId()).getValue().getDeviation();
            return d * d;
          }).sum();

      String names = subset.stream().map(player -> String.format("%s(%d/%d)",
          player.getUsername(),
          (int)ratings.get(player.getId()).getValue().getMean(),
          (int)ratings.get(player.getId()).getValue().getDeviation())
      ).collect(Collectors.joining(","));
      log.info("[getBalancedTeams] rating={}/{} [{}]",
          (int) sumMean, (int) Math.sqrt(sumVar), names);
    }
  }

  private LeaderboardRating aggregateRatings(List<LeaderboardRating> leaderboardRatings) {
    LeaderboardRating lbr = RatingUtil.getAggregateRating(leaderboardRatings);
    if (lbr.getNumberOfGames() > 0) {
      return lbr;
    }
    lbr = LeaderboardRating.create((float) gameInfo.getInitialMean(), (float) gameInfo.getInitialStandardDeviation());
    lbr.setNumberOfGames(0);
    return lbr;
  }

  public static <T> List<List<T>> generateCombinations(List<T> items, int k) {
    List<List<T>> result = new ArrayList<>();
    generateCombinationsRecursive(items, 0, k, new ArrayList<>(), result);
    return result;
  }

  private static <T> void generateCombinationsRecursive(List<T> items, int start, int k,
                                                        List<T> current, List<List<T>> result) {
    if (current.size() == k) {
      result.add(new ArrayList<>(current));
      return;
    }

    for (int i = start; i < items.size(); i++) {
      current.add(items.get(i));
      generateCombinationsRecursive(items, i + 1, k, current, result);
      current.remove(current.size() - 1);
    }
  }

}
