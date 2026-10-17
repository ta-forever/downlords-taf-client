package com.faforever.client.library;

import com.faforever.client.fa.MapTool;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.Game;
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewOverlayType;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.preferences.PreferencesService;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Map previews for Library mod maps (Create Game and the game list). Wires the real sources into
 * {@link LibraryMapPreviews}: maptool over the Library content folders, the TAF map of the same name, and the
 * registry catalogue's preview PNG. Lookups run off the JavaFX thread and are remembered for the session.
 */
@Lazy
@Service
@Slf4j
public class LibraryMapPreviewService {

  private static final int MAX_POSITIONS = 10;
  private static final String POSITIONS_COORDS_FOLDER = "positions-coords";
  private static final long INSTALLED_MODS_TTL_MS = TimeUnit.SECONDS.toMillis(30);
  private static final long CATALOGUE_TTL_MS = TimeUnit.HOURS.toMillis(1);

  private final LibraryService libraryService;
  private final MapService mapService;
  private final PreferencesService preferencesService;
  private final Executor executor;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private final Map<String, CompletableFuture<Optional<Image>>> previewsByKey = new ConcurrentHashMap<>();
  private final Map<String, CachedCatalogue> cataloguesByRegistry = new ConcurrentHashMap<>();
  private CompletableFuture<List<LibraryMod>> installedMods;
  private long installedModsAt;

  private record CachedCatalogue(RegistryMapPreviews catalogue, long at) {
  }

  @Autowired
  public LibraryMapPreviewService(LibraryService libraryService, MapService mapService,
                                  PreferencesService preferencesService, ExecutorService taskExecutor) {
    this.executor = taskExecutor;
    this.libraryService = libraryService;
    this.mapService = mapService;
    this.preferencesService = preferencesService;
  }

  /** The preview image of a map of this Library mod, or empty when no source has one. Never fails. */
  public CompletableFuture<Optional<Image>> loadPreview(@Nullable String modId, @Nullable String registry,
                                                        String mapKey) {
    return loadPreview(modId, registry, mapKey, PreviewType.MINI);
  }

  /**
   * The preview of a map in the given style. Only the minimap has fallbacks (a TAF map of the same name, the
   * registry's PNG); the others come from maptool over the Library folders or not at all. One shared
   * {@link Image} per map and style, so callers that ask again get the very same instance.
   */
  public CompletableFuture<Optional<Image>> loadPreview(@Nullable String modId, @Nullable String registry,
                                                        String mapKey, PreviewType previewType) {
    String cacheKey = modId + "|" + registry + "|" + mapKey + "|" + previewType.getToolName();
    return cachedImage(cacheKey, () -> previewType == PreviewType.MINI
        ? findUri(modId, registry, mapKey)
        : findInLayers(modId, mapKey, previewType.getToolName(), previewType.getToolName(), ".png",
        (layer, name, cacheRoot) -> MapTool.generatePreview(layer, null, name, cacheRoot, previewType, MAX_POSITIONS)));
  }

  /**
   * An overlay (start positions, metal, geothermal vents, reclaim) for a map, drawn by maptool from the Library
   * folders that hold the map. Approximate: maptool reads the layers one folder at a time, so features that only
   * a later layer defines may be missing. Empty when maptool made nothing.
   */
  public CompletableFuture<Optional<Image>> loadOverlay(@Nullable String modId, String mapKey,
                                                        PreviewOverlayType overlayType, int maxPositions) {
    if (overlayType == null || overlayType == PreviewOverlayType.NONE) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    String cacheKey = modId + "|" + mapKey + "|" + overlayType.getToolName() + "|" + maxPositions;
    String folder = overlayType.getToolName() + "_" + maxPositions;
    return cachedImage(cacheKey, () -> findInLayers(modId, mapKey, overlayType.getToolName(), folder, ".png",
        (layer, name, cacheRoot) -> MapTool.generateOverlayPreview(layer, null, name, cacheRoot, overlayType,
            maxPositions)));
  }

  /**
   * The start positions of a map of this Library mod, as maptool's {@code positions-coords} side-car gives them,
   * cached like TAF's own ({@code <cache>/maps/positions-coords_<N>/<name>.txt}). Blocking (maptool may run), so
   * call it off the JavaFX thread. Empty when no source has the map.
   */
  public List<MapService.StartPosition> loadStartPositions(@Nullable String modId, String mapKey, int maxPositions) {
    String folder = POSITIONS_COORDS_FOLDER + "_" + maxPositions;
    try {
      return findInLayers(modId, mapKey, POSITIONS_COORDS_FOLDER, folder, ".txt",
          (layer, name, cacheRoot) -> MapTool.generateStartPositions(layer, null, name, cacheRoot, maxPositions))
          .map(uri -> MapService.readStartPositions(Path.of(uri)))
          .orElse(List.of());
    } catch (RuntimeException e) {
      log.warn("[Library] no start positions for {}", mapKey, e);
      return List.of();
    }
  }

