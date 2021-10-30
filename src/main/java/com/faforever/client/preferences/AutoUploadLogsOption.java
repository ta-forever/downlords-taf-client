package com.faforever.client.preferences;

public enum AutoUploadLogsOption {
  ASK("settings.autoLogsUpload.ask"),
  ALLOW("settings.autoLogsUpload.allow"),
  DENY("settings.autoLogsUpload.deny");

  private final String displayNameKey;

  AutoUploadLogsOption(String displayNameKey) {
    this.displayNameKey = displayNameKey;
  }
  public String getI18nKey() {
    return displayNameKey;
  }
}

