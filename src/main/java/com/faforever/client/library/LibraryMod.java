package com.faforever.client.library;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;

/**
 * A Library mod installed in Open Annihilation's user folder: what its {@code oamod.yaml} says, plus where it
 * came from if OA recorded that. TAF never installs these; OA's Library does.
 *
 * @param revision packaging revision of the version, or null if the profile has none
 * @param registry the registry OA recorded when it installed the mod, or null for a mod OA has no record for
 * @param folder   the mod's folder under {@code Mods}
 */
public record LibraryMod(String id, String name, String version, @Nullable Integer revision,
                         @Nullable String registry, String baseGame, List<String> requiredExpansions, Path folder) {

  /** The base game of a mod whose profile names none. */
  public static final String DEFAULT_BASE_GAME = "totala";
}
