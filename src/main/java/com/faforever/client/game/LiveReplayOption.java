package com.faforever.client.game;

public enum LiveReplayOption {
  // These must match the preview URLs
  DISABLED(-1, "liveReplay.disabled"),
  ZERO_DELAY(0, "liveReplay.zeroDelay"),
  FIVE_MINUTES(300, "liveReplay.fiveMinutes");

  private final int delaySeconds;
  private final String i18nKey;

  LiveReplayOption(int delaySeconds, String i18nKey) {
    this.delaySeconds = delaySeconds;
    this.i18nKey = i18nKey;
  }

  public String getI18nKey() {
    return i18nKey;
  }

  public int getDelaySeconds() {
    return delaySeconds;
  }
}

