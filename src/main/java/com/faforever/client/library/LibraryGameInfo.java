package com.faforever.client.library;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The per-game Library fields: what the host's client sends with {@code game_host} and the server broadcasts in
 * {@code game_info}. Describes the content of a Library mod game, which is stored under the umbrella featured mod
 * {@link #UMBRELLA_FEATURED_MOD} (docs/adr/0001).
 */
public record LibraryGameInfo(String modId, String name, @Nullable String registry, @Nullable String version,
                              @Nullable Integer revision, @Nullable String simHash, @Nullable String baseGame,
                              List<String> expansions) {

  /** The technical name of the one featured mod every Library mod game is stored under. */
  public static final String UMBRELLA_FEATURED_MOD = "oalibrary";

  public static boolean isLibraryMod(@Nullable String featuredModTechnicalName) {
    return UMBRELLA_FEATURED_MOD.equalsIgnoreCase(featuredModTechnicalName);
  }

  public static LibraryGameInfo of(LibraryMod mod, @Nullable String simHash) {
    return new LibraryGameInfo(mod.id(), mod.name(), mod.registry(), mod.version(), mod.revision(), simHash,
        mod.baseGame(), List.copyOf(mod.requiredExpansions()));
  }

  /** What the game list shows in place of the umbrella's name: "Talon 1.03". */
  public String displayLabel() {
    return version == null || version.isBlank() ? name : name + " " + version;
  }
}
