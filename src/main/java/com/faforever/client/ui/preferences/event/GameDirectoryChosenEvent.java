package com.faforever.client.ui.preferences.event;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Value
public class GameDirectoryChosenEvent {
  @Nullable
  private Path path;
  String commandLineOptions;
  private Optional<CompletableFuture<Path>> future;
  private String modTechnicalName;
}
