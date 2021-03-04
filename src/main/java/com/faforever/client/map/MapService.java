package com.faforever.client.map;

import com.faforever.client.config.CacheNames;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.config.ClientProperties.Vault;
import com.faforever.client.fa.MapTool;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapBean.Type;
import com.faforever.client.notification.DismissAction;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
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
import com.faforever.client.update.ClientConfiguration.Downloadable;
import com.faforever.client.util.ProgrammingError;
import com.faforever.client.util.Tuple;
import com.faforever.client.vault.search.SearchController.SearchConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import javafx.application.Platform;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_ARCHIVE;
import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_DESCRIPTION;
import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_SIZE;
import static com.github.nocatch.NoCatch.noCatch;
import static com.google.common.net.UrlEscapers.urlFragmentEscaper;
import static java.lang.String.format;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.concurrent.CompletableFuture.completedFuture;


@Lazy
@Service
public class MapService implements InitializingBean, DisposableBean {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PreferencesService preferencesService;
  private final TaskService taskService;
  private final ApplicationContext applicationContext;
  private final FafService fafService;
  private final AssetService assetService;
  private final NotificationService notificationService;
  private final I18n i18n;
  private final UiService uiService;
  private final ClientProperties clientProperties;
  private final EventBus eventBus;
  private final PlayerService playerService;

  private final String mapDownloadUrlFormat;
  private final String mapPreviewUrlFormat;

  private class Installation {
    final String modTechnicalName;
    private final Map<String, MapBean> mapsByName = new HashMap<>();
    private final ObservableList<MapBean> maps = FXCollections.observableArrayList();
    private Thread directoryWatcherThread;
    private Integer enumerationsRequested = 0;

    public Installation(String modTechnical) {
      this.modTechnicalName = modTechnical;

      maps.addListener((ListChangeListener<MapBean>) change -> {
        while (change.next()) {
          for (MapBean mapBean : change.getRemoved()) {
            mapsByName.remove(mapBean.getMapName());
          }
          for (MapBean mapBean : change.getAddedSubList()) {
            mapsByName.put(mapBean.getMapName(), mapBean);
          }
        }
      });
    }

    private void removeMap(String mapName) {
      maps.remove(mapsByName.remove(mapName));
    }

    private void addMap(String mapName, String mapDetail[]) {
      MapBean mapBean = readMap(mapName, mapDetail);
      addMap(mapBean);
    }

    private void addMap(MapBean mapBean) {
      if (!mapsByName.containsKey(mapBean.getMapName())) {
        mapsByName.put(mapBean.getMapName(), mapBean);
        maps.add(mapBean);
      }
    }
  }

  // keyed by ModTechnical
  private Map<String, Installation> installations = new HashMap<>();
  private List<Downloadable> downloadables;

  public Installation getInstallation(String modTechnical) {
    Installation installation = installations.get(modTechnical);
    if (installation == null) {
      installations.put(modTechnical, new Installation(modTechnical));
    }
    return installations.get(modTechnical);
  }

