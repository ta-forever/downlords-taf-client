package com.faforever.client.preferences;

public enum AskAlwaysOrNever {
  ASK("ask"),
  ALWAYS("always"),
  NEVER("never");

  private final String displayNameKey;

  AskAlwaysOrNever(String displayNameKey) {
    this.displayNameKey = displayNameKey;
  }
  public String getI18nKey() {
    return displayNameKey;
  }
}

