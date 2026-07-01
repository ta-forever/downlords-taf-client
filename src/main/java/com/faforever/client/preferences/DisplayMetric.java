package com.faforever.client.preferences;

/**
 * Which standing a player identity shows by default (LADDER_POINTS_DESIGN.md §13.1). Swaps only
 * the static gauge (how a standing is represented); per-game deltas are always LP regardless.
 */
public enum DisplayMetric {
  LADDER_POINTS,
  RATINGS
}
