package com.faforever.client.replay;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.discord.DiscordSpectateEvent;
import com.faforever.client.fa.DemoFile;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameService;
import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.i18n.I18n;
import com.faforever.client.io.DownloadService;
import com.faforever.client.main.event.ShowTadaReplayEvent;
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapService;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.CopyErrorAction;
import com.faforever.client.notification.DismissAction;
import com.faforever.client.notification.GetHelpAction;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.PersistentNotification;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.tada.event.UploadToTadaEvent;
import com.faforever.client.task.TaskService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.Tuple;
import com.faforever.client.vault.search.SearchController.SortConfig;
import com.faforever.client.vault.search.SearchController.SortOrder;
import com.github.rutledgepaulv.qbuilders.conditions.Condition;
import com.github.rutledgepaulv.qbuilders.visitors.RSQLVisitor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.Bytes;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.faforever.client.notification.Severity.WARN;
import static com.faforever.commons.api.elide.ElideNavigator.qBuilder;
import static com.github.nocatch.NoCatch.noCatch;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.move;
import static java.util.Collections.singletonList;

@Lazy
@Service
@Slf4j
@RequiredArgsConstructor
public class ReplayService implements InitializingBean {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * Byte offset at which a SupCom replay's version number starts.
   */
  private static final int VERSION_OFFSET = 0x18;
  private static final int MAP_NAME_OFFSET = 0x2D;
  private static final byte[] MAP_FOLDER_START_PATTERN = new byte[]
      {0x53, 0x63, 0x65, 0x6E, 0x61, 0x72, 0x69, 0x6F, 0x46, 0x69, 0x6C, 0x65, 0x00, 0x01};
  private static final String TAD_REPLAY_FILE_ENDING = ".tad";
  private static final String FAF_REPLAY_FILE_ENDING = ".fafreplay";
  private static final String SUP_COM_REPLAY_FILE_ENDING = ".scfareplay";
  private static final String TAF_LIFE_PROTOCOL = "taflive";
  private static final String GPGNET_SCHEME = "gpgnet";
  private static final String TEMP_SCFA_REPLAY_FILE_NAME = "temp.scfareplay";
  private static final Pattern invalidCharacters = Pattern.compile("[?@*%{}<>|\"]");

  private final ClientProperties clientProperties;
  private final PreferencesService preferencesService;
  private final UserService userService;
  private final ReplayFileReader replayFileReader;
  private final NotificationService notificationService;
  private final DownloadService downloadService;
  private final GameService gameService;
  private final TaskService taskService;
  private final I18n i18n;
  private final ReportingService reportingService;
  private final ApplicationContext applicationContext;
  private final PlatformService platformService;
  private final FafService fafService;
  private final ModService modService;
  private final MapService mapService;
  private final EventBus eventBus;
  protected List<Replay> localReplays = new ArrayList<>();

  @Override
  public void afterPropertiesSet() {
    eventBus.register(this);
  }

  @VisibleForTesting
  static Integer parseSupComVersion(byte[] rawReplayBytes) {
    int versionDelimiterIndex = Bytes.indexOf(rawReplayBytes, (byte) 0x00);
    return Integer.parseInt(new String(rawReplayBytes, VERSION_OFFSET, versionDelimiterIndex - VERSION_OFFSET, US_ASCII));
  }

  @VisibleForTesting
  static String parseMapName(byte[] rawReplayBytes) {
    int mapDelimiterIndex = Bytes.indexOf(rawReplayBytes, new byte[]{0x00, 0x0D, 0x0A, 0x1A});
    String mapPath = new String(rawReplayBytes, MAP_NAME_OFFSET, mapDelimiterIndex - MAP_NAME_OFFSET, US_ASCII);
    Matcher matcher = invalidCharacters.matcher(mapPath);
    if (matcher.find()) {
      throw new IllegalArgumentException("Map Name Contains Invalid Characters");
    }
    return mapPath.split("/")[2];
  }

