package com.faforever.client.library;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class LooseMapStagerTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Path userFolder;
  private Path cache;
  private LooseMapStager stager;

  @Before
  public void setUp() throws Exception {
    userFolder = temp.newFolder("user").toPath();
    cache = temp.newFolder("cache").toPath();
    addPack("pic-nic-pack", "(Not a) Pic-Nic");
    addPack("other-pack", "Other");
    stager = new LooseMapStager(userFolder, cache);
  }

  private void addPack(String id, String map) throws Exception {
    Path pack = userFolder.resolve("Maps").resolve(id);
    Files.createDirectories(pack.resolve("maps"));
    Files.createDirectories(pack.resolve("previews"));
    Files.writeString(pack.resolve("maps").resolve(map + ".ota"), "ota");
    Files.writeString(pack.resolve("maps").resolve(map + ".tnt"), "tnt");
    Files.write(pack.resolve("previews").resolve(map + ".png"), new byte[]{1});
    Files.writeString(pack.resolve("oamap.yaml"), "oamap: 1\n");
  }

  @Test
  public void findsThePackByMapNameOrByTheKeysPack() {
    assertThat(stager.findPack("(Not a) Pic-Nic").map(p -> p.getFileName().toString()),
        is(Optional.of("pic-nic-pack")));
    assertThat(stager.findPack("Other@other-pack").map(p -> p.getFileName().toString()),
        is(Optional.of("other-pack")));
    assertThat(stager.findPack("(NOT A) PIC-NIC@missing-pack").isPresent(), is(true));
    assertThat(stager.findPack("Nowhere").isPresent(), is(false));
  }

  @Test
  public void stagesAnArchiveAndTheFeaturesAndRepacksOnlyWhenTheSourceChanged() throws Exception {
    Files.writeString(cache.resolve("TA_Features_2013.ccx"), "features");

    Path stage = stager.stage("(Not a) Pic-Nic").orElseThrow();

    Path archive = stage.resolve("pic-nic-pack.ufo");
    assertThat(Files.isRegularFile(archive), is(true));
    assertThat(Files.isRegularFile(stage.resolve("TA_Features_2013.ccx")), is(true));
    assertThat(stage.startsWith(cache.resolve("library").resolve("staged")), is(true));

    Files.setLastModifiedTime(archive, FileTime.fromMillis(System.currentTimeMillis() + 60_000));
    long before = Files.getLastModifiedTime(archive).toMillis();
    stager.stage("(Not a) Pic-Nic");
    assertThat(Files.getLastModifiedTime(archive).toMillis(), is(before));

    Path tnt = userFolder.resolve("Maps/pic-nic-pack/maps/(Not a) Pic-Nic.tnt");
    Files.setLastModifiedTime(tnt, FileTime.fromMillis(System.currentTimeMillis() + 120_000));
    stager.stage("(Not a) Pic-Nic");
    assertThat(Files.getLastModifiedTime(archive).toMillis() == before, is(false));
  }

  @Test
  public void aMapNoPackHasStagesNothing() throws Exception {
    assertThat(stager.stage("Nowhere"), is(Optional.empty()));
  }

  @Test
  public void offersThePacksOwnPreview() {
    assertThat(stager.packPreview("(Not a) Pic-Nic").map(p -> p.getFileName().toString()),
        is(Optional.of("(Not a) Pic-Nic.png")));
  }
}
