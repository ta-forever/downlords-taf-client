package com.faforever.client.library;

import static org.hamcrest.Matchers.hasItem;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

public class LibraryLaunchTest {

  private static final String GUID = "{4a7c1d0e-6c62-5d6e-9a49-0f1d2c3b4a59}";
  private static final Path STATUS = Path.of("logs", "oa_status_196786.jsonl");
  private static final Path EXE = Path.of("oa", "open-annihilation.exe");

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parse(String launchFile) {
    return (Map<String, Object>) new Yaml().load(launchFile);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> group(Map<String, Object> file, String name) {
    return (Map<String, Object>) file.get(name);
  }

  private static LibraryLaunch host() {
    return LibraryLaunch.host("talon", "totala", List.of("core-contingency"), "Commander", "TAF196786", GUID,
        "Red Planet@coreprime-maps", STATUS);
  }

  private static LibraryLaunch join() {
    return LibraryLaunch.join("talon", "totala", List.of("core-contingency"), "Commander", "127.0.0.1", GUID, STATUS);
  }

  @Test
  public void aHostLaunchFileNamesTheContentMapRulesGuidAndStatusStream() {
    String text = host().toOaLaunch();

    assertThat(text, startsWith("oalaunch: 1\n"));
    Map<String, Object> file = parse(text);
    assertThat(group(file, "game").get("role"), is("host"));
    assertThat(group(file, "game").get("name"), is("TAF196786"));
    assertThat(group(group(file, "game"), "directplay").get("application-guid"), is(GUID));
    assertThat(group(file, "player").get("name"), is("Commander"));
    assertThat(file.get("map"), is("Red Planet@coreprime-maps"));
    assertThat(group(file, "rules").containsKey("commander-death"), is(false));
    assertThat(group(file, "rules").get("lock-options"), is(false));
    assertThat(group(file, "rules").get("line-of-sight"), is(true));
    assertThat(group(file, "rules").get("start-positions"), is("random"));
    assertThat(group(file, "content").get("mod"), is("talon"));
    assertThat(group(file, "content").get("base-game"), is("totala"));
    assertThat(group(file, "content").get("expansions"), is(List.of("core-contingency")));
    assertThat(group(file, "status").get("file"), is(STATUS.toString()));
  }

  @Test
  public void aJoinLaunchFileHasAnAddressAndLeavesTheHostsRulesAndMapOut() {
    Map<String, Object> file = parse(join().toOaLaunch());

    assertThat(group(file, "game").get("role"), is("join"));
    assertThat(group(file, "game").get("address"), is("127.0.0.1"));
    assertThat(group(group(file, "game"), "directplay").get("application-guid"), is(GUID));
    assertThat(file, not(hasKey("map")));
    assertThat(file, not(hasKey("rules")));
    assertThat(group(file, "content").get("mod"), is("talon"));
    assertThat(group(file, "content").get("expansions"), is(List.of("core-contingency")));
  }

  @Test
  public void aModWithNoRequiredExpansionsWritesNoneNotAnOptionalOne() {
    LibraryLaunch launch = LibraryLaunch.join("rhyoss", "totala", List.of(), "C", "127.0.0.1", GUID, STATUS);

    assertThat(group(parse(launch.toOaLaunch()), "content").get("expansions"), is(List.of()));
  }

  @Test
  public void namesWithYamlSpecialCharactersSurviveTheRoundTrip() {
    LibraryLaunch launch = LibraryLaunch.host("talon", "totala", List.of(), "Dr: \"Who\" #1 \\o/", "TAF1", GUID,
        "Map: \"Odd\" & Co@pack", STATUS);

    Map<String, Object> file = parse(launch.toOaLaunch());

    assertThat(group(file, "player").get("name"), is("Dr: \"Who\" #1 \\o/"));
    assertThat(file.get("map"), is("Map: \"Odd\" & Co@pack"));
  }

  @Test
  public void thePlayerNameIsCutToSixteenCharactersLikeOaDoes() {
    LibraryLaunch launch = LibraryLaunch.join("talon", "totala", List.of(), "ABCDEFGHIJKLMNOPQRST", "127.0.0.1", GUID, STATUS);

    assertThat(group(parse(launch.toOaLaunch()), "player").get("name"), is("ABCDEFGHIJKLMNOP"));
  }

  // --- command lines ---

  @Test
  public void aLibraryLaunchStartsOaFromTheLaunchFileWithNoGameDirOrUserFolder() {
    Path file = Path.of("logs", "oalibrary_196786.oalaunch");

    List<String> command = LibraryCommands.launch(EXE, file, "");

    assertThat(command, contains(EXE.toString(), "-c", file.toString()));
    assertThat(command.contains("--game-dir"), is(false));
    assertThat(command.contains("--user-folder"), is(false));
  }

  @Test
  public void extraArgsFollowTheLaunchFile() {
    assertThat(LibraryCommands.launch(EXE, Path.of("l.oalaunch"), " --mute  --zoom 2"),
        contains(EXE.toString(), "-c", "l.oalaunch", "--mute", "--zoom", "2"));
  }

  @Test
  public void listContentAsksForJson() {
    assertThat(LibraryCommands.listContent(EXE), contains(EXE.toString(), "--list-content", "--json"));
  }

  @Test
  public void listContentForAModUsesTheModsFolderAndContent() {
    Path modDir = Path.of("Mods", "talon");

    assertThat(LibraryCommands.listContent(EXE, modDir, "totala", List.of("core-contingency")), contains(
        EXE.toString(), "--list-content", "--json", "--mod-dir", modDir.toString(),
        "--content", "totala:core-contingency"));
    assertThat(LibraryCommands.listContent(EXE, modDir, "totala", List.of()), contains(
        EXE.toString(), "--list-content", "--json", "--mod-dir", modDir.toString(), "--content", "totala"));
  }

  @Test
  public void printProfileForAModFolderUsesModDir() {
    Path modDir = Path.of("Mods", "talon");

    assertThat(LibraryCommands.printProfile(EXE, modDir), contains(
        EXE.toString(), "--mod-dir", modDir.toString(), "--print-profile"));
  }

  @Test
  public void theSimHashIsTheSha256SimLineOfPrintProfile() {
    String hash = "a5eed394ee460d28768eec0b9421a40d59796521734e220fb538d1c4a3bdb350";
    String output = "{\"id\":\"talon\"}\nsha256-sim  " + hash + "\nsha256-full  " + "b".repeat(64) + "\nOpen Annihilation 0.8.1\n";

    assertThat(LibraryCommands.simHash(output).orElseThrow(), is(hash));
    assertThat(LibraryCommands.simHash("no oamod.yaml; the folder plays base 3.1c").isPresent(), is(false));
  }

  @Test
  public void aStartedMatchIsRecognisedOnTheStatusStream() {
    assertThat(LibraryCommands.isMatchStarted("{\"event\":\"match\",\"seq\":7,\"frame\":200,\"tick\":0,\"change\":\"started\"}"), is(true));
    assertThat(LibraryCommands.isMatchStarted("{\"event\":\"screen\",\"seq\":6,\"name\":\"match\"}"), is(false));
    assertThat(LibraryCommands.isMatchStarted("{\"event\":\"match\",\"change\":\"ended\"}"), is(false));
  }

  @Test
  public void aVaultReplayPlaysWithTheRecordedModsFolderAndContentAndNoGameDir() {
    Path modDir = Path.of("Mods", "talon");
    Path demo = Path.of("cache", "replays", "196786.tad");

    List<String> command = LibraryCommands.playDemo(EXE, modDir, "totala", List.of("core-contingency"), demo, " --mute");

    assertThat(command, contains(EXE.toString(), "--skip-intro", "--mod-dir", modDir.toString(),
        "--content", "totala:core-contingency", "--play-demo", demo.toString(), "--mute"));
    assertThat(command, not(hasItem("--game-dir")));
  }

  @Test
  public void theHeadlessPreflightOfAReplayUsesTheSameContent() {
    Path modDir = Path.of("Mods", "talon");
    Path demo = Path.of("196786.tad");

    assertThat(LibraryCommands.checkDemo(EXE, modDir, "totala", List.of(), demo), contains(
        EXE.toString(), "--skip-intro", "--mod-dir", modDir.toString(), "--content", "totala",
        "--headless-check", "--play-demo", demo.toString(), "--match-ticks", "1"));
  }

  @Test
  public void aLiveReplayJoinsTheReplayersLoopbackGameWithTheRecordedContentAndNoPlayerName() {
    Path modDir = Path.of("Mods", "talon");

    List<String> command = LibraryCommands.replayViewer(EXE, modDir, "totala", List.of("core-contingency"), "");

    assertThat(command, contains(EXE.toString(), "--skip-intro", "--mod-dir", modDir.toString(),
        "--content", "totala:core-contingency", "--replay-viewer", "-n1:127.0.0.1"));
    assertThat(command, not(hasItem("--player-name")));
  }
}
