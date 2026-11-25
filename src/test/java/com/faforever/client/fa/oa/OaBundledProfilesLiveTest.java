package com.faforever.client.fa.oa;

import com.faforever.client.fa.oa.OaInstallationService.ProfileInfo;
import com.faforever.client.preferences.PreferencesService;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

/**
 * Feeds every bundled profile to a real Open Annihilation ({@code --print-profile}) so a registry typo or an
 * out-of-range value fails here instead of at game launch. Skipped when no OA is installed: set {@code OA_EXE} and
 * {@code OA_GAME_DIR} (any folder with the TA data) to run it elsewhere.
 */
public class OaBundledProfilesLiveTest {

  private static final List<String> MODS = List.of("tacc", "tavmod", "taesc", "tazero", "tamayhem", "tatw");

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private static Path env(String name, String fallback) {
    String value = System.getenv(name);
    return Path.of(value != null && !value.isBlank() ? value : fallback);
  }

  @Test
  public void realOaAcceptsEveryBundledProfile() throws Exception {
    Path exe = env("OA_EXE", "D:/games/open-annihilation-v0.7.3-windows-x64/open-annihilation.exe");
    Path gameDir = env("OA_GAME_DIR", "D:/games/TAF-Core Contingency");
    Assume.assumeTrue("no Open Annihilation at " + exe, Files.isRegularFile(exe));
    Assume.assumeTrue("no game folder at " + gameDir, Files.isDirectory(gameDir));

    OaProfileInstaller installer = new OaProfileInstaller();
    OaInstallationService service = new OaInstallationService(mock(PreferencesService.class));
    for (String mod : MODS) {
      Path dir = temp.newFolder("mod-" + mod).toPath();
      // a copied install: the profile in the folder root, the TA data one level up is found through --game-dir
      Files.writeString(dir.resolve("oamod.yaml"), installer.bundledProfile(mod).orElseThrow());
      Path profile = dir.resolve("oamod.yaml");

      Process process = new ProcessBuilder(exe.toString(), "--game-dir", gameDir.toString(), "--mod", profile.toString(),
          "--print-profile").redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes());
      assertThat(mod + ": " + output, process.waitFor(), is(0));

      ProfileInfo info = OaInstallationService.parseProfile(0, output);
      assertThat(mod + " has a sim hash", info.simHash().isPresent(), is(true));
      System.out.println("SIMHASH " + mod + " " + info.simHash().orElseThrow());
    }
    assertThat(service != null, is(true));
  }
}
