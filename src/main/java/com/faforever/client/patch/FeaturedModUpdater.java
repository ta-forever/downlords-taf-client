package com.faforever.client.patch;

import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.FeaturedModVersion;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface FeaturedModUpdater {

  /**
   * Updates the specified featured mod to the specified version. If {@code version} is null, it will update to the
   * latest version
   */
  CompletableFuture<String> updateMod(FeaturedMod featuredMod, @Nullable String version);

  /**
   * Returns {@code true} if this updater is able to update the specified featured mod.
   */
  boolean canUpdate(FeaturedMod featuredMod);
}
