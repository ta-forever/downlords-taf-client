package com.faforever.client.galacticwar;

import com.faforever.client.game.Faction;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Getter
@Setter
public class Planet {
  Integer id;

  @SerializedName("label")
  String name;

  @SerializedName("map")
  String mapName;

  @SerializedName("mod")
  String modTechnical;
  Double size;
  Map<Faction, Double> score;
  Map<Integer, Map<Faction, GwPlayerScore>> belligerents;

  @SerializedName("capital_of")
  Faction capitalOf;

  @SerializedName("controlled_by")
  Faction controlledBy;

  @SerializedName("about_to_be_captured")
  Boolean aboutToBeCaptured;

  @SerializedName("effective_threshold")
  Float effectiveThreshold;

  @SerializedName("contested_periods")
  Integer contestedPeriods;

  @SerializedName("previous_names")
  List<String> previousNames;

  @SerializedName("journal")
  List<JournalEntry> journal;

  PlanetGraphics graphics;

  @Getter
  @Setter
  public static class JournalEntry {
    String timestamp;
    String name;
    String map;
  }

  public String toString() {
    return name;
  }

  Optional<Integer> getHeroicPlayerId() {
    if (controlledBy == null || belligerents == null || belligerents.isEmpty()) {
      return Optional.empty();
    }

    return belligerents.entrySet().stream()
        .map(entry -> {
          Integer playerId = entry.getKey();
          GwPlayerScore score =
              entry.getValue().get(controlledBy);

          if (score == null) {
            return null;
          }

          return Map.entry(playerId, score.getCumWinningScores());
        })
        .filter(Objects::nonNull)
        .filter(e -> e.getValue() > 0.0)
        .max(Comparator.comparingDouble(Map.Entry::getValue))
        .map(Map.Entry::getKey);
  }

}