  @Inject
  public MapService(PreferencesService preferencesService,
                    TaskService taskService,
                    ApplicationContext applicationContext,
                    FafService fafService,
                    AssetService assetService,
                    NotificationService notificationService,
                    I18n i18n,
                    UiService uiService,
                    ClientProperties clientProperties,
                    EventBus eventBus, PlayerService playerService) {
    this.preferencesService = preferencesService;
    this.taskService = taskService;
    this.applicationContext = applicationContext;
    this.fafService = fafService;
    this.assetService = assetService;
    this.notificationService = notificationService;
    this.i18n = i18n;
    this.uiService = uiService;
    this.clientProperties = clientProperties;
    this.eventBus = eventBus;
    this.playerService = playerService;
    Vault vault = clientProperties.getVault();
    this.mapDownloadUrlFormat = vault.getMapDownloadUrlFormat();
    this.mapPreviewUrlFormat = vault.getMapPreviewUrlFormat();

    try {
      downloadables = preferencesService.getRemotePreferences().getDownloadables();
    }
    catch (IOException e) {
      logger.warn("Unable to retrieve list of downloadables from remote preferences");
    }

    preferencesService.getTotalAnnihilationAllMods().addListener((ListChangeListener<TotalAnnihilationPrefs>) change -> {
      while (change.next()) {
        for (TotalAnnihilationPrefs taPrefs : change.getAddedSubList()) {
          taPrefs.getInstalledExePathProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> {tryLoadMaps(taPrefs.getBaseGameName());});
          });
        }
      }
    });

    for (TotalAnnihilationPrefs taPrefs: preferencesService.getTotalAnnihilationAllMods()) {
      if (taPrefs == null) {
        continue;
      }
      String modTechnical = taPrefs.getBaseGameName();
      Installation installation = new Installation(modTechnical);
      installations.put(modTechnical, installation);
    }
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
      "Plains and Passes", "Polar Range", "Polyp Fields", "Red River North", "Red River", "Ror Shock", "Sail Away", "Sector 410b",
      "Show Down", "Slate Gordon", "Slated Fate", "Steel Jungle", "Surface Meltdown", "Temblorian Mist", "The Barrier Reef", "The Bayou",
      "Town & Country", "Trout Farm"
  );

  @VisibleForTesting
  Set<String> btMaps = ImmutableSet.of(
      "Aqua Verdigris", "Brilliant Cut Lake", "Canal Crossing", "Coremageddon", "Metal Gridlock", "Wretched Ridges"
  );

  @VisibleForTesting
  Set<String> cdMaps = ImmutableSet.of(
      "A Plethora of Ponds", "Abysmal Lake", "Ancient Issaquah", "Cloudious Prime", "Long Lakes", "LUSCHIE",
      "Luschinfloggen", "Luschious", "Mounds of Mars", "PC Games' Evad River Delta",
      "Starfish Isles", "Thundurlok Rok", "Tropical Paradise"
  );

  private URL getDownloadUrl(String hpiArchiveName) {
    if (this.downloadables == null) {
      return null;
    }
    for (Downloadable downloadable : this.downloadables) {
      if (downloadable.getWhat().toLowerCase().equals(hpiArchiveName.toLowerCase())) {
        noCatch(() -> new URL(downloadable.getUrl()));
      }
    }
    return null;
  }

  private static URL getPreviewUrl(String mapName, String baseUrl, PreviewType previewType) {
    return noCatch(() -> new URL(format(baseUrl, previewType.folderName, urlFragmentEscaper().escape(mapName).toLowerCase(Locale.US))));
  }

  @Override
  public void afterPropertiesSet() {
    eventBus.register(this);

    for (TotalAnnihilationPrefs taPrefs: preferencesService.getTotalAnnihilationAllMods()) {
      if (taPrefs == null) {
        continue;
      }
      String modTechnical = taPrefs.getBaseGameName();
      JavaFxUtil.addListener(taPrefs.getInstalledExePathProperty(), observable -> tryLoadMaps(modTechnical));
      tryLoadMaps(modTechnical);
    }
  }

  private void tryLoadMaps(String modTechnical) {
    Path mapsDirectory = preferencesService.getTotalAnnihilation(modTechnical).getInstalledPath();
    if (mapsDirectory == null) {
      logger.warn(String.format("Could not load maps: installation path is not set for mod: %s",modTechnical));
      return;
    }

    Installation installation = getInstallation(modTechnical);

    try {
      Files.createDirectories(mapsDirectory);
      Optional.ofNullable(installation.directoryWatcherThread).ifPresent(Thread::interrupt);
      installation.directoryWatcherThread = startDirectoryWatcher(installation, mapsDirectory);
    } catch (IOException e) {
      logger.warn("Could not start map directory watcher", e);
      // TODO notify user
    }

    loadInstalledMaps(installation);
  }

  private Thread startDirectoryWatcher(Installation installation, Path mapsDirectory) {
    Thread thread = new Thread(() -> noCatch(() -> {
      try (WatchService watcher = mapsDirectory.getFileSystem().newWatchService()) {
        // beware potential bug: this used to register with forgedAlliancePreferences.getCustomMapsDirectory() ...
        mapsDirectory.register(watcher, new WatchEvent.Kind[]{ENTRY_DELETE, ENTRY_MODIFY, ENTRY_CREATE});
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.{ufo,hpi,ccx}");
        while (!Thread.interrupted()) {
          WatchKey key = watcher.take();
          List<WatchEvent<?>> events = key.pollEvents();
          if (events.stream()
              .filter(event -> matcher.matches((Path)event.context()) )
              .findAny().isPresent()) {
            Platform.runLater(() -> { loadInstalledMaps(installation); });
          }
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

  public void loadInstalledMaps(String modTechnical) {
    Installation installation = getInstallation(modTechnical);
    loadInstalledMaps(installation);
  }

  private void loadInstalledMaps(Installation installation) {
    synchronized(installation.enumerationsRequested) {
      ++installation.enumerationsRequested;
      if (installation.enumerationsRequested > 1) {
        return;
      }
    }

    taskService.submitTask(new CompletableTask<Void>(Priority.LOW) {

      protected Void call() {
        updateTitle(i18n.get("mapVault.loadingMaps"));

        Path gamePath = preferencesService.getTotalAnnihilation(installation.modTechnicalName).getInstalledPath();
        if (gamePath == null) {
          synchronized(installation.enumerationsRequested) {
            installation.enumerationsRequested = 0;
          }
          return null;
        }

        List<MapBean> mapList = new ArrayList<>();
        for (String[] details: MapTool.listMaps(gamePath, null,null, preferencesService.getCacheDirectory().resolve("maps"), false)) {
          MapBean mapBean = readMap(details[0], details);
          mapList.add(mapBean);
        }
        installation.maps.clear();
        installation.maps.addAll(mapList);
        if (installation.maps.isEmpty()) {
          for (String map : otaMaps) {
            installation.addMap(map, null);
          }
        }
        updateProgress(1, 1);

        boolean again = false;
        synchronized(installation.enumerationsRequested) {
          if (installation.enumerationsRequested > 1) {
            installation.enumerationsRequested = 1;
            again = true;
          }
          else {
            installation.enumerationsRequested = 0;
          }
        }
        if (again) {
          return call();
        }
        else {
          return null;
        }
      }
    });
  }

  @NotNull
  public MapBean readMap(String mapName, String [] mapDetails) {
    MapBean mapBean = new MapBean();
    String archiveName = mapDetails != null ? mapDetails[MAP_DETAIL_COLUMN_ARCHIVE] : "<unknown hpi>";
    String description = mapDetails != null ? mapDetails[MAP_DETAIL_COLUMN_DESCRIPTION] : mapName;
    String mapSizeStr = mapDetails != null ? mapDetails[MAP_DETAIL_COLUMN_SIZE] : "16 x 16";

    String mapSizeArray[] = mapSizeStr.replaceAll("[^0-9x]", "").split("x");

    if (downloadables != null) {
      Optional<Downloadable> downloadURL = downloadables.stream().filter((downloadable) -> downloadable.getWhat() == archiveName).findFirst();
      if (downloadURL.isPresent()) {
        try {
          mapBean.setDownloadUrl(new URL(downloadURL.get().getUrl()));
        } catch (MalformedURLException e) {
          logger.warn("bad download url: {}", downloadURL.get());
        }
      }
    }

    mapBean.setMapName(mapName);
    mapBean.setDescription(description.replaceAll("[ ][ ]+", "\n"));  // some maps insert spaces into description to move to new line when displayed in TA lobby
    mapBean.setHpiArchiveName(archiveName);
    mapBean.setPlayers(10);
    mapBean.setType(Type.SKIRMISH);

    mapBean.setSize(MapSize.valueOf(256, 256));
    try {
      if (mapSizeArray.length == 2) {
        Integer w = Integer.parseInt(mapSizeArray[0].trim());
        Integer h = Integer.parseInt(mapSizeArray[1].trim());
        mapBean.setSize(MapSize.valueOf(w * 16, h * 16));
      }
    }
    catch (NumberFormatException e) {
      logger.error("Map '{}' has unparsable size '{}'", mapName, mapSizeStr);
    }

    return mapBean;
  }

  public ObservableList<MapBean> getInstalledMaps(String modTechnical) {
    Installation installation = getInstallation(modTechnical);
    return installation.maps;
  }

  public Optional<MapBean> getMapLocallyFromName(String modTechnical, String mapName) {
    logger.debug("Trying to find map '{}' locally", mapName);
    return Optional.ofNullable(installations.getOrDefault(modTechnical, new Installation(modTechnical)).mapsByName.get(mapName));
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

  public boolean isInstalled(String modTechnical, String mapName) {
    return getInstallation(modTechnical).mapsByName.containsKey(mapName);
  }

  private void removeConflictingArchive(String hpiArchiveName, Path conflictingPath) {
    try {
      logger.info("Archive {} conflicts with {}: renaming to .taforever.bak", conflictingPath, hpiArchiveName);
      Files.move(conflictingPath, Paths.get(conflictingPath + ".taforever.bak"));
    } catch (IOException e1) {
      try {
        logger.warn("{}.taforever.bak already exists.  will just delete", conflictingPath);
        Files.deleteIfExists(conflictingPath);
      } catch (IOException e2) {
        logger.warn("Unable to delete conflicting archive {}", conflictingPath);
      }
    }
  }

  public CompletableFuture<Void> download(String modTechnical, String hpiArchiveName) {
    CompletableFuture<Void> future = completedFuture(null);
    Optional<Downloadable> downloadable = downloadables.stream().filter(d -> d.getWhat().equals(hpiArchiveName)).findFirst();
    if (downloadable.isPresent()) {
      try {
        URL url = new URL(downloadable.get().getUrl());
        future = downloadAndInstallArchive(modTechnical, hpiArchiveName, url, null, null);
        for(String dependency: downloadable.get().getDepends()) {
          future = future.thenCompose(aVoid -> download(modTechnical, dependency));
        }
        for(String conflict: downloadable.get().getConflicts()) {
          Path conflictingPath = preferencesService.getTotalAnnihilation(modTechnical).getInstalledPath().resolve(conflict);
          if (conflictingPath.toFile().exists()) {
            future = future.thenRun(() -> removeConflictingArchive(hpiArchiveName, conflictingPath));
          }
        }
      } catch (MalformedURLException e) {
        logger.error("bad archive download url: {}", downloadable.get().getUrl());
      }
    }
    else {
      notificationService.addNotification(new ImmediateNotification(
          i18n.get("mapDownloadTask.title", hpiArchiveName), i18n.get("mapDownloadTask.notFound", modTechnical, preferencesService.getTotalAnnihilation(modTechnical).getInstalledPath()),
          Severity.WARN, Collections.singletonList(new DismissAction(i18n))));
    }
    return future;
  }

  public CompletableFuture<Void> downloadAndInstallMap(String modTechnicalName, MapBean map, @Nullable DoubleProperty progressProperty, @Nullable StringProperty titleProperty) {
    return downloadAndInstallArchive(modTechnicalName, map.getHpiArchiveName(), map.getDownloadUrl(), progressProperty, titleProperty);
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

  public void generatePreview(String modTechnical, String mapName, Path cachedFile, PreviewType previewType, int maxPositions) {
    if (previewType == PreviewType.MINI && Files.exists(cachedFile)) {
      return;
    }

    Path gamePath = preferencesService.getTotalAnnihilation(modTechnical).getInstalledPath();
    if (gamePath == null) {
      gamePath = preferencesService.getTotalAnnihilation(KnownFeaturedMod.DEFAULT.getTechnicalName()).getInstalledPath();
    }
    if (gamePath == null) {
      return;
    }

    MapTool.generatePreview(gamePath, mapName, cachedFile.getParent().getParent(), previewType, maxPositions);
  }

  /**
   * Loads the preview of a map or returns a "unknown map" image.
   */

  @SneakyThrows()
  @NotNull
  @Cacheable(value = CacheNames.MAP_PREVIEW, condition = "#previewType.getDisplayName().equals('Minimap')")
  public Image loadPreview(String modTechnicalName, String mapName, PreviewType previewType, int maxPositions) {
    return loadPreview(modTechnicalName, mapName, getPreviewUrl(mapName, mapPreviewUrlFormat, previewType), previewType, maxPositions);
  }

  @Cacheable(value = CacheNames.MAP_PREVIEW, condition = "#previewType.getDisplayName().equals('Minimap')")
  public Image loadPreview(String modTechnical, MapBean map, PreviewType previewType, int maxNumPlayers) {
    URL url = map.getLargeThumbnailUrl();
    return loadPreview(modTechnical, map.getMapName(), url, previewType, maxNumPlayers);
  }

  @Cacheable(value = CacheNames.MAP_PREVIEW, condition = "#previewType.getDisplayName().equals('Minimap')")
  private Image loadPreview(String modTechnical, String mapName, URL url, PreviewType previewType, int maxPositions) {

    String urlString = url.toString();
    String cachedFilename = urlString.substring(urlString.lastIndexOf('/') + 1);
    Path cacheSubFolder = Paths.get("maps").resolve(previewType.getFolderName(maxPositions));
    Path cachedFile = preferencesService.getCacheDirectory().resolve(cacheSubFolder).resolve(cachedFilename);

    generatePreview(modTechnical, mapName, cachedFile, previewType, maxPositions);

    return assetService.loadAndCacheImage(url, Paths.get("maps").resolve(previewType.getFolderName(maxPositions)),
        () -> uiService.getThemeImage(UiService.UNKNOWN_MAP_IMAGE));
  }

  public CompletableFuture<Void> uninstallMap(MapBean map) {
    if (isOfficialMap(map.getMapName())) {
      throw new IllegalArgumentException("Attempt to uninstall an official map");
    }
    UninstallMapTask task = applicationContext.getBean(com.faforever.client.map.UninstallMapTask.class);
    task.setMap(map);
    return taskService.submitTask(task).getFuture();
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

  public CompletableFuture<Optional<MapBean>> findByMapFolderName(String modTechnical, String folderName) {
    Optional<MapBean> installed = getMapLocallyFromName(modTechnical, folderName);
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

  private CompletableFuture<Void> downloadAndInstallArchive(String modTechnicalName, String hpiArchive, URL downloadUrl, @Nullable DoubleProperty progressProperty, @Nullable StringProperty titleProperty) {

    Path mapsDirectory = preferencesService.getTotalAnnihilation(modTechnicalName).getInstalledPath();
    if (mapsDirectory == null) {
      logger.warn(String.format("Could not load maps: installation path is not set for mod: %s",modTechnicalName));
      return new CompletableFuture<Void>();
    }

    DownloadMapTask task = applicationContext.getBean(DownloadMapTask.class);
    task.setMapUrl(downloadUrl);
    task.setInstallationPath(mapsDirectory);
    task.setHpiArchiveName(hpiArchive);

    if (progressProperty != null) {
      progressProperty.bind(task.progressProperty());
    }
    if (titleProperty != null) {
      titleProperty.bind(task.titleProperty());
    }

    return taskService.submitTask(task).getFuture();
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
    for (Installation installation: installations.values()) {
      Optional.ofNullable(installation.directoryWatcherThread).ifPresent(Thread::interrupt);
    }
  }

  public enum PreviewType {
    // These must match the preview URLs
    MINI("mini"),
    POSITIONS("positions"),
    MEXES("mexes"),
    GEOS("geos"),
    ROCKS("rocks"),
    TREES("trees");

    private String folderName;

    PreviewType(String folderName) {
      this.folderName = folderName;
    }

    public String getDisplayName() {
      switch (this) {
        case MINI: return "Minimap";
        case POSITIONS: return "Positions";
        case MEXES: return "Metal Patches";
        case GEOS: return "Geo Vents";
        case ROCKS: return "Reclaim (metal)";
        case TREES: return "Reclaim (energy)";
        default: return "null";
      }
    }

    String getFolderName(int maxNumPlayers) {
      switch (this) {
        case MINI:
          return this.folderName;
        default:
          return String.format("%s_%s", this.folderName, maxNumPlayers);
      }
    }
  }
}
