package com.faforever.client.library;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Open Annihilation command lines for Library mod games. Pure, so the argument shapes are unit-testable.
 *
 * <p>Unlike {@link com.faforever.client.fa.oa.OaCommands}, none of these pass {@code --game-dir}: a Library game
 * uses the player's own OA install, game folder and user folder (where the Library keeps its mods).
 */
public final class LibraryCommands {

  private static final Pattern SIM_HASH = Pattern.compile("^sha256-sim\\s+([0-9a-f]{64})\\s*$", Pattern.MULTILINE);

  private LibraryCommands() {
  }

  /** Starts a network game from a {@code .oalaunch} file. */
  public static List<String> launch(Path oaExe, Path launchFile, String extraArgs) {
    List<String> command = new ArrayList<>(List.of(oaExe.toString(), "-c", launchFile.toString()));
    command.addAll(split(extraArgs));
    return command;
  }

  /** Lists the maps, mods and expansions OA can play (JSON on the last stdout line). */
  public static List<String> listContent(Path oaExe) {
    return new ArrayList<>(List.of(oaExe.toString(), "--list-content", "--json"));
  }

  /** Lists what OA can play with the given mod folder and content, which decides the map list. */
  public static List<String> listContent(Path oaExe, Path modDir, String baseGame, List<String> expansions) {
    List<String> command = listContent(oaExe);
    command.addAll(List.of("--mod-dir", modDir.toString()));
    command.add("--content");
    command.add(expansions.isEmpty() ? baseGame : baseGame + ":" + String.join(",", expansions));
    return command;
  }

  /** Prints the profile a mod folder would play and its sim hash. */
  public static List<String> printProfile(Path oaExe, Path modDir) {
    return new ArrayList<>(List.of(oaExe.toString(), "--mod-dir", modDir.toString(), "--print-profile"));
  }

  /** The sim hash line ({@code sha256-sim  <64 hex>}) of {@code --print-profile} output, if the mod has a profile. */
  public static Optional<String> simHash(String printProfileOutput) {
    Matcher matcher = SIM_HASH.matcher(printProfileOutput);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  /** Whether a line of OA's status stream says the match is now running. */
  public static boolean isMatchStarted(String statusLine) {
    return statusLine.contains("\"event\":\"match\"") && statusLine.contains("\"change\":\"started\"");
  }

  private static List<String> split(String extraArgs) {
    if (extraArgs == null || extraArgs.isBlank()) {
      return List.of();
    }
    return Arrays.stream(extraArgs.trim().split("\\s+")).toList();
  }
}
