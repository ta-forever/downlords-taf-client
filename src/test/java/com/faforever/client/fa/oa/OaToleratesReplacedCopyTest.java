package com.faforever.client.fa.oa;

import com.faforever.client.preferences.EngineUpdates;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.update.ClientConfiguration;
import com.faforever.client.update.ClientConfiguration.OaMod;
import com.faforever.client.update.ClientConfiguration.OaRelease;
import com.faforever.client.update.ClientConfiguration.OaSupport;
import com.faforever.client.update.ClientConfiguration.OpenAnnihilation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Engine updates = TAF while OA's own updater replaces TAF's copies (ADR 0002): TAF trusts --version, not the folder
 * name, and never fights OA's updater.
 */
public class OaToleratesReplacedCopyTest {

  private static final String MOD = "tacc";
  private static final String PLATFORM = OaPlatform.current();
  private static final String EXE = OaPlatform.executableName(PLATFORM);

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private PreferencesService preferencesService;
  private TotalAnnihilationPrefs prefs;
  private OaMod policy;
  private OpenAnnihilation oa;
  private OaInstallationService service;
  private final AtomicInteger downloads = new AtomicInteger();
  private final List<Runnable> offeredSwitches = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    preferencesService = org.mockito.Mockito.mock(PreferencesService.class);
    org.mockito.Mockito.when(preferencesService.getFafDataDirectory()).thenReturn(temp.newFolder("faf").toPath());
    prefs = new TotalAnnihilationPrefs(MOD, null, "", null);
    org.mockito.Mockito.when(preferencesService.getTotalAnnihilation(MOD)).thenReturn(prefs);
    org.mockito.Mockito.when(preferencesService.isOpenAnnihilation(MOD)).thenReturn(true);
    policy = new OaMod();
    policy.setModTechnical(MOD);
    policy.setSupport(OaSupport.OA_ONLY);
    oa = new OpenAnnihilation();
    oa.setMods(List.of(policy));
    oa.setReleases(List.of(release("0.8.14")));
    ClientConfiguration configuration = new ClientConfiguration();
    configuration.setOpenAnnihilation(oa);
    org.mockito.Mockito.when(preferencesService.getClientRemoteConfiguration()).thenReturn(configuration);

