package com.faforever.client.map.event;

import java.nio.file.Path;

public class MapUploadedEvent {
  private Path stagingDirectory;

  public MapUploadedEvent(Path stagingDirectory) {
    this.stagingDirectory = stagingDirectory;
  }

  public Path getStagingDirectory() {
    return stagingDirectory;
  }
}
