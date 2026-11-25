package com.faforever.client.fa.oa;

import com.faforever.client.game.KnownFeaturedMod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Puts the mod's Open Annihilation profile ({@code oamod.yaml}) into its install folder before OA runs there
 * (OPEN_ANNIHILATION_INTEGRATION.md section 2.2, OA_PROFILES.md).
 *
 * <p>The profiles ship inside the client ({@code /oa/profiles/<mod>.yaml}, generated from tadr-ddraw's per-mod
 * configs). Every player of a game therefore runs the profile their client version carries, and the pre-launch
 * sim-hash check catches the case where two clients disagree. A folder holds one profile at most; a copy the
 * client wrote earlier is replaced, but a file someone else wrote is never touched. TotalA.exe ignores the file,
 * so the folder keeps working for the Cavedog engine.
 */
@Slf4j
@Service
public class OaProfileInstaller {

  public static final String PROFILE_FILE = "oamod.yaml";
  /** First line of every generated profile; the client overwrites only files that start with it. */
  public static final String MANAGED_MARKER = "# taf-client-managed oa-profile";

  public enum Outcome {
    /** The mod has no bundled profile: OA plays it as 3.1c. */
    NO_PROFILE,
    /** The folder already held the current profile. */
    UNCHANGED,
    /** The profile was written, or replaced an older one the client had written. */
    INSTALLED,
    /** The folder holds a profile the client did not write; it was left alone. */
    FOREIGN_PROFILE_KEPT
  }

  /** The bundled profile for a featured mod; {@code ladder1v1} shares {@code tacc}'s install, so its profile. */
  public Optional<String> bundledProfile(String modTechnical) {
    String base = baseGameName(modTechnical);
    try (InputStream in = OaProfileInstaller.class.getResourceAsStream("/oa/profiles/" + base + ".yaml")) {
      if (in == null) {
        return Optional.empty();
      }
      return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      log.warn("Could not read the bundled Open Annihilation profile for {}", modTechnical, e);
      return Optional.empty();
    }
  }

  private static final java.util.regex.Pattern NETWORK_VERSION =
      java.util.regex.Pattern.compile("^\\s*network-version:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*]", java.util.regex.Pattern.MULTILINE);

  /**
   * The multiplayer version ({@code MAJOR.MINOR}) the mod's TotalA.exe announces, which its OA profile repeats as
   * {@code identity.network-version}; 3.1c's {@code 3.1} when the profile leaves it out. Empty when the mod has no
   * bundled profile.
   */
  public Optional<String> networkVersion(String modTechnical) {
    return bundledProfile(modTechnical).map(profile -> {
      java.util.regex.Matcher matcher = NETWORK_VERSION.matcher(profile);
      return matcher.find() ? matcher.group(1) + "." + matcher.group(2) : "3.1";
    });
  }

  public Outcome ensureInstalled(String modTechnical, Path modDir) throws IOException {
    Optional<String> bundled = bundledProfile(modTechnical);
    if (bundled.isEmpty()) {
      return Outcome.NO_PROFILE;
    }
    Path target = modDir.resolve(PROFILE_FILE);
    if (Files.isRegularFile(target)) {
      String existing = Files.readString(target, StandardCharsets.UTF_8);
      if (existing.equals(bundled.get())) {
        return Outcome.UNCHANGED;
      }
      if (!existing.startsWith(MANAGED_MARKER)) {
        log.warn("{} holds an {} that the TAF client did not write; keeping it. Delete it to get the TAF profile.",
            modDir, PROFILE_FILE);
        return Outcome.FOREIGN_PROFILE_KEPT;
      }
    }
    Path temp = Files.createTempFile(modDir, PROFILE_FILE, ".tmp");
    try {
      Files.writeString(temp, bundled.get(), StandardCharsets.UTF_8);
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
    log.info("Installed the Open Annihilation profile for {} into {}", modTechnical, modDir);
    return Outcome.INSTALLED;
  }

  static String baseGameName(String modTechnical) {
    KnownFeaturedMod known = KnownFeaturedMod.fromString(modTechnical);
    return known != null ? known.getBaseGameName() : modTechnical;
  }
}
