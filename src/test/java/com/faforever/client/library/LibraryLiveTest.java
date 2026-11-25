package com.faforever.client.library;

import com.faforever.client.library.OaContentListing.ListedMap;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

/**
 * Opt-in test against the REAL Open Annihilation 0.8.x binary; skipped unless {@code OA_LIVE_EXE} is set.
 * Not part of CI. Set {@code OA_LIVE_EXE} to open-annihilation(.exe) and {@code OA_LIVE_GAME_DIR} to a Total
 * Annihilation folder (a copied install such as D:/games/TA-Base). Everything else happens in a scratch user
 * folder, so the real OA preferences and Library are left alone.
 *
 * <p>Covers {@code --list-content --json}, {@code --print-profile --mod-dir} (sim hash) and a {@code .oalaunch}
 * naming a Library mod. See skill oa-client-launch-path-testing.
 */
public class LibraryLiveTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Path exe;
  private Path gameDir;
  private Path userFolder;
  private Path modFolder;

  @Before
  public void setUp() throws IOException {
    String exeEnv = System.getenv("OA_LIVE_EXE");
    Assume.assumeTrue("set OA_LIVE_EXE to run the live Library test", exeEnv != null && !exeEnv.isBlank());
    exe = Path.of(exeEnv);
    String gameEnv = System.getenv("OA_LIVE_GAME_DIR");
    gameDir = Path.of(gameEnv != null && !gameEnv.isBlank() ? gameEnv : "D:/games/TA-Base");
    Assume.assumeTrue("no OA at " + exe, Files.isRegularFile(exe));
    Assume.assumeTrue("no game folder at " + gameDir, Files.isDirectory(gameDir));

    userFolder = temp.newFolder("oa-user").toPath();
    modFolder = userFolder.resolve("Mods").resolve("probe");
    Files.createDirectories(modFolder);
    Files.writeString(modFolder.resolve("oamod.yaml"), "oamod: 1\nid: probe\nname: Probe Mod\nversion: \"1.0\"\n"
        + "author: {name: Test}\npackaging: {revision: 3, date: 2026-10-04, packager: Test}\n"
        + "requiredExpansions: [core-contingency]\nlimits:\n  units-per-player: {default: 500, max: 1000}\n"
        + "hacks:\n  units.id-reuse-delay: true\n");
  }

  private List<String> withOaFolders(List<String> command) {
    List<String> full = new ArrayList<>(command);
    full.addAll(List.of("--game-dir", gameDir.toString(), "--user-folder", userFolder.toString()));
    return full;
  }

  private ProcessBuilder oa(List<String> command) {
    ProcessBuilder builder = new ProcessBuilder(withOaFolders(command)).redirectErrorStream(true)
        .directory(exe.toAbsolutePath().getParent().toFile());
    builder.environment().put("SDL_VIDEO_DRIVER", "dummy");
    builder.environment().put("SDL_AUDIO_DRIVER", "dummy");
    return builder;
  }

  private String run(List<String> command) throws Exception {
    Process process = oa(command).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(60, TimeUnit.SECONDS), is(true));
    return output;
  }

  @Test
  public void listContentJsonListsTheInstalledLibraryModAndItsMaps() throws Exception {
    OaContentListing listing = OaContentListing.parse(run(LibraryCommands.listContent(exe)));

    assertThat(listing.hasMod("probe"), is(true));
    assertThat(LibraryDiscovery.discover(userFolder, listing).get(0).requiredExpansions(), hasItem("core-contingency"));
    assertThat(listing.maps().size(), greaterThan(0));
  }

  /** The Mayhem empty-map-list question: the maps listed for a mod include the ones its own folder adds. */
  @Test
  public void listContentForTheModsFolderIncludesTheMapsThatFolderCarries() throws Exception {
    Path ufo = findAnyUfo();
    Assume.assumeTrue("set OA_LIVE_MAP_UFO to a .ufo the game folder lacks", ufo != null);
    OaContentListing base = OaContentListing.parse(run(LibraryCommands.listContent(exe)));
    Files.copy(ufo, modFolder.resolve(ufo.getFileName()));

    OaContentListing forMod = OaContentListing.parse(
        run(LibraryCommands.listContent(exe, modFolder, "totala", List.of("core-contingency"))));

    assertThat(forMod.maps().size(), greaterThan(base.maps().size()));
    List<String> keys = forMod.maps().stream().map(ListedMap::key).toList();
    assertThat(keys, hasItem(not(is(""))));
  }

  @Test
  public void printProfileForTheModFolderGivesTheSimHash() throws Exception {
    String hash = LibraryCommands.simHash(run(LibraryCommands.printProfile(exe, modFolder))).orElseThrow();

    assertThat(hash, matchesPattern("[0-9a-f]{64}"));
  }

  @Test
  public void anOalaunchNamingAnInstalledLibraryModHostsABattleRoom() throws Exception {
    Path status = temp.getRoot().toPath().resolve("status.jsonl");
    Path launch = temp.getRoot().toPath().resolve("host.oalaunch");
    String map = OaContentListing.parse(run(LibraryCommands.listContent(exe))).maps().get(0).key();
    String text = LibraryLaunch.host("probe", "totala", List.of("core-contingency"), "LiveHost",
        "TAFLIVE", "{99797420-f5f5-11cf-9827-00a0241496c8}", map, status).toOaLaunch();
    // With --game-dir (a copied install, as here, where OA has had no first-run set-up) there is no managed base
    // game or expansion to name, and OA refuses content.base-game. A real Library user has them; leave them out.
    text = text.lines().filter(line -> !line.contains("base-game") && !line.contains("expansions"))
        .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
    Files.writeString(launch, text, StandardCharsets.UTF_8);

    Process process = oa(LibraryCommands.launch(exe, launch, "")).start();
    try {
      long deadline = System.currentTimeMillis() + 60_000;
      boolean sawBattleRoom = false;
      while (System.currentTimeMillis() < deadline && process.isAlive() && !sawBattleRoom) {
        Thread.sleep(500);
        sawBattleRoom = Files.isRegularFile(status) && Files.readString(status).contains("battleroom");
      }
      assertThat("OA stayed up and reached the battle room: " + (Files.isRegularFile(status) ? Files.readString(status) : "no status"),
          sawBattleRoom && process.isAlive(), is(true));
    } finally {
      process.destroyForcibly();
    }
  }

  @Test
  public void anOalaunchNamingAnUninstalledModIsRefusedWithStatusTwo() throws Exception {
    Path status = temp.getRoot().toPath().resolve("refused.jsonl");
    Path launch = temp.getRoot().toPath().resolve("refused.oalaunch");
    Files.writeString(launch, LibraryLaunch.join("not-installed", "totala", List.of(), "LiveJoin", "127.0.0.1",
        "{99797420-f5f5-11cf-9827-00a0241496c8}", status).toOaLaunch(), StandardCharsets.UTF_8);

    Process process = oa(LibraryCommands.launch(exe, launch, "")).start();
    process.getInputStream().readAllBytes();

    assertThat(process.waitFor(60, TimeUnit.SECONDS), is(true));
    assertThat(process.exitValue(), is(2));
  }

  /** A .ufo map that the game folder does not already have: {@code OA_LIVE_MAP_UFO}, e.g. one from TAF-Mayhem. */
  private Path findAnyUfo() {
    String env = System.getenv("OA_LIVE_MAP_UFO");
    return env == null || env.isBlank() || !Files.isRegularFile(Path.of(env)) ? null : Path.of(env);
  }
}
