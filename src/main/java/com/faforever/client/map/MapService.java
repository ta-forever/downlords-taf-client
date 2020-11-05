package com.faforever.client.map;

import com.faforever.client.config.CacheNames;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.config.ClientProperties.Vault;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapBean.Type;
import com.faforever.client.map.generator.MapGeneratorService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.remote.AssetService;
import com.faforever.client.remote.FafService;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.CompletableTask.Priority;
import com.faforever.client.task.TaskService;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.ProgrammingError;
import com.faforever.client.util.Tuple;
import com.faforever.client.vault.search.SearchController.SearchConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import static com.github.nocatch.NoCatch.noCatch;
import static com.google.common.net.UrlEscapers.urlFragmentEscaper;
import static java.lang.String.format;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;


@Lazy
@Service
public class MapService implements InitializingBean, DisposableBean {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PreferencesService preferencesService;
  private final TaskService taskService;
  private final ApplicationContext applicationContext;
  private final FafService fafService;
  private final AssetService assetService;
  private final I18n i18n;
  private final UiService uiService;
  private final MapGeneratorService mapGeneratorService;
  private final ClientProperties clientProperties;
  private final EventBus eventBus;
  private final PlayerService playerService;

  private final String mapDownloadUrlFormat;
  private final String mapPreviewUrlFormat;
  private final Map<Path, MapBean> pathToMap = new HashMap<>();
  private final ObservableList<MapBean> installedMaps = FXCollections.observableArrayList();
  private final Map<String, MapBean> mapsByFolderName = new HashMap<>();
  private Thread directoryWatcherThread;

  @Inject
  public MapService(PreferencesService preferencesService,
                    TaskService taskService,
                    ApplicationContext applicationContext,
                    FafService fafService,
                    AssetService assetService,
                    I18n i18n,
                    UiService uiService,
                    MapGeneratorService mapGeneratorService,
                    ClientProperties clientProperties,
                    EventBus eventBus, PlayerService playerService) {
    this.preferencesService = preferencesService;
    this.taskService = taskService;
    this.applicationContext = applicationContext;
    this.fafService = fafService;
    this.assetService = assetService;
    this.i18n = i18n;
    this.uiService = uiService;
    this.mapGeneratorService = mapGeneratorService;
    this.clientProperties = clientProperties;
    this.eventBus = eventBus;
    this.playerService = playerService;
    Vault vault = clientProperties.getVault();
    this.mapDownloadUrlFormat = vault.getMapDownloadUrlFormat();
    this.mapPreviewUrlFormat = vault.getMapPreviewUrlFormat();

    installedMaps.addListener((ListChangeListener<MapBean>) change -> {
      while (change.next()) {
        for (MapBean mapBean : change.getRemoved()) {
          mapsByFolderName.remove(mapBean.getFolderName().toLowerCase());
        }
        for (MapBean mapBean : change.getAddedSubList()) {
          mapsByFolderName.put(mapBean.getFolderName().toLowerCase(), mapBean);
        }
      }
    });
  }

  @VisibleForTesting
  Set<String> otaMaps = ImmutableSet.of(
      "Anteer Straight", "Ashap Plateau", "Caldera's Rim", "Coast To Coast", "Dark Side", "Etorrep Glacier", "Evad River Confluence",
      "Fox Holes", "Full Moon", "Gods of War", "Great Divide", "Greenhaven", "Hundred Isles", "Kill The Middle", "King of the Hill",
      "Lava & Two Hills", "Lava Alley", "Lava Highground", "Lava Mania", "Lava Run", "Metal Heck", "Over Crude Water", "Painted Desert",
      "Pincushion", "Red Hot Lava", "Red Planet", "Red Triangle", "Ring Atol", "Rock Alley", "Seven Islands", "SHERWOOD",
      "Shore to Shore", "The Cold Place", "The Desert Triad", "The Pass", "Two Continents", "Yerrot Mountains"
  );

