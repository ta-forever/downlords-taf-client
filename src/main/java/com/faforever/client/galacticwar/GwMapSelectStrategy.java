package com.faforever.client.galacticwar;

public enum GwMapSelectStrategy {
  REGEX("REGEX"),
  MAP_POOL("MAP_POOL");

  private final String name;

  GwMapSelectStrategy(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
