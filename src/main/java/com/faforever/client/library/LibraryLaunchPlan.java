package com.faforever.client.library;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;

/**
 * What a Library mod game or replay needs OA to load, known before gpgnet4ta asks the launch server to start OA.
 *
 * @param mapKey    the host's map key as {@code --list-content} gives it; null for a joiner, whose map is the host's
 * @param modFolder the installed mod's folder, for a live replay (OA is then started with {@code --mod-dir}); null
 *                  for a game, which names the mod by id in its launch file
 */
public record LibraryLaunchPlan(String mod, String baseGame, List<String> expansions, @Nullable String mapKey,
                                @Nullable Path modFolder) {

  public LibraryLaunchPlan(String mod, String baseGame, List<String> expansions, @Nullable String mapKey) {
    this(mod, baseGame, expansions, mapKey, null);
  }

  /** The plan for watching a game's replay with the installed copy of its mod. */
  public static LibraryLaunchPlan forReplay(LibraryMod installed, LibraryGameInfo recorded) {
    return new LibraryLaunchPlan(installed.id(),
        recorded.baseGame() == null ? LibraryMod.DEFAULT_BASE_GAME : recorded.baseGame(), recorded.expansions(), null,
        installed.folder());
  }
}
