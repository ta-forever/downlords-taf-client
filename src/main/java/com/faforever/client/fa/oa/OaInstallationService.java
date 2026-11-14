package com.faforever.client.fa.oa;

import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.PersistentNotification;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.EngineUpdates;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.update.ClientConfiguration.OaMod;
import com.faforever.client.update.ClientConfiguration.OaRelease;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
@Slf4j
public class OaInstallationService {

  static final String VERSION_FILE = "VERSION";
  private static final int HTTP_FOLLOW_LIMIT = 5;
  private static final int KEEP_VERSIONS = 2;

  /** Where {@code self-updating/<platform>/} lives under {@link #enginesRoot()}: OA's own updater owns it, TAF never prunes it. */
  static final String SELF_UPDATING = "self-updating";

  /** Streams a URL to a file and returns its lower-case hex sha256; replaceable by tests. */
  @FunctionalInterface
  interface Downloader {
    String download(URL url, Path destination) throws IOException;
  }

  /** Offers the player, once, to hand updating over to OA after it replaced TAF's copies (ADR 0002). */
  @FunctionalInterface
  interface SwitchHint {
    void offer(String modTechnical, Runnable switchAction);
  }

  private final PreferencesService preferencesService;

  Downloader downloader = OaInstallationService::download;

  /** Tells the version of an executable by running it with {@code --version}; replaceable by tests. */
  Function<Path, Optional<OaVersion>> versionAsker = this::askVersion;

  SwitchHint switchHint = (mod, switchAction) -> { };

  @Autowired
  public OaInstallationService(PreferencesService preferencesService, NotificationService notificationService, I18n i18n) {
    this(preferencesService);
    this.switchHint = (mod, switchAction) -> notificationService.addNotification(new PersistentNotification(
        i18n.get("oa.engineUpdates.replacedHint"), Severity.INFO,
        List.of(new Action(i18n.get("oa.engineUpdates.replacedHint.switch"), event -> switchAction.run()))));
  }

  public OaInstallationService(PreferencesService preferencesService) {
    this.preferencesService = preferencesService;
  }

  Path selfUpdatingDir(String platform) {
    return enginesRoot().resolve(SELF_UPDATING).resolve(platform);
  }

  Path enginesRoot() {
    return preferencesService.getFafDataDirectory().resolve("engines").resolve("open-annihilation");
  }

  /**
   * The executable to run for {@code modTechnical}: the user's own for OWN_INSTALL, the self-updating copy for
   * OPEN_ANNIHILATION (installed once, never touched again), the pinned download for TAF (downloaded now if missing). Completes exceptionally with a message fit for the user.
   */
  public CompletableFuture<Path> ensureExecutable(String modTechnical) {
    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    OaMod policy = OaPolicy.forMod(preferencesService.getClientRemoteConfiguration(), modTechnical).orElse(null);
    if (policy == null) {
      return CompletableFuture.failedFuture(new IllegalStateException("Open Annihilation is not supported for " + modTechnical));
    }

    if (prefs.getOaInstallation() == EngineUpdates.OWN_INSTALL) {
      Path exe = prefs.getOaExecutablePath();
      return CompletableFuture.supplyAsync(() -> validateExisting(exe, policy));
    }

    String platform = OaPlatform.current();
    if (prefs.getOaInstallation() == EngineUpdates.OPEN_ANNIHILATION) {
      // once installed, OA's own updater owns the folder: no download, replace or prune, whatever the server lists
      Path dir = selfUpdatingDir(platform);
      try {
        Optional<Path> installed = findExecutable(dir, platform);
        if (installed.isPresent()) {
          Path exe = installed.get();
          return CompletableFuture.supplyAsync(() -> validateExisting(exe, policy));
        }
      } catch (IOException e) {
        return CompletableFuture.failedFuture(e);
      }
      OaRelease first = OaPolicy.pickRelease(preferencesService.getClientRemoteConfiguration(), modTechnical, platform)
          .orElse(null);
      if (first == null) {
        return CompletableFuture.failedFuture(new IllegalStateException(
            "The server offers no Open Annihilation release for " + platform + " that is allowed for " + modTechnical));
      }
      return CompletableFuture.supplyAsync(() -> {
        try {
          return validateExisting(installRelease(first, platform, dir, false), policy);
        } catch (IOException e) {
          throw new IllegalStateException("Could not install Open Annihilation " + first.getVersion() + ": " + e.getMessage(), e);
        }
      });
    }

    OaRelease release = OaPolicy.pickRelease(preferencesService.getClientRemoteConfiguration(), modTechnical, platform)
        .orElse(null);
    if (release == null) {
      return CompletableFuture.failedFuture(new IllegalStateException(
          "The server offers no Open Annihilation release for " + platform + " that is allowed for " + modTechnical));
    }
    try {
      // OA may have replaced a copy of ours: what counts is the version it reports, not its folder name
      Optional<TafCopy> existing = findTafCopy(policy, release, platform);
      if (existing.isPresent()) {
        noteIfReplaced(modTechnical, prefs, existing.get());
        Path exe = existing.get().exe();
        return CompletableFuture.supplyAsync(() -> exe);
      }
    } catch (IOException e) {
      return CompletableFuture.failedFuture(e);
    }
    return CompletableFuture.supplyAsync(() -> {
      try {
        Path target = freshCopyDir(release.getVersion(), platform);
        return installRelease(release, platform, target, true);
      } catch (IOException e) {
        throw new IllegalStateException("Could not install Open Annihilation " + release.getVersion() + ": " + e.getMessage(), e);
      }
    });
  }

