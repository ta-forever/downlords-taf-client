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

  @Test
  public void hostUsesABlankFreeSessionNameAndTheLoginAsPlayerName() {
    assertThat(OaCommands.host(EXE, DIR, "TheArmCommander", 196786, ""), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--skip-intro",
        "--player-name", "TheArmCommander", "--host", "--game-name", "TAF196786"));
  }

  @Test
  public void joinTargetsTheLoopbackProxy() {
    assertThat(OaCommands.join(EXE, DIR, "TheCoreCommander", ""), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--skip-intro",
        "--player-name", "TheCoreCommander", "--join", "127.0.0.1"));
  }

  @Test
  public void playerNamesAreCutToSixteenCharacters() {
    List<String> command = OaCommands.join(EXE, DIR, "ABCDEFGHIJKLMNOPQRST", "");
    assertThat(command.get(command.indexOf("--player-name") + 1), is("ABCDEFGHIJKLMNOP"));
  }

  @Test
  public void offlineHasNoNetworkSwitchesAndKeepsExtraArgs() {
    assertThat(OaCommands.offline(EXE, DIR, false, "  --mute   --zoom 2 "), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--mute", "--zoom", "2"));
    assertThat(OaCommands.offline(EXE, DIR, true, null), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--skip-intro"));
  }

  @Test
  public void playDemoPassesTheTadFile() {
    Path tad = Path.of("replays", "196464.tad");
    assertThat(OaCommands.playDemo(EXE, DIR, tad, ""), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--skip-intro", "--play-demo", tad.toString()));
  }

  @Test
  public void checkDemoLoadsOneTickHeadless() {
    Path tad = Path.of("replays", "196464.tad");
    assertThat(OaCommands.checkDemo(EXE, DIR, tad), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--skip-intro",
        "--headless-check", "--play-demo", tad.toString(), "--match-ticks", "1"));
  }

  @Test
  public void replayViewerJoinsTheLoopbackReplayer() {
    // OA 0.7.3 refuses --player-name here: "--player-name needs --host or --join"
    assertThat(OaCommands.replayViewer(EXE, DIR, ""), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--skip-intro", "--replay-viewer", "-n1:127.0.0.1"));
  }

  @Test
  public void printProfileOnlyNeedsTheGameDir() {
    assertThat(OaCommands.printProfile(EXE, DIR), contains(
        EXE.toString(), "--game-dir", DIR.toString(), "--print-profile"));
  }
}
