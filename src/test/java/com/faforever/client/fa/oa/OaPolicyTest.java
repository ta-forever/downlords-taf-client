package com.faforever.client.fa.oa;

import com.faforever.client.update.ClientConfiguration;
import com.faforever.client.update.ClientConfiguration.OaMod;
import com.faforever.client.update.ClientConfiguration.OaRelease;
import com.faforever.client.update.ClientConfiguration.OaSupport;
import com.faforever.client.update.ClientConfiguration.OpenAnnihilation;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class OaPolicyTest {

  private static OaRelease release(String version, String platform, String sha256) throws MalformedURLException {
    OaRelease release = new OaRelease();
    release.setVersion(version);
    release.setPlatform(platform);
    release.setUrl(new URL("https://example.com/oa-" + version + ".zip"));
    release.setSha256(sha256);
    return release;
  }

  private static OaMod mod(String technical, OaSupport support, String min, String max) {
    OaMod mod = new OaMod();
    mod.setModTechnical(technical);
    mod.setSupport(support);
    mod.setMinVersion(min);
    mod.setMaxVersion(max);
    return mod;
  }

  private static ClientConfiguration configuration(List<OaRelease> releases, List<OaMod> mods) {
    OpenAnnihilation oa = new OpenAnnihilation();
    oa.setReleases(releases);
    oa.setMods(mods);
    ClientConfiguration configuration = new ClientConfiguration();
    configuration.setOpenAnnihilation(oa);
    return configuration;
  }

  @Test
  public void versionParsing() {
    assertThat(OaVersion.find("v0.7.3"), is(new OaVersion(0, 7, 3)));
    assertThat(OaVersion.find("open-annihilation-v0.7.3-windows-x64"), is(new OaVersion(0, 7, 3)));
    assertThat(OaVersion.find("1.2"), is(new OaVersion(1, 2, 0)));
    assertThat(OaVersion.find("nothing"), is((OaVersion) null));
    assertThat(OaVersion.find("0.10.0").compareTo(OaVersion.find("0.9.9")) > 0, is(true));
  }

  @Test
  public void noConfigurationMeansNoSupport() {
    assertThat(OaPolicy.isOaSupported(new ClientConfiguration(), "tacc"), is(false));
    assertThat(OaPolicy.isOaSupported(null, "tacc"), is(false));
  }

  @Test
  public void supportDependsOnTheModAndIsNotNone() {
    ClientConfiguration configuration = configuration(List.of(), List.of(
        mod("tacc", OaSupport.OA_ONLY, null, null), mod("taesc", OaSupport.NONE, null, null)));
    assertThat(OaPolicy.isOaSupported(configuration, "tacc"), is(true));
    assertThat(OaPolicy.isOaSupported(configuration, "TACC"), is(true));
    assertThat(OaPolicy.isOaSupported(configuration, "taesc"), is(false));
    assertThat(OaPolicy.isOaSupported(configuration, "tavmod"), is(false));
  }

  @Test
  public void versionBoundsAreInclusive() {
    OaMod mod = mod("tacc", OaSupport.OA_ONLY, "0.7.3", "0.8.0");
    assertThat(OaPolicy.isVersionAllowed(mod, new OaVersion(0, 7, 2)), is(false));
    assertThat(OaPolicy.isVersionAllowed(mod, new OaVersion(0, 7, 3)), is(true));
    assertThat(OaPolicy.isVersionAllowed(mod, new OaVersion(0, 8, 0)), is(true));
    assertThat(OaPolicy.isVersionAllowed(mod, new OaVersion(0, 8, 1)), is(false));
    assertThat(OaPolicy.isVersionAllowed(mod("tacc", OaSupport.NONE, null, null), new OaVersion(0, 7, 3)), is(false));
    assertThat(OaPolicy.isVersionAllowed(mod, null), is(false));
  }

  @Test
  public void picksTheNewestAllowedReleaseForThePlatform() throws Exception {
    ClientConfiguration configuration = configuration(List.of(
        release("0.7.2", "windows-x64", "aa"),
        release("0.7.3", "windows-x64", "bb"),
        release("0.7.4", "windows-x64", "cc"),
        release("0.7.3", "linux-x86_64", "dd")),
        List.of(mod("tacc", OaSupport.OA_ONLY, "0.7.2", "0.7.3")));

    assertThat(OaPolicy.pickRelease(configuration, "tacc", "windows-x64").orElseThrow().getSha256(), is("bb"));
    assertThat(OaPolicy.pickRelease(configuration, "tacc", "linux-x86_64").orElseThrow().getSha256(), is("dd"));
    assertThat(OaPolicy.pickRelease(configuration, "tacc", "macos-universal").isPresent(), is(false));
    assertThat(OaPolicy.pickRelease(configuration, "taesc", "windows-x64").isPresent(), is(false));
  }

  @Test
  public void aReleaseWithoutAHashIsNeverPicked() throws Exception {
    ClientConfiguration configuration = configuration(List.of(release("0.7.3", "windows-x64", null),
        release("0.7.2", "windows-x64", " ")), List.of(mod("tacc", OaSupport.OA_ONLY, null, null)));
    assertThat(OaPolicy.pickRelease(configuration, "tacc", "windows-x64").isPresent(), is(false));
  }

  @Test
  public void expectedSimHashIsLookedUpByVersion() {
    OaMod mod = mod("tacc", OaSupport.OA_ONLY, null, null);
    mod.setSimHashes(Map.of("0.7.3", "abc"));
    assertThat(OaPolicy.expectedSimHash(mod, new OaVersion(0, 7, 3)).orElseThrow(), is("abc"));
    assertThat(OaPolicy.expectedSimHash(mod, new OaVersion(0, 7, 4)).isPresent(), is(false));
  }

  @Test
  public void platformNames() {
    assertThat(OaPlatform.of("Windows 10", "amd64"), is("windows-x64"));
    assertThat(OaPlatform.of("Windows 7", "x86"), is("windows-x86"));
    assertThat(OaPlatform.of("Linux", "amd64"), is("linux-x86_64"));
    assertThat(OaPlatform.of("Linux", "aarch64"), is("linux-arm64"));
    assertThat(OaPlatform.of("Linux", "arm"), is("linux-armhf"));
    assertThat(OaPlatform.of("Mac OS X", "aarch64"), is("macos-universal"));
    assertThat(OaPlatform.executableName("windows-x64"), is("open-annihilation.exe"));
    assertThat(OaPlatform.executableName("linux-x86_64"), is("open-annihilation"));
  }
}
