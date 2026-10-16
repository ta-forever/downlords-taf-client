package com.faforever.client.fa.oa;

import com.faforever.client.preferences.OaInstallationMode;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.update.ClientConfiguration.OaMod;
import com.faforever.client.update.ClientConfiguration.OaRelease;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Finds, downloads and validates the Open Annihilation executable (OPEN_ANNIHILATION_INTEGRATION.md §6.1).
 *
 * <p>TAF-managed copies live in {@code <fafData>/engines/open-annihilation/<version>/<platform>/}.
 * Only releases that the server-supplied configuration lists, with a sha256, are ever installed;
 * GitHub "latest" is never followed.
 */
@Lazy
@Service
@RequiredArgsConstructor
@Slf4j
public class OaInstallationService {

  static final String VERSION_FILE = "VERSION";
  private static final int HTTP_FOLLOW_LIMIT = 5;
  private static final int KEEP_VERSIONS = 2;

  private final PreferencesService preferencesService;

  Path enginesRoot() {
    return preferencesService.getFafDataDirectory().resolve("engines").resolve("open-annihilation");
  }

  /**
   * The executable to run for {@code modTechnical}: the user's own for EXISTING, the pinned download
   * for TAF_MANAGED (downloaded now if missing). Completes exceptionally with a message fit for the user.
   */
  public CompletableFuture<Path> ensureExecutable(String modTechnical) {
    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    OaMod policy = OaPolicy.forMod(preferencesService.getClientRemoteConfiguration(), modTechnical).orElse(null);
    if (policy == null) {
      return CompletableFuture.failedFuture(new IllegalStateException("Open Annihilation is not supported for " + modTechnical));
    }

    if (prefs.getOaInstallation() == OaInstallationMode.EXISTING) {
      Path exe = prefs.getOaExecutablePath();
      return CompletableFuture.supplyAsync(() -> validateExisting(exe, policy));
    }

    String platform = OaPlatform.current();
    OaRelease release = OaPolicy.pickRelease(preferencesService.getClientRemoteConfiguration(), modTechnical, platform)
        .orElse(null);
    if (release == null) {
      return CompletableFuture.failedFuture(new IllegalStateException(
          "The server offers no Open Annihilation release for " + platform + " that is allowed for " + modTechnical));
    }
    return CompletableFuture.supplyAsync(() -> {
      try {
        return installRelease(release, platform);
      } catch (IOException e) {
        throw new IllegalStateException("Could not install Open Annihilation " + release.getVersion() + ": " + e.getMessage(), e);
      }
    });
  }

  /** @throws IllegalStateException when the executable is missing or its version is not allowed */
  Path validateExisting(Path exe, OaMod policy) {
    if (exe == null || !Files.isRegularFile(exe)) {
      throw new IllegalStateException("The Open Annihilation executable was not found: " + exe);
    }
    OaVersion version = detectVersion(exe).orElseThrow(() -> new IllegalStateException(
        "Cannot tell which Open Annihilation version " + exe + " is. Put a VERSION file beside it, or keep the "
            + "release folder name (open-annihilation-vX.Y.Z-...)"));
    if (!OaPolicy.isVersionAllowed(policy, version)) {
      throw new IllegalStateException("Open Annihilation " + version + " is not allowed by the server (needs "
          + (policy.getMinVersion() == null ? "any" : policy.getMinVersion()) + " to "
          + (policy.getMaxVersion() == null ? "any" : policy.getMaxVersion()) + ")");
    }
    return exe;
  }

