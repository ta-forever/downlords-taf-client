package com.faforever.client.remote.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.faforever.client.theme.UiService.CHAT_LIST_STATUS_HOSTED;
import static com.faforever.client.theme.UiService.CHAT_LIST_STATUS_HOSTING;
import static com.faforever.client.theme.UiService.CHAT_LIST_STATUS_PLAYING;

public enum GameStatus {

  UNKNOWN("unknown", null),
  SPAWNING("spawning", null),        // OS has spawned the executable but things are still initialising
  STAGING("staging", CHAT_LIST_STATUS_HOSTING),      // chat room has been opened but game hasn't been launched
  BATTLEROOM("battleroom", CHAT_LIST_STATUS_HOSTED), // players are in game battleroom. new players can still join
  LAUNCHING("launching", CHAT_LIST_STATUS_PLAYING),  // game has been started. new players can no longer join. teams have not been finalised
  LIVE("live", CHAT_LIST_STATUS_PLAYING),            // game in progress, teams have been finalised
  ENDED("ended", null);               // game has terminated

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final Map<String, GameStatus> fromString;

  static {
    fromString = new HashMap<>();
    for (GameStatus gameStatus : values()) {
      fromString.put(gameStatus.string, gameStatus);
    }
  }

  private final String string;
  private final String themeImageFileName;

  GameStatus(String string, String themeImageFileName) {
    this.string = string;
    this.themeImageFileName = themeImageFileName;
  }

  public boolean isOpen() {
    return this == STAGING || this == BATTLEROOM;
  }

  public boolean isInProgress() {
    return this == LAUNCHING || this == LIVE;
  }

  public static GameStatus fromString(String string) {
    GameStatus gameStatus = fromString.get(string != null ? string.toLowerCase(Locale.US) : null);
    if (gameStatus == null) {
      logger.warn("Unknown game state: {}", string);
      return UNKNOWN;
    }
    return gameStatus;
  }

  public String getString() {
    return string;
  }

  public String getThemeImageFileName() { return themeImageFileName; }
}
