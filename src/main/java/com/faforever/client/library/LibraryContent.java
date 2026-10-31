package com.faforever.client.library;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which OA expansions a Library mod game plays with. Pure.
 *
 * <p>OA's {@code --content} (and a launch file's {@code content.expansions}) replaces the Library's own choice, so
 * naming only a mod's {@code requiredExpansions} switches everything else off. Most profiles name none (they still
 * say {@code requires.base: ta-3.1c}), which left Core Contingency and Battle Tactics, and so their maps, out.
 * A game therefore plays with the mod's required expansions plus every expansion the host has installed that
 * extends 3.1c, except Music, which changes no rules and is never compared between players.
 */
public final class LibraryContent {

  /** The expansions of 3.1c in OA's dependency order (an expansion comes after those it needs). */
  static final List<String> TOTALA_EXPANSIONS = List.of(
      "campaign", "core-contingency", "battle-tactics", "cavedog-units", "cavedog-tilesets");

  private static final String TOTALA = "totala";

  private LibraryContent() {
  }

  /**
   * @param baseGame  the mod's base game
   * @param required  the expansions the mod's profile requires, in the order written
   * @param installed the expansions OA lists as installed ({@code --list-content --json})
   */
  public static List<String> gameExpansions(String baseGame, List<String> required, List<String> installed) {
    if (!TOTALA.equals(baseGame)) {
      return List.copyOf(required);
    }
    Set<String> expansions = new LinkedHashSet<>();
    for (String expansion : TOTALA_EXPANSIONS) {
      if (required.contains(expansion) || installed.contains(expansion)) {
        expansions.add(expansion);
      }
    }
    // a required id this client does not know keeps its place after the known ones
    expansions.addAll(required);
    return List.copyOf(new ArrayList<>(expansions));
  }

  /** The host's expansions, in the host's order, without those this player has not installed. */
  public static List<String> expansionsThisPlayerHas(List<String> hostExpansions, List<String> installed) {
    return hostExpansions.stream().filter(installed::contains).toList();
  }
}
