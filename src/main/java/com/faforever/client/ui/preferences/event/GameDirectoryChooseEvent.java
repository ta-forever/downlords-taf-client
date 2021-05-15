package com.faforever.client.ui.preferences.event;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Event to be fired whenever the game directory needs to be set.
 */
public class GameDirectoryChooseEvent {
  private final String modTechnicalName;
  private final CompletableFuture<Path> future;

  public GameDirectoryChooseEvent(String modTechnicalName) {
    this.future = null;
    this.modTechnicalName = modTechnicalName;
  }

  public GameDirectoryChooseEvent(String modTechnicalName, @Nullable CompletableFuture<Path> future) {
    this.future = future;
    this.modTechnicalName = modTechnicalName;
  }

  public Optional<CompletableFuture<Path>> getFuture() {
    return Optional.ofNullable(future);
  }
  public String getModTechnicalName() { return modTechnicalName; }
}
