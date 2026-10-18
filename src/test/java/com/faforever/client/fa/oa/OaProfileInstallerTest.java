package com.faforever.client.fa.oa;

import com.faforever.client.fa.oa.OaProfileInstaller.Outcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

public class OaProfileInstallerTest {

  private static final List<String> SUPPORTED = List.of("tacc", "tavmod", "taesc", "tazero", "tamayhem", "tatw");

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private final OaProfileInstaller installer = new OaProfileInstaller();

  @Test
  public void everySupportedModHasABundledProfile() {
    for (String mod : SUPPORTED) {
      String profile = installer.bundledProfile(mod).orElseThrow(() -> new AssertionError("no profile for " + mod));
      assertThat(mod, profile, startsWith(OaProfileInstaller.MANAGED_MARKER));
      assertThat(mod, profile, containsString("\noamod: 1\n"));
      assertThat(mod, profile, containsString("\nhacks:\n"));
    }
  }

  @Test
  public void profilesArePlainLfUtf8ThatOaCanRead() {
    // OA refuses a byte order mark and tabs in indentation (oamod-standard.md section 3.3)
    for (String mod : SUPPORTED) {
      String profile = installer.bundledProfile(mod).orElseThrow();
      assertThat(mod, profile.startsWith("﻿"), is(false));
      assertThat(mod, profile.contains("\r"), is(false));
      assertThat(mod, profile.contains("\t"), is(false));
      assertThat(mod, profile.getBytes(StandardCharsets.UTF_8).length < 256 * 1024, is(true));
    }
  }

  @Test
  public void theNetworkVersionIsWhatTheModsProfileSays() {
    assertThat(installer.networkVersion("tacc").orElseThrow(), is("3.1"));
    assertThat(installer.networkVersion("ladder1v1").orElseThrow(), is("3.1"));
    assertThat(installer.networkVersion("tavmod").orElseThrow(), is("4.8"));
    assertThat(installer.networkVersion("taesc").orElseThrow(), is("10.2"));
    assertThat(installer.networkVersion("tamayhem").orElseThrow(), is("11.3"));
    assertThat(installer.networkVersion("tatw").orElseThrow(), is("2.0"));
    assertThat(installer.networkVersion("nosuchmod").isPresent(), is(false));
  }

  @Test
  public void ladderAndTaccShareTheSameInstallAndSoTheSameProfile() {
    assertThat(installer.bundledProfile("ladder1v1"), is(installer.bundledProfile("tacc")));
  }

  @Test
  public void aModWithoutAProfilePlaysAsBase31c() throws IOException {
    assertThat(installer.bundledProfile("coop").isPresent(), is(false));
    assertThat(installer.ensureInstalled("coop", temp.newFolder("coop").toPath()), is(Outcome.NO_PROFILE));
    assertThat(Files.exists(temp.getRoot().toPath().resolve("coop").resolve("oamod.yaml")), is(false));
  }

  @Test
  public void installsThenLeavesItAlone() throws IOException {
    Path dir = temp.newFolder("prota").toPath();

    assertThat(installer.ensureInstalled("tavmod", dir), is(Outcome.INSTALLED));
    assertThat(Files.readString(dir.resolve("oamod.yaml")), is(installer.bundledProfile("tavmod").orElseThrow()));
    assertThat(installer.ensureInstalled("tavmod", dir), is(Outcome.UNCHANGED));
    try (var files = Files.list(dir)) {
      assertThat("no temp files are left behind", files.count(), is(1L));
    }
  }

  @Test
  public void replacesAnOlderProfileTheClientWrote() throws IOException {
    Path dir = temp.newFolder("esc").toPath();
    Files.writeString(dir.resolve("oamod.yaml"), OaProfileInstaller.MANAGED_MARKER + " v0\noamod: 1\n");

    assertThat(installer.ensureInstalled("taesc", dir), is(Outcome.INSTALLED));

    assertThat(Files.readString(dir.resolve("oamod.yaml")), is(installer.bundledProfile("taesc").orElseThrow()));
  }

  @Test
  public void neverOverwritesAProfileSomeoneElseWrote() throws IOException {
    Path dir = temp.newFolder("mine").toPath();
    String mine = "oamod: 1\nid: my-own-mod\n";
    Files.writeString(dir.resolve("oamod.yaml"), mine);

    assertThat(installer.ensureInstalled("tavmod", dir), is(Outcome.FOREIGN_PROFILE_KEPT));

    assertThat(Files.readString(dir.resolve("oamod.yaml")), is(mine));
    assertThat(Files.readString(dir.resolve("oamod.yaml")), not(startsWith(OaProfileInstaller.MANAGED_MARKER)));
  }
}