  /**
   * The Open Annihilation that is on disk for {@code modTechnical} right now. Never downloads and never rejects a
   * version, so it can say what is there while the caller decides whether it is allowed.
   *
   * @param exe     the executable, or null if there is none yet (a TAF-managed copy not downloaded counts as none)
   * @param version its version, or null if there is no executable or the version cannot be told
   */
  public record InstalledOa(Path exe, OaVersion version) {
    public static final InstalledOa NONE = new InstalledOa(null, null);
  }

  public InstalledOa findInstalled(String modTechnical) {
    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    if (prefs.getOaInstallation() == EngineUpdates.OWN_INSTALL) {
      Path exe = prefs.getOaExecutablePath();
      return exe != null && Files.isRegularFile(exe) ? new InstalledOa(exe, detectVersion(exe).orElse(null)) : InstalledOa.NONE;
    }
    String platform = OaPlatform.current();
    if (prefs.getOaInstallation() == EngineUpdates.OPEN_ANNIHILATION) {
      try {
        return findExecutable(selfUpdatingDir(platform), platform)
            .map(exe -> new InstalledOa(exe, detectVersion(exe).orElse(null)))
            .orElse(InstalledOa.NONE);
      } catch (IOException e) {
        log.warn("Could not look for the self-updating Open Annihilation", e);
        return InstalledOa.NONE;
      }
    }
    OaRelease release = OaPolicy.pickRelease(preferencesService.getClientRemoteConfiguration(), modTechnical, platform)
        .orElse(null);
    if (release == null) {
      return InstalledOa.NONE;
    }
    try {
      OaMod policy = OaPolicy.forMod(preferencesService.getClientRemoteConfiguration(), modTechnical).orElse(null);
      return findTafCopy(policy, release, platform)
          .map(copy -> new InstalledOa(copy.exe(), copy.version()))
          .orElse(InstalledOa.NONE);
    } catch (IOException e) {
      log.warn("Could not look for Open Annihilation {}", release.getVersion(), e);
      return InstalledOa.NONE;
    }
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

  /** Versions told by {@code --version}, per executable file as it was last modified (it is a process start). */
  private final Map<String, OaVersion> versionCache = new ConcurrentHashMap<>();

  /**
   * Asks the executable: {@code open-annihilation --version} prints {@code Open Annihilation 0.8.1 (windows-x64
   * x86_64, 1a2b3c4d)} and exits 0 without opening a window (0.8.0 and later). Only an executable that does not
   * answer (0.7.x has no such switch) falls back on the {@code VERSION} file TAF writes beside the ones it
   * installs, then on the release folder name.
   */
  public Optional<OaVersion> detectVersion(Path exe) {
    if (exe == null) {
      return Optional.empty();
    }
    Optional<OaVersion> asked = versionAsker.apply(exe);
    return asked.isPresent() ? asked : detectVersionFromFiles(exe);
  }

  private Optional<OaVersion> askVersion(Path exe) {
    try {
      Path file = exe.toAbsolutePath();
      String key = file + "|" + Files.size(file) + "|" + Files.getLastModifiedTime(file).toMillis();
      OaVersion known = versionCache.get(key);
      if (known != null) {
        return Optional.of(known);
      }
      // output goes to a file, so the wait below is the only thing that can block: an OA that ignores --version
      // and opens its window (0.7.x) is killed after the timeout instead of hanging the read
      Path outputFile = Files.createTempFile("oa-version", ".txt");
      Process process = new ProcessBuilder(file.toString(), "--version")
          .directory(file.getParent().toFile())
          .redirectErrorStream(true)
          .redirectOutput(outputFile.toFile())
          .start();
      try {
        process.getOutputStream().close();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
          process.destroyForcibly();
          return Optional.empty();
        }
        String output = Files.readString(outputFile, StandardCharsets.UTF_8);
        OaVersion version = process.exitValue() == 0 ? parseVersionLine(output) : null;
        if (version != null) {
          versionCache.put(key, version);
        }
        return Optional.ofNullable(version);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
        return Optional.empty();
      } finally {
        outputFile.toFile().deleteOnExit(); // a killed OA may still hold the file
        try {
          Files.deleteIfExists(outputFile);
        } catch (IOException ignored) {
          // deleteOnExit has it
        }
      }
    } catch (IOException | RuntimeException e) {
      log.debug("{} --version did not answer: {}", exe, e.getMessage());
      return Optional.empty();
    }
  }