    service = new OaInstallationService(preferencesService);
    service.downloader = (url, destination) -> {
      downloads.incrementAndGet();
      byte[] zip = zip();
      Files.write(destination, zip);
      return sha256(zip);
    };
    // stands in for "open-annihilation --version": a "reported" file beside the executable
    service.versionAsker = exe -> {
      Path reported = exe.resolveSibling("reported");
      try {
        return Files.isRegularFile(reported) ? Optional.ofNullable(OaVersion.find(Files.readString(reported)))
            : Optional.empty();
      } catch (IOException e) {
        return Optional.empty();
      }
    };
    service.switchHint = (mod, switchAction) -> offeredSwitches.add(switchAction);
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] zip() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream out = new ZipOutputStream(bytes)) {
      out.putNextEntry(new ZipEntry(EXE));
      out.write("MZ".getBytes());
      out.closeEntry();
    }
    return bytes.toByteArray();
  }

  private OaRelease release(String version) throws Exception {
    OaRelease release = new OaRelease();
    release.setVersion(version);
    release.setPlatform(PLATFORM);
    release.setUrl(new URL("http://example.invalid/oa-" + version + ".zip"));
    release.setSha256(sha256(zip()));
    return release;
  }

  private Path engines() {
    return preferencesService.getFafDataDirectory().resolve("engines").resolve("open-annihilation");
  }

  /** A TAF copy in folder {@code folder} that, asked, says it is {@code reported}. */
  private Path plant(String folder, String reported) throws IOException {
    Path dir = engines().resolve(folder).resolve(PLATFORM);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve(EXE), "MZ");
    Files.writeString(dir.resolve("reported"), reported);
    return dir.resolve(EXE);
  }

  @Test
  public void askingForTheVersionOfACopyNeverBlocksTheCallingThread() throws Exception {
    plant("0.8.7", "0.8.14");
    java.util.concurrent.CountDownLatch asked = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    Thread caller = Thread.currentThread();
    java.util.function.Function<Path, Optional<OaVersion>> slow = service.versionAsker;
    service.versionAsker = exe -> {
      assertThat(Thread.currentThread() == caller, is(false));
      asked.countDown();
      try {
        release.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return slow.apply(exe);
    };

    java.util.concurrent.CompletableFuture<Path> future = service.ensureExecutable(MOD);

    assertThat(future.isDone(), is(false));
    assertThat(asked.await(5, java.util.concurrent.TimeUnit.SECONDS), is(true));
    release.countDown();
    assertThat(future.get(5, java.util.concurrent.TimeUnit.SECONDS).getFileName().toString(), is(EXE));
  }

  @Test
  public void aCopyOaReplacedWithAnAllowedNewerVersionLaunchesAsIs() throws Exception {
    Path replaced = plant("0.8.7", "0.8.14");

    Path exe = service.ensureExecutable(MOD).join();

    assertThat(exe, is(replaced));
    assertThat(downloads.get(), is(0));
  }

  @Test
  public void aCopyOaReplacedWithAVersionOutsideTheBoundsIsRefusedAndTheAllowedReleaseInstalledElsewhere() throws Exception {
    policy.setMaxVersion("0.8.14");
    Path replaced = plant("0.8.14", "0.8.20");

    Path exe = service.ensureExecutable(MOD).join();

    assertThat(downloads.get(), is(1));
    assertThat(exe.equals(replaced), is(false));
    assertThat(Files.isRegularFile(replaced), is(true));
    assertThat(service.findInstalled(MOD).exe(), is(exe));
    assertThat(service.findInstalled(MOD).version(), is(OaVersion.find("0.8.14")));
  }

  @Test
  public void versionComesFromVersionNotTheFolderName() throws Exception {
    plant("0.8.7", "0.8.14");

    assertThat(service.findInstalled(MOD).version(), is(OaVersion.find("0.8.14")));
    assertThat(service.engineReport(MOD).version(), is("0.8.14"));
  }

  @Test
  public void anOlderCopyStillGetsTheNewestAllowedReleaseInstalled() throws Exception {
    plant("0.8.7", "0.8.7");

    service.ensureExecutable(MOD).join();

    assertThat(downloads.get(), is(1));
  }

  @Test
  public void linuxLauncherIsPreferredOverTheCopyInVersions() throws Exception {
    Path root = temp.newFolder("linux").toPath();
    Files.createDirectories(root.resolve("versions").resolve("0.8.14"));
    Files.writeString(root.resolve("versions").resolve("0.8.14").resolve("open-annihilation"), "ELF");
    Files.writeString(root.resolve("open-annihilation"), "ELF");

    Path found = OaInstallationService.findExecutable(root, "linux-x86_64").orElseThrow();

    assertThat(found, is(root.resolve("open-annihilation")));
  }

  @Test
  public void aVersionsFolderIsOnlyUsedWhenThereIsNoLauncher() throws Exception {
    Path root = temp.newFolder("linux2").toPath();
    Files.createDirectories(root.resolve("versions").resolve("0.8.14"));
    Files.writeString(root.resolve("versions").resolve("0.8.14").resolve("open-annihilation"), "ELF");

    assertThat(OaInstallationService.findExecutable(root, "linux-x86_64").isPresent(), is(true));
  }

  @Test
  public void pruningNeverDeletesTheLaunchingFolderEvenWhenItsNameIsOld() throws Exception {
    Path launching = plant("0.8.1", "0.8.9");
    plant("0.8.5", "0.8.5");
    plant("0.8.6", "0.8.6");

    service.pruneOldVersions(launching.getParent().getParent());

    assertThat(Files.isDirectory(engines().resolve("0.8.1")), is(true));
    assertThat(Files.isDirectory(engines().resolve("0.8.6")), is(true));
    assertThat(Files.isDirectory(engines().resolve("0.8.5")), is(false));
  }

  @Test
  public void pruningJudgesAgeFromVersionNotFromTheFolderName() throws Exception {
    plant("0.8.2", "0.8.20"); // oldest by name, newest by --version
    plant("0.8.5", "0.8.5");
    plant("0.8.6", "0.8.6");
    Path launching = plant("0.8.7", "0.8.7");

    service.pruneOldVersions(launching.getParent().getParent());

    assertThat(Files.isDirectory(engines().resolve("0.8.2")), is(true));
    assertThat(Files.isDirectory(engines().resolve("0.8.5")), is(false));
    assertThat(Files.isDirectory(engines().resolve("0.8.6")), is(false));
    assertThat(Files.isDirectory(engines().resolve("0.8.7")), is(true));
  }

  @Test
  public void theSwitchHintComesOnlyFromTheSecondDetectionOnAndOnlyOnce() throws Exception {
    Path first = plant("0.8.7", "0.8.14");
    service.ensureExecutable(MOD).join();
    service.ensureExecutable(MOD).join(); // the same replaced copy again is not a new detection
    assertThat(offeredSwitches.size(), is(0));

    Files.writeString(first.resolveSibling("reported"), "0.8.15"); // OA replaced it once more
    service.ensureExecutable(MOD).join();
    assertThat(offeredSwitches.size(), is(1));

    Files.writeString(first.resolveSibling("reported"), "0.8.16");
    service.ensureExecutable(MOD).join();
    assertThat(offeredSwitches.size(), is(1));
  }

  @Test
  public void aVersionsFolderAppearingCountsAsAReplacement() throws Exception {
    Path exe = plant("0.8.14", "0.8.14");
    Files.createDirectories(exe.resolveSibling("versions").resolve("0.8.15"));
    service.ensureExecutable(MOD).join();
    Path other = plant("0.8.20", "0.8.20");
    oa.setReleases(List.of(release("0.8.20")));
    Files.createDirectories(other.resolveSibling("versions").resolve("0.8.21"));
    service.ensureExecutable(MOD).join();

    assertThat(offeredSwitches.size(), is(1));
  }

  @Test
  public void aCopyThatMatchesItsFolderNeverTriggersTheHint() throws Exception {
    plant("0.8.14", "0.8.14");
    for (int i = 0; i < 3; i++) {
      service.ensureExecutable(MOD).join();
    }

    assertThat(offeredSwitches.size(), is(0));
  }

  @Test
  public void theHintButtonSwitchesEngineUpdatesToOpenAnnihilation() throws Exception {
    Path copy = plant("0.8.7", "0.8.14");
    service.ensureExecutable(MOD).join();
    Files.writeString(copy.resolveSibling("reported"), "0.8.15");
    service.ensureExecutable(MOD).join();

    offeredSwitches.get(0).run();

    assertThat(prefs.getOaInstallation(), is(EngineUpdates.OPEN_ANNIHILATION));
  }
}
