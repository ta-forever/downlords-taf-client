package com.faforever.client.library;

import com.faforever.client.fa.MapTool;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.Game;
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapService;
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
import java.util.concurrent.Executors;
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
  private static final long INSTALLED_MODS_TTL_MS = TimeUnit.SECONDS.toMillis(30);
  private static final long CATALOGUE_TTL_MS = TimeUnit.HOURS.toMillis(1);

  private final LibraryService libraryService;
  private final MapService mapService;
  private final PreferencesService preferencesService;
  private final Executor executor = Executors.newFixedThreadPool(2, runnable -> {
    Thread thread = new Thread(runnable, "library-map-preview");
    thread.setDaemon(true);
    return thread;
  });
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private final Map<String, CompletableFuture<Optional<URI>>> previewsByKey = new ConcurrentHashMap<>();
  private final Map<String, CachedCatalogue> cataloguesByRegistry = new ConcurrentHashMap<>();
  private CompletableFuture<List<LibraryMod>> installedMods;
  private long installedModsAt;

  private record CachedCatalogue(RegistryMapPreviews catalogue, long at) {
  }

  @Autowired
  public LibraryMapPreviewService(LibraryService libraryService, MapService mapService,
                                  PreferencesService preferencesService) {
    this.libraryService = libraryService;
    this.mapService = mapService;
    this.preferencesService = preferencesService;
  }

  /** The preview image of a map of this Library mod, or empty when no source has one. Never fails. */
  public CompletableFuture<Optional<Image>> loadPreview(@Nullable String modId, @Nullable String registry,
                                                        String mapKey) {
    String cacheKey = modId + "|" + registry + "|" + mapKey;
    return previewsByKey.computeIfAbsent(cacheKey, key -> CompletableFuture.supplyAsync(
            () -> findUri(modId, registry, mapKey), executor))
        .thenApply(uri -> uri.map(value -> new Image(value.toString(), true)));
  }

  /** The preview of a game's map for the game list; null (so the placeholder shows) until and unless one is found. */
  public ObjectProperty<Image> previewProperty(Game game) {
    return new GamePreview(game);
  }

  private final class GamePreview extends SimpleObjectProperty<Image> {
    private final Game game;
    // held here because the listeners on the game are weak
    private final InvalidationListener strongListener = observable -> refresh();

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
        JavaFxUtil.runLater(() -> set(null));
        return;
      }
      loadPreview(library.modId(), library.registry(), mapName)
          .thenAccept(image -> JavaFxUtil.runLater(() -> {
            if (mapName.equals(game.getMapName())) {
              set(image.orElse(null));
            }
          }));
    }
  }

  private Optional<URI> findUri(@Nullable String modId, @Nullable String registry, String mapKey) {
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
    Path cacheRoot = preferencesService.getCacheDirectory().resolve("maps").resolve("library")
        .resolve(layer.getFileName() + "-" + Integer.toHexString(layer.toAbsolutePath().toString().hashCode()));
    Path png = cacheRoot.resolve(PreviewType.MINI.getToolName()).resolve(mapName + ".png");
    if (!Files.exists(png)) {
      MapTool.generatePreview(layer, null, mapName, cacheRoot, PreviewType.MINI, MAX_POSITIONS);
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