  /**
   * OA 0.7.3 has no {@code --version} (upstream ask U3), so read the {@code VERSION} file TAF
   * writes, then fall back to the release folder name.
   */
  public Optional<OaVersion> detectVersion(Path exe) {
    if (exe == null) {
      return Optional.empty();
    }
    Path dir = exe.toAbsolutePath().getParent();
    if (dir == null) {
      return Optional.empty();
    }
    Path versionFile = dir.resolve(VERSION_FILE);
    if (Files.isRegularFile(versionFile)) {
      try {
        OaVersion version = OaVersion.find(Files.readString(versionFile, StandardCharsets.UTF_8));
        if (version != null) {
          return Optional.of(version);
        }
      } catch (IOException e) {
        log.warn("Could not read {}", versionFile, e);
      }
    }
    for (Path p = dir; p != null && p.getFileName() != null; p = p.getParent()) {
      String name = p.getFileName().toString();
      if (name.toLowerCase().contains("annihilation")) {
        OaVersion version = OaVersion.find(name);
        if (version != null) {
          return Optional.of(version);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * What the client tells the lobby server about the engine it will run the mod on, so that the server can
   * keep Open Annihilation and TotalA.exe players apart where the mod's policy says so.
   *
   * @param engine  {@code "cavedog"} or {@code "oa"}
   * @param version the Open Annihilation version, null for cavedog or when it cannot be told
   */
  public record EngineReport(String engine, String version) {
    public static final EngineReport CAVEDOG = new EngineReport("cavedog", null);
  }

  public EngineReport engineReport(String modTechnical) {
    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    if (!prefs.isOpenAnnihilation()) {
      return EngineReport.CAVEDOG;
    }
    Optional<OaVersion> version;
    if (prefs.getOaInstallation() == OaInstallationMode.EXISTING) {
      version = detectVersion(prefs.getOaExecutablePath());
    } else {
      version = OaPolicy.pickRelease(preferencesService.getClientRemoteConfiguration(), modTechnical, OaPlatform.current())
          .map(release -> OaVersion.find(release.getVersion()));
    }
    return new EngineReport("oa", version.map(OaVersion::toString).orElse(null));
  }

  /** The result of {@code --print-profile}: the resolved profile of a game dir. */
  public record ProfileInfo(int exitCode, boolean hasProfile, Optional<String> simHash, String output) {
  }

  private static final Pattern SIM_HASH_LINE = Pattern.compile("^sha256-sim\\s+([0-9a-fA-F]{64})\\s*$", Pattern.MULTILINE);

  /** Parses the output of {@code open-annihilation --print-profile}. */
  static ProfileInfo parseProfile(int exitCode, String output) {
    Matcher matcher = SIM_HASH_LINE.matcher(output);
    Optional<String> simHash = matcher.find() ? Optional.of(matcher.group(1).toLowerCase()) : Optional.empty();
    // a folder without oamod.yaml plays base 3.1c rules: valid, but there is no sim hash to compare
    return new ProfileInfo(exitCode, !output.contains("no oamod.yaml"), simHash, output);
  }

  /** Runs {@code --print-profile --game-dir <modDir>} (blocking, bounded). */
  public ProfileInfo printProfile(Path exe, Path modDir) throws IOException {
    Process process = new ProcessBuilder(OaCommands.printProfile(exe, modDir))
        .directory(exe.toAbsolutePath().getParent().toFile())
        .redirectErrorStream(true)
        .start();
    try {
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(30, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IOException("--print-profile did not finish within 30 s");
      }
      return parseProfile(process.exitValue(), output);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new IOException("interrupted", e);
    }
  }

  /**
   * Asks OA whether it can play {@code demo} with this mod install, before a window is opened for it.
   *
   * @return null if OA accepts the demo, else OA's own refusal text
   */
  public String checkDemo(Path exe, Path modDir, Path demo) {
    try {
      Process process = new ProcessBuilder(OaCommands.checkDemo(exe, modDir, demo))
          .directory(exe.toAbsolutePath().getParent().toFile())
          .redirectErrorStream(true)
          .start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return "Open Annihilation did not finish checking the replay within 60 s";
      }
      return process.exitValue() == 0 ? null : refusalText(output);
    } catch (IOException e) {
      return "Could not run Open Annihilation: " + e.getMessage();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "interrupted";
    }
  }

  /** OA ends a refusal with its reason on the last line ("open-annihilation: demo playback needs ..."). */
  static String refusalText(String output) {
    String last = "";
    for (String line : output.split("\\R")) {
      if (!line.isBlank()) {
        last = line.trim();
      }
    }
    return last.startsWith("open-annihilation:") ? last.substring("open-annihilation:".length()).trim() : last;
  }

  /**
   * The pre-launch check (OPEN_ANNIHILATION_INTEGRATION.md §6.4): the mod dir's profile must resolve, and
   * its sim hash must equal what the server expects for this OA version, when the server publishes one.
   *
   * @return null if the game may proceed, else a message for the user
   */
  public String checkProfile(String modTechnical, Path exe, Path modDir) {
    try {
      ProfileInfo info = printProfile(exe, modDir);
      if (info.exitCode() != 0) {
        return "Open Annihilation rejects the profile of " + modDir + ": " + info.output();
      }
      OaMod policy = OaPolicy.forMod(preferencesService.getClientRemoteConfiguration(), modTechnical).orElse(null);
      OaVersion version = detectVersion(exe).orElse(null);
      Optional<String> expected = OaPolicy.expectedSimHash(policy, version);
      if (expected.isPresent() && !expected.get().equalsIgnoreCase(info.simHash().orElse(""))) {
        return "The Open Annihilation profile of this install (sim hash " + info.simHash().orElse("none")
            + ") differs from the one the server expects (" + expected.get() + "). Repair the install or update.";
      }
      return null;
    } catch (IOException e) {
      return "Could not check the Open Annihilation profile: " + e.getMessage();
    }
  }

  private Path installRelease(OaRelease release, String platform) throws IOException {
    Path target = enginesRoot().resolve(release.getVersion()).resolve(platform);
    Optional<Path> existing = findExecutable(target, platform);
    if (existing.isPresent()) {
      return existing.get();
    }

    Files.createDirectories(target.getParent());
    Path download = Files.createTempFile(target.getParent(), "oa-", ".zip");
    Path staging = target.resolveSibling(platform + ".part");
    try {
      log.info("Downloading Open Annihilation {} from {}", release.getVersion(), release.getUrl());
      String sha256 = download(release.getUrl(), download);
      if (!sha256.equalsIgnoreCase(release.getSha256().trim())) {
        throw new IOException("sha256 mismatch (expected " + release.getSha256() + ", got " + sha256 + ")");
      }
      deleteRecursively(staging);
      Files.createDirectories(staging);
      unzip(download, staging);
      Path exe = findExecutable(staging, platform).orElseThrow(
          () -> new IOException("the archive contains no " + OaPlatform.executableName(platform)));
      Files.writeString(exe.getParent().resolve(VERSION_FILE), release.getVersion() + "\n", StandardCharsets.UTF_8);
      makeExecutable(exe);
      deleteRecursively(target);
      Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(download);
      deleteRecursively(staging);
    }
    pruneOldVersions(release.getVersion());
    clearMacQuarantine(target);
    return findExecutable(target, platform).orElseThrow(() -> new IOException("installed executable vanished"));
  }

  static Optional<Path> findExecutable(Path root, String platform) throws IOException {
    if (!Files.isDirectory(root)) {
      return Optional.empty();
    }
    String name = OaPlatform.executableName(platform);
    try (Stream<Path> files = Files.walk(root, 3)) {
      return files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().equals(name)).findFirst();
    }
  }

  /** Streams {@code url} to {@code destination} and returns the lower-case hex sha256 of what was written. */
  static String download(URL url, Path destination) throws IOException {
    URL current = url;
    for (int hops = 0; hops <= HTTP_FOLLOW_LIMIT; hops++) {
      HttpURLConnection connection = (HttpURLConnection) current.openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(15));
      connection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(30));
      int status = connection.getResponseCode();
      if (status >= 300 && status < 400 && connection.getHeaderField("Location") != null) {
        current = new URL(current, connection.getHeaderField("Location"));
        connection.disconnect();
        continue;
      }
      if (status != 200) {
        connection.disconnect();
        throw new IOException("HTTP " + status + " from " + current);
      }
      try (InputStream in = connection.getInputStream()) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream digesting = new DigestInputStream(in, digest)) {
          Files.copy(digesting, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return HexFormat.of().formatHex(digest.digest());
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      } finally {
        connection.disconnect();
      }
    }
    throw new IOException("too many redirects from " + url);
  }