  @VisibleForTesting
  Set<String> ccMaps = ImmutableSet.of(
      "Acid Foursome", "Acid Pools", "Acid Trip", "Assault on Suburbia", "Biggie Biggs", "Block Wars", "Brain Coral", "Cavedog Links CC",
      "Checker Ponds", "Cluster Freak", "Comet Catcher", "Core Prime Industrial Area", "Crater Islands", "Crystal Cracked",
      "Crystal Isles", "Crystal Maze", "Crystal Treasure Island", "Dire Straits", "East Indeez", "Eastside Westside",
      "Expanded Confluence", "Flooded Glaciers", "Gasbag Forests", "Gasplant Plain", "Higher Ground", "Ice Scream", "Icy Bergs",
      "John's Pass", "Lake Shore", "Lusch Puppy", "Luschaven", "Metal Isles", "Moon Quartet", "Ooooweeee", "Pillopeens",
      "Plains and Passes", "Polar Range", "Poly Fields", "Red River North", "Red River", "Ror Shock", "Sail Away", "Sector 410b",
      "Show Down", "Slate Gordon", "Slated Fate", "Steel Jungle", "Surface Meltdown", "Temblorian Mist", "The Barrier Reef", "The Bayou",
      "Town & Country", "Trout Farm"
  );

  @VisibleForTesting
  Set<String> btMaps = ImmutableSet.of(
      "Aqua Verdigris", "Brilliant Cut Lake", "Canal Crossing", "Coremageddon", "Metal Gridlock", "Wretched Ridges"
  );

  @VisibleForTesting
  Set<String> cdMaps = ImmutableSet.of(
      "A Plethora of Ponds", "Abysmal Lake", "Ancient Issaquah", "Cloudious Prime", "Comet Catcher", "Long Lakes", "LUSCHIE",
      "Luschinfloggen", "Luschious", "Metal Isles", "Mounds of Mars", "PC Games' Evad River Delta", "Plains and Passes",
      "Starfish Isles", "Thundurlok Rok", "Tropical Paradise"
  );

  private static URL getDownloadUrl(String mapName, String baseUrl) {
    return noCatch(() -> new URL(format(baseUrl, urlFragmentEscaper().escape(mapName).toLowerCase(Locale.US))));
  }

  private static URL getPreviewUrl(String mapName, String baseUrl, PreviewSize previewSize) {
    return noCatch(() -> new URL(format(baseUrl, previewSize.folderName, urlFragmentEscaper().escape(mapName).toLowerCase(Locale.US))));
  }

  @Override
  public void afterPropertiesSet() {
    eventBus.register(this);
    JavaFxUtil.addListener(preferencesService.getTotalAnnihilation(KnownFeaturedMod.DEFAULT.getTechnicalName()).getInstalledPathProperty(), observable -> tryLoadMaps(KnownFeaturedMod.DEFAULT.getTechnicalName()));
    tryLoadMaps(KnownFeaturedMod.DEFAULT.getTechnicalName());
  }

  private void tryLoadMaps(String modelTechnical) {
    Path mapsDirectory = preferencesService.getTotalAnnihilation(modelTechnical).getInstalledPath();
    if (mapsDirectory == null) {
      logger.warn(String.format("Could not load maps: installation path is not set for mod: %s",modelTechnical));
      return;
    }

    try {
      Files.createDirectories(mapsDirectory);
      Optional.ofNullable(directoryWatcherThread).ifPresent(Thread::interrupt);
      directoryWatcherThread = startDirectoryWatcher(mapsDirectory);
    } catch (IOException e) {
      logger.warn("Could not start map directory watcher", e);
      // TODO notify user
    }

    installedMaps.clear();
    loadInstalledMaps(modelTechnical);
  }

  private Thread startDirectoryWatcher(Path mapsDirectory) {
    Thread thread = new Thread(() -> noCatch(() -> {
      try (WatchService watcher = mapsDirectory.getFileSystem().newWatchService()) {
        // beware potential bug: this used to register with forgedAlliancePreferences.getCustomMapsDirectory() ...
        mapsDirectory.register(watcher, ENTRY_DELETE);
        while (!Thread.interrupted()) {
          WatchKey key = watcher.take();
          key.pollEvents().stream()
              .filter(event -> event.kind() == ENTRY_DELETE)
              .forEach(event -> removeMap(mapsDirectory.resolve((Path) event.context())));
          key.reset();
        }
      } catch (InterruptedException e) {
        logger.debug("Watcher terminated ({})", e.getMessage());
      }
    }));
    thread.setDaemon(true);
    thread.start();
    return thread;
  }


  private void loadInstalledMaps(String modTechnicalName) {
    taskService.submitTask(new CompletableTask<Void>(Priority.LOW) {

      protected Void call() {
        updateTitle(i18n.get("mapVault.loadingMaps"));

        Path installationPath = preferencesService.getTotalAnnihilation(modTechnicalName).getInstalledPath();

        if (installationPath.resolve("total2.hpi").toFile().exists()) {
          for (String map : otaMaps) {
            addInstalledMap(Paths.get(map));
          }
        }
        updateProgress(1, 4);
        if (installationPath.resolve("ccmaps.ccx").toFile().exists()) {
          for (String map : ccMaps) {
            addInstalledMap(Paths.get(map));
          }
        }
        updateProgress(2, 4);
        if (installationPath.resolve("btmaps.ccx").toFile().exists()) {
          for (String map : btMaps) {
            addInstalledMap(Paths.get(map));
          }
        }
        updateProgress(3, 4);
        if (installationPath.resolve("cdmaps.ccx").toFile().exists()) {
          for (String map : cdMaps) {
            addInstalledMap(Paths.get(map));
          }
        }
        updateProgress(4, 4);
        return null;
      }
    });
  }

  private void removeMap(Path path) {
//    installedMaps.remove(pathToMap.remove(path));
  }

  private void addInstalledMap(Path path) throws MapLoadException {
    try {
      MapBean mapBean = readMap(path);
      pathToMap.put(path, mapBean);
      if (!mapsByFolderName.containsKey(mapBean.getFolderName())) {
        installedMaps.add(mapBean);
      }
    } catch (MapLoadException e) {
      logger.warn("Map could not be read: " + path.getFileName(), e);
    }
  }


  @NotNull
  public MapBean readMap(Path mapFolder) throws MapLoadException {
      MapBean mapBean = new MapBean();
      mapBean.setFolderName(mapFolder.toString());
      mapBean.setDisplayName(mapFolder.toString());
      mapBean.setDescription(mapFolder.toString());
      mapBean.setType(Type.SKIRMISH);
      mapBean.setSize(MapSize.valueOf(512, 512));
      mapBean.setPlayers(10);

      return mapBean;
  }

  @SneakyThrows(IOException.class)
  @NotNull
  @Cacheable(value = CacheNames.MAP_PREVIEW, unless = "#result == null")
  public Image loadPreview(String modTechnicalName, String mapName, PreviewSize previewSize) {
    if (mapGeneratorService.isGeneratedMap(mapName)) {
      Path previewPath = preferencesService.getTotalAnnihilation(modTechnicalName).getInstalledPath().resolve(mapName).resolve(mapName + "_preview.png");
      if (Files.exists(previewPath)) {
        return new Image(Files.newInputStream(previewPath));
      } else {
        return mapGeneratorService.getGeneratedMapPreviewImage();
      }
    }
    return loadPreview(getPreviewUrl(mapName, mapPreviewUrlFormat, previewSize), previewSize);
  }


  public ObservableList<MapBean> getInstalledMaps() {
    return installedMaps;
  }

  public Optional<MapBean> getMapLocallyFromName(String mapFolderName) {
    logger.debug("Trying to find map '{}' locally", mapFolderName);
    String mapFolderKey = mapFolderName.toLowerCase();
    return Optional.ofNullable(mapsByFolderName.get(mapFolderKey));
  }

  public boolean isOfficialMap(String mapName) {
    Set<String> officialMaps = new HashSet<>();
    officialMaps.addAll(otaMaps);
    officialMaps.addAll(ccMaps);
    officialMaps.addAll(btMaps);
    officialMaps.addAll(cdMaps);
    return officialMaps.stream().anyMatch(name -> name.equalsIgnoreCase(mapName));
  }


