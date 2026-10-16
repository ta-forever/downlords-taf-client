package com.faforever.client.library;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Whether the local player can join a hosted Library mod game, given what is installed locally. The version
 * difference between host and joiner is a hint only and never blocks the join. Pure: no UI, no files.
 *
 * @param modName           the hosted mod's name, for messages
 * @param hostVersion       the host's version (with the revision when only the revision differs), for the hint
 * @param localVersion      the local version, only set for {@link Outcome#JOINABLE_WITH_VERSION_HINT}
 * @param requiredOaVersion the server's minimum OA version, only set for {@link Outcome#NEEDS_OA}
 */
public record LibraryJoinVerdict(Outcome outcome, String modName, @Nullable String hostVersion,
                                 @Nullable String localVersion, @Nullable String requiredOaVersion) {

  public enum Outcome {
    JOINABLE,
    JOINABLE_WITH_VERSION_HINT,
    NEEDS_MOD,
    /** OA's first-run setup is not done. */
    NEEDS_SETUP,
    /** No OA, or its version is not allowed. */
    NEEDS_OA
  }

  public static LibraryJoinVerdict decide(LibraryGameInfo game, LibraryReadiness readiness, List<LibraryMod> installed) {
    switch (readiness.status()) {
      case NO_OA, OA_VERSION_NOT_ALLOWED -> {
        return new LibraryJoinVerdict(Outcome.NEEDS_OA, game.name(), null, null, readiness.requiredVersion());
      }
      case SETUP_NOT_DONE -> {
        return new LibraryJoinVerdict(Outcome.NEEDS_SETUP, game.name(), null, null, null);
      }
      default -> {
      }
    }
    List<LibraryMod> candidates = installed.stream().filter(mod -> mod.id().equals(game.modId())).toList();
    if (candidates.isEmpty()) {
      return new LibraryJoinVerdict(Outcome.NEEDS_MOD, game.name(), null, null, null);
    }
    LibraryMod local = localModFor(game, candidates).orElseThrow();
    return hintFor(game, local)
        .map(hint -> new LibraryJoinVerdict(Outcome.JOINABLE_WITH_VERSION_HINT, game.name(), hint.recordedVersion(),
            hint.localVersion(), null))
        .orElseGet(() -> new LibraryJoinVerdict(Outcome.JOINABLE, game.name(), null, null, null));
  }

  /**
   * The installed copy of the game's mod to play it with: the one whose version matches the recorded one, else
   * the newest. Empty when the mod is not installed.
   */
  public static Optional<LibraryMod> localModFor(LibraryGameInfo game, List<LibraryMod> installed) {
    List<LibraryMod> candidates = installed.stream().filter(mod -> mod.id().equals(game.modId())).toList();
    return candidates.stream().filter(mod -> hintFor(game, mod).isEmpty()).findFirst()
        .or(() -> candidates.stream().max((a, b) -> LibraryDiscovery.compareVersions(a.version(), b.version())));
  }

  private static Optional<LibraryVersionHint> hintFor(LibraryGameInfo game, LibraryMod local) {
    return LibraryVersionHint.compare(game.version(), game.revision(), local.version(), local.revision());
  }

  /** The join cannot go ahead; the player has to fix something first. */
  public boolean blocksJoin() {
    return outcome == Outcome.NEEDS_MOD || outcome == Outcome.NEEDS_SETUP || outcome == Outcome.NEEDS_OA;
  }

  public boolean hasVersionHint() {
    return outcome == Outcome.JOINABLE_WITH_VERSION_HINT;
  }
}
