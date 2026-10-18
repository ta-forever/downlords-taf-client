package com.faforever.client.library;

import com.faforever.client.fa.oa.OaInstallationService;
import com.faforever.client.fa.oa.OaInstallationService.InstalledOa;
import com.faforever.client.fa.oa.OaLibraryNavigator;
import com.faforever.client.fa.oa.OaPolicy;
import com.faforever.client.fa.oa.OaVersion;
import com.faforever.client.library.OaContentListing.ListedMap;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.update.ClientConfiguration.OaMod;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs the player's own Open Annihilation to find out which Library mods they have installed, which maps a mod
 * has, and what to record about a hosted game. TAF never installs or updates Library content; OA's Library does.
 *
 * <p>All OA runs here use the OA executable of the pseudo-mod {@value LibraryGameInfo#UMBRELLA_FEATURED_MOD}
 * (the user's own install, or TAF's pinned download when they chose that) and OA's real user folder, with the
 * pseudo-mod's "extra args" appended (e.g. {@code --game-dir} / {@code --user-folder} for a non-standard setup).
 */
@Lazy
@Service
@Slf4j
public class LibraryService {

  private static final int TIMEOUT_SECONDS = 60;
  private static final long STALE_AFTER_MILLIS = 60_000;

  /** Runs a command and returns its exit code and combined output. Replaceable for tests. */
  @FunctionalInterface
  interface CommandRunner {
    CommandResult run(List<String> command) throws IOException;
  }

  record CommandResult(int exitCode, String output) {
  }

  /** Downloads a registry catalogue (no token needed). Replaceable for tests. */
  @FunctionalInterface
  interface CatalogueFetcher {
    String fetch(String url) throws IOException;
  }

  /** In the log folder; OA writes the address and token of its automation endpoint here. */
  static final String LIBRARY_ENDPOINT_FILE = "oa_fark_library.json";

  private final PreferencesService preferencesService;
  private final OaInstallationService oaInstallationService;
  private final CommandRunner runner;
  private final CatalogueFetcher catalogueFetcher;
  private final ProcessStarter starter;
  private final Consumer<Path> libraryNavigator;
  private final ObjectProperty<LibrarySnapshot> snapshot = new SimpleObjectProperty<>();
  private volatile long snapshotTakenAtMillis;
  private volatile CompletableFuture<LibrarySnapshot> refreshing;

  /** Starts a process and returns at once with a future that completes when it exits. Replaceable for tests. */
  @FunctionalInterface
  interface ProcessStarter {
    CompletableFuture<Void> start(List<String> command) throws IOException;
  }

  @Autowired
  public LibraryService(PreferencesService preferencesService, OaInstallationService oaInstallationService) {
    this(preferencesService, oaInstallationService, LibraryService::runProcess, LibraryService::downloadCatalogue,
        LibraryService::startProcess, LibraryService::navigateInBackground);
  }

  LibraryService(PreferencesService preferencesService, OaInstallationService oaInstallationService,
                 CommandRunner runner, CatalogueFetcher catalogueFetcher, ProcessStarter starter) {
    this(preferencesService, oaInstallationService, runner, catalogueFetcher, starter, endpoint -> {
    });
  }

  LibraryService(PreferencesService preferencesService, OaInstallationService oaInstallationService,
                 CommandRunner runner, CatalogueFetcher catalogueFetcher, ProcessStarter starter,
                 Consumer<Path> libraryNavigator) {
    this.libraryNavigator = libraryNavigator;
    this.preferencesService = preferencesService;
    this.oaInstallationService = oaInstallationService;
    this.runner = runner;
    this.catalogueFetcher = catalogueFetcher;
    this.starter = starter;
  }

  /** The last check of the player's OA and Library, or null before the first one finishes. */
  public ObjectProperty<LibrarySnapshot> snapshotProperty() {
    return snapshot;
  }

  /**
   * Checks the player's OA now: is there one, is its version allowed, is its first-run setup done, and which
   * Library mods are installed. Publishes the result as {@link #snapshotProperty()}.
   *
   * @param allowDownload whether a TAF-managed OA that is not on disk yet may be downloaded (the engine TAF pins
   *                      for the user, never Library content); the game list passes false so that merely looking
   *                      at it downloads nothing
   */
  public CompletableFuture<LibrarySnapshot> refresh(boolean allowDownload) {
    return CompletableFuture.supplyAsync(() -> {
      LibrarySnapshot taken = takeSnapshot(allowDownload);
      snapshotTakenAtMillis = System.currentTimeMillis();
      snapshot.set(taken);
      return taken;
    });
  }

  /** Re-checks unless a check is already running or the last one is recent. For callers that run often. */
  public void refreshIfStale() {
    CompletableFuture<LibrarySnapshot> running = refreshing;
    if (running != null && !running.isDone()) {
      return;
    }
    if (snapshot.get() != null && System.currentTimeMillis() - snapshotTakenAtMillis < STALE_AFTER_MILLIS) {
      return;
    }
    refreshing = refresh(false);
  }

  private LibrarySnapshot takeSnapshot(boolean allowDownload) {
    OaMod policy = OaPolicy.forMod(preferencesService.getClientRemoteConfiguration(), LibraryGameInfo.UMBRELLA_FEATURED_MOD)
        .orElse(null);
    InstalledOa found = oaInstallationService.findInstalled(LibraryGameInfo.UMBRELLA_FEATURED_MOD);
    Path exe = found.exe();
    OaVersion version = found.version();
    if (exe == null && allowDownload && policy != null) {
      try {
        exe = oaInstallationService.ensureExecutable(LibraryGameInfo.UMBRELLA_FEATURED_MOD).join();
        version = oaInstallationService.detectVersion(exe).orElse(version);
      } catch (RuntimeException e) {
        log.info("[Library] no Open Annihilation to use: {}", e.getMessage());
        exe = null;
      }
    }
    LibraryReadiness readiness = LibraryReadiness.assess(policy, exe != null, version, null);
    if (!readiness.isReady()) {
      return new LibrarySnapshot(readiness, List.of());
    }
    try {
      CommandResult result = runner.run(withExtraArgs(LibraryCommands.listContent(exe)));
      readiness = LibraryReadiness.assess(policy, true, version, result.exitCode());
      if (!readiness.isReady()) {
        log.info("[Library] --list-content exited {}: {}", result.exitCode(), result.output());
        return new LibrarySnapshot(readiness, List.of());
      }
      List<LibraryMod> installed = LibraryDiscovery.discover(userFolder(), OaContentListing.parse(result.output()));
      LibraryPolicy libraryPolicy = LibraryPolicy.from(preferencesService.getClientRemoteConfiguration());
      Path listedBy = exe;
      return new LibrarySnapshot(readiness,
          LibraryTrust.filter(installed, libraryPolicy, lazyCatalogues(libraryPolicy), mod -> simHashOf(listedBy, mod)));
    } catch (IOException | IllegalArgumentException e) {
      log.warn("[Library] could not list the content of Open Annihilation", e);
      return new LibrarySnapshot(LibraryReadiness.assess(policy, true, version, 1), List.of());
    }
  }

  /**
   * Opens Open Annihilation itself, so the player can finish its first-run setup or install a Library mod.
   * Completes once OA has exited and the Library has been checked again, so {@link #snapshotProperty()} is current.
   */
  public CompletableFuture<Void> openOa() {
    return open(false);
  }

  /**
   * Like {@link #openOa()}, but OA comes up at its Library screen, for installing mods and maps. OA has no option
   * for that, so it is started with its automation endpoint and the Library button is clicked once its main menu is
   * up (and its intro is skipped). If that does not work out OA is just left at whatever screen it reached.
   */
  public CompletableFuture<Void> openOaAtLibrary() {
    return open(true);
  }

  /** The OA this client opened and that is still up; a second click joins it rather than starting another. */
  private CompletableFuture<Void> openOa;

  private synchronized CompletableFuture<Void> open(boolean atLibrary) {
    if (openOa != null && !openOa.isDone()) {
      log.info("[Library] Open Annihilation is already open; not starting another");
      return openOa;
    }
    openOa = startOa(atLibrary);
    return openOa;
  }

  private CompletableFuture<Void> startOa(boolean atLibrary) {
    return CompletableFuture.supplyAsync(() -> {
      InstalledOa found = oaInstallationService.findInstalled(LibraryGameInfo.UMBRELLA_FEATURED_MOD);
      Path exe = found.exe() != null ? found.exe()
          : oaInstallationService.ensureExecutable(LibraryGameInfo.UMBRELLA_FEATURED_MOD).join();
      List<String> command = new ArrayList<>(List.of(exe.toString()));
      Path endpoint = null;
      if (atLibrary) {
        endpoint = preferencesService.getFafLogDirectory().resolve(LIBRARY_ENDPOINT_FILE);
        try {
          // a file left by an earlier OA holds a dead address and token
          Files.deleteIfExists(endpoint);
        } catch (IOException e) {
          log.debug("[Library] could not remove the old automation endpoint file {}", endpoint, e);
        }
        command.addAll(List.of("--skip-intro", "--fark", "--fark-file", endpoint.toString()));
      }
      try {
        CompletableFuture<Void> exited = starter.start(withExtraArgs(command));
        if (endpoint != null) {
          libraryNavigator.accept(endpoint);
        }
        return exited;
      } catch (IOException e) {
        throw new IllegalStateException("Could not start Open Annihilation: " + e.getMessage(), e);
      }
    }).thenCompose(exited -> exited).thenCompose(ignored -> refresh(false)).thenApply(snapshot -> null);
  }

  /** Clicks OA to its Library on a thread of its own, so the wait for OA's main menu holds nothing up. */
  private static void navigateInBackground(Path endpoint) {
    Thread thread = new Thread(() -> OaLibraryNavigator.openLibrary(endpoint), "oa-library-navigator");
    thread.setDaemon(true);
    thread.start();
  }

  private String extraArgs() {
    return preferencesService.getTotalAnnihilation(LibraryGameInfo.UMBRELLA_FEATURED_MOD).getOaExtraArgs();
  }

  /** OA's user folder: {@code --user-folder} from the extra args, else {@code Documents/Open Annihilation}. */
  public Path userFolder() {
    return userFolder(extraArgs(), Path.of(System.getProperty("user.home")));
  }

  static Path userFolder(String extraArgs, Path home) {
    String[] words = extraArgs == null ? new String[0] : extraArgs.trim().split("\\s+");
    for (int i = 0; i < words.length - 1; i++) {
      if (words[i].equals("--user-folder")) {
        return Path.of(words[i + 1]);
      }
    }
    return home.resolve("Documents").resolve("Open Annihilation");
  }

  /**
   * The Library mods that are installed and that OA lists. Empty when OA is not ready (see {@link #refresh}) or
   * the Library has no mods.
   */
  public CompletableFuture<List<LibraryMod>> installedMods() {
    return refresh(true).thenApply(LibrarySnapshot::mods);
  }

  /** Catalogues by registry id, each downloaded at most once per call and only if asked for. */
  private Function<String, Optional<RegistryCatalogue>> lazyCatalogues(LibraryPolicy policy) {
    Map<String, Optional<RegistryCatalogue>> loaded = new HashMap<>();
    return registryId -> loaded.computeIfAbsent(registryId, id -> policy.trustedRegistries().stream()
        .filter(registry -> registry.id().equals(id) && registry.catalogueUrl() != null)
        .findFirst()
        .flatMap(registry -> {
          try {
            return Optional.of(RegistryCatalogue.parse(catalogueFetcher.fetch(registry.catalogueUrl())));
          } catch (IOException | IllegalArgumentException e) {
            log.warn("[Library] could not read the catalogue of {}: {}", id, e.getMessage());
            return Optional.empty();
          }
        }));
  }

  private Optional<String> simHashOf(Path exe, LibraryMod mod) {
    try {
      return LibraryCommands.simHash(runner.run(withExtraArgs(LibraryCommands.printProfile(exe, mod.folder()))).output());
    } catch (IOException e) {
      log.debug("[Library] no sim hash for {}: {}", mod.id(), e.getMessage());
      return Optional.empty();
    }
  }

  /** The maps OA has for this mod's content: its shown names and the keys a launch file uses. */
  public CompletableFuture<List<ListedMap>> mapsFor(LibraryMod mod) {
    return oaInstallationService.ensureExecutable(LibraryGameInfo.UMBRELLA_FEATURED_MOD).thenApplyAsync(exe -> {
      try {
        CommandResult result = runner.run(withExtraArgs(
            LibraryCommands.listContent(exe, mod.folder(), mod.baseGame(), mod.requiredExpansions())));
        if (result.exitCode() != 0) {
          throw new IllegalStateException("Open Annihilation could not list the maps of " + mod.name() + ": "
              + result.output());
        }
        return OaContentListing.parse(result.output()).maps();
      } catch (IOException e) {
        throw new IllegalStateException("Could not run Open Annihilation: " + e.getMessage(), e);
      }
    });
  }

  /**
   * What the host sends with the game: the mod's identity from {@code oamod.yaml} and its sim hash from
   * {@code --print-profile --mod-dir}. A missing sim hash is recorded as null; it is never compared.
   */
  public CompletableFuture<LibraryGameInfo> hostInfo(LibraryMod mod) {
    return oaInstallationService.ensureExecutable(LibraryGameInfo.UMBRELLA_FEATURED_MOD).thenApplyAsync(exe -> {
      String simHash = null;
      try {
        CommandResult result = runner.run(withExtraArgs(LibraryCommands.printProfile(exe, mod.folder())));
        simHash = LibraryCommands.simHash(result.output()).orElse(null);
      } catch (IOException e) {
        log.warn("[Library] could not read the sim hash of {}", mod.id(), e);
      }
      return LibraryGameInfo.of(mod, simHash);
    });
  }

  private List<String> withExtraArgs(List<String> command) {
    List<String> full = new ArrayList<>(command);
    String extra = extraArgs();
    if (extra != null && !extra.isBlank()) {
      full.addAll(List.of(extra.trim().split("\\s+")));
    }
    return full;
  }

  private static CompletableFuture<Void> startProcess(List<String> command) throws IOException {
    Process process = new ProcessBuilder(command)
        .directory(Path.of(command.get(0)).toAbsolutePath().getParent().toFile())
        .start();
    return process.onExit().thenApply(exited -> null);
  }

  private static String downloadCatalogue(String url) throws IOException {
    java.net.URLConnection connection = java.net.URI.create(url).toURL().openConnection();
    connection.setConnectTimeout(10_000);
    connection.setReadTimeout(20_000);
    try (java.io.InputStream in = connection.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static CommandResult runProcess(List<String> command) throws IOException {
    Process process = new ProcessBuilder(command)
        .directory(Path.of(command.get(0)).toAbsolutePath().getParent().toFile())
        .redirectErrorStream(true)
        .start();
    try {
      byte[] output = process.getInputStream().readAllBytes();
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IOException(command.get(0) + " did not finish within " + TIMEOUT_SECONDS + " s");
      }
      return new CommandResult(process.exitValue(), new String(output, StandardCharsets.UTF_8));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new IOException("interrupted", e);
    }
  }
}
