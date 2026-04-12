package com.faforever.client.galacticwar;

import com.faforever.client.game.Faction;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@NoArgsConstructor
public class Scenario {
  String label;

  @SerializedName("node")
  List<Planet> planets;

  @SerializedName("edge")
  List<JumpGate> jumpGates;

  @SerializedName("players")
  Map<Integer, Map<String, GwPlayerScore>> players;

  @SerializedName("technical_name")
  String technicalName = "galaxy";

  @SerializedName("display_name")
  String displayName = "The Galaxy";

  @SerializedName("rank_thresholds")
  List<Integer> rankThresholds = List.of(60, 150, 300, 600, 1200, 2400, 4800, 9600);

  @SerializedName("dominance_threshold")
  Float dominanceThreshold = 3.0f;

  @SerializedName("factions")
  List<Faction> factions = List.of(Faction.ARM, Faction.CORE);

  @SerializedName("map_select_strategy")
  GwMapSelectStrategy mapSelectStrategy;

  @SerializedName("map_select_regexes")
  Map<String, List<String>> mapSelectRegexes = new ConcurrentHashMap<>();// keyed by mod technical name

  @SerializedName("map_select_mmq_id")
  Map<String, Integer> mapSelectMMQId = new ConcurrentHashMap<>();  // keyed by mod technical name

  @SerializedName("iteration")
  Integer iteration = 1;

  @SerializedName("last_galaxy_winner")
  String lastGalaxyWinner;

  @SerializedName("dominance_decay_period")
  Integer dominanceDecayPeriod;

  @SerializedName("dominance_decay_thresholds")
  List<Float> dominanceDecayThresholds;

  @SerializedName("update_crontab")
  String updateCrontab;

  static public Scenario fromFile(Path path) throws IOException {
    return new Gson().fromJson(Files.newBufferedReader(path), Scenario.class);
  }

  public GwRank rankForPlayerScore(GwPlayerScore score) {
    List<Integer> rankThresholds = getRankThresholds();
    if (rankThresholds == null) {
      return GwRank.PRIVATE;
    }

    List<Integer> th = rankThresholds.stream()
        .sorted(java.util.Comparator.reverseOrder())
        .toList();

    float metric = score.getCumWinningScores();
    if (metric >= th.get(0)) return GwRank.COMMANDER;
    if (metric >= th.get(1)) return GwRank.GENERAL;
    if (metric >= th.get(2)) return GwRank.COLONEL;
    if (metric >= th.get(3)) return GwRank.MAJOR;
    if (metric >= th.get(4)) return GwRank.CAPTAIN;
    if (metric >= th.get(5)) return GwRank.LIEUTENANT;
    if (metric >= th.get(6)) return GwRank.SERGEANT;
    if (metric >= th.get(7)) return GwRank.CORPORAL;
    return GwRank.PRIVATE;
  }

  public Optional<Faction> getPlayerFaction(int playerId) {
    Map<Integer, Map<String, GwPlayerScore>> players = getPlayers();
    if (!players.containsKey(playerId)) {
      return Optional.empty();
    }

    Map<String, GwPlayerScore> scores = players.get(playerId);
    Optional<Map.Entry<String, GwPlayerScore>> topEntry =
        scores.entrySet().stream()
            .max(java.util.Comparator.comparingInt(
                e -> rankForPlayerScore(e.getValue()).getTier()));

    if (topEntry.isEmpty()) {
      return Optional.empty();
    }

    String factionName = topEntry.get().getKey();
    return Optional.of(Faction.fromString(factionName));
  }

  public String getMedalIconPath(String factionName, GwRank rank) {
    return String.format(
        "images/ranks/RANK_%s%d.png",
        factionName,
        rank.getTier()
    );
  }

  public record FactionScoreRank(
      Faction faction,
      GwPlayerScore score,
      GwRank rank
  ) {}

  Optional<FactionScoreRank> getFactionScoreRank(Integer playerId) {
    Map<String, GwPlayerScore> scores = this.getPlayers().getOrDefault(playerId, null);
    return this.getFactionScoreRank(scores);
  }

  Optional<FactionScoreRank> getFactionScoreRank(Map<String, GwPlayerScore> scores) {
    if (scores == null) {
      return Optional.empty();
    }

    Optional<Entry<String, GwPlayerScore>> topEntry =
        scores.entrySet().stream()
            .max(java.util.Comparator.comparingInt(
                e -> this.rankForPlayerScore(e.getValue()).getTier()));

    if (topEntry.isEmpty()) {
      return Optional.empty();
    }

    String factionName = topEntry.get().getKey();
    Faction faction = Faction.fromString(factionName);
    GwPlayerScore score = topEntry.get().getValue();
    GwRank gwRank = this.rankForPlayerScore(score);
    return Optional.of(new FactionScoreRank(faction, score, gwRank));
  }
}
