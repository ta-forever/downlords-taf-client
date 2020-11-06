package com.faforever.client.game;

import java.util.HashMap;
import java.util.Map;

/**
 * An enumeration of "known" featured mods. They might be added and removed to the server arbitrarily, which is why
 * the client should rely as little as possible on this static definition.
 */
public enum KnownFeaturedMod {
  TACC("tacc", "tacc", "TotalA.exe"),
  LADDER_1V1("ladder1v1", "tacc", "TotalA.exe"),
  TAESC("taesc", "taesc", "TotalA.exe"),
  TAZERO("tazero", "tazero", "TotalA.exe"),
  TAMAYHEM("tamayhem", "tamayhem", "TotalA.exe"),
  COOP("coop", "coop", "TotalA.exe");

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
  private final String executableFileName;

  KnownFeaturedMod(String technicalName, String baseGameName, String executableFileName) {
    this.technicalName = technicalName;
    this.baseGameName = baseGameName;
    this.executableFileName = executableFileName;
  }

  public static KnownFeaturedMod fromString(String string) {
    return fromString.get(string);
  }

  public String getTechnicalName() {
    return technicalName;
  }
  public String getBaseGameName() { return baseGameName; }
  public String getExecutableFileName() { return executableFileName; }
}
