package com.faforever.client.galacticwar;

import com.faforever.client.game.Faction;
import com.google.gson.annotations.SerializedName;
import lombok.Value;

import java.util.Map;

@Value
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
  Map<Integer, Map<Faction, Double>> belligerents;

  @SerializedName("capital_of")
  Faction capitalOf;

  @SerializedName("controlled_by")
  Faction controlledBy;

  PlanetGraphics graphics;

  public String toString() {
    return name;
  }
}
