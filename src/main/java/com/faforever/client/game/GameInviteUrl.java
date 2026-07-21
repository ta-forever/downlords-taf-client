package com.faforever.client.game;

import java.util.regex.Pattern;

public final class GameInviteUrl {

  private static final String DEFAULT_WEBSITE_BASE_URL = "https://www.taforever.com";
  private static final String JOIN_GAME_PATH = "/play/join/";

  private GameInviteUrl() {
  }

  public static String build(String websiteBaseUrl, int gameId) {
    return normalizeWebsiteBaseUrl(websiteBaseUrl) + JOIN_GAME_PATH + gameId;
  }

  public static Pattern pattern(String websiteBaseUrl) {
    return Pattern.compile(Pattern.quote(normalizeWebsiteBaseUrl(websiteBaseUrl)) + JOIN_GAME_PATH + "(\\d+)/?");
  }

  private static String normalizeWebsiteBaseUrl(String websiteBaseUrl) {
    String normalized = websiteBaseUrl == null || websiteBaseUrl.isBlank()
        ? DEFAULT_WEBSITE_BASE_URL
        : websiteBaseUrl;
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
