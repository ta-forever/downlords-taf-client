package com.faforever.client.remote.domain;

import java.util.HashMap;
import java.util.Map;

public enum MessageTarget {
  GAME("game"),
  CONNECTIVITY("connectivity"),
  // Generic forward target for tournament commands. The server forwards
  // these without parsing the body, so new commands can be added without
  // a server rebuild.
  TOURNAMENT("tournament"),
  // Same generic-forward pattern for live-game wagering (WAGER_DESIGN.md §14): the lobby
  // forwards these unparsed to wager_service, so adding/changing a wager command needs no
  // lobby rebuild.
  WAGER("wager"),
  CLIENT(null);

  private static final Map<String, MessageTarget> fromString;

  static {
    fromString = new HashMap<>();
    for (MessageTarget messageTarget : values()) {
      fromString.put(messageTarget.string, messageTarget);
    }
  }

  private String string;

  MessageTarget(String string) {
    this.string = string;
  }

  public static MessageTarget fromString(String string) {
    return fromString.get(string);
  }

  public String getString() {
    return string;
  }
}
