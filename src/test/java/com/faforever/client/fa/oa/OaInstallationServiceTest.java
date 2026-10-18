package com.faforever.client.fa.oa;

import com.faforever.client.fa.oa.OaInstallationService.ProfileInfo;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.update.ClientConfiguration.OaMod;
import com.faforever.client.update.ClientConfiguration.OaSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

public class OaInstallationServiceTest {

  private static final String SIM = "a5eed394ee460d28768eec0b9421a40d59796521734e220fb538d1c4a3bdb350";

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private final OaInstallationService service = new OaInstallationService(mock(PreferencesService.class));

  private static OaMod policy(String min, String max) {
    OaMod mod = new OaMod();
    mod.setModTechnical("tacc");
    mod.setSupport(OaSupport.OA_ONLY);
    mod.setMinVersion(min);
    mod.setMaxVersion(max);
    return mod;
  }

  private Path fakeExe(String folder) throws IOException {
    Path dir = temp.newFolder(folder).toPath();
    return Files.writeString(dir.resolve("open-annihilation.exe"), "MZ");
  }

  @Test
  public void theVersionIsReadFromTheVersionLineOfDashDashVersion() {
    assertThat(OaInstallationService.parseVersionLine("Open Annihilation 0.8.1 (windows-x64 x86_64, 1a2b3c4d)\n"),
        is(new OaVersion(0, 8, 1)));
    assertThat(OaInstallationService.parseVersionLine("noise\nOpen Annihilation 0.9 (linux-x86_64 x86_64, unknown)"),
        is(new OaVersion(0, 9, 0)));
    assertThat(OaInstallationService.parseVersionLine("usage: open-annihilation"), is((OaVersion) null));
  }

  @Test
  public void versionComesFromTheVersionFileFirst() throws IOException {
    Path exe = fakeExe("open-annihilation-v0.1.0-windows-x64");
    Files.writeString(exe.resolveSibling(OaInstallationService.VERSION_FILE), "0.7.3\n");

    assertThat(service.detectVersion(exe).orElseThrow(), is(new OaVersion(0, 7, 3)));
  }

  @Test
  public void versionFallsBackToTheReleaseFolderName() throws IOException {
    Path exe = fakeExe("open-annihilation-v0.7.3-windows-x64");

    assertThat(service.detectVersion(exe).orElseThrow(), is(new OaVersion(0, 7, 3)));
  }

  @Test
  public void anUnlabelledFolderHasNoVersion() throws IOException {
    assertThat(service.detectVersion(fakeExe("oa")).isPresent(), is(false));
  }

  @Test
  public void findInstalledReportsAnExistingExecutableAndItsVersionWithoutJudgingIt() throws IOException {
    PreferencesService preferencesService = mock(PreferencesService.class);
    com.faforever.client.preferences.TotalAnnihilationPrefs prefs =
        new com.faforever.client.preferences.TotalAnnihilationPrefs("oalibrary", null, "", null);
    prefs.setOaInstallation(com.faforever.client.preferences.OaInstallationMode.EXISTING);
    org.mockito.Mockito.when(preferencesService.getTotalAnnihilation("oalibrary")).thenReturn(prefs);
    Path exe = fakeExe("open-annihilation-v0.7.3-windows-x64");
    prefs.setOaExecutablePath(exe);

    OaInstallationService.InstalledOa found = new OaInstallationService(preferencesService).findInstalled("oalibrary");

    assertThat(found.exe(), is(exe));
    assertThat(found.version(), is(new OaVersion(0, 7, 3)));

    prefs.setOaExecutablePath(exe.resolveSibling("gone.exe"));
    assertThat(new OaInstallationService(preferencesService).findInstalled("oalibrary"),
        is(OaInstallationService.InstalledOa.NONE));
  }

  @Test
  public void existingInstallMustBeAnAllowedVersion() throws IOException {
    Path exe = fakeExe("open-annihilation-v0.7.3-windows-x64");

    assertThat(service.validateExisting(exe, policy("0.7.3", null)), is(exe));
    IllegalStateException tooOld = assertThrows(IllegalStateException.class,
        () -> service.validateExisting(exe, policy("0.8.0", null)));
    assertThat(tooOld.getMessage().contains("0.7.3"), is(true));
  }

  @Test
  public void existingInstallMustExistAndBeLabelled() throws IOException {
    assertThrows(IllegalStateException.class, () -> service.validateExisting(null, policy(null, null)));
    assertThrows(IllegalStateException.class,
        () -> service.validateExisting(temp.getRoot().toPath().resolve("missing.exe"), policy(null, null)));
    Path unlabelled = fakeExe("oa");
    assertThrows(IllegalStateException.class, () -> service.validateExisting(unlabelled, policy(null, null)));
  }

  @Test
  public void profileOutputYieldsTheSimHash() {
    String output = "{\n  \"id\": \"taf-m0-prota\"\n}\nsha256-sim  " + SIM + "\n";

    ProfileInfo info = OaInstallationService.parseProfile(0, output);

    assertThat(info.exitCode(), is(0));
    assertThat(info.hasProfile(), is(true));
    assertThat(info.simHash().orElseThrow(), is(SIM));
  }

  @Test
  public void aFolderWithoutAProfilePlaysBase31cAndHasNoHash() {
    ProfileInfo info = OaInstallationService.parseProfile(0,
        "D:\\games\\TAF-Core Contingency: no oamod.yaml; the folder plays base 3.1c\n");

    assertThat(info.hasProfile(), is(false));
    assertThat(info.simHash().isPresent(), is(false));
  }

  @Test
  public void refusalTextIsOasLastLineWithoutItsPrefix() {
    String output = String.join(System.lineSeparator(),
        "Offline match world prepared for Canal Crossing",
        "demo unit table: 316 recorded, 0 matched",
        "open-annihilation: demo playback needs the recording's unit definitions: 316 missing",
        "", "");

    assertThat(OaInstallationService.refusalText(output),
        is("demo playback needs the recording's unit definitions: 316 missing"));
  }

  @Test
  public void unzipRefusesEntriesThatEscapeTheTarget() throws IOException {
    Path zip = temp.newFile("evil.zip").toPath();
    try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(out)) {
      zos.putNextEntry(new ZipEntry("../escaped.txt"));
      zos.write("x".getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    Path target = temp.newFolder("target").toPath();

    assertThrows(IOException.class, () -> OaInstallationService.unzip(zip, target));
    assertThat(Files.exists(target.resolveSibling("escaped.txt")), is(false));
  }

  @Test
  public void unzipThenFindExecutableInsideTheReleaseFolder() throws IOException {
    Path zip = temp.newFile("oa.zip").toPath();
    try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(out)) {
      zos.putNextEntry(new ZipEntry("open-annihilation-v0.7.3-windows-x64/open-annihilation.exe"));
      zos.write("MZ".getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
      zos.putNextEntry(new ZipEntry("open-annihilation-v0.7.3-windows-x64/languages/en.txt"));
      zos.write("hi".getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    Path target = temp.newFolder("installed").toPath();

    OaInstallationService.unzip(zip, target);

    Path exe = OaInstallationService.findExecutable(target, "windows-x64").orElseThrow();
    assertThat(exe.getFileName().toString(), is("open-annihilation.exe"));
    assertThat(Files.exists(exe.resolveSibling("languages").resolve("en.txt")), is(true));
    assertThat(OaInstallationService.findExecutable(target, "linux-x86_64").isPresent(), is(false));
  }
}