  /**
   * Returns {@code true} if the given map is available locally, {@code false} otherwise.
   */

  public boolean isInstalled(String mapFolderName) {
    return mapsByFolderName.containsKey(mapFolderName.toLowerCase());
  }


  public CompletableFuture<Void> download(String modTechnicalname, String technicalMapName) {
    URL mapUrl = getDownloadUrl(technicalMapName, mapDownloadUrlFormat);
    return downloadAndInstallMap(modTechnicalname, technicalMapName, mapUrl, null, null);
  }


  public CompletableFuture<Void> downloadAndInstallMap(String modTechnicalName, MapBean map, @Nullable DoubleProperty progressProperty, @Nullable StringProperty titleProperty) {
    return downloadAndInstallMap(modTechnicalName, map.getFolderName(), map.getDownloadUrl(), progressProperty, titleProperty);
  }

  public CompletableFuture<Tuple<List<MapBean>, Integer>> getRecommendedMapsWithPageCount(int count, int page) {
    return preferencesService.getRemotePreferencesAsync()
        .thenCompose(
            clientConfiguration -> {
              List<Integer> recommendedMapIds = clientConfiguration.getRecommendedMaps();
              return fafService.getMapsByIdWithPageCount(recommendedMapIds, count, page);
            }
        );
  }

  public CompletableFuture<Tuple<List<MapBean>, Integer>> getHighestRatedMapsWithPageCount(int count, int page) {
    return fafService.getHighestRatedMapsWithPageCount(count, page);
  }

  public CompletableFuture<Tuple<List<MapBean>, Integer>> getNewestMapsWithPageCount(int count, int page) {
    return fafService.getNewestMapsWithPageCount(count, page);
  }


  public CompletableFuture<Tuple<List<MapBean>, Integer>> getMostPlayedMapsWithPageCount(int count, int page) {
    return fafService.getMostPlayedMapsWithPageCount(count, page);
  }

  /**
   * Loads the preview of a map or returns a "unknown map" image.
   */

  @Cacheable(CacheNames.MAP_PREVIEW)
  public Image loadPreview(MapBean map, PreviewSize previewSize) {
    URL url;
    switch (previewSize) {
      case SMALL:
        url = map.getSmallThumbnailUrl();
        break;
      case LARGE:
        url = map.getLargeThumbnailUrl();
        break;
      default:
        throw new ProgrammingError("Uncovered preview size: " + previewSize);
    }
    return loadPreview(url, previewSize);
  }

  @Cacheable(CacheNames.MAP_PREVIEW)
  public Image loadPreview(URL url, PreviewSize previewSize) {
    return assetService.loadAndCacheImage(url, Paths.get("maps").resolve(previewSize.folderName),
        () -> uiService.getThemeImage(UiService.UNKNOWN_MAP_IMAGE));
  }


  public CompletableFuture<Void> uninstallMap(MapBean map) {
    if (isOfficialMap(map.getFolderName())) {
      throw new IllegalArgumentException("Attempt to uninstall an official map");
    }
    UninstallMapTask task = applicationContext.getBean(com.faforever.client.map.UninstallMapTask.class);
    task.setMap(map);
    return taskService.submitTask(task).getFuture();
  }


  public Path getPathForMap(Path localMapPath, MapBean map) {
    return getPathForMapInsensitive(localMapPath, map.getFolderName());
  }

  private Path getMapsDirectory(String modTechnicalName) {
    return preferencesService.getTotalAnnihilation(modTechnicalName).getInstalledPath();
  }

  public Path getPathForMap(Path localMapsPath, String technicalName) {
    Path path = localMapsPath.resolve(technicalName);
    if (Files.notExists(path)) {
      return null;
    }
    return path;
  }

  public Path getPathForMapInsensitive(Path localMapPath, String approxName) {
    for (Path entry : noCatch(() -> Files.newDirectoryStream(localMapPath))) {
      if (entry.getFileName().toString().equalsIgnoreCase(approxName)) {
        return entry;
      }
    }
    return null;
  }

