package com.faforever.client.library;

import com.faforever.client.fa.oa.OaInstallationService;
import com.faforever.client.library.OaContentListing.ListedMap;
import com.faforever.client.preferences.PreferencesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

  private final PreferencesService preferencesService;
  private final OaInstallationService oaInstallationService;
  private final CommandRunner runner;
  private final CatalogueFetcher catalogueFetcher;

  @Autowired
  public LibraryService(PreferencesService preferencesService, OaInstallationService oaInstallationService) {
    this(preferencesService, oaInstallationService, LibraryService::runProcess, LibraryService::downloadCatalogue);
  }

  LibraryService(PreferencesService preferencesService, OaInstallationService oaInstallationService,
                 CommandRunner runner, CatalogueFetcher catalogueFetcher) {
    this.preferencesService = preferencesService;
    this.oaInstallationService = oaInstallationService;
    this.runner = runner;
    this.catalogueFetcher = catalogueFetcher;
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
   * The Library mods that are installed, that OA lists, that are not hidden and that come from a trusted registry. Empty when OA is not ready (its first-run setup is
   * not done, so {@code --list-content} exits 1) or the Library has no mods.
   * Completes exceptionally when there is no usable OA executable.
   */
  public CompletableFuture<List<LibraryMod>> installedMods() {
    return oaInstallationService.ensureExecutable(LibraryGameInfo.UMBRELLA_FEATURED_MOD).thenApplyAsync(exe -> {
      try {
        CommandResult result = runner.run(withExtraArgs(LibraryCommands.listContent(exe)));
        if (result.exitCode() != 0) {
          log.info("[Library] --list-content exited {}: {}", result.exitCode(), result.output());
          return List.of();
        }
        List<LibraryMod> installed = LibraryDiscovery.discover(userFolder(), OaContentListing.parse(result.output()));
        LibraryPolicy policy = LibraryPolicy.from(preferencesService.getClientRemoteConfiguration());
        return LibraryTrust.filter(installed, policy, lazyCatalogues(policy), mod -> simHashOf(exe, mod));
      } catch (IOException | IllegalArgumentException e) {
        log.warn("[Library] could not list OA's content", e);
        return List.of();
      }
    });
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
