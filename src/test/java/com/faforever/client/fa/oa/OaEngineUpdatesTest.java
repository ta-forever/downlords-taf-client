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
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Engine updates = Open Annihilation: TAF installs once into self-updating/, then never touches it. */
public class OaEngineUpdatesTest {

  private static final String MOD = "tacc";
  private static final String PLATFORM = OaPlatform.current();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private PreferencesService preferencesService;
  private TotalAnnihilationPrefs prefs;
  private OaMod policy;
  private OpenAnnihilation oa;
  private OaInstallationService service;
  private final AtomicInteger downloads = new AtomicInteger();

  @Before
  public void setUp() throws Exception {
    preferencesService = mock(PreferencesService.class);
    when(preferencesService.getFafDataDirectory()).thenReturn(temp.newFolder("faf").toPath());
    prefs = new TotalAnnihilationPrefs(MOD, null, "", null);
    when(preferencesService.getTotalAnnihilation(MOD)).thenReturn(prefs);
    when(preferencesService.isOpenAnnihilation(MOD)).thenReturn(true);
    policy = new OaMod();
    policy.setModTechnical(MOD);
    policy.setSupport(OaSupport.OA_ONLY);
    oa = new OpenAnnihilation();
    oa.setMods(List.of(policy));
    oa.setReleases(List.of(release("0.8.7")));
    ClientConfiguration configuration = new ClientConfiguration();
    configuration.setOpenAnnihilation(oa);
    when(preferencesService.getClientRemoteConfiguration()).thenReturn(configuration);

    service = new OaInstallationService(preferencesService);
    service.downloader = (url, destination) -> {
      downloads.incrementAndGet();
      byte[] zip = zip();
      Files.write(destination, zip);
      return sha256(zip);
    };
    prefs.setOaInstallation(EngineUpdates.OPEN_ANNIHILATION);
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
      out.putNextEntry(new ZipEntry(OaPlatform.executableName(PLATFORM)));
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

  private Path selfUpdating() {
    return preferencesService.getFafDataDirectory().resolve("engines").resolve("open-annihilation")
        .resolve("self-updating").resolve(PLATFORM);
  }

  @Test
  public void theFirstInstallGoesIntoSelfUpdating() {
    Path exe = service.ensureExecutable(MOD).join();

    assertThat(exe.startsWith(selfUpdating()), is(true));
    assertThat(downloads.get(), is(1));
  }

  @Test
  public void thereIsNoSecondDownloadEvenWhenTheServerListsANewerRelease() throws Exception {
    Path first = service.ensureExecutable(MOD).join();
    oa.setReleases(List.of(release("0.8.7"), release("0.8.14")));

    Path second = service.ensureExecutable(MOD).join();

    assertThat(second, is(first));
    assertThat(downloads.get(), is(1));
  }

  @Test
  public void pruningNeverTouchesSelfUpdating() throws Exception {
    service.ensureExecutable(MOD).join();
    prefs.setOaInstallation(EngineUpdates.TAF);
    for (String version : new String[]{"0.8.8", "0.8.9", "0.8.10"}) {
      oa.setReleases(List.of(release(version)));
      service.ensureExecutable(MOD).join();
    }

    assertThat(Files.isRegularFile(selfUpdating().resolve(OaPlatform.executableName(PLATFORM))), is(true));
  }

  @Test
  public void switchingFromTafStartsAFreshInstallAndLeavesTafsCopyAlone() {
    prefs.setOaInstallation(EngineUpdates.TAF);
    Path tafCopy = service.ensureExecutable(MOD).join();
    prefs.setOaInstallation(EngineUpdates.OPEN_ANNIHILATION);

    Path own = service.ensureExecutable(MOD).join();

    assertThat(downloads.get(), is(2));
    assertThat(own.startsWith(selfUpdating()), is(true));
    assertThat(Files.isRegularFile(tafCopy), is(true));
  }

  @Test
  public void anOaUpdatedInstallBelowMinVersionIsRefusedAndOneInsideTheBoundsLaunches() {
    Path exe = service.ensureExecutable(MOD).join();

    policy.setMinVersion("0.8.0");
    assertThat(service.ensureExecutable(MOD).join(), is(exe));

    policy.setMinVersion("0.8.8");
    CompletionException refused = assertThrows(CompletionException.class, () -> service.ensureExecutable(MOD).join());
    assertThat(refused.getCause().getMessage().contains("0.8.7"), is(true));
  }

  @Test
  public void engineReportSaysTheVersionOfTheOaUpdatedInstallNotTheOneThePolicyWouldPick() throws Exception {
    service.ensureExecutable(MOD).join();
    oa.setReleases(List.of(release("0.8.14")));

    assertThat(service.engineReport(MOD).version(), is("0.8.7"));
  }
}
