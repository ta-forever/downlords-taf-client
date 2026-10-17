package com.faforever.client.fa.oa;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * Against the real Open Annihilation 0.8.x that TAF installed on this machine: starts it, lets
 * {@link OaLibraryNavigator} open the Library and checks that OA reports the Library dialog. Opens a real OA window
 * for a few seconds, and is skipped when there is no OA at the usual place.
 */
public class OaLibraryNavigatorLiveTest {

  private static final Path OA_ROOT = Path.of("C:/ProgramData/TAForever/engines/open-annihilation");

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void theNavigatorLandsOnTheLibraryScreenOfTheRealOa() throws Exception {
    Path exe = findExe();
    Assume.assumeTrue("no installed OA", exe != null);
    Path endpoint = temp.getRoot().toPath().resolve("fark.json");
    Process oa = new ProcessBuilder(exe.toString(), "--skip-intro", "--fark", "--fark-file", endpoint.toString())
        .directory(exe.getParent().toFile())
        .redirectErrorStream(true)
        .redirectOutput(temp.newFile("oa.log"))
        .start();
    try {
      OaLibraryNavigator.openLibrary(endpoint);

      try (OaFarkClient fark = connectWhenFree(endpoint)) {
        JsonNode screen = fark.request(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("op", "screen"));
        List<String> dialogs = new java.util.ArrayList<>();
        screen.path("dialogs").forEach(dialog -> dialogs.add(dialog.asText()));
        assertThat(dialogs, hasItem(OaLibraryNavigator.LIBRARY_DIALOG));
        assertThat(oa.isAlive(), is(true));
        fark.quit();
      }
      assertThat(oa.waitFor(15, java.util.concurrent.TimeUnit.SECONDS), is(true));
    } finally {
      oa.destroyForcibly();
    }
  }

  /** The endpoint serves one client at a time and frees the navigator's connection a moment after it closes. */
  private static OaFarkClient connectWhenFree(Path endpoint) throws Exception {
    java.io.IOException last = null;
    for (int attempt = 0; attempt < 20; attempt++) {
      try {
        return new OaFarkClient(endpoint);
      } catch (java.io.IOException e) {
        last = e;
        Thread.sleep(300);
      }
    }
    throw last;
  }

  private static Path findExe() throws java.io.IOException {
    if (!Files.isDirectory(OA_ROOT)) {
      return null;
    }
    try (var files = Files.walk(OA_ROOT, 6)) {
      return files.filter(path -> path.getFileName().toString().equals("open-annihilation.exe"))
          .sorted().reduce((first, second) -> second).orElse(null);
    }
  }
}
