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

  private static final String LOOPBACK = "127.0.0.1";

  private LibraryCommands() {
  }

  /** Starts a network game from a {@code .oalaunch} file. */
  public static List<String> launch(Path oaExe, Path launchFile, String extraArgs) {
    List<String> command = new ArrayList<>(List.of(oaExe.toString(), "-c", launchFile.toString()));
    command.addAll(split(extraArgs));
    return command;
  }

  /**
   * Local playback of a vault {@code .tad} with the mod folder and content the replay was recorded with. Not
   * {@code --game-dir}: the player's own OA install and Library are used.
   */
  public static List<String> playDemo(Path oaExe, Path modDir, String baseGame, List<String> expansions, Path demo,
                                      String extraArgs) {
    List<String> command = replayBase(oaExe, modDir, baseGame, expansions);
    command.addAll(List.of("--play-demo", demo.toString()));
    command.addAll(split(extraArgs));
    return command;
  }

  /** Loads a demo headlessly for one tick and exits: status 0 if OA can play it, else 1 with the reason. */
  public static List<String> checkDemo(Path oaExe, Path modDir, String baseGame, List<String> expansions, Path demo) {
    List<String> command = replayBase(oaExe, modDir, baseGame, expansions);
    command.addAll(List.of("--headless-check", "--play-demo", demo.toString(), "--match-ticks", "1"));
    return command;
  }

  /**
   * Joins the game replayer.exe hosts on loopback (live replay). OA refuses {@code --player-name} together with
   * {@code --replay-viewer}, so none is passed.
   */
  public static List<String> replayViewer(Path oaExe, Path modDir, String baseGame, List<String> expansions,
                                          String extraArgs) {
    List<String> command = replayBase(oaExe, modDir, baseGame, expansions);
    command.addAll(List.of("--replay-viewer", "-n1:" + LOOPBACK));
    command.addAll(split(extraArgs));
    return command;
  }

  private static List<String> replayBase(Path oaExe, Path modDir, String baseGame, List<String> expansions) {
    List<String> command = new ArrayList<>();
    command.add(oaExe.toString());
    command.add("--skip-intro");
    command.addAll(List.of("--mod-dir", modDir.toString()));
    command.add("--content");
    command.add(expansions.isEmpty() ? baseGame : baseGame + ":" + String.join(",", expansions));
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

  /**
   * Names the Library's copy of the base game ({@code <user folder>/Games/<baseGame>}) with {@code --game-dir}.
   * OA's replay playback with {@code --mod-dir} does not look the game folder up on its own ("no Total Annihilation
   * folder is known"), unlike a launch file. Left alone when the extra args already name a game folder or the
   * Library has no such folder.
   */
  public static List<String> withBaseGameDir(List<String> command, String extraArgs, String baseGame) {
    if (split(extraArgs).contains("--game-dir")) {
      return command;
    }
    Path gameDir = LibraryService.userFolder(extraArgs, Path.of(System.getProperty("user.home")))
        .resolve("Games").resolve(baseGame);
    if (!java.nio.file.Files.isDirectory(gameDir)) {
      return command;
    }
    List<String> out = new ArrayList<>(command);
    out.addAll(1, List.of("--game-dir", gameDir.toString()));
    return out;
  }

  private static List<String> split(String extraArgs) {
    if (extraArgs == null || extraArgs.isBlank()) {
      return List.of();
    }
    return Arrays.stream(extraArgs.trim().split("\\s+")).toList();
  }
}
