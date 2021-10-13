package com.faforever.client.preferences;

public enum TadaIntegrationOption {
  ASK("settings.tada.ask"),
  INTEGRATED("settings.tada.integrated"),
  BROWSER("settings.tada.browser");

  private final String displayNameKey;

  TadaIntegrationOption(String displayNameKey) {
    this.displayNameKey = displayNameKey;
  }
  public String getI18nKey() {
    return displayNameKey;
  }
}

