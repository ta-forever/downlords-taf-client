package com.faforever.client.fa.oa;

import com.faforever.client.fa.oa.OaHostSetup.CommanderDeath;
import com.faforever.client.fa.oa.OaHostSetup.Settings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Drives a real headless Open Annihilation host into a map and the commander-death rule through
 * {@link OaHostSetup}. Skipped when no OA is installed: set {@code OA_EXE} and {@code OA_GAME_DIR} (a folder
 * with the TA data and several multiplayer maps) to run it elsewhere.
 */
public class OaHostSetupLiveTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private static Path env(String name, String fallback) {
    String value = System.getenv(name);
    return Path.of(value != null && !value.isBlank() ? value : fallback);
  }

  @Test
  public void realOaHostTakesTheMapAndTheRule() throws Exception {
    Path exe = env("OA_EXE", "D:/games/open-annihilation-v0.7.3-windows-x64/open-annihilation.exe");
    Path gameDir = env("OA_GAME_DIR", "D:/games/TAF-Core Contingency");
    Assume.assumeTrue("no Open Annihilation at " + exe, Files.isRegularFile(exe));
    Assume.assumeTrue("no game folder at " + gameDir, Files.isDirectory(gameDir));

    Path fark = temp.getRoot().toPath().resolve("fark.json");
    ProcessBuilder builder = new ProcessBuilder(exe.toString(), "--game-dir", gameDir.toString(), "--skip-intro",
        "--host", "--player-name", "Live", "--game-name", "LiveSetup", "--fark", "--fark-file", fark.toString())
        .redirectErrorStream(true).redirectOutput(temp.newFile("oa.log"));
    builder.environment().put("SDL_VIDEO_DRIVER", "dummy");
    builder.environment().put("SDL_AUDIO_DRIVER", "dummy");
    Process process = builder.start();
    try {
      OaFarkClient client = awaitBattleRoom(fark, process);
      List<String> problems = new OaHostSetup(client).apply(new Settings("Acid Pools", CommanderDeath.ENDS));
      assertThat(problems.toString(), problems.isEmpty(), is(true));

      JsonNode room = client.request(new ObjectMapper().createObjectNode().put("op", "room"));
      assertThat(room.path("map").asText(), is("Acid Pools"));
      assertThat(room.path("options").path("commander").asText(), is("ends"));
      client.close();
    } finally {
      process.destroyForcibly();
    }
  }

  private static OaFarkClient awaitBattleRoom(Path fark, Process process) throws Exception {
    long deadline = System.currentTimeMillis() + 60_000;
    while (System.currentTimeMillis() < deadline && process.isAlive()) {
      if (Files.isRegularFile(fark)) {
        try {
          OaFarkClient client = new OaFarkClient(fark);
          if ("mp_battleroom".equals(client.screen())) {
            return client;
          }
          client.close();
        } catch (java.io.IOException | RuntimeException e) {
          // the endpoint is not up yet, or it is busy
        }
      }
      Thread.sleep(500);
    }
    throw new AssertionError("OA did not reach the battle room");
  }
}
