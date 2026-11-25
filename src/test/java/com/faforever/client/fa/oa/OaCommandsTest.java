package com.faforever.client.fa.oa;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

public class OaCommandsTest {

  private static final Path EXE = Path.of("oa", "open-annihilation");
  private static final Path DIR = Path.of("games", "TAF-Core Contingency");
  private static final String GUID = "{a1304614-7f7b-52a6-baf8-163213fbe736}";

  @Test
  public void hostUsesABlankFreeSessionNameAndTheLoginAsPlayerName() {
    assertThat(OaCommands.host(EXE, DIR, "TheArmCommander", 196786, GUID, ""), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--base-game", "--skip-intro",
        "--player-name", "TheArmCommander", "--host", "--game-name", "TAF196786", "--dplay-guid", GUID));
  }

  @Test
  public void joinTargetsTheLoopbackProxy() {
    assertThat(OaCommands.join(EXE, DIR, "TheCoreCommander", GUID, ""), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--base-game", "--skip-intro",
        "--player-name", "TheCoreCommander", "--join", "127.0.0.1", "--dplay-guid", GUID));
  }

  @Test
  public void playerNamesAreCutToSixteenCharacters() {
    List<String> command = OaCommands.join(EXE, DIR, "ABCDEFGHIJKLMNOPQRST", GUID, "");
    assertThat(command.get(command.indexOf("--player-name") + 1), is("ABCDEFGHIJKLMNOP"));
  }

  @Test
  public void offlineHasNoNetworkSwitchesAndKeepsExtraArgs() {
    assertThat(OaCommands.offline(EXE, DIR, false, "  --mute   --zoom 2 "), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--base-game", "--mute", "--zoom", "2"));
    assertThat(OaCommands.offline(EXE, DIR, true, null), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--base-game", "--skip-intro"));
  }

  @Test
  public void playDemoPassesTheTadFile() {
    Path tad = Path.of("replays", "196464.tad");
    assertThat(OaCommands.playDemo(EXE, DIR, tad, ""), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--base-game", "--skip-intro", "--play-demo", tad.toString()));
  }

  @Test
  public void checkDemoLoadsOneTickHeadless() {
    Path tad = Path.of("replays", "196464.tad");
    assertThat(OaCommands.checkDemo(EXE, DIR, tad), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--base-game", "--skip-intro",
        "--headless-check", "--play-demo", tad.toString(), "--match-ticks", "1"));
  }

  @Test
  public void replayViewerJoinsTheLoopbackReplayer() {
    // OA 0.7.3 refuses --player-name here: "--player-name needs --host or --join"
    assertThat(OaCommands.replayViewer(EXE, DIR, ""), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--base-game", "--skip-intro", "--replay-viewer", "-n1:127.0.0.1"));
  }

  @Test
  public void printProfileOnlyNeedsTheGameDir() {
    assertThat(OaCommands.printProfile(EXE, DIR), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--base-game", "--print-profile"));
  }
}
