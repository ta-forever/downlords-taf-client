package com.faforever.client.game;

import java.util.HashMap;
import java.util.Map;

/**
 * An enumeration of "known" featured mods. They might be added and removed to the server arbitrarily, which is why
 * the client should rely as little as possible on this static definition.
 */
public enum KnownFeaturedMod {
  TACC("tacc", "tacc"),
  LADDER_1V1("ladder1v1", "tacc"),
  TAESC("taesc", "taesc"),
  TAZERO("tazero", "tazero"),
  TAMAYHEM("tamayhem", "tamayhem"),
  COOP("coop", "coop");

  public static final KnownFeaturedMod DEFAULT = TACC;

  private static final Map<String, KnownFeaturedMod> fromString;

  static {
    fromString = new HashMap<>();
    for (KnownFeaturedMod knownFeaturedMod : values()) {
      fromString.put(knownFeaturedMod.technicalName, knownFeaturedMod);
    }
  }

  private final String technicalName;
  private final String baseGameName;

  KnownFeaturedMod(String technicalName, String baseGameName) {
    this.technicalName = technicalName;
    this.baseGameName = baseGameName;
  }

  public static KnownFeaturedMod fromString(String string) {
    return fromString.get(string);
  }

  public String getTechnicalName() {
    return technicalName;
  }
  public String getBaseGameName() { return baseGameName; }
}
