package com.faforever.client.chat;

import lombok.Getter;

public enum RatingMetric {
  TRUESKILL("userInfo.ratingHistory.trueskill"),
  STREAK("userInfo.ratingHistory.streak");

  @Getter
  private final String i18nKey;

  RatingMetric(String i18nKey) {
    this.i18nKey = i18nKey;
  }
}
