package com.faforever.client.remote.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum PlayerStatus {

  IDLE("idle"),
  HOSTING("hosting"),  // created game but did not launch TA battleroom yet
  HOSTED("hosted"),    // joined game but did not launch TA battleroom yet
  JOINING("joining"),  // created game and launched TA battleroom
  JOINED("joined"),    // joined game and launched TA battleroom
  PLAYING("playing");  // game in progress

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final Map<String, PlayerStatus> fromString;

  static {
    fromString = new HashMap<>();
    for (PlayerStatus playerStatus : values()) {
      fromString.put(playerStatus.string, playerStatus);
    }
  }

  private final String string;

  PlayerStatus(String string) {
    this.string = string;
  }

  public static PlayerStatus fromString(String string) {
    PlayerStatus playerStatus = fromString.get(string != null ? string.toLowerCase(Locale.US) : null);
    if (playerStatus == null) {
      logger.warn("Unknown player state: {}", string);
      return IDLE;
    }
    return playerStatus;
  }

  public String getString() {
    return string;
  }
}
