package com.faforever.client.preferences;

public enum MaxPacketSizeOption {
  TINY("tiny"),
  NORMAL("normal"),
  JUMBO("jumbo");

  private final String displayNameKey;

  MaxPacketSizeOption(String displayNameKey) {
    this.displayNameKey = displayNameKey;
  }
  public String getI18nKey() {
    return displayNameKey;
  }
}