  @VisibleForTesting
  static String parseMapFolderName(byte[] rawReplayBytes) {
    // "ScenarioFile" in hex
    int mapStartIndex = Bytes.indexOf(rawReplayBytes, MAP_FOLDER_START_PATTERN) + MAP_FOLDER_START_PATTERN.length;
    int mapEndIndex = 0;

    for (mapEndIndex = mapStartIndex; mapEndIndex < rawReplayBytes.length - 1; mapEndIndex++) {
      //0x00 0x01 is the field delimiter
      if (rawReplayBytes[mapEndIndex] == 0x00 && rawReplayBytes[mapEndIndex + 1] == 0x01) {
        break;
      }
    }

    String mapPath = new String(rawReplayBytes, mapStartIndex, mapEndIndex + 1 - mapStartIndex, US_ASCII);
    //mapPath looks like /maps/my_awesome_map.v008/my_awesome_map.lua
    Matcher matcher = invalidCharacters.matcher(mapPath);
    if (matcher.find()) {
      throw new IllegalArgumentException("Map Name Contains Invalid Characters");
    }
    return mapPath.split("/")[2];
  }

  @VisibleForTesting
  static String guessModByFileName(String fileName) {
    String[] splitFileName = fileName.split("\\.");
    if (splitFileName.length > 2) {
      return splitFileName[splitFileName.length - 2];
    }
    return KnownFeaturedMod.DEFAULT.getTechnicalName();
  }

