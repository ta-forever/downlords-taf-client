package com.faforever.client.rating;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.config.ClientProperties.TrueSkill;
import com.faforever.client.game.Game;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
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
import java.util.Comparator;
import java.util.HashMap;
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
  private final PreferencesService preferencesService;

  public JSkillsRatingService(ClientProperties clientProperties, PreferencesService preferencesService,
                              PlayerService playerService, FafService fafService) {
    this.playerService = playerService;
    this.fafService = fafService;
    this.preferencesService = preferencesService;
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
    List<Player> hostAndPlayers = getPlayingRoster(game);
    if (hostAndPlayers.isEmpty()) return hostAndPlayers;

    Map<Integer, javafx.util.Pair<String, LeaderboardRating>> playerRatings = getRosterRatings(game, hostAndPlayers);

    List<Player> result = balancePlayers(hostAndPlayers, playerRatings);
    logTeamRatings(game.getId(), result, playerRatings);
    return result;
  }

  @Override
  public List<Player> getBalancedTeams(Game game, Map<Integer, Integer> pinnedTeamByPlayerId) {
    if (pinnedTeamByPlayerId == null || pinnedTeamByPlayerId.isEmpty()) {
      return getBalancedTeams(game);
    }
    List<Player> hostAndPlayers = getPlayingRoster(game);
    if (hostAndPlayers.isEmpty()) return hostAndPlayers;

    Map<Integer, javafx.util.Pair<String, LeaderboardRating>> playerRatings = getRosterRatings(game, hostAndPlayers);

    Integer hostId = playerService.getPlayerForUsername(game.getHost()).map(Player::getId).orElse(null);
    List<Player> result = balancePlayersConstrained(hostAndPlayers, playerRatings, pinnedTeamByPlayerId, hostId);
    logTeamRatings(game.getId(), result, playerRatings);
    return result;
  }

  /**
   * The set of players that will actually play {@code game}: the host (unless
   * watching) plus everyone in a team bucket other than observers. Includes the
   * "no team" bucket (key "1"/"null") that joined players sit in while the game
   * is still staging — compare keys as strings since "null" would break
   * parseInt and only observers (key "-1") are excluded.
   */
  private List<Player> getPlayingRoster(Game game) {
    Optional<Player> host = playerService.getPlayerForUsername(game.getHost());
    if (host.isEmpty()) return List.of();

    Map<?, List<String>> currentTeams = game.getTeams().entrySet().stream()
        .filter(entry -> !"-1".equals(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    List<Player> players = getNonHostPlayers(currentTeams, game.getHost());
    boolean isHostWatching = game.getTeams().getOrDefault("-1", List.of()).contains(game.getHost());

    List<Player> hostAndPlayers = new ArrayList<>();
    if (!isHostWatching) hostAndPlayers.add(host.get());
    hostAndPlayers.addAll(players);
    return hostAndPlayers;
  }

  private Map<Integer, javafx.util.Pair<String, LeaderboardRating>> getRosterRatings(Game game, List<Player> roster) {
    Set<String> teamsLeaderboards = getLeaderboards(game.getFeaturedMod(), true);
    Set<String> singlesLeaderboards = getLeaderboards(game.getFeaturedMod(), false);
    return getDistilledPlayerRatings(roster, teamsLeaderboards, singlesLeaderboards, "teams");
  }

  /**
   * Like {@link #balancePlayers}, but with some players pinned to a team by the
   * host. Pinned players (id -&gt; team 0/1) stay where they are; the remaining
   * "free" players are distributed to optimise balance.
   *
   * <p>The result is interleaved as team0[0], team1[0], team0[1], … and consumed
   * by {@code +autoteam} as {@code allyTeam = i % 2}, so it is only valid when
   * team 0 (the host's "Team 1", at the even positions) is equal to team 1 or
   * exactly one larger. Pins are <strong>soft</strong>: if they can't form such
   * a split (a team is over-pinned), the unassigned players are pinned to the
   * under-pinned team, all non-host players are unpinned from the over-pinned
   * team, and the freed players are then balanced around what remains.</p>
   *
   * @param hostId the host's player id (never unpinned), or null
   */
  List<Player> balancePlayersConstrained(List<Player> allPlayers,
                                         Map<Integer, javafx.util.Pair<String, LeaderboardRating>> ratings,
                                         Map<Integer, Integer> pinnedInput,
                                         Integer hostId) {
    Map<Integer, Integer> pinned = new HashMap<>(pinnedInput);

    List<Player> result = trySplit(allPlayers, ratings, pinned);
    if (result != null) {
      return result;
    }

    // A team is over-pinned. Pin the unassigned players to the under-pinned
    // team, unpin every non-host player from the over-pinned team, then balance.
    relaxOverpinnedTeam(allPlayers, pinned, hostId);
    result = trySplit(allPlayers, ratings, pinned);
    if (result != null) {
      return result;
    }

    // Defensive: keep only the host's pin and let the balancer place the rest.
    pinned.entrySet().removeIf(e -> hostId == null || !e.getKey().equals(hostId));
    result = trySplit(allPlayers, ratings, pinned);
    return result == null ? List.of() : result;
  }

  /**
   * Find the best valid split for the given pins, or null if no split keeps
   * team 0 (even positions) equal to or one larger than team 1. Returns the
   * interleaved start-position order.
   */
  private List<Player> trySplit(List<Player> allPlayers,
                                Map<Integer, javafx.util.Pair<String, LeaderboardRating>> ratings,
                                Map<Integer, Integer> pinned) {
    List<Player> pinned0 = new ArrayList<>();
    List<Player> pinned1 = new ArrayList<>();
    List<Player> free = new ArrayList<>();
    for (Player p : allPlayers) {
      Integer t = pinned.get(p.getId());
      if (t != null && t == 0) {
        pinned0.add(p);
      } else if (t != null && t == 1) {
        pinned1.add(p);
      } else {
        free.add(p);
      }
    }

    double bestScore = Double.NEGATIVE_INFINITY;
    List<Player> bestTeam0 = null;
    List<Player> bestTeam1 = null;
    for (int k = 0; k <= free.size(); k++) {
      // k free players go to team 0, the rest to team 1. Only splits where team 0
      // (host's team, even positions) equals or is one larger than team 1 can be
      // reproduced by the i%2 interleave.
      int diff = (pinned0.size() + k) - (pinned1.size() + (free.size() - k));
      if (diff < 0 || diff > 1) {
        continue;
      }
      for (List<Player> chosen : generateCombinations(free, k)) {
        List<Player> team0 = new ArrayList<>(pinned0);
        team0.addAll(chosen);
        Set<Integer> team0Ids = team0.stream().map(Player::getId).collect(Collectors.toSet());
        List<Player> team1 = new ArrayList<>(pinned1);
        for (Player p : free) {
          if (!team0Ids.contains(p.getId())) {
            team1.add(p);
          }
        }
        double score = computeScore(team0, team1, ratings);
        if (score > bestScore) {
          bestScore = score;
          bestTeam0 = team0;
          bestTeam1 = team1;
        }
      }
    }

    if (bestTeam0 == null) {
      return null;
    }
    List<Player> orderedTeam1 = orderIsomorphTeam2(bestTeam0, bestTeam1, ratings);
    return interleave(bestTeam0, orderedTeam1);
  }

  /**
   * Resolve an over-pinned team in {@code pinned} (mutated in place): pin every
   * unassigned player to the under-pinned team, then unpin all non-host players
   * from the over-pinned (larger) team so the balancer can redistribute them.
   */
  private void relaxOverpinnedTeam(List<Player> allPlayers, Map<Integer, Integer> pinned, Integer hostId) {
    List<Player> pinned0 = new ArrayList<>();
    List<Player> pinned1 = new ArrayList<>();
    List<Player> free = new ArrayList<>();
    for (Player p : allPlayers) {
      Integer t = pinned.get(p.getId());
      if (t != null && t == 0) {
        pinned0.add(p);
      } else if (t != null && t == 1) {
        pinned1.add(p);
      } else {
        free.add(p);
      }
    }

    int overTeam = pinned0.size() >= pinned1.size() ? 0 : 1;
    int underTeam = 1 - overTeam;
    List<Player> overPinned = overTeam == 0 ? pinned0 : pinned1;

    for (Player p : free) {
      pinned.put(p.getId(), underTeam);
    }
    for (Player p : overPinned) {
      if (hostId != null && p.getId() == hostId) {
        continue;
      }
      pinned.remove(p.getId());
    }
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

    List<Player> result = balancePlayers(players, distilledRatings);
    logTeamRatings(replay.getId(), result, distilledRatings);
    return result;
  }

  private List<Player> balancePlayers(List<Player> allPlayers,
                                      Map<Integer, javafx.util.Pair<String, LeaderboardRating>> distilledRatings) {
    List<Player> firstPlayersPartnersPool = allPlayers.subList(1, allPlayers.size());
    int numTeammates = firstPlayersPartnersPool.size() / 2;
    List<List<Player>> combinations = generateCombinations(firstPlayersPartnersPool, numTeammates);

    class ScoredTeam {
      final double score;
      final List<Player> team;
      ScoredTeam(double score, List<Player> team) {
        this.score = score;
        this.team = team;
      }
    }

    List<ScoredTeam> scoredTeams = new ArrayList<>();
    for (List<Player> teammates : combinations) {
      List<Player> team1 = new ArrayList<>(List.of(allPlayers.get(0)));
      team1.addAll(teammates);

      Set<Integer> team1Ids = team1.stream().map(Player::getId).collect(Collectors.toSet());
      List<Player> team2 = allPlayers.stream()
          .filter(p -> !team1Ids.contains(p.getId()))
          .collect(Collectors.toList());

      double score = computeScore(team1, team2, distilledRatings);

      team2 = orderIsomorphTeam2(team1, team2, distilledRatings);
      List<Player> combined = interleave(team1, team2);
      scoredTeams.add(new ScoredTeam(score, combined));
    }

    if (scoredTeams.isEmpty()) {
      return null;
    }

    // Compute sigmoid weights
    double th = this.preferencesService.getClientRemoteConfiguration().getAutoBalance().getThreshold();
    double k = this.preferencesService.getClientRemoteConfiguration().getAutoBalance().getScale();
    if (java.lang.Math.abs(k) > 1e-3) {
      List<Double> weights = scoredTeams.stream()
          .map(t -> (t.score - th) / k)
          .map(z -> 1.0 / (1.0 + Math.exp(-z)))
          .toList();

      // Compute cumulative distribution
      double totalWeight = weights.stream().mapToDouble(Double::doubleValue).sum();
      double r = Math.random() * totalWeight;
      double cumulative = 0.0;

      for (int i = 0; i < scoredTeams.size(); i++) {
        cumulative += weights.get(i);
        if (r <= cumulative) {
          ScoredTeam selected = scoredTeams.get(i);
          return selected.team;
        }
      }
    }

    scoredTeams.sort(Comparator.comparingDouble(a -> a.score));
    ScoredTeam fallback = scoredTeams.get(scoredTeams.size() - 1);
    return fallback.team;
  }

  List<Player> orderIsomorphTeam2(List<Player> _team1, List<Player> team2,
                                  Map<Integer, javafx.util.Pair<String, LeaderboardRating>> ratings) {

    Comparator<Player> ratingComparator = Comparator.comparingDouble(
        p -> {
          LeaderboardRating r = ratings.get(p.getId()).getValue();
          return r.getMean() - 3 * r.getDeviation();
        }
    );

    List<Player> team1 = _team1;
    if (team1.size() > team2.size()) {
      team1 = team1.subList(0, team2.size());
    }
    List<Player> team1Sorted = new ArrayList<>(team1);

    team1Sorted.sort(ratingComparator);
    Map<Integer, Integer> team1RanksByPlayerId = new HashMap<>();
    for (int i = 0; i < team1Sorted.size(); i++) {
      team1RanksByPlayerId.put(team1Sorted.get(i).getId(), i);
    }

    List<Player> team2Sorted = new ArrayList<>(team2);
    team2Sorted.sort(ratingComparator);

    List<Player> newTeam2 = new ArrayList<>();
    for (int i=0; i<team2.size(); ++i) {
      if (i < team1.size()) {
        int team1PlayerId = team1.get(i).getId();
        int rank = team1RanksByPlayerId.get(team1PlayerId);
        assert(rank < team1.size());
        Player team2Player = team2Sorted.get(rank);
        newTeam2.add(team2Player);
      }
      else {
        Player team2Player = team2Sorted.get(i);
        newTeam2.add(team2Player);
      }
    }

    return newTeam2;
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
    double score = switch (this.preferencesService.getClientRemoteConfiguration().getAutoBalance().getMetric()) {
      case "kl" -> -computeKLDivergence(team1, team2, ratings);
      case "wasserstein" -> -computeWasserstein(team1, team2, ratings);
      case "trueskill" -> computeTrueSkillQuality(team1, team2, ratings);
      default -> -computeKLDivergence(team1, team2, ratings);
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

  private void logTeamRatings(int gameId, List<Player> bestTeams,
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
      log.info("[getBalancedTeams] gameId={}, team={}: rating={}/{} [{}]",
          gameId, parity, (int) sumMean, (int) Math.sqrt(sumVar), names);
    }
  }

  private LeaderboardRating aggregateRatings(List<LeaderboardRating> leaderboardRatings) {
    if (!leaderboardRatings.isEmpty()) {
      LeaderboardRating lbr = RatingUtil.getAggregateRating(leaderboardRatings);
      if (lbr != null && lbr.getNumberOfGames() > 0) {
        return lbr;
      }
    }
    LeaderboardRating lbr = LeaderboardRating.create((float) gameInfo.getInitialMean(), (float) gameInfo.getInitialStandardDeviation());
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
