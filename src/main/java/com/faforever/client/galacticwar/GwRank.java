package com.faforever.client.galacticwar;

public enum GwRank {
  PRIVATE("Private", 1),
  CORPORAL("Corporal", 2),
  SERGEANT("Sergeant", 3),
  LIEUTENANT("Lieutenant", 4),
  CAPTAIN("Captain", 5),
  MAJOR("Major", 6),
  COLONEL("Colonel", 7),
  GENERAL("General", 8),
  COMMANDER("Commander", 9);

  private final String displayName;
  private final int tier;

  GwRank(String displayName, int tier) {
    this.displayName = displayName;
    this.tier = tier;
  }

  public String getDisplayName() {
    return displayName;
  }

  public int getTier() {
    return tier;
  }

  @Override
  public String toString() {
    return displayName;
  }
}