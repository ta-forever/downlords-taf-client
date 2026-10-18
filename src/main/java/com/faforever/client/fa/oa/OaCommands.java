package com.faforever.client.fa.oa;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds Open Annihilation command lines (OPEN_ANNIHILATION_INTEGRATION.md §6.3, with the
 * v0.7.3 switches found in M0.6). Pure, so the argument shapes are unit-testable.
 *
 * <p>TAF never touches the user's stored mod choice: {@code --game-dir} makes the run use the TAF
 * mod install dir (a "copied install" carrying {@code oamod.yaml}) and locks OA's Mods page.
 */
public final class OaCommands {

  /** OA cuts player and game names to this many characters, as the lobby screens do. */
  static final int MAX_NAME_LENGTH = 16;
  static final String LOOPBACK = "127.0.0.1";

  private OaCommands() {
  }

  /** Plain single-player / skirmish launch of the mod. */
  public static List<String> offline(Path oaExe, Path modDir, boolean skipIntro, String extraArgs) {
    List<String> command = base(oaExe, modDir, skipIntro);
    command.addAll(split(extraArgs));
    return command;
  }

  /** OA hosts the game; gpgnet4ta's proxy sits between it and the ICE adapter. */
  public static List<String> host(Path oaExe, Path modDir, String playerName, int gameId, String extraArgs) {
    List<String> command = base(oaExe, modDir, true);
    command.addAll(List.of("--player-name", clip(playerName), "--host", "--game-name", gameName(gameId)));
    command.addAll(split(extraArgs));
    return command;
  }

  /** OA joins the game gpgnet4ta's proxy announces on loopback. It auto-joins the first answer within 20 s. */
  public static List<String> join(Path oaExe, Path modDir, String playerName, String extraArgs) {
    List<String> command = base(oaExe, modDir, true);
    command.addAll(List.of("--player-name", clip(playerName), "--join", LOOPBACK));
    command.addAll(split(extraArgs));
    return command;
  }

  /** Local playback of a vault {@code .tad}. */
  public static List<String> playDemo(Path oaExe, Path modDir, Path demo, String extraArgs) {
    List<String> command = base(oaExe, modDir, true);
    command.addAll(List.of("--play-demo", demo.toString()));
    command.addAll(split(extraArgs));
    return command;
  }

  /**
   * Loads a demo headlessly for one tick and exits: status 0 if OA can play it, 1 with the reason on
   * stdout if it refuses (unit table mismatch, ten players, missing map). Takes about 1 to 4 s.
   */
  public static List<String> checkDemo(Path oaExe, Path modDir, Path demo) {
    List<String> command = base(oaExe, modDir, true);
    command.addAll(List.of("--headless-check", "--play-demo", demo.toString(), "--match-ticks", "1"));
    return command;
  }

  /**
   * Join a game that replayer.exe hosts (live watch / streamed replay). OA 0.7.3 refuses
   * {@code --player-name} together with {@code --replay-viewer} ("--player-name needs --host or --join"),
   * so the watcher keeps OA's own nickname.
   */
  public static List<String> replayViewer(Path oaExe, Path modDir, String extraArgs) {
    List<String> command = base(oaExe, modDir, true);
    command.addAll(List.of("--replay-viewer", "-n1:" + LOOPBACK));
    command.addAll(split(extraArgs));
    return command;
  }

  /** Prints the resolved profile and its sim hash, then exits (status 1 on errors). */
  public static List<String> printProfile(Path oaExe, Path modDir) {
    return new ArrayList<>(List.of(oaExe.toString(), "--game-dir", modDir.toString(), "--base-game", "--print-profile"));
  }

  /** Session name that survives OA's "the name ends at the first blank" rule. */
  public static String gameName(int gameId) {
    return "TAF" + gameId;
  }

  static String clip(String name) {
    String trimmed = name == null ? "" : name.trim();
    return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
  }

  private static List<String> base(Path oaExe, Path modDir, boolean skipIntro) {
    List<String> command = new ArrayList<>();
    command.add(oaExe.toString());
    command.add("--game-dir");
    command.add(modDir.toString());
    // The game folder is the mod (a copied install with its own oamod.yaml): never the mod folder the player
    // picked in OA's Library, which OA remembers between runs and refuses to combine with a copied install.
    command.add("--base-game");
    if (skipIntro) {
      command.add("--skip-intro");
    }
    return command;
  }

  private static List<String> split(String extraArgs) {
    if (extraArgs == null || extraArgs.isBlank()) {
      return List.of();
    }
    return Arrays.stream(extraArgs.trim().split("\\s+")).toList();
  }
}
