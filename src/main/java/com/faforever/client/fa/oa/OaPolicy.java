package com.faforever.client.fa.oa;

import com.faforever.client.update.ClientConfiguration;
import com.faforever.client.update.ClientConfiguration.OaMod;
import com.faforever.client.update.ClientConfiguration.OaRelease;
import com.faforever.client.update.ClientConfiguration.OaSupport;
import com.faforever.client.update.ClientConfiguration.OpenAnnihilation;

import java.util.Comparator;
import java.util.Optional;

/**
 * What the server-supplied configuration says about Open Annihilation for a mod.
 * Pure functions over {@link ClientConfiguration}, so they can be tested without Spring.
 */
public final class OaPolicy {

  private OaPolicy() {
  }

  public static Optional<OaMod> forMod(ClientConfiguration configuration, String modTechnical) {
    OpenAnnihilation oa = configuration == null ? null : configuration.getOpenAnnihilation();
    if (oa == null || oa.getMods() == null) {
      return Optional.empty();
    }
    return oa.getMods().stream()
        .filter(mod -> modTechnical.equalsIgnoreCase(mod.getModTechnical()))
        .findFirst();
  }

  /** Whether the engine section is offered for this mod at all. */
  public static boolean isOaSupported(ClientConfiguration configuration, String modTechnical) {
    return forMod(configuration, modTechnical)
        .map(mod -> mod.getSupport() != null && mod.getSupport() != OaSupport.NONE)
        .orElse(false);
  }

  /**
   * Whether a mod really runs on Open Annihilation: the player chose it AND the server offers OA for the mod.
   * A choice saved while the server offered OA for a mod (or before it stopped) must not outlive that offer, or the
   * host/join request carries engine "oa" and the server refuses it ("Open Annihilation is not enabled for this mod").
   */
  public static boolean usesOpenAnnihilation(ClientConfiguration configuration, String modTechnical, boolean chosenInPrefs) {
    return chosenInPrefs && isOaSupported(configuration, modTechnical);
  }

  public static boolean isVersionAllowed(OaMod mod, OaVersion version) {
    if (mod == null || version == null || mod.getSupport() == null || mod.getSupport() == OaSupport.NONE) {
      return false;
    }
    OaVersion min = OaVersion.find(mod.getMinVersion());
    OaVersion max = OaVersion.find(mod.getMaxVersion());
    return (min == null || version.compareTo(min) >= 0) && (max == null || version.compareTo(max) <= 0);
  }

  /**
   * The release TAF should download for the mod: the newest one for {@code platform} that the
   * mod's version bounds allow and that carries a sha256.
   */
  public static Optional<OaRelease> pickRelease(ClientConfiguration configuration, String modTechnical, String platform) {
    OpenAnnihilation oa = configuration == null ? null : configuration.getOpenAnnihilation();
    Optional<OaMod> mod = forMod(configuration, modTechnical);
    if (oa == null || oa.getReleases() == null || mod.isEmpty()) {
      return Optional.empty();
    }
    return oa.getReleases().stream()
        .filter(release -> platform.equals(release.getPlatform()))
        .filter(release -> release.getUrl() != null && release.getSha256() != null && !release.getSha256().isBlank())
        .filter(release -> isVersionAllowed(mod.get(), OaVersion.find(release.getVersion())))
        .max(Comparator.comparing(release -> OaVersion.find(release.getVersion())));
  }

  /** The sim hash the server expects for {@code version}, if it publishes one. */
  public static Optional<String> expectedSimHash(OaMod mod, OaVersion version) {
    if (mod == null || mod.getSimHashes() == null || version == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(mod.getSimHashes().get(version.toString()));
  }
}
