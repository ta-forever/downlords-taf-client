package com.faforever.client.replay;

/** Controls which replay details are included in a downloaded replay's file name. */
public record ReplayDownloadNameOptions(
    boolean includeDate,
    boolean includeMod,
    boolean includeMap,
    boolean includePlayers) {

  public static ReplayDownloadNameOptions all() {
    return new ReplayDownloadNameOptions(true, true, true, true);
  }
}