  public CompletableTask<Void> uploadMap(Path mapPath, boolean ranked) {
    MapUploadTask mapUploadTask = applicationContext.getBean(MapUploadTask.class);
    mapUploadTask.setMapPath(mapPath);
    mapUploadTask.setRanked(ranked);

    return taskService.submitTask(mapUploadTask);
  }


  @CacheEvict(CacheNames.MAPS)
  public void evictCache() {
    // Nothing to see here
  }

  /**
   * Tries to find a map my its folder name, first locally then on the server.
   */

  public CompletableFuture<Optional<MapBean>> findByMapFolderName(String folderName) {
    Optional<MapBean> installed = getMapLocallyFromName(folderName);
    if (installed.isPresent()) {
      return CompletableFuture.completedFuture(installed);
    }
    return fafService.findMapByFolderName(folderName);
  }

  public CompletableFuture<Boolean> hasPlayedMap(int playerId, String mapVersionId) {
    return fafService.getLastGameOnMap(playerId, mapVersionId)
        .thenApply(Optional::isPresent);
  }


  @Async
  public CompletableFuture<Integer> getFileSize(URL downloadUrl) {
    return CompletableFuture.completedFuture(noCatch(() -> downloadUrl
        .openConnection()
        .getContentLength()));
  }


  public CompletableFuture<Tuple<List<MapBean>, Integer>> findByQueryWithPageCount(SearchConfig searchConfig, int count, int page) {
    return fafService.findMapsByQueryWithPageCount(searchConfig, count, page);
  }


  public Optional<MapBean> findMap(String id) {
    return fafService.findMapById(id);
  }


  public CompletableFuture<Tuple<List<MapBean>, Integer>> getLadderMapsWithPageCount(int loadMoreCount, int page) {
    return fafService.getLadder1v1MapsWithPageCount(loadMoreCount, page);
  }

  private CompletableFuture<Void> downloadAndInstallMap(String modTechnicalName, String folderName, URL downloadUrl, @Nullable DoubleProperty progressProperty, @Nullable StringProperty titleProperty) {
    if (mapGeneratorService.isGeneratedMap(folderName)) {
      return mapGeneratorService.generateMap(folderName).thenRun(() -> {
      });
    }

    DownloadMapTask task = applicationContext.getBean(DownloadMapTask.class);
    task.setMapUrl(downloadUrl);
    task.setFolderName(folderName);

    if (progressProperty != null) {
      progressProperty.bind(task.progressProperty());
    }
    if (titleProperty != null) {
      titleProperty.bind(task.titleProperty());
    }

    Path localMapPath = this.getMapsDirectory(modTechnicalName);
    return taskService.submitTask(task).getFuture()
        .thenAccept(aVoid -> noCatch(() -> addInstalledMap(getPathForMapInsensitive(localMapPath, folderName))));
  }

  public CompletableFuture<Tuple<List<MapBean>, Integer>> getOwnedMapsWithPageCount(int loadMoreCount, int page) {
    Player player = playerService.getCurrentPlayer()
        .orElseThrow(() -> new IllegalStateException("Current player not set"));
    int playerId = player.getId();
    return fafService.getOwnedMapsWithPageCount(playerId, loadMoreCount, page);
  }

  public CompletableFuture<Void> hideMapVersion(MapBean map) {
    applicationContext.getBean(this.getClass()).evictCache();
    return fafService.hideMapVersion(map);
  }

  public CompletableFuture<Void> unrankMapVersion(MapBean map) {
    applicationContext.getBean(this.getClass()).evictCache();
    return fafService.unrankeMapVersion(map);
  }

  @Override
  public void destroy() {
    Optional.ofNullable(directoryWatcherThread).ifPresent(Thread::interrupt);
  }

  public enum PreviewSize {
    // These must match the preview URLs
    SMALL("small"), LARGE("large");

    String folderName;

    PreviewSize(String folderName) {
      this.folderName = folderName;
    }
  }
}
