package com.faforever.client.map;

import com.faforever.client.config.CacheNames;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.config.ClientProperties.Vault;
import com.faforever.client.fa.MapTool;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.io.FileUtils;
import com.faforever.client.map.MapBean.Type;
import com.faforever.client.notification.Action;
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
import com.faforever.client.teammatchmaking.MatchmakingQueue;
import com.faforever.client.theme.UiService;
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
import javafx.util.Pair;
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
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_ARCHIVE;
import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_CRC;
import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_DESCRIPTION;
import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_NAME;
import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_SIZE;
import static com.faforever.client.util.LinkOrCopy.linkOrCopyWithBackup;
import static com.github.nocatch.NoCatch.noCatch;
import static com.google.common.net.UrlEscapers.urlFragmentEscaper;
import static java.lang.String.format;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;


@Lazy
@Service
public class MapService implements InitializingBean, DisposableBean {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String DEBUG = "debug";

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
  private final PlatformService platformService;

  private final String mapDownloadUrlFormat;
  private final String mapPreviewUrlFormat;
  private final Object notifiedBadMapToolLock = new Object();
  private Boolean notifiedBadMapTool = false;

  private class Installation {
    final String modTechnicalName;
    private final Map<String, MapBean> mapsByName = new HashMap<>();
    private final ObservableList<MapBean> maps = FXCollections.observableArrayList();
    private List<String> downloadingList = new ArrayList<>(); // guard against multiple attempts to download same archive prolly due to clicky users
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

