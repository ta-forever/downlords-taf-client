package com.faforever.client.wager;

/**
 * Shared display labels for wager outcomes. For TEAM_WIN markets the outcome key is the raw
 * lobby team number (what results settle on), but the UI numbers teams the way the team card
 * does — {@code parseInt(key) - 1}, because lobby team 1 means "no team" — so wager team
 * numbers line up with the team cards everywhere (outcomes, chart, portfolio).
 */
public final class WagerLabels {

  private WagerLabels() {
  }

  public static String outcomeLabel(String marketType, String outcomeKey, String fallback) {
    if ("TEAM_WIN".equals(marketType) && outcomeKey != null) {
      try {
        return "Team " + (Integer.parseInt(outcomeKey.trim()) - 1);
      } catch (NumberFormatException ignored) {
        // not a numeric team key — fall through
      }
    }
    if (fallback != null) {
      return fallback;
    }
    return outcomeKey != null ? outcomeKey : "?";
  }
}