  static void unzip(Path zip, Path destination) throws IOException {
    Path root = destination.toAbsolutePath().normalize();
    try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
      for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        Path out = root.resolve(entry.getName()).normalize();
        if (!out.startsWith(root)) {
          throw new IOException("archive entry escapes the target folder: " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(out);
        } else {
          Files.createDirectories(out.getParent());
          Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private static void makeExecutable(Path exe) {
    if (OaPlatform.isWindows()) {
      return;
    }
    try {
      Files.setPosixFilePermissions(exe, Set.of(
          PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
          PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE));
    } catch (IOException | UnsupportedOperationException e) {
      log.warn("Could not mark {} executable", exe, e);
    }
  }

  private void clearMacQuarantine(Path dir) {
    if (!OaPlatform.isMac()) {
      return;
    }
    try {
      new ProcessBuilder("xattr", "-dr", "com.apple.quarantine", dir.toString()).start().waitFor(10, TimeUnit.SECONDS);
    } catch (IOException e) {
      log.warn("Could not clear the quarantine flag on {}", dir, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Keeps the newest few versions; the rest are garbage. */
  private void pruneOldVersions(String keepVersion) {
    Path root = enginesRoot();
    if (!Files.isDirectory(root)) {
      return;
    }
    try (Stream<Path> versions = Files.list(root)) {
      versions.filter(Files::isDirectory)
          .filter(dir -> !dir.getFileName().toString().equals(keepVersion))
          .sorted(Comparator.comparing((Path dir) -> OaVersion.find(dir.getFileName().toString()),
              Comparator.nullsFirst(Comparator.<OaVersion>naturalOrder())).reversed())
          .skip(KEEP_VERSIONS - 1)
          .toList()
          .forEach(OaInstallationService::deleteRecursively);
    } catch (IOException e) {
      log.warn("Could not prune old Open Annihilation versions", e);
    }
  }

  static void deleteRecursively(Path dir) {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> files = Files.walk(dir)) {
      files.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          log.warn("Could not delete {}", p, e);
        }
      });
    } catch (IOException e) {
      log.warn("Could not delete {}", dir, e);
    }
  }
}
