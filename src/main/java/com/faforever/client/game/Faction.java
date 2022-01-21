package com.faforever.client.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

public enum Faction {
  // Order is crucial
  // Same order as the info from the server (1=CORE, 6=ARM)
  ZERO_FACTION("Zero"),
  CORE("Core"),
  GOK("GoK"),
  THREE_FACTION("Three"),
  FOUR_FACTION("Four"),
  FIVE_FACTION("Five"),
  ARM("Arm"),
  RANDOM("random");

  private static final Map<String, Faction> fromString;

  static {
    fromString = new HashMap<>();
    for (Faction faction : values()) {
      fromString.put(faction.string, faction);
    }
  }

  private final String string;

  Faction(String string) {
    this.string = string;
  }

  @JsonCreator
  public static Faction fromTaValue(int value) {
    //return Faction.values()[value];
    return RANDOM;  // faction is somehow getting muddled up before making it into database
  }

  public static Faction fromString(String string) {
    return fromString.get(string);
  }

  /**
   * Returns the faction value used as in "Total Annihilation".
   */
  @JsonValue
  public int toTaValue() {
    return ordinal();
  }

  /**
   * Returns the string value of the faction, as used in the game and the server.
   */
  public String getString() {
    return string;
  }
}
