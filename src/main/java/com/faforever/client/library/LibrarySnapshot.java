package com.faforever.client.library;

import java.util.List;

/**
 * What the player's Open Annihilation looked like when last checked: whether it can play Library mods at all, and
 * which Library mods are installed. Games are judged against this.
 */
public record LibrarySnapshot(LibraryReadiness readiness, List<LibraryMod> mods) {

  public LibraryJoinVerdict verdictFor(LibraryGameInfo game) {
    return LibraryJoinVerdict.decide(game, readiness, mods);
  }
}
