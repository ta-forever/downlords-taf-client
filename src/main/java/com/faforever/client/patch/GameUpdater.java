package com.faforever.client.patch;

import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.mod.FeaturedMod;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Execute all necessary tasks such as downloading featured mod, patching the executable, downloading other sim mods and
 * generating the init file in order to put the preferences into a runnable state for a specific featured mod and version.
 */
public interface GameUpdater {

  /**
   * Adds an updater to the chain. For each mod to update, the first updater which can update a mod will be called.
   */
  GameUpdater addFeaturedModUpdater(FeaturedModUpdater featuredModUpdater);

  /**
   * @param featuredMod the featured "base" mod is the one onto which other mods base on (usually {@link
   * KnownFeaturedMod#DEFAULT}).
   * @return version that was finally updated to (for GitLfsFeaturedModUpdater, a git commit hash)
   */
  CompletableFuture<String> update(FeaturedMod featuredMod, String version);

  CompletableFuture<List<FeaturedMod>> getFeaturedMods();
}
