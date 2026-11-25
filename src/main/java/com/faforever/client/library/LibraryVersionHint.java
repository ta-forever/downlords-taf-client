package com.faforever.client.library;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * How a recorded Library mod version (a host's game, or later a replay) differs from the locally installed one, as
 * the two strings to show in the hint ("Host plays Talon 1.03, you have 1.02"). Pure: no UI, no files. Only things
 * the recording actually holds are compared, so a game without a recorded version never yields a hint.
 *
 * @param recordedVersion the recorded version, with its revision when only the revision differs
 * @param localVersion    the local version, with its revision when only the revision differs
 */
public record LibraryVersionHint(String recordedVersion, String localVersion) {

  public static Optional<LibraryVersionHint> compare(@Nullable String recordedVersion, @Nullable Integer recordedRevision,
                                                     String localVersion, @Nullable Integer localRevision) {
    if (recordedVersion == null || recordedVersion.isBlank()) {
      return Optional.empty();
    }
    boolean versionDiffers = !recordedVersion.trim().equals(localVersion.trim());
    boolean revisionDiffers = recordedRevision != null && localRevision != null && !recordedRevision.equals(localRevision);
    if (versionDiffers) {
      return Optional.of(new LibraryVersionHint(recordedVersion, localVersion));
    }
    if (revisionDiffers) {
      return Optional.of(new LibraryVersionHint(withRevision(recordedVersion, recordedRevision), withRevision(localVersion, localRevision)));
    }
    return Optional.empty();
  }

  private static String withRevision(String version, Integer revision) {
    return version + " (revision " + revision + ")";
  }
}
