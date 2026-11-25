package com.faforever.client.library;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class LibraryDiscoveryTest {

  /** What OA 0.8.1 prints for {@code --list-content --json}, trimmed to what matters. */
  private static final String LISTING = """
      {"maps":[{"shown-name":"Acid Pools","key":"Acid Pools","players":10,"size":"32 x 23"},
               {"shown-name":"Red Planet","key":"Red Planet@coreprime-maps","players":2,"size":"9 x 9"}],
       "mods":[{"id":"talon","name":"Talon"},{"id":"rainbow-edition","name":"Rainbow Edition"}],
       "expansions":[{"id":"core-contingency","name":"Core Contingency"}]}""".replace("\n", "");

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Path userFolder() throws IOException {
    return temp.newFolder("Open Annihilation").toPath();
  }

  private static void installMod(Path userFolder, String folder, String yaml) throws IOException {
    Path dir = userFolder.resolve("Mods").resolve(folder);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("oamod.yaml"), yaml);
  }

  private static String oamod(String id, String name, String version, int revision, String extra) {
    return "oamod: 1\nid: " + id + "\nname: " + name + "\nversion: \"" + version + "\"\n"
        + "author: {name: A. Modder}\n"
        + "packaging: {revision: " + revision + ", date: 2026-10-04, packager: P. Packer}\n" + extra;
  }

  private static List<LibraryMod> discover(Path userFolder) {
    return LibraryDiscovery.discover(userFolder, OaContentListing.parse(LISTING));
  }

  @Test
  public void listsAnInstalledModWithItsIdentity() throws IOException {
    Path user = userFolder();
    installMod(user, "talon", oamod("talon", "Talon", "1.03", 2, ""));

    List<LibraryMod> mods = discover(user);

    assertThat(mods.size(), is(1));
    LibraryMod talon = mods.get(0);
    assertThat(talon.id(), is("talon"));
    assertThat(talon.name(), is("Talon"));
    assertThat(talon.version(), is("1.03"));
    assertThat(talon.revision(), is(2));
    assertThat(talon.baseGame(), is("totala"));
    assertThat(talon.requiredExpansions(), is(empty()));
    assertThat(talon.folder(), is(user.resolve("Mods").resolve("talon")));
    assertThat(talon.registry(), is(nullValue()));
  }

  @Test
  public void requiredExpansionsAndTheFirstSupportedGameAreKept() throws IOException {
    Path user = userFolder();
    installMod(user, "rainbow-edition", oamod("rainbow-edition", "Rainbow Edition", "2.0", 1,
        "supportedGames: [totala, totala-demo]\nrequiredExpansions: [core-contingency]\n"));

    LibraryMod mod = discover(user).get(0);

    assertThat(mod.requiredExpansions(), contains("core-contingency"));
    assertThat(mod.baseGame(), is("totala"));
  }

  @Test
  public void versionsInstalledSideBySideAreBothListed() throws IOException {
    Path user = userFolder();
    installMod(user, "talon", oamod("talon", "Talon", "1.03", 1, ""));
    installMod(user, "talon-1.02", oamod("talon", "Talon", "1.02", 1, ""));

    List<LibraryMod> mods = discover(user);

    assertThat(mods.stream().map(LibraryMod::version).toList(), contains("1.02", "1.03"));
    assertThat(mods.stream().map(m -> m.folder().getFileName().toString()).toList(),
        contains("talon-1.02", "talon"));
  }

  @Test
  public void dotFoldersAndBackupsAreSkipped() throws IOException {
    Path user = userFolder();
    installMod(user, "talon", oamod("talon", "Talon", "1.03", 2, ""));
    installMod(user, ".oamod-staging", oamod("talon", "Talon", "9.9", 1, ""));
    installMod(user, ".backup", oamod("talon", "Talon", "0.1", 1, ""));
    installMod(user, "talon/.backup", oamod("talon", "Talon", "1.02", 1, ""));

    assertThat(discover(user).stream().map(LibraryMod::version).toList(), contains("1.03"));
  }

  @Test
  public void aModWithoutOamodYamlOrWithACorruptOneIsSkipped() throws IOException {
    Path user = userFolder();
    installMod(user, "talon", oamod("talon", "Talon", "1.03", 2, ""));
    Files.createDirectories(user.resolve("Mods").resolve("rainbow-edition"));
    installMod(user, "broken", "oamod: 1\nid: [unterminated\n  : :");
    installMod(user, "idless", "oamod: 1\nname: No Id\nversion: \"1\"\n");

    assertThat(discover(user).stream().map(LibraryMod::id).toList(), contains("talon"));
  }

  @Test
  public void aModThatOaDoesNotListIsDropped() throws IOException {
    Path user = userFolder();
    installMod(user, "ridge", oamod("ridge", "Ridge", "1.0", 1, ""));
    installMod(user, "talon", oamod("talon", "Talon", "1.03", 2, ""));

    assertThat(discover(user).stream().map(LibraryMod::id).toList(), contains("talon"));
  }

  @Test
  public void thereIsNothingWhenTheUserFolderHasNoModsFolder() throws IOException {
    assertThat(discover(userFolder()), is(empty()));
    assertThat(discover(null), is(empty()));
  }

  @Test
  public void theRegistryComesFromOasOriginRecordWhenThereIsOne() throws IOException {
    Path user = userFolder();
    installMod(user, "talon", oamod("talon", "Talon", "1.03", 2, ""));
    Files.writeString(user.resolve("Mods").resolve("talon").resolve(".oa-origin.yaml"),
        "oa-origin: 1\norigin: catalogue\nregistry: coreprime\ncatalogue-id: talon\nrelease: 4\n"
            + "sha256: " + "a".repeat(64) + "\ninstalled: 2026-10-04\n");

    assertThat(discover(user).get(0).registry(), is("coreprime"));
  }

  @Test
  public void theListingExposesMapsWithShownNamesAndKeys() {
    OaContentListing listing = OaContentListing.parse(LISTING);

    assertThat(listing.maps().size(), is(2));
    assertThat(listing.maps().get(1).shownName(), is("Red Planet"));
    assertThat(listing.maps().get(1).key(), is("Red Planet@coreprime-maps"));
    assertThat(listing.maps().get(1).players(), is(2));
    assertThat(listing.expansions().get(0).id(), is("core-contingency"));
  }

  @Test
  public void theListingIsTheLastJsonLineOfOutputThatHasTimingLinesBeforeIt() {
    String output = "timing: start 12ms\nsomething else\n{\"maps\":[],\"mods\":[{\"id\":\"talon\",\"name\":\"Talon\"}],\"expansions\":[]}\n";

    assertThat(OaContentListing.parse(output).mods().get(0).id(), is("talon"));
  }

  @Test
  public void aListingThatIsNotJsonIsAnError() {
    org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> OaContentListing.parse("no game folder"));
  }
}
