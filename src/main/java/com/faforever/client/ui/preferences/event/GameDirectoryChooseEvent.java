package com.faforever.client.ui.preferences.event;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Event to be fired whenever the game directory needs to be set.
 */
public class GameDirectoryChooseEvent {
  private final String baseGameName;
  private final CompletableFuture<Path> future;

  public GameDirectoryChooseEvent(String baseGameName) {
    this.future = null;
    this.baseGameName = baseGameName;
  }

  public GameDirectoryChooseEvent(String baseGameName, @Nullable CompletableFuture<Path> future) {
    this.future = future;
    this.baseGameName = baseGameName;
  }

  public Optional<CompletableFuture<Path>> getFuture() {
    return Optional.ofNullable(future);
  }
  public String getBaseGameName() { return baseGameName; }
}
