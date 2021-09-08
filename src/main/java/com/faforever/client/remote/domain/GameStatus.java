package com.faforever.client.remote.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum GameStatus {

  UNKNOWN("unknown"),
  SPAWNING("spawning"),     // OS has spawned the executable but things are still initialising
  STAGING("staging"),       // chat room has been opened but game hasn't been launched
  BATTLEROOM("battleroom"), // players are in game battleroom. new players can still join
  LAUNCHING("launching"),   // game has been started. new players can no longer join. teams have not been finalised
  LIVE("live"),             // game in progress, teams have been finalised
  ENDED("ended");           // game has terminated

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final Map<String, GameStatus> fromString;

  static {
    fromString = new HashMap<>();
    for (GameStatus gameStatus : values()) {
      fromString.put(gameStatus.string, gameStatus);
    }
  }

  private final String string;

  GameStatus(String string) {
    this.string = string;
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
}