  private CompletableFuture<Optional<Image>> cachedImage(String cacheKey, java.util.function.Supplier<Optional<URI>> finder) {
    return previewsByKey.computeIfAbsent(cacheKey, key -> CompletableFuture.supplyAsync(
        () -> finder.get().map(uri -> new Image(uri.toString(), true)), executor));
  }

  @FunctionalInterface
  private interface Generator {
    void generate(Path layer, String mapName, Path cacheRoot) throws IOException;
  }

  private Optional<URI> findInLayers(@Nullable String modId, String mapKey, String toolName, String folder,
                                     String extension, Generator generator) {
    String name = LibraryMapPreviews.mapName(mapKey);
    Optional<URI> loose = looseOutput(mapKey, folder, extension, generator);
    if (loose.isPresent()) {
      return loose;
    }
    Path userFolder = libraryService.userFolder();
    for (Path layer : installedMod(modId).map(mod -> LibraryMapPreviews.layers(userFolder, mod)).orElse(List.of())) {
      try {
        Optional<URI> found = maptoolOutput(layer, name, folder, extension, generator);
        if (found.isPresent()) {
          return found;
        }
      } catch (Exception e) {
        log.debug("[Library] no {} from {}: {}", toolName, layer, e.getMessage());
      }
    }
    return Optional.empty();
  }

  /** The preview of a game's map for the game list; null (so the placeholder shows) until and unless one is found. */
  public ObjectProperty<Image> previewProperty(Game game) {
    return new GamePreview(game);
  }

  private final class GamePreview extends SimpleObjectProperty<Image> {
    private final Game game;
    // held here because the listeners on the game are weak
    private final InvalidationListener strongListener = observable -> refresh();
    private String shownKey;

    private GamePreview(Game game) {
      this.game = game;
      JavaFxUtil.addListener(game.mapNameProperty(), new WeakInvalidationListener(strongListener));
      JavaFxUtil.addListener(game.libraryProperty(), new WeakInvalidationListener(strongListener));
      refresh();
    }

    private void refresh() {
      LibraryGameInfo library = game.getLibrary();
      String mapName = game.getMapName();
      if (library == null || mapName == null) {
        shownKey = null;
        JavaFxUtil.runLater(() -> set(null));
        return;
      }
      // every GAME_INFO replaces the library record, equal or not; only a different map needs a new preview
      String key = library.modId() + "|" + library.registry() + "|" + mapName;
      if (key.equals(shownKey)) {
        return;
      }
      shownKey = key;
      loadPreview(library.modId(), library.registry(), mapName)
          .thenAccept(image -> JavaFxUtil.runLater(() -> {
            if (key.equals(shownKey)) {
              set(image.orElse(null));
            }
          }));
    }
  }

  private Optional<URI> findUri(@Nullable String modId, @Nullable String registry, String mapKey) {
    Optional<URI> loose = looseOutput(mapKey, PreviewType.MINI.getToolName(), ".png",
        (folder, name, cacheRoot) -> MapTool.generatePreview(folder, null, name, cacheRoot, PreviewType.MINI,
            MAX_POSITIONS));
    if (loose.isPresent()) {
      return loose;
    }
    Optional<URI> found = findUriInLayers(modId, registry, mapKey);
    if (found.isPresent()) {
      return found;
    }
    return looseStager().packPreview(mapKey).map(Path::toUri);
  }

  private Optional<URI> findUriInLayers(@Nullable String modId, @Nullable String registry, String mapKey) {
    Path userFolder = libraryService.userFolder();
    List<Path> layers = installedMod(modId)
        .map(mod -> LibraryMapPreviews.layers(userFolder, mod))
        .orElse(List.of());
    LibraryMapPreviews previews = new LibraryMapPreviews(
        this::maptoolPreview,
        this::tafPreview,
        key -> registryPreview(registry, key));
    return previews.find(mapKey, layers);
  }

  private Optional<LibraryMod> installedMod(@Nullable String modId) {
    if (modId == null) {
      return Optional.empty();
    }
    try {
      CompletableFuture<List<LibraryMod>> listing;
      synchronized (this) {
        long now = System.currentTimeMillis();
        if (installedMods == null || now - installedModsAt > INSTALLED_MODS_TTL_MS) {
          installedMods = libraryService.installedMods();
          installedModsAt = now;
        }
        listing = installedMods;
      }
      return listing.get(70, TimeUnit.SECONDS).stream().filter(mod -> mod.id().equals(modId)).findFirst();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.info("[Library] no installed mods for previews: {}", e.getMessage());
    }
    return Optional.empty();
  }

