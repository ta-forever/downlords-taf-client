package com.faforever.client.library;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What one Library mod game launch tells Open Annihilation, and the {@code .oalaunch} file that says it.
 * Pure: values in, text out.
 *
 * <p>The content is exactly the base game, the mod and the expansions the mod requires; nothing optional is ever
 * added. A host sets the map and rules; a joiner leaves both out because the host's rules are the ones that play.
 * See OA's docs/command-line.md "Launch files".
 *
 * @param host       true to host, false to join
 * @param mod        the mod's id (OA resolves whichever version it has installed); null for a TAF featured mod,
 *                   whose game folder is named on the command line: the file then has no content group (and a null
 *                   {@code mapKey} leaves the battle room's own map)
 * @param gameName   the host's session name; null for a joiner
 * @param address    the address a joiner connects to (gpgnet4ta's loopback proxy); null for a host
 * @param dplayGuid  the per-game DirectPlay application GUID gpgnet4ta answers under
 * @param mapKey     the host's map, as {@code --list-content} keys it ({@code name@pack} for a pack map)
 * @param statusFile where OA writes its status stream (JSON lines)
 * @param fixedPositions host only: players start at the map's start positions in lobby order (TAF's fixed positions
 *                       pill) rather than at random
 */
public record LibraryLaunch(boolean host, String mod, String baseGame, List<String> expansions, String playerName,
                            String gameName, String address, String dplayGuid, String mapKey, Path statusFile, boolean fixedPositions) {

  /** OA cuts player names to this many characters. */
  static final int MAX_NAME_LENGTH = 16;
  /** A commander's death ends that player's game, as in TAF's featured mods. */
  static final String COMMANDER_DEATH = "ends";

  public static LibraryLaunch host(String mod, String baseGame, List<String> expansions, String playerName,
                                   String gameName, String dplayGuid, String mapKey, Path statusFile) {
    return host(mod, baseGame, expansions, playerName, gameName, dplayGuid, mapKey, statusFile, false);
  }

  public static LibraryLaunch host(String mod, String baseGame, List<String> expansions, String playerName,
                                   String gameName, String dplayGuid, String mapKey, Path statusFile,
                                   boolean fixedPositions) {
    return new LibraryLaunch(true, mod, baseGame, List.copyOf(expansions), playerName, gameName, null, dplayGuid,
        mapKey, statusFile, fixedPositions);
  }

  public static LibraryLaunch join(String mod, String baseGame, List<String> expansions, String playerName,
                                   String address, String dplayGuid, Path statusFile) {
    return new LibraryLaunch(false, mod, baseGame, List.copyOf(expansions), playerName, null, address, dplayGuid,
        null, statusFile, false);
  }

  /** The text of the {@code .oalaunch} file (YAML; UTF-8; the first line is {@code oalaunch: 1}). */
  public String toOaLaunch() {
    StringBuilder out = new StringBuilder("oalaunch: 1\n");
    out.append("game:\n");
    out.append("  role: ").append(host ? "host" : "join").append('\n');
    if (host) {
      out.append("  name: ").append(quote(gameName)).append('\n');
    } else {
      out.append("  address: ").append(quote(address)).append('\n');
    }
    out.append("  directplay:\n");
    out.append("    application-guid: ").append(quote(dplayGuid)).append('\n');
    out.append("player:\n");
    out.append("  name: ").append(quote(clip(playerName))).append('\n');
    if (host) {
      if (mapKey != null) {
        out.append("map: ").append(quote(mapKey)).append('\n');
      }
      out.append("rules:\n");
      out.append("  commander-death: ").append(COMMANDER_DEATH).append('\n');
      out.append("  line-of-sight: true\n");
      out.append("  start-positions: ").append(fixedPositions ? "fixed" : "random").append('\n');
      out.append("  lock-options: false\n");
    }
    if (mod != null) {
      out.append("content:\n");
      out.append("  base-game: ").append(quote(baseGame)).append('\n');
      out.append("  expansions: [")
          .append(expansions.stream().map(LibraryLaunch::quote).collect(Collectors.joining(", ")))
          .append("]\n");
      out.append("  mod: ").append(quote(mod)).append('\n');
    }
    out.append("status:\n");
    out.append("  file: ").append(quote(statusFile.toString())).append('\n');
    return out.toString();
  }

  static String clip(String name) {
    String trimmed = name == null ? "" : name.trim();
    return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
  }

  /** A YAML double-quoted scalar. */
  static String quote(String value) {
    StringBuilder out = new StringBuilder("\"");
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\x%02x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }
}
