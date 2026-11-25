package com.faforever.client.library;

import com.faforever.client.fa.oa.OaPolicy;
import com.faforever.client.fa.oa.OaVersion;
import com.faforever.client.update.ClientConfiguration.OaMod;

import javax.annotation.Nullable;

/**
 * Whether this player's Open Annihilation can host or join Library mod games at all: there is an OA, its version is
 * in the range the server allows for {@code oalibrary}, and its first-run setup is done ({@code --list-content}
 * exits 0; exit 1 means OA wants to be opened once first). Pure: the facts in, the verdict out.
 *
 * @param requiredVersion the server's minimum OA version, for messages ("needs Open Annihilation 0.8+")
 * @param maxVersion      the server's maximum OA version, or null
 * @param foundVersion    the OA version found, or null if there is none or it cannot be told
 */
public record LibraryReadiness(Status status, @Nullable String requiredVersion, @Nullable String maxVersion,
                               @Nullable String foundVersion) {

  public enum Status {
    READY,
    /** No OA, or the server offers none for Library mods. */
    NO_OA,
    OA_VERSION_NOT_ALLOWED,
    /** {@code --list-content} failed: OA has not completed its first-run setup. */
    SETUP_NOT_DONE
  }

  /**
   * @param policy          the server's {@code oalibrary} entry, or null if it has none
   * @param oaFound         whether a usable OA executable exists
   * @param version         its version, or null if it cannot be told (not held against it)
   * @param listContentExit the exit code of {@code --list-content --json}, or null if it was not run
   */
  public static LibraryReadiness assess(@Nullable OaMod policy, boolean oaFound, @Nullable OaVersion version,
                                        @Nullable Integer listContentExit) {
    String min = policy == null ? null : policy.getMinVersion();
    String max = policy == null ? null : policy.getMaxVersion();
    String found = version == null ? null : version.toString();
    if (policy == null || !oaFound) {
      return new LibraryReadiness(Status.NO_OA, min, max, found);
    }
    if (version != null && !OaPolicy.isVersionAllowed(policy, version)) {
      return new LibraryReadiness(Status.OA_VERSION_NOT_ALLOWED, min, max, found);
    }
    if (listContentExit != null && listContentExit != 0) {
      return new LibraryReadiness(Status.SETUP_NOT_DONE, min, max, found);
    }
    return new LibraryReadiness(Status.READY, min, max, found);
  }

  public boolean isReady() {
    return status == Status.READY;
  }
}