  private Optional<URI> maptoolPreview(Path layer, String mapName) throws IOException {
    return maptoolOutput(layer, mapName, PreviewType.MINI.getToolName(),
        (folder, name, cacheRoot) -> MapTool.generatePreview(folder, null, name, cacheRoot, PreviewType.MINI,
            MAX_POSITIONS));
  }

  private LooseMapStager looseStager() {
    return new LooseMapStager(libraryService.userFolder(), preferencesService.getCacheDirectory().resolve("maps"));
  }

  /**
   * Output for a map that OA installed as a loose folder, made by maptool over a packed copy of it. It goes
   * where TAF's own map previews and overlays go ({@code <cache>/maps/<folder>/<name>.png}), so it is cached
   * the same way and generated only once. Empty when no loose pack has the map or maptool made nothing.
   */
  private Optional<URI> looseOutput(String mapKey, String outputFolder, String extension, Generator generator) {
    String name = LibraryMapPreviews.mapName(mapKey);
    try {
      Path cacheRoot = preferencesService.getCacheDirectory().resolve("maps");
      Path png = cacheRoot.resolve(outputFolder).resolve(name + extension);
      LooseMapStager stager = looseStager();
      if (stager.findPack(mapKey).isEmpty()) {
        return Optional.empty();
      }
      if (!Files.exists(png)) {
        Optional<Path> stage = stager.stage(mapKey);
        if (stage.isPresent()) {
          generator.generate(stage.get(), name, cacheRoot);
        }
      }
      return Files.exists(png) ? Optional.of(png.toUri()) : Optional.empty();
    } catch (Exception e) {
      log.debug("[Library] no {} for loose map {}: {}", outputFolder, mapKey, e.getMessage());
      return Optional.empty();
    }
  }

  private Optional<URI> maptoolOutput(Path layer, String mapName, String outputFolder, Generator generator)
      throws IOException {
    return maptoolOutput(layer, mapName, outputFolder, ".png", generator);
  }

  private Optional<URI> maptoolOutput(Path layer, String mapName, String outputFolder, String extension,
                                      Generator generator) throws IOException {
    Path cacheRoot = preferencesService.getCacheDirectory().resolve("maps").resolve("library")
        .resolve(layer.getFileName() + "-" + Integer.toHexString(layer.toAbsolutePath().toString().hashCode()));
    Path png = cacheRoot.resolve(outputFolder).resolve(mapName + extension);
    if (!Files.exists(png)) {
      generator.generate(layer, mapName, cacheRoot);
    }
    return Files.exists(png) ? Optional.of(png.toUri()) : Optional.empty();
  }

  private Optional<URI> tafPreview(String mapName) throws Exception {
    List<MapBean> maps = mapService.findServerMapsByName(mapName).get(10, TimeUnit.SECONDS);
    return maps.stream()
        .filter(map -> map.getThumbnailUrl() != null && mapName.equalsIgnoreCase(map.getMapName()))
        .findFirst()
        .map(map -> URI.create(map.getThumbnailUrl().toString()));
  }

  private Optional<URI> registryPreview(@Nullable String registry, String mapKey) throws Exception {
    // the catalogue address comes from the server's trusted registries; a game that names none gets the first
    LibraryPolicy policy = LibraryPolicy.from(preferencesService.getClientRemoteConfiguration());
    Optional<LibraryPolicy.Registry> trusted = policy.trustedRegistries().stream()
        .filter(candidate -> candidate.catalogueUrl() != null
            && (registry == null || registry.isBlank() || candidate.id().equals(registry)))
        .findFirst();
    if (trusted.isEmpty()) {
      return Optional.empty();
    }
    String registryId = trusted.get().id();
    String catalogueUrl = trusted.get().catalogueUrl();
    CachedCatalogue cached = cataloguesByRegistry.get(registryId);
    if (cached == null || System.currentTimeMillis() - cached.at() > CATALOGUE_TTL_MS) {
      HttpResponse<String> response = httpClient.send(
          HttpRequest.newBuilder(URI.create(catalogueUrl)).timeout(Duration.ofSeconds(20)).build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        throw new IOException("Catalogue of " + registryId + " answered " + response.statusCode());
      }
      cached = new CachedCatalogue(RegistryMapPreviews.parse(catalogueUrl, response.body()), System.currentTimeMillis());
      cataloguesByRegistry.put(registryId, cached);
    }
    return cached.catalogue().previewOf(mapKey);
  }
}