  @Async
  public CompletableFuture<Tuple<List<Replay>, Integer>> loadLocalReplayPage(int pageSize, int page) throws IOException {
    String replayFileGlob = clientProperties.getReplay().getReplayFileGlob();

    Path replaysDirectory = preferencesService.getReplaysDirectory();
    if (Files.notExists(replaysDirectory)) {
      noCatch(() -> createDirectories(replaysDirectory));
    }

    int skippedReplays = pageSize * (page - 1);

    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(replaysDirectory, replayFileGlob)) {
      Stream<Path> filesStream = StreamSupport.stream(directoryStream.spliterator(), false)
          .sorted(Comparator.comparing(path -> noCatch(() -> Files.getLastModifiedTime((Path) path))).reversed());

      List<Path> filesList = filesStream.collect(Collectors.toList());
      int numPages = filesList.size() / pageSize;

      List<CompletableFuture<Replay>> replayFutures = filesList.stream()
          .skip(skippedReplays)
          .limit(pageSize)
          .map(this::tryLoadingLocalReplay)
          .filter(e -> !e.isCompletedExceptionally())
          .collect(Collectors.toList());

      return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
          .thenApply(ignoredVoid ->
              replayFutures.stream()
                  .map(CompletableFuture::join)
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList()))
          .thenApply(replays -> new Tuple<>(replays, numPages));
    }
  }


  private CompletableFuture<Replay> tryLoadingLocalReplay(Path replayFile) {
    try {
      LocalReplayInfo replayInfo = replayFileReader.parseMetaData(replayFile);

      CompletableFuture<FeaturedMod> featuredModFuture = modService.getFeaturedMod(replayInfo.getFeaturedMod());
      FeaturedMod featuredMod = featuredModFuture.join();
      CompletableFuture<Optional<MapBean>> mapBeanFuture = mapService.findMapByName(featuredMod.getTechnicalName(), replayInfo.getMapname());

      return CompletableFuture.allOf(featuredModFuture, mapBeanFuture).thenApply(ignoredVoid -> {
        Optional<MapBean> mapBean = mapBeanFuture.join();
        if (mapBean.isEmpty()) {
          logger.warn("Could not find map for replay file '{}'", replayFile);
        }
        return new Replay(replayInfo, replayFile, featuredModFuture.join(), mapBean.orElse(null));
      });
    } catch (Exception e) {
      logger.warn("Could not read replay file '{}'", replayFile, e);
      moveCorruptedReplayFile(replayFile);
      return CompletableFuture.completedFuture(null);
    }
  }

  private void moveCorruptedReplayFile(Path replayFile) {
    Path corruptedReplaysDirectory = preferencesService.getCorruptedReplaysDirectory();
    noCatch(() -> createDirectories(corruptedReplaysDirectory));

    Path target = corruptedReplaysDirectory.resolve(replayFile.getFileName());

    logger.debug("Moving corrupted replay file from {} to {}", replayFile, target);

    try {
      move(replayFile, target);
    } catch (IOException e) {
      logger.warn("Failed to move corrupt replay to " + target, e);
      return;
    }

    notificationService.addNotification(new PersistentNotification(
        i18n.get("corruptedReplayFiles.notification"), WARN,
        singletonList(
            new Action(i18n.get("corruptedReplayFiles.show"), event -> platformService.reveal(replayFile))
        )
    ));
  }


  public void runLiveReplay(Game game) {
    URI uri = UriComponentsBuilder.newInstance()
        .scheme(TAF_LIFE_PROTOCOL)
        .host(clientProperties.getReplay().getRemoteHost())
        .port(clientProperties.getReplay().getReplayServerPort())
        .path("/" + game.getId())
        .build()
        .toUri();

    gameService.runWithReplay(uri.toString(), game)
        .exceptionally(throwable -> {
          notificationService.addNotification(new ImmediateNotification(
              i18n.get("errorTitle"),
              i18n.get("replayCouldNotBeStarted"),
              Severity.ERROR, throwable,
              List.of(new CopyErrorAction(i18n, reportingService, throwable), new GetHelpAction(i18n, reportingService), new DismissAction(i18n))
          ));
          return null;
        });
  }


  public void runDownloadReplay(Replay replay) {
    downloadReplay(replay.getId())
        .thenApply(replayFile -> DemoFile.sneakyGetInfo(replayFile.toString()))
        .thenAccept(this.gameService::runWithReplay)
        .exceptionally(throwable -> {
          if (throwable.getCause() instanceof FileNotFoundException) {
            log.warn("Replay not available on server yet", throwable);
            notificationService.addImmediateWarnNotification("replayNotAvailable", replay.getId());
          } else {
            log.error("Replay could not be started", throwable);
            notificationService.addImmediateErrorNotification(throwable, "replayCouldNotBeStarted", replay.getId());
          }
          return null;
        });
  }


  public CompletableFuture<Tuple<List<Replay>, Integer>> getNewestReplaysWithPageCount(int topElementCount, int page) {
    return fafService.getNewestReplaysWithPageCount(topElementCount, page);
  }

  public CompletableFuture<Tuple<List<Replay>, Integer>> getReplaysForPlayerWithPageCount(int playerId, int maxResults, int page, SortConfig sortConfig) {
    Condition<?> filterCondition = qBuilder().intNum("playerStats.player.id").eq(playerId);
    String query = filterCondition.query(new RSQLVisitor());
    return fafService.findReplaysByQueryWithPageCount(query, maxResults, page, sortConfig);
  }

  public CompletableFuture<Tuple<List<Replay>, Integer>> getHighestRatedReplaysWithPageCount(int topElementCount, int page) {
    return fafService.getHighestRatedReplaysWithPageCount(topElementCount, page);
  }

  public CompletableFuture<Tuple<List<Replay>, Integer>> findByQueryWithPageCount(String query, int maxResults, int page, SortConfig sortConfig) {
    return fafService.findReplaysByQueryWithPageCount(query, maxResults, page, sortConfig);
  }

  public CompletableFuture<Optional<Replay>> findById(int id) {
    return fafService.findReplayById(id);
  }

  public CompletableFuture<Path> downloadReplay(int id) {
    ReplayDownloadTask task = applicationContext.getBean(ReplayDownloadTask.class);
    task.setReplayId(Integer.toString(id));
    task.setDownloadPath(preferencesService.getCacheDirectory().resolve("replays").resolve(String.format("%s.tad", id)));
    return taskService.submitTask(task).getFuture();
  }

  /**
   * Reads the specified replay file in order to add more information to the specified replay instance.
   */
  public void enrich(Replay replay, Path path) {
    /*
    ReplayData replayData = replayFileReader.parseReplay(path);
    replay.getChatMessages().setAll(replayData.getChatMessages().stream()
        .map(chatMessage -> new ChatMessage(chatMessage.getTime(), chatMessage.getSender(), chatMessage.getMessage()))
        .collect(Collectors.toList())
    );
    replay.getGameOptions().setAll(replayData.getGameOptions().stream()
        .map(gameOption -> new GameOption(gameOption.getKey(), gameOption.getValue()))
        .collect(Collectors.toList())
    );
    */
  }


  @SneakyThrows
  public CompletableFuture<Integer> getSize(int id) {
    return CompletableFuture.supplyAsync(() -> noCatch(() -> new URL(String.format(clientProperties.getVault().getReplayDownloadUrlFormat(), id))
        .openConnection()
        .getContentLength()));
  }


  public boolean replayChangedRating(Replay replay) {
    return replay.getTeamPlayerStats().values().stream()
        .flatMap(Collection::stream)
        .anyMatch(playerStats -> playerStats.getAfterMean() != null && playerStats.getAfterDeviation() != null);
  }


  @EventListener
  public void onDiscordGameJoinEvent(DiscordSpectateEvent discordSpectateEvent) {
    Integer gameId = discordSpectateEvent.getReplayId();
    Game game = gameService.getByUid(gameId);
    if (game == null) {
      throw new RuntimeException("There's no game with ID: " + gameId);
    }
    runLiveReplay(game);
  }

  public CompletableFuture<Tuple<List<Replay>, Integer>> getOwnReplaysWithPageCount(int maxResults, int page) {
    SortConfig sortConfig = new SortConfig("startTime", SortOrder.DESC);
    return getReplaysForPlayerWithPageCount(userService.getUserId(), maxResults, page, sortConfig);
  }

  @Data class SignedUrl { private String signedUrl; };

  @Subscribe
  public void startReplay(ShowTadaReplayEvent event) throws MalformedURLException, UnsupportedEncodingException {

    Path downloadPath = preferencesService.getCacheDirectory().resolve("replays").resolve(event.getFilename());

    CompletableFuture<Path> downloadedReplayPathFuture;
    if (Files.exists(Path.of(event.getFilename()))) {
      logger.info("Local replay file: {}", event.getTadaReplayId());
      downloadedReplayPathFuture = CompletableFuture.completedFuture(Path.of(event.getFilename()));
    }
    else if (downloadPath.toFile().exists()) {
      logger.info("Replay {} already exists at {}", event.getFilename(), downloadPath);
      downloadedReplayPathFuture = CompletableFuture.completedFuture(downloadPath);
    }
    else {
      URL tadaDownloadEndpoint = new URL(String.format(
          clientProperties.getTada().getReplayDownloadEndpointFormat(),
          event.getKey(),
          event.getTadaReplayId(),
          URLEncoder.encode(event.getFilename(), StandardCharsets.UTF_8.toString())
      ));

      downloadedReplayPathFuture = taskService.submitTask(applicationContext.getBean(ReplayDownloadTask.class)
          .setReplayId(event.getTadaReplayId())
          .setReplayUrl(tadaDownloadEndpoint)
          .setDownloadPath(downloadPath))
          .getFuture();
    }

    downloadedReplayPathFuture
        .thenApply(replayFile -> DemoFile.sneakyGetInfo(replayFile.toString()))
        .thenCompose(this.gameService::runWithReplay)
        .exceptionally(throwable -> {
          if (throwable.getCause() instanceof FileNotFoundException) {
            log.warn("Replay not available on server yet", throwable);
            notificationService.addImmediateWarnNotification("replayNotAvailable", event.getTadaReplayId());
          } else {
            log.error("Replay could not be started", throwable);
            notificationService.addImmediateErrorNotification(throwable, "replayCouldNotBeStarted", event.getTadaReplayId());
          }
          return null;
        });
  }

  public boolean uploadReplayToTadaPermitted(Replay replay) {
    if (replay == null) {
      return false;
    }

    if (replay.getId() > 6000 && replay.getId() < 9340) {
      return false;
    }

    if (replay.getTadaAvailable()) {
      return false;
    }

    List<String> players = replay.getTeams().values().stream()
        .reduce(new ArrayList<>(), (a,b) -> {
          a.addAll(b);
          return a;
        });

    boolean result = players.contains(userService.getUsername());
    return result;
  }

  public void uploadReplayToTada(Integer replayId) {
    this.fafService.uploadReplayToTada(replayId);
  }

  @Subscribe
  public void onUploadToTadaEvent(UploadToTadaEvent event) {
    uploadReplayToTada(event.getReplayId());
  }
}