    private void addMap(String mapName, String[] mapDetail, Function<Void, String> installedMapCrcGetter) {
      MapBean mapBean = readMap(mapName, mapDetail, installedMapCrcGetter);
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
                    EventBus eventBus,
                    PlayerService playerService,
                    PlatformService platformService) {
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
    this.platformService = platformService;
    Vault vault = clientProperties.getVault();
    this.mapDownloadUrlFormat = vault.getMapDownloadUrlFormat();
    this.mapPreviewUrlFormat = vault.getMapPreviewUrlFormat();

    preferencesService.getTotalAnnihilationAllMods().addListener((ListChangeListener<TotalAnnihilationPrefs>) change -> {
      while (change.next()) {
        for (TotalAnnihilationPrefs taPrefs : change.getAddedSubList()) {
          taPrefs.getInstalledExePathProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> {tryLoadMaps(taPrefs.getBaseGameName());});
          });
          Platform.runLater(() -> {tryLoadMaps(taPrefs.getBaseGameName());});
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

  @VisibleForTesting
  Set<String> officialMapArchives = ImmutableSet.of(
      "btdata.ccx", "btmaps.ccx", "ccdata.ccx", "ccmaps.ccx", "ccmiss.ccx", "cdmaps.ccx", "rev31.gp3",
      "tactics1.hpi", "tactics2.hpi", "tactics3.hpi", "tactics4.hpi", "tactics5.hpi", "tactics6.hpi", "tactics7.hpi", "tactics8.hpi",
      "totala1.hpi", "totala2.hpi", "totala3.hpi", "totala4.hpi", "worlds.hpi", "afark.ufo", "aflea.ufo", "ascarab.ufo",
      "cometctr.ufo", "cormabm.ufo", "cornecro.ufo", "corplas.ufo", "evadrivd.ufo", "example.ufo", "floggen.ufo", "mndsmars.ufo",
      "tademo.ufo");

  private static String HPI_ARCHIVE_TA_FEATURES_2013 = "TA_Features_2013.ccx";

  private static URL getDownloadUrl(String hpiArchiveName, String baseUrl) {
    return noCatch(() -> new URL(format(baseUrl, urlFragmentEscaper().escape(hpiArchiveName))));
  }

  private static URL getPreviewUrl(String mapName, String baseUrl, PreviewType previewType) {
    return noCatch(() -> new URL(format(baseUrl, previewType.folderName, urlFragmentEscaper().escape(mapName))));
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

    loadInstalledMaps(installation, false);
  }

  private final Object deferInstalledMapsUpdateMutex = new Object();
  private Integer deferInstalledMapsUpdate = 0;
  public void addInstalledMapsUpdateDeferal() {
      synchronized(deferInstalledMapsUpdateMutex) {
        deferInstalledMapsUpdate += 1;
      }
  }

  public void releaseInstalledMapsUpdateDeferal() {
    synchronized(deferInstalledMapsUpdateMutex) {
      deferInstalledMapsUpdate = Math.max(0, deferInstalledMapsUpdate - 1);
    }

    if (deferInstalledMapsUpdate == 0) {
      for (Installation installation: installations.values()) {
        synchronized(installation) {
          if (installation.enumerationsRequested > 0) {
            installation.enumerationsRequested = 1; // there may have been multiple load requests while deferred
            loadInstalledMaps(installation, true);
          }
        }
      }
    }
  }

  private boolean isInstalledMapsUpdateDeferred() {
    synchronized(deferInstalledMapsUpdateMutex) {
      return deferInstalledMapsUpdate > 0;
    }
  }

  private Thread startDirectoryWatcher(Installation installation, Path mapsDirectory) {
    Thread thread = new Thread(() -> noCatch(() -> {
      try (WatchService watcher = mapsDirectory.getFileSystem().newWatchService()) {
        // beware potential bug: this used to register with forgedAlliancePreferences.getCustomMapsDirectory() ...
        mapsDirectory.register(watcher, ENTRY_DELETE, ENTRY_MODIFY, ENTRY_CREATE);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.{ufo,hpi,ccx}");
        while (!Thread.interrupted()) {
          WatchKey key = watcher.take();
          List<WatchEvent<?>> events = key.pollEvents();
          if (events.stream()
              .anyMatch(event -> matcher.matches((Path)event.context()) )) {
            Platform.runLater(() -> loadInstalledMaps(installation, false) );
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

  private void removeVersionTag(File archive) {
    Pattern pattern = Pattern.compile("(.*)(.v[0-9]{4})(.ufo)");
    Matcher matcher = pattern.matcher(archive.toString());
    if (matcher.matches()) {
      File renamedArchive = new File(String.format("%s%s", matcher.group(1), matcher.group(3)));
      if (archive.exists() && !renamedArchive.exists()) {
        logger.info("[removeVersionTag] renaming {} to {}", archive, renamedArchive);
        archive.renameTo(renamedArchive);
      }
    }
  }

  public void loadInstalledMaps(String modTechnical) {
    Installation installation = getInstallation(modTechnical);
    loadInstalledMaps(installation, false);
  }

  private void loadInstalledMaps(Installation installation, boolean ignoreEnumerationsRequestedCount) {
    if (!ignoreEnumerationsRequestedCount) {
      synchronized (installation) {
        ++installation.enumerationsRequested;
        if (installation.enumerationsRequested > 1) {
          return;
        }
      }
    }

    if (isInstalledMapsUpdateDeferred()) {
      return;
    }

    taskService.submitTask(new CompletableTask<Void>(Priority.LOW) {

      protected Void call() {
        updateTitle(i18n.get("mapVault.loadingMaps"));

        Path exePath = preferencesService.getTotalAnnihilation(installation.modTechnicalName).getInstalledExePath();
        if (exePath == null || !Files.isExecutable(exePath)) {
          synchronized(installation) {
            installation.enumerationsRequested = 0;
          }
          return null;
        }
        Path gamePath = exePath.getParent();

        List<MapBean> mapList = new ArrayList<>();
        try {
          for (String[] details : MapTool.listMapsInstalled(gamePath, preferencesService.getCacheDirectory().resolve("maps"), false)) {
            Function<Void,String> getInstalledMapCrc = (aVoid) -> {
              try {
                List<String[]> detailsWithCrc = MapTool.listMap(gamePath, details[MAP_DETAIL_COLUMN_NAME]);
                return detailsWithCrc.get(0)[MAP_DETAIL_COLUMN_CRC];
              } catch (IOException e) {
                notifyBadMapTool(e);
                return "00000000";
              }
            };
            MapBean mapBean = readMap(details[0], details, getInstalledMapCrc);
            mapList.add(mapBean);
          }
        }
        catch (IOException e) {
          notifyBadMapTool(e);
        }
        JavaFxUtil.runLater(() -> {
              installation.maps.setAll(mapList);
              if (installation.maps.isEmpty()) {
                logger.warn("no maps found for mod={}. inserting OTA maps", installation.modTechnicalName);
                for (String map : otaMaps) {
                  installation.addMap(map, null, null);
                }
              }
            });
        updateProgress(1, 1);

        boolean again = false;
        synchronized(installation) {
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

  static final Pattern MAP_SIZE_FROM_DESCRIPTION_REGEX = Pattern.compile("([0-9]+\\s?[xX]\\s?[0-9]+)[\\s\\.].*");
  @NotNull
  public MapBean readMap(String mapName, @Nullable String [] mapDetails, @Nullable Function<Void, String> getInstalledMapCrc) {
    MapBean mapBean = new MapBean();

    String archiveName = "unknown.ufo";
    String description = mapName;
    String mapSizeStr = "16 x 16";
    String crc = "00000000";

    try {
      if (mapDetails != null) {
        archiveName = mapDetails[MAP_DETAIL_COLUMN_ARCHIVE];
        description = mapDetails[MAP_DETAIL_COLUMN_DESCRIPTION];
        mapSizeStr = mapDetails[MAP_DETAIL_COLUMN_SIZE];
        crc = mapDetails[MAP_DETAIL_COLUMN_CRC];
      }
      else {
        logger.warn("null map details for map: {}", mapName);
      }
    }
    catch (ArrayIndexOutOfBoundsException e) {
      logger.warn("index out of bounds for map: {}. details: {}", String.join("/",mapDetails));
    }

    if (mapSizeStr.isEmpty()) {
      Matcher matcher = MAP_SIZE_FROM_DESCRIPTION_REGEX.matcher(description);
      if (matcher.find()) {
        mapSizeStr = matcher.group(1);
      }
    }
    String mapSizeArray[] = mapSizeStr.replaceAll("[^0-9x]", "").split("x");

    mapBean.setDownloadUrl(getDownloadUrl(archiveName, mapDownloadUrlFormat));
    mapBean.setMapName(mapName);
    mapBean.setDescription(description.replaceAll("[ ][ ]+", "\n"));  // some maps insert spaces into description to move to new line when displayed in TA lobby
    mapBean.setHpiArchiveName(archiveName);
    mapBean.setPlayers(10);
    mapBean.setType(Type.SKIRMISH);

    if (!"00000000".equals(crc)) {
      mapBean.setCrcFuture(CompletableFuture.completedFuture(crc));
    }
    else if (getInstalledMapCrc != null) {
      mapBean.setInstalledMapCrcGetter(this.taskService, getInstalledMapCrc);
    }
    else {
      mapBean.setCrcFuture(CompletableFuture.completedFuture(crc));
    }

    mapBean.setSize(MapSize.valueOf(0, 0));
    try {
      if (mapSizeArray.length == 2) {
        Integer w = Integer.parseInt(mapSizeArray[0].trim());
        Integer h = Integer.parseInt(mapSizeArray[1].trim());
        mapBean.setSize(MapSize.valueOf(w, h));
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

  public ObservableList<MapBean> getOfficialMaps() {
    ObservableList<MapBean> maps = FXCollections.observableArrayList();
    maps.add(readMap(
        "SHERWOOD",
        new String[]{"SHERWOOD", "totala2.hpi", "ead82fc5", "TAF had trouble finding your maps so we just put this one in cos we know you probably have it", "7 x 7", "3"},
        null));
    return maps;
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

  public boolean isOfficialArchive(Path archivePath) {
    String archvieFileName = archivePath.getFileName().toString();
    return officialMapArchives.stream().anyMatch(name -> name.equalsIgnoreCase(archvieFileName));
  }

  /**
   * Returns {@code true} if the given map is available locally, {@code false} otherwise.
   */
  public CompletableFuture<Boolean> isInstalled(String modTechnical, String mapName, String mapCrc) {
    Installation installation = getInstallation(modTechnical);
    if (!installation.mapsByName.containsKey(mapName)) {
      return CompletableFuture.completedFuture(false);
    }

    MapBean installedMap = installation.mapsByName.get(mapName);
    if (installedMap.getCrcFuture() == null) {
      logger.warn("[isInstalled] Unable to retrieve CRC for installed map '{}'.  Assuming it matches {}", mapName, mapCrc);
      return CompletableFuture.completedFuture(true);
    }

    return installation.mapsByName.get(mapName).getCrcFuture().
        thenApply(installedMapCrc -> {
          if (installedMapCrc == null || "00000000".equals(installedMapCrc)) {
            logger.warn("[isInstalled] Retrieved null CRC for installed map '{}'.  Assuming it matches {}", mapName, mapCrc);
            return true;
          }
          return installedMapCrc.equals(mapCrc);
        });
  }

  public void removeConflictingArchives(String modTechnical, java.util.Map<String,MapBean> preExistingMaps, Path newArchive) {
    Path installationPath = preferencesService.getTotalAnnihilation(modTechnical).getInstalledPath();
    try {
      List<String[]> archiveMaps = MapTool.listMapsInArchive(newArchive, null, false);
      archiveMaps.stream()
          .map(mapDetails -> mapDetails[MapTool.MAP_DETAIL_COLUMN_NAME])
          .filter(preExistingMaps::containsKey)
          .map(mapName -> preExistingMaps.get(mapName).getHpiArchiveName())
          .distinct()
          .filter(archive -> !archive.equals(newArchive.getFileName().toString()))
          .forEach(archive -> {
            try {
              removeArchive(installationPath.resolve(archive));
            } catch (IOException e) {
              logger.error(String.format("[removeConflictingArchives] Unable to remove archive '{}'", archive), e);
            }
          });
    }
    catch (IOException e) {
      notifyBadMapTool(e);
    }
  }

  private void removeArchive(Path archivePath) throws IOException {
    if (isOfficialArchive(archivePath)) {
      logger.info("Ignoring attempt to delete official map archive '{}' ...", archivePath);
      return;
    }

    Path cachedArchivePath = preferencesService.getCacheDirectory().resolve("maps").resolve(archivePath.getFileName());
    if (Files.exists(cachedArchivePath) && Files.size(cachedArchivePath) == Files.size(archivePath)) {
      final Path finalCachedArchivePath1 = cachedArchivePath;
      notificationService.addNotification(new ImmediateNotification(
          i18n.get("mapVault.removingArchive", archivePath),
          i18n.get("mapVault.removingArchiveAlreadyAt", archivePath, cachedArchivePath),
          Severity.INFO, Arrays.asList(
          new Action(i18n.get("mapVault.removingArchiveShow"), Action.Type.OK_STAY, event -> this.platformService.reveal(archivePath)),
          new Action(i18n.get("mapVault.removingArchiveIgnore"), Action.Type.OK_DONE, event -> {}),
          new Action(i18n.get("mapVault.removingArchiveDelete"), Action.Type.OK_DONE, event -> {
            try {
              logger.info("Deleting archive {} (a file of that name and size can be found at {})", archivePath, finalCachedArchivePath1);
              Files.delete(archivePath);
            } catch (IOException e) {
              logger.error(String.format("[removeArchive] Unable to delete {}", archivePath), e);
            }
          }))
      ));
      return;
    }

    if (Files.exists(cachedArchivePath)) {
      // cached file exists but is of different size.  lets append crc and (attempt to) move to cache
      long archiveCrc = FileUtils.getCRC(archivePath.toFile());
      cachedArchivePath = preferencesService.getCacheDirectory().resolve("maps").resolve(archivePath.getFileName() + "." + Long.toHexString(archiveCrc));
    }

    if (Files.exists(cachedArchivePath)) {
      final Path finalCachedArchivePath2 = cachedArchivePath;
      notificationService.addNotification(new ImmediateNotification(
          i18n.get("mapVault.removingArchive", archivePath),
          i18n.get("mapVault.removingArchiveAlreadyAt", cachedArchivePath),
          Severity.INFO, Arrays.asList(
          new Action(i18n.get("mapVault.removingArchiveShow"), Action.Type.OK_STAY, event -> this.platformService.reveal(archivePath)),
          new Action(i18n.get("mapVault.removingArchiveIgnore"), Action.Type.OK_DONE, event -> {}),
          new Action(i18n.get("mapVault.removingArchiveDelete"), Action.Type.OK_DONE, event -> {
            try {
              logger.info("Deleting archive {} (a file of that name and crc can be found at {})", archivePath, finalCachedArchivePath2);
              Files.delete(archivePath);
            } catch (IOException e) {
              logger.error(String.format("[removeArchive] Unable to delete {}", archivePath), e);
            }
          }))
      ));
    }
    else {
      final Path finalCachedArchivePath = cachedArchivePath;
      notificationService.addNotification(new ImmediateNotification(
          i18n.get("mapVault.removingArchive", archivePath),
          i18n.get("mapVault.removingArchiveMoveTo", cachedArchivePath),
          Severity.INFO, Arrays.asList(
          new Action(i18n.get("mapVault.removingArchiveShow"), Action.Type.OK_STAY, event -> this.platformService.reveal(archivePath)),
          new Action(i18n.get("mapVault.removingArchiveIgnore"), Action.Type.OK_DONE, event -> {}),
          new Action(i18n.get("mapVault.removingArchiveDelete"), Action.Type.OK_DONE, event -> {
            try {
              logger.info("Moving archive {} to {})", archivePath, finalCachedArchivePath);
              Files.move(archivePath, finalCachedArchivePath);
            } catch (IOException e) {
              logger.error(String.format("[removeArchive] Unable to move {} to {}", archivePath, finalCachedArchivePath), e);
            }
          }))
      ));
    }
  }

  public CompletableFuture<MapBean> ensureMapLatestVersion(String modTechnical, MapBean map) {
    return ensureMap(modTechnical, map.getMapName(), null, null, null, null);
  }

  public CompletableFuture<MapBean> ensureMap(String modTechnicalName, MapBean map, @Nullable DoubleProperty progressProperty, @Nullable StringProperty titleProperty) {
    return map.getCrcFuture().thenCompose(mapCrc ->
        ensureMap(modTechnicalName, map.getMapName(), mapCrc, map.getHpiArchiveName(), progressProperty, titleProperty));
  }

  /// Set mapCrc to null to ensure the most recent version
  public CompletableFuture<MapBean> ensureMap(
      String modTechnical,
      @Nullable String mapName, @Nullable String mapCrc, @Nullable String downloadHpiArchiveName,
      @Nullable DoubleProperty progressProperty, @Nullable StringProperty titleProperty) {

    final MapBean installedVersion = getInstallation(modTechnical).mapsByName.getOrDefault(mapName, null);

    if (mapName != null && isOfficialMap(mapName)) {
      logger.info("[ensureMap] '{}'/{} is an official cavedog map, so taking no action", mapName, mapCrc);
      return CompletableFuture.completedFuture(installedVersion);
    }

    try {
      if (installedVersion != null && mapName != null && mapCrc != null && isInstalled(modTechnical, mapName, mapCrc).get()) {
        logger.info("[ensureMap] '{}'/{} is already installed", mapName, mapCrc);
        return CompletableFuture.completedFuture(installedVersion);
      }
    } catch (InterruptedException | ExecutionException e) {
      logger.warn("[ensureMap] exception while determining isInstalled() '{}'/{}.  Assuming the installed version matches", mapName, mapCrc);
      return CompletableFuture.completedFuture(installedVersion);
    }

    if (mapName == null) {
      return _ensureMap(modTechnical, mapName, mapCrc, downloadHpiArchiveName, progressProperty, titleProperty)
          .thenApply(aVoid -> null);
    }

    return findServerMapsByName(mapName)
        .thenCompose(knownServerVersions -> {
          boolean installedVersionIsKnown = installedVersion != null && knownServerVersions.stream()
              .anyMatch(serverMapVersion -> serverMapVersion.getCrcValue().equals(installedVersion.getCrcValue()));

          if (installedVersion != null && !installedVersionIsKnown) {
            logger.info("[ensureMap] Leaving '{}' in place as installed '{}'/{} is not known to server",
                installedVersion.getHpiArchiveName(), installedVersion.getMapName(), installedVersion.getCrcValue());
            return CompletableFuture.completedFuture(installedVersion);
          }

          MapBean latestVersion = knownServerVersions.stream()
              .filter(map -> !map.isHidden())
              .sorted((a, b) -> Objects.requireNonNull(a.getVersion()).compareTo(b.getVersion()))
              .reduce((a, b) -> b)
              .orElse(null);

          if (mapCrc == null && latestVersion != null) {
            try {
              if (isInstalled(modTechnical, latestVersion.getMapName(), latestVersion.getCrcValue()).get()) {
                logger.info("[ensureMap] '{}'/{} is the latest known version and is already installed", latestVersion.getMapName(), latestVersion.getCrcValue());
                return CompletableFuture.completedFuture(latestVersion);
              }
              else {
                return _ensureMap(modTechnical, latestVersion.getMapName(), latestVersion.getCrcValue(), latestVersion.getHpiArchiveName(), progressProperty, titleProperty)
                    .thenApply(aVoid -> latestVersion);
              }
            } catch (InterruptedException | ExecutionException e) {
              logger.warn("[ensureMap] exception while determining isInstalled() latest version '{}'/{}.  Assuming the installed version matches",
                  latestVersion.getMapName(), latestVersion.getCrcValue());
            }
          }
          return _ensureMap(modTechnical, mapName, mapCrc, downloadHpiArchiveName, progressProperty, titleProperty)
              .thenApply(aVoid -> null);
        });
  }

  /// @note even if mapName etc are null, will still check for TA_features_2013.ccx
  private CompletableFuture<Void> _ensureMap(
      String modTechnicalName,
      @Nullable String mapName, @Nullable String mapCrc, @Nullable String downloadHpiArchiveName,
      @Nullable DoubleProperty progressProperty, @Nullable StringProperty titleProperty) {

    Path installationPath = preferencesService.getTotalAnnihilation(modTechnicalName).getInstalledPath();
    List<String> downloadList = new ArrayList<>();
    if (!Files.exists(installationPath.resolve(HPI_ARCHIVE_TA_FEATURES_2013))) {
      downloadList.add(HPI_ARCHIVE_TA_FEATURES_2013);
    }

    addInstalledMapsUpdateDeferal();

    // make a copy of this list before we do anything to the installation
    HashMap<String, MapBean> alreadyInstalledForModAllMaps = new HashMap<>(getInstallation(modTechnicalName).mapsByName);

    if (mapName != null && mapCrc != null && downloadHpiArchiveName != null) {
      List<Pair<Installation,MapBean>> alreadyInstalledAnywhere = installations.values().stream()
          .map(installation -> new Pair<>(installation, installation.mapsByName.getOrDefault(mapName, null)))
          .filter(pair -> pair.getValue() != null)
          .filter(pair -> pair.getValue().getMapName().equals(mapName))
          .filter(pair -> pair.getValue().getCrcValue().equals("00000000") || pair.getValue().getCrcValue().equals(mapCrc))
          .sorted((a,b) -> b.getValue().getCrcValue().compareTo(a.getValue().getCrcValue()))  // those with non-zero crc first
          .collect(Collectors.toList());

      List<Pair<Installation,MapBean>> alreadyInstalledForMod = alreadyInstalledAnywhere.stream()
          .filter(pair -> pair.getKey().modTechnicalName.equals(modTechnicalName))
          .collect(Collectors.toList());

      if (!alreadyInstalledForMod.isEmpty()) {
        MapBean installedMap = alreadyInstalledForMod.get(0).getValue();
        logger.info("{}/{}/{} already installed",
            installedMap.getHpiArchiveName(), installedMap.getMapName(), installedMap.getCrcValue());

      } else if (!alreadyInstalledAnywhere.isEmpty()) {
        String donorModName = alreadyInstalledAnywhere.get(0).getKey().modTechnicalName;
        String donatedArchiveName = alreadyInstalledAnywhere.get(0).getValue().getHpiArchiveName();
        Path source = preferencesService.getTotalAnnihilation(donorModName).getInstalledPath().resolve(donatedArchiveName);
        Path dest = installationPath.resolve(donatedArchiveName);
        try {
          logger.info("Installing {}/{}: link/copied {} to {}", mapName, mapCrc, source, dest);
          linkOrCopyWithBackup(source, dest);
          removeConflictingArchives(modTechnicalName, alreadyInstalledForModAllMaps, dest);
          if (!preferencesService.getPreferences().isGameDataMapDownloadKeepVersionTag()) {
            removeVersionTag(dest.toFile());
          }
          resetPreviews(mapName);
          loadInstalledMaps(modTechnicalName);
        } catch (IOException ex) {
          logger.info("Unable to link/copy {} to {}: {}", source, dest, ex.getMessage());
          notificationService.addNotification(new ImmediateNotification(
              i18n.get("mapDownloadTask.title", downloadHpiArchiveName, "?"),
              "Unable to link/copy map pack into your installation!",
              Severity.WARN, Collections.singletonList(new DismissAction(i18n))));
        }
      }
      else {
        downloadList.add(downloadHpiArchiveName);
      }
    }

    if (downloadList.isEmpty()) {
      releaseInstalledMapsUpdateDeferal();
      return CompletableFuture.completedFuture(null);
    }

    downloadList = downloadList.stream()
        .filter(archive -> !getInstallation(modTechnicalName).downloadingList.contains(archive))
        .collect(Collectors.toList());
    getInstallation(modTechnicalName).downloadingList.addAll(downloadList);

    if (downloadList.isEmpty()) {
      logger.info("[ensureMap] Dude, hold up! {} is already downloading", downloadHpiArchiveName);
      releaseInstalledMapsUpdateDeferal();
      return CompletableFuture.completedFuture(null);
    }

    CompletableFuture<Void> future =
        downloadAndInstallArchive(modTechnicalName, downloadList.get(0), progressProperty, titleProperty);
    if (downloadList.size() > 1) {
      future = future.thenCompose(aVoid ->
          downloadAndInstallArchive(modTechnicalName, downloadHpiArchiveName, progressProperty, titleProperty));
    }

    if (downloadList.stream().anyMatch(archiveName -> archiveName.equals(downloadHpiArchiveName))) {
      future = future.thenRun(() -> {
        removeConflictingArchives(
            modTechnicalName, alreadyInstalledForModAllMaps, installationPath.resolve(downloadHpiArchiveName));
        getInstallation(modTechnicalName).downloadingList.removeIf(
            archive -> Arrays.asList(downloadHpiArchiveName, HPI_ARCHIVE_TA_FEATURES_2013).contains(archive));
        if (!preferencesService.getPreferences().isGameDataMapDownloadKeepVersionTag()) {
          removeVersionTag(installationPath.resolve(downloadHpiArchiveName).toFile());
        }
        resetPreviews(mapName);
        loadInstalledMaps(modTechnicalName);
      });
    }

    return future
        .thenRun(this::releaseInstalledMapsUpdateDeferal);
  }

  public CompletableFuture<MapBean> optionalEnsureMapLatestVersion(String modTechnical, MapBean map) {
    if (preferencesService.getPreferences().isGameDataMapManagementEnabled()) {
      return ensureMapLatestVersion(modTechnical, map);
    }
    else {
      return CompletableFuture.completedFuture(map);
    }
  }

  public CompletableFuture<MapBean> optionalEnsureMap(String modTechnicalName, MapBean map, @Nullable DoubleProperty progressProperty, @Nullable StringProperty titleProperty) {
    if (preferencesService.getPreferences().isGameDataMapManagementEnabled()) {
      return ensureMap(modTechnicalName, map, progressProperty, titleProperty);
    }
    else {
      return CompletableFuture.completedFuture(map);
    }
  }

  public CompletableFuture<MapBean> optionalEnsureMap(
      String modTechnical,
      @Nullable String mapName, @Nullable String mapCrc, @Nullable String downloadHpiArchiveName,
      @Nullable DoubleProperty progressProperty, @Nullable StringProperty titleProperty) {
    if (preferencesService.getPreferences().isGameDataMapManagementEnabled()) {
      return ensureMap(modTechnical, mapName, mapCrc, downloadHpiArchiveName, progressProperty, titleProperty);
    }
    else {
      return CompletableFuture.completedFuture(null);
    }
  }

  public CompletableFuture<Void> downloadAndInstallArchive(String modTechnical, String hpiArchiveName) {
    return downloadAndInstallArchive(modTechnical, hpiArchiveName, null, null);
  }

  private CompletableFuture<Void> downloadAndInstallArchive(String modTechnicalName, String hpiArchive, @Nullable DoubleProperty progressProperty, @Nullable StringProperty titleProperty) {
    logger.info("downloadAndInstallArchive {}", hpiArchive);

    URL downloadUrl = getDownloadUrl(hpiArchive, mapDownloadUrlFormat);
    Path mapsDirectory = preferencesService.getTotalAnnihilation(modTechnicalName).getInstalledPath();
    if (mapsDirectory == null) {
      logger.warn(String.format("Could not load maps: installation path is not set for mod: %s",modTechnicalName));
      return CompletableFuture.completedFuture(null);
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

    try {
      MapTool.generatePreview(gamePath, mapName, cachedFile.getParent().getParent(), previewType, maxPositions);
    }
    catch (IOException e) {
      notifyBadMapTool(e);
    }
  }

  private void notifyBadMapTool(Throwable e) {
    logger.error(e.getMessage());
    synchronized (notifiedBadMapToolLock) {
      if (!notifiedBadMapTool) {
        notificationService.addImmediateErrorNotification(e, "maptool.error");
        notifiedBadMapTool = true;
      }
    }
  }

  /**
   * Loads the preview of a map or returns a "unknown map" image.
   */

  @SneakyThrows()
  @NotNull
  @Cacheable(value = CacheNames.MAP_PREVIEW)
  public Image loadPreview(String modTechnicalName, String mapName, PreviewType previewType, int maxPositions) {
    return loadPreview(modTechnicalName, mapName, getPreviewUrl(mapName, mapPreviewUrlFormat, previewType), previewType, maxPositions);
  }

  @Cacheable(value = CacheNames.MAP_PREVIEW)
  public Image loadPreview(String modTechnical, MapBean map, PreviewType previewType, int maxPositions) {
    URL url = map.getThumbnailUrl();
    return loadPreview(modTechnical, map.getMapName(), url, previewType, maxPositions);
  }

  private Image loadPreview(String modTechnical, String mapName, URL url, PreviewType previewType, int maxPositions) {
    Path cacheDir = preferencesService.getCacheDirectory().resolve("maps").resolve(previewType.getFolderName(maxPositions));
    Path cachedFile = cacheDir.resolve(mapName+".png");
    generatePreview(modTechnical, mapName, cachedFile, previewType, maxPositions);
    return assetService.loadAndCacheImage(url, cacheDir, null);
  }

  private CompletableFuture<Image> loadPreviewFuture(String modTechnical, String mapName, URL url, PreviewType previewType, int maxPositions) {
    CompletableFuture<Image> f = new CompletableFuture<Image>();
    Path cacheDir = preferencesService.getCacheDirectory().resolve("maps").resolve(previewType.getFolderName(maxPositions));
    Path cachedFile = cacheDir.resolve(mapName+".png");
    generatePreview(modTechnical, mapName, cachedFile, previewType, maxPositions);
    Image im = assetService.loadAndCacheImage(url, cacheDir, null);
    if (im == null) {
      f.complete(null);
      return f;
    }

    im.errorProperty().addListener((obs, oldValue, newValue) -> f.complete(uiService.getThemeImage(UiService.UNKNOWN_MAP_IMAGE)));
    im.progressProperty().addListener((obs, oldValue, newValue) -> {
      if (newValue.intValue() >= 1 && !f.isDone()) {
        f.complete(im);
      }
    });

    return f;
  }


  @CacheEvict(value = CacheNames.MAP_PREVIEW, allEntries = true)
  public void resetPreviews(String mapName) {
    for (PreviewType previewType: PreviewType.values()) {
      for (int maxPositions=2; maxPositions<=10; ++maxPositions) {
        resetPreview(getPreviewUrl(mapName, mapPreviewUrlFormat, previewType), previewType, maxPositions);
      }
    }
  }

  private void resetPreview(URL url, PreviewType previewType, int maxPositions) {
    String urlString = url.toString();
    try {
      urlString = java.net.URLDecoder.decode(url.toString(), StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException ignored) { }

    String cachedFilename = urlString.substring(urlString.lastIndexOf('/') + 1);
    Path cacheSubFolder = Paths.get("maps").resolve(previewType.getFolderName(maxPositions));
    Path cachedFile = preferencesService.getCacheDirectory().resolve(cacheSubFolder).resolve(cachedFilename);
    try {
      Files.deleteIfExists(cachedFile);
    } catch (IOException e) {
      logger.error("Unable to delete cached preview {}", cachedFile);
    }
  }

  public CompletableFuture<Void> uninstallMap(String modTechnicalName, String mapName, String mapCrc) {
    if (isOfficialMap(mapName)) {
      throw new IllegalArgumentException("Attempt to uninstall an official map");
    }

    try {
      if (!isInstalled(modTechnicalName, mapName, mapCrc).get()) {
        return CompletableFuture.completedFuture(null);
      }
    } catch (InterruptedException | ExecutionException e) {
      logger.warn("[uninstallMap] exception trying to uninstall map {}/{}", mapName, mapCrc);
      return CompletableFuture.completedFuture(null);
    }

    Path mapsDirectory = preferencesService.getTotalAnnihilation(modTechnicalName).getInstalledPath();
    Installation installation = installations.get(modTechnicalName);
    MapBean installedMap = installation.mapsByName.get(mapName);

    UninstallMapTask task = applicationContext.getBean(UninstallMapTask.class);
    task.setInstallationPath(mapsDirectory);
    task.setHpiArchiveName(installedMap.getHpiArchiveName());
    return taskService.submitTask(task).getFuture();
  }

  public CompletableTask<Void> uploadMap(Path stagingDirectory, String archiveFileName, boolean ranked, List<Map<String,String>> mapDetails) {
    MapUploadTask mapUploadTask = applicationContext.getBean(MapUploadTask.class);
    mapUploadTask.setArchiveFileName(archiveFileName);
    mapUploadTask.setStagingDirectory(stagingDirectory);
    mapUploadTask.setRanked(ranked);
    mapUploadTask.setMapDetails(mapDetails);

    return taskService.submitTask(mapUploadTask);
  }

  @CacheEvict(CacheNames.MAPS)
  public void evictCache() {
    // Nothing to see here
  }

  /**
   * Tries to find a map by its folder name, first locally then on the server.
   */
  public CompletableFuture<Optional<MapBean>> findMapByName(String modTechnical, String displayName) {
    Optional<MapBean> installed = getMapLocallyFromName(modTechnical, displayName);
    if (installed.isPresent()) {
      return CompletableFuture.completedFuture(installed);
    }
    return fafService.findMapByName(displayName);
  }

  public CompletableFuture<List<MapBean>> findServerMapsByName(String displayName) {
    return fafService.findMapsByName(displayName);
  }

  public CompletableFuture<Optional<MapBean>> getMapLatestVersion(String mapDisplayName) {
    return fafService.getMapLatestVersion(mapDisplayName);
  }

  public CompletableFuture<Optional<MapBean>> findMapVersion(String displayName, String crc) {
    return fafService.findMapVersion(displayName, crc);
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

  public CompletableFuture<Tuple<List<MapBean>, Integer>> getMatchmakerMapsWithPageCount(MatchmakingQueue matchmakerQueue, int count, int page) {
    Player player = playerService.getCurrentPlayer().orElseThrow(() -> new IllegalStateException("No user is logged in"));
    float meanRating = Optional.ofNullable(player.getLeaderboardRatings().get(matchmakerQueue.getLeaderboard().getTechnicalName()))
        .map(LeaderboardRating::getMean).orElse(0f);
    return fafService.getMatchmakerMapsWithPageCount(matchmakerQueue.getQueueId(), meanRating, count, page);
  }

  public CompletableFuture<List<MapBean>> getMatchmakerMaps(MatchmakingQueue matchmakerQueue) {
    Player player = playerService.getCurrentPlayer().orElseThrow(() -> new IllegalStateException("No user is logged in"));
    float meanRating = Optional.ofNullable(player.getLeaderboardRatings().get(matchmakerQueue.getLeaderboard().getTechnicalName()))
        .map(LeaderboardRating::getMean).orElse(0f);
    return fafService.getMatchmakerMaps(matchmakerQueue.getQueueId(), meanRating);
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
    return fafService.unRankMapVersion(map);
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
