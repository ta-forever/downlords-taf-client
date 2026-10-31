package com.faforever.client.library;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

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

  /** Browser watch (livescene) cannot draw Library mods' units, so it is never offered for their games. */
  public static boolean offersBrowserWatch(@Nullable String featuredModTechnicalName) {
    return !isLibraryMod(featuredModTechnicalName);
  }

  /**
   * The fields as the API stores them with a vault game (expansions as a JSON array in one column); empty for a
   * game without a Library mod id. Unreadable expansions count as none rather than failing the replay list.
   */
  public static Optional<LibraryGameInfo> fromStored(@Nullable String modId, @Nullable String name,
                                                     @Nullable String registry, @Nullable String version,
                                                     @Nullable Integer revision, @Nullable String simHash,
                                                     @Nullable String baseGame, @Nullable String expansionsJson) {
    if (modId == null || modId.isBlank()) {
      return Optional.empty();
    }
    List<String> expansions = List.of();
    if (expansionsJson != null && !expansionsJson.isBlank()) {
      try {
        String[] parsed = new Gson().fromJson(expansionsJson, String[].class);
        expansions = parsed == null ? List.of() : List.of(parsed);
      } catch (JsonParseException e) {
        expansions = List.of();
      }
    }
    return Optional.of(new LibraryGameInfo(modId, name == null || name.isBlank() ? modId : name, registry, version,
        revision, simHash, baseGame, expansions));
  }

  public static LibraryGameInfo of(LibraryMod mod, @Nullable String simHash) {
    return of(mod, simHash, mod.requiredExpansions());
  }

  /** @param expansions what the game plays with ({@link LibraryContent#gameExpansions}) */
  public static LibraryGameInfo of(LibraryMod mod, @Nullable String simHash, List<String> expansions) {
    return new LibraryGameInfo(mod.id(), mod.name(), mod.registry(), mod.version(), mod.revision(), simHash,
        mod.baseGame(), List.copyOf(expansions));
  }

  /** What the game list shows in place of the umbrella's name: "Talon 1.03". */
  public String displayLabel() {
    return version == null || version.isBlank() ? name : name + " " + version;
  }
}