  /** The version of {@code Open Annihilation 0.8.1 (windows-x64 x86_64, 1a2b3c4d)}; null if the text is not that. */
  static OaVersion parseVersionLine(String output) {
    Matcher matcher = VERSION_LINE.matcher(output == null ? "" : output);
    return matcher.find() ? OaVersion.find(matcher.group(1)) : null;
  }

  private static final Pattern VERSION_LINE = Pattern.compile("^Open Annihilation (\\d+\\.\\d+(?:\\.\\d+)?)",
      Pattern.MULTILINE);

  private Optional<OaVersion> detectVersionFromFiles(Path exe) {
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
    if (!preferencesService.isOpenAnnihilation(modTechnical)) {
      return EngineReport.CAVEDOG;
    }
    Optional<OaVersion> version;
    if (prefs.getOaInstallation() == EngineUpdates.OWN_INSTALL) {
      version = detectVersion(prefs.getOaExecutablePath());
    } else {
      // what the installed executable says it is; the release the policy would pick only before it is installed
      version = Optional.ofNullable(findInstalled(modTechnical).version())
          .or(() -> OaPolicy.pickRelease(preferencesService.getClientRemoteConfiguration(), modTechnical, OaPlatform.current())
              .map(release -> OaVersion.find(release.getVersion())));
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
    return checkDemo(OaCommands.checkDemo(exe, modDir, demo), exe);
  }

  /** The same for a ready-made check command (a Library mod's replay). */
  public String checkDemo(java.util.List<String> command, Path exe) {
    try {
      Process process = new ProcessBuilder(command)
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

  /** A copy TAF installed (or OA replaced) under {@code engines/open-annihilation/<folder>/<platform>/}. */
  private record TafCopy(Path folder, Path exe, OaVersion version, boolean hasVersionsDir) {
  }

  /**
   * The copy to launch for the picked release: one whose own --version is allowed and at least the picked release,
   * the picked release's folder first. A copy outside the server's bounds, or older than the picked release, is
   * not one.
   */
  private Optional<TafCopy> findTafCopy(OaMod policy, OaRelease picked, String platform) throws IOException {
    Path root = enginesRoot();
    if (!Files.isDirectory(root)) {
      return Optional.empty();
    }
    OaVersion pickedVersion = OaVersion.find(picked.getVersion());
    List<Path> folders;
    try (Stream<Path> list = Files.list(root)) {
      folders = new ArrayList<>(list.filter(Files::isDirectory)
          .filter(dir -> !dir.getFileName().toString().equals(SELF_UPDATING))
          .sorted(Comparator.comparing((Path dir) -> !dir.getFileName().toString().equals(picked.getVersion()))
              .thenComparing(Comparator.comparing((Path dir) -> dir.getFileName().toString()).reversed()))
          .toList());
    }
    for (Path folder : folders) {
      Optional<Path> exe = findExecutable(folder.resolve(platform), platform);
      if (exe.isEmpty()) {
        continue;
      }
      OaVersion version = detectVersion(exe.get()).orElseGet(() -> OaVersion.find(folder.getFileName().toString()));
      if (version == null || (policy != null && !OaPolicy.isVersionAllowed(policy, version))
          || (pickedVersion != null && version.compareTo(pickedVersion) < 0)) {
        continue;
      }
      boolean versionsDir = Files.isDirectory(folder.resolve(platform).resolve("versions"))
          || Files.isDirectory(exe.get().getParent().resolve("versions"));
      return Optional.of(new TafCopy(folder, exe.get(), version, versionsDir));
    }
    return Optional.empty();
  }

  /** A folder for a new copy of {@code version}: never one that holds an executable already (OA may own it now). */
  private Path freshCopyDir(String version, String platform) throws IOException {
    Path dir = enginesRoot().resolve(version);
    for (int n = 1; findExecutable(dir.resolve(platform), platform).isPresent(); n++) {
      dir = enginesRoot().resolve(version + "-" + n);
    }
    return dir.resolve(platform);
  }

  /**
   * Notes that OA replaced a copy of ours (its --version differs from the folder name, or a versions/ folder
   * appeared). The second distinct replacement offers, once, to switch Engine updates to Open Annihilation.
   */
  private void noteIfReplaced(String modTechnical, TotalAnnihilationPrefs prefs, TafCopy copy) {
    OaVersion folderVersion = OaVersion.find(copy.folder().getFileName().toString());
    if (!copy.hasVersionsDir() && (folderVersion == null || folderVersion.equals(copy.version()))) {
      return;
    }
    String key = copy.folder().getFileName() + "=" + copy.version() + (copy.hasVersionsDir() ? "+versions" : "");
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    String saved = prefs.getOaReplacedCopies();
    if (!saved.isEmpty()) {
      seen.addAll(List.of(saved.split("\\|")));
    }
    if (!seen.add(key)) {
      return;
    }
    log.info("Open Annihilation has replaced TAF's copy {} (now {})", copy.folder(), copy.version());
    prefs.setOaReplacedCopies(String.join("|", seen));
    boolean offer = seen.size() >= 2 && !prefs.isOaSwitchHintShown();
    if (offer) {
      prefs.setOaSwitchHintShown(true);
    }
    preferencesService.storeInBackground();
    if (offer) {
      switchHint.offer(modTechnical, () -> {
        prefs.setOaInstallation(EngineUpdates.OPEN_ANNIHILATION);
        preferencesService.storeInBackground();
      });
    }
  }

  private Path installRelease(OaRelease release, String platform, Path target, boolean prune) throws IOException {
    Optional<Path> existing = findExecutable(target, platform);
    if (existing.isPresent()) {
      return existing.get();
    }

    Files.createDirectories(target.getParent());
    Path download = Files.createTempFile(target.getParent(), "oa-", ".zip");
    Path staging = target.resolveSibling(platform + ".part");
    try {
      log.info("Downloading Open Annihilation {} from {}", release.getVersion(), release.getUrl());
      String sha256 = downloader.download(release.getUrl(), download);
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
    if (prune) {
      pruneOldVersions(target.getParent());
    }
    clearMacQuarantine(target);
    return findExecutable(target, platform).orElseThrow(() -> new IOException("installed executable vanished"));
  }

  /**
   * The launcher of a copy. Where OA has updated itself (Linux) the copy also holds {@code versions/<v>/open-annihilation};
   * the launcher beside it is the one to run, so the shallowest match outside versions/ wins.
   */
  static Optional<Path> findExecutable(Path root, String platform) throws IOException {
    if (!Files.isDirectory(root)) {
      return Optional.empty();
    }
    String name = OaPlatform.executableName(platform);
    try (Stream<Path> files = Files.walk(root, 3)) {
      return files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().equals(name))
          .min(Comparator.comparing((Path p) -> {
            Path relative = root.relativize(p);
            for (int i = 0; i < relative.getNameCount() - 1; i++) {
              if (relative.getName(i).toString().equals("versions")) {
                return 1;
              }
            }
            return 0;
          }).thenComparingInt(p -> root.relativize(p).getNameCount()).thenComparing(Path::toString));
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

  /**
   * Keeps the newest few copies and never {@code keepFolder} (the one about to launch). Age is what each copy's
   * own --version says where it can tell, else its folder name: OA may have replaced the copy under an old name.
   */
  void pruneOldVersions(Path keepFolder) {
    Path root = enginesRoot();
    if (!Files.isDirectory(root)) {
      return;
    }
    String platform = OaPlatform.current();
    Path keep = keepFolder.toAbsolutePath().normalize();
    try (Stream<Path> versions = Files.list(root)) {
      versions.filter(Files::isDirectory)
          .filter(dir -> !dir.toAbsolutePath().normalize().equals(keep))
          .filter(dir -> !dir.getFileName().toString().equals(SELF_UPDATING))
          .sorted(Comparator.comparing((Path dir) -> copyAge(dir, platform),
              Comparator.nullsFirst(Comparator.<OaVersion>naturalOrder())).reversed())
          .skip(KEEP_VERSIONS - 1)
          .toList()
          .forEach(OaInstallationService::deleteRecursively);
    } catch (IOException e) {
      log.warn("Could not prune old Open Annihilation versions", e);
    }
  }

  private OaVersion copyAge(Path folder, String platform) {
    try {
      Optional<Path> exe = findExecutable(folder.resolve(platform), platform);
      if (exe.isPresent()) {
        Optional<OaVersion> reported = detectVersion(exe.get());
        if (reported.isPresent()) {
          return reported.get();
        }
      }
    } catch (IOException e) {
      log.debug("Could not tell the version of {}", folder, e);
    }
    return OaVersion.find(folder.getFileName().toString());
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
