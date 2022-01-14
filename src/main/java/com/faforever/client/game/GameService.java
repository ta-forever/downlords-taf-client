package com.faforever.client.game;

import com.faforever.client.chat.ChatService;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.discord.DiscordRichPresenceService;
import com.faforever.client.fa.CloseGameEvent;
import com.faforever.client.fa.DemoFileInfo;
import com.faforever.client.fa.MapTool;
import com.faforever.client.fa.TotalAnnihilationService;
import com.faforever.client.fa.relay.event.AutoJoinRequestEvent;
import com.faforever.client.fa.relay.event.RehostRequestEvent;
import com.faforever.client.fa.relay.ice.IceAdapter;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.JoinChannelEvent;
import com.faforever.client.main.event.ShowReplayEvent;
import com.faforever.client.map.MapService;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.ModService;
import com.faforever.client.net.ConnectionState;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.PersistentNotification;
import com.faforever.client.notification.Severity;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.UserOfflineEvent;
import com.faforever.client.preferences.AutoUploadLogsOption;
import com.faforever.client.preferences.NotificationsPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.ReconnectTimerService;
import com.faforever.client.remote.domain.GameInfoMessage;
import com.faforever.client.remote.domain.GameLaunchMessage;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.remote.domain.GameType;
import com.faforever.client.remote.domain.LoginMessage;
import com.faforever.client.replay.Replay;
import com.faforever.client.replay.ReplayServer;
import com.faforever.client.tada.event.UploadToTadaEvent;
import com.faforever.client.task.ResourceLocks;
import com.faforever.client.ui.preferences.event.GameDirectoryChooseEvent;
import com.faforever.client.util.RatingUtil;
import com.faforever.client.util.TimeUtil;
import com.faforever.client.util.ZipUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.github.nocatch.NoCatch.noCatch;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Downloads necessary maps, mods and updates before starting
 */
@Lazy
@Service
@Slf4j
@RequiredArgsConstructor
public class GameService implements InitializingBean {

  public static final String DEFAULT_RATING_TYPE = "global";
  public static final String CUSTOM_GAME_CHANNEL_REGEX = "^#.+\\[.+\\]$";

  /**
   * An observable copy of {@link #uidToGameInfoBean}. <strong>Do not modify its content directly</strong>.
   */
  private final ObservableMap<Integer, Game> uidToGameInfoBean;

  private final ClientProperties clientProperties;
  private final FafService fafService;
  private final TotalAnnihilationService totalAnnihilationService;
  private final MapService mapService;
  private final PreferencesService preferencesService;
  private final NotificationService notificationService;
  private final I18n i18n;
  private final ExecutorService executorService;
  private final PlayerService playerService;
  private final EventBus eventBus;
  private final IceAdapter iceAdapter;
  private final ModService modService;
  private final PlatformService platformService;
  private final DiscordRichPresenceService discordRichPresenceService;
  private final ReplayServer replayServer;
  private final ReconnectTimerService reconnectTimerService;
  private final ChatService chatService;

  private final ObservableList<Game> games;
  private final String faWindowTitle;
  private final String ircHostAndPort;
  private final BooleanProperty inMatchmakerQueue;
  private final BooleanProperty inOthersParty;

  @VisibleForTesting
  final IntegerProperty runningGameUidProperty;   // as determined locally

  /** TODO: Explain why access needs to be synchronized. */
  @VisibleForTesting
  final SimpleObjectProperty<Game> currentGame;   // as indicated by server

  // Set to SPAWNING when game process is started by OS
  // Then udpated in response to server GameInfo messages
  // And then set to UNKNOWN when currentGame is set to null
  final SimpleObjectProperty<GameStatus> currentGameStatusProperty;

  @VisibleForTesting
  String matchedQueueRatingType;
  private Process process;
  private Optional<Game> rehostRequested;

  @Inject
  public GameService(ClientProperties clientProperties,
                     FafService fafService,
                     TotalAnnihilationService totalAnnihilationService,
                     MapService mapService,
                     PreferencesService preferencesService,
                     NotificationService notificationService,
                     I18n i18n,
                     ExecutorService executorService,
                     PlayerService playerService,
                     EventBus eventBus,
                     IceAdapter iceAdapter,
                     ModService modService,
                     PlatformService platformService,
                     DiscordRichPresenceService discordRichPresenceService,
                     ReplayServer replayServer,
                     ReconnectTimerService reconnectTimerService,
                     ChatService chatService) {

    this.clientProperties = clientProperties;
    this.fafService = fafService;
    this.totalAnnihilationService = totalAnnihilationService;
    this.mapService = mapService;
    this.preferencesService = preferencesService;
    this.notificationService = notificationService;
    this.i18n = i18n;
    this.executorService = executorService;
    this.playerService = playerService;
    this.eventBus = eventBus;
    this.iceAdapter = iceAdapter;
    this.modService = modService;
    this.platformService = platformService;
    this.discordRichPresenceService = discordRichPresenceService;
    this.replayServer = replayServer;
    this.reconnectTimerService = reconnectTimerService;
    this.chatService = chatService;

    ircHostAndPort = String.format("%s:%d", clientProperties.getIrc().getHost(), 6667);//clientProperties.getIrc().getPort());
    faWindowTitle = clientProperties.getForgedAlliance().getWindowTitle();
    uidToGameInfoBean = FXCollections.observableMap(new ConcurrentHashMap<>());
    inMatchmakerQueue = new SimpleBooleanProperty(false);
    inOthersParty = new SimpleBooleanProperty(false);
    runningGameUidProperty = new SimpleIntegerProperty();
    currentGame = new SimpleObjectProperty<>();
    currentGameStatusProperty = new SimpleObjectProperty<>();

    games = FXCollections.observableList(new ArrayList<>(),
        item -> new Observable[]{item.statusProperty(), item.getTeams()}
    );
  }

  @Override
  public void afterPropertiesSet() {
    currentGame.addListener((observable, oldValue, newValue) -> {
      JavaFxUtil.assertApplicationThread();
      Player currentPlayer = playerService.getCurrentPlayer().get();

      if (newValue == null) {
        discordRichPresenceService.clearGameInfo();
        currentGameStatusProperty.setValue(GameStatus.UNKNOWN);

        if (currentPlayer != null && currentPlayer.getStatus() != PlayerStatus.PLAYING) {
          // this is here to cope with host leaves before player starts TA
          // In that case gpgnet4ta is still waiting for TA to start, so it won't shutdown unless explicitly told to
          killGame();
        }

        return;
      }

      if (newValue.getGameType() != GameType.MATCHMAKER) {
        String newGameChannel = getInGameIrcChannel(newValue);
        Set<String> userChannels = chatService.getUserChannels(currentPlayer.getUsername());
        userChannels.stream()
            .filter(channel -> channel.matches(CUSTOM_GAME_CHANNEL_REGEX) && !channel.equals(newGameChannel))
            .forEach(channel -> chatService.leaveChannel(channel));
        userChannels.stream()
            .filter(channel -> channel.matches(CUSTOM_GAME_CHANNEL_REGEX) && channel.equals(newGameChannel))
            .findAny()
            .ifPresentOrElse((String) -> {
            }, () -> eventBus.post(new JoinChannelEvent(newGameChannel)));
      }

      InvalidationListener listener = generateNumberOfPlayersChangeListener(newValue);
      JavaFxUtil.addListener(newValue.numPlayersProperty(), listener);
      listener.invalidated(newValue.numPlayersProperty());

      ChangeListener<GameStatus> statusChangeListener = generateGameStatusListener(newValue);
      JavaFxUtil.addListener(newValue.statusProperty(), statusChangeListener);
      statusChangeListener.changed(newValue.statusProperty(), newValue.getStatus(), newValue.getStatus());
    });

    JavaFxUtil.attachListToMap(games, uidToGameInfoBean);
    JavaFxUtil.addListener(
        runningGameUidProperty,
        (observable, oldValue, newValue) -> reconnectTimerService.setGameRunning(newValue != null)
    );

    eventBus.register(this);

    fafService.addOnMessageListener(GameInfoMessage.class, message -> {
      JavaFxUtil.runLater(() -> onGameInfo(message));
    });
    fafService.addOnMessageListener(LoginMessage.class, message -> onLoggedIn());

    JavaFxUtil.addListener(
        fafService.connectionStateProperty(),
        (observable, oldValue, newValue) -> {
          if (newValue == ConnectionState.DISCONNECTED) {
            synchronized (uidToGameInfoBean) {
              uidToGameInfoBean.clear();
            }
          }
        }
    );
  }

  @NotNull
  private InvalidationListener generateNumberOfPlayersChangeListener(Game game) {
    return new InvalidationListener() {
      @Override
      public void invalidated(Observable observable) {
        Game currentGame = getCurrentGame();
        if (currentGame == null || !Objects.equals(game, currentGame)) {
          observable.removeListener(this);
          return;
        }
        final Player currentPlayer = playerService.getCurrentPlayer().orElseThrow(() -> new IllegalStateException("Player must be set"));
        discordRichPresenceService.updatePlayedGameTo(currentGame, currentPlayer.getId(), currentPlayer.getUsername());

        if (currentPlayer.getStatus() == PlayerStatus.JOINING && currentGame.getStatus() == GameStatus.BATTLEROOM && preferencesService.getPreferences().getAutoLaunchOnJoinEnabled()) {
          GameService.this.startBattleRoom();
        }
      }
    };
  }

  @NotNull
  private ChangeListener<GameStatus> generateGameStatusListener(Game game) {
    return new ChangeListener<>() {
      @Override
      public void changed(ObservableValue<? extends GameStatus> observable, GameStatus oldStatus, GameStatus newStatus) {
        if (observable.getValue() == GameStatus.ENDED) {
          observable.removeListener(this);
        }

        Player currentPlayer = getCurrentPlayer();
        Game currentGame = getCurrentGame();
        boolean playerStillInGame = currentPlayer != null && currentGame != null && currentPlayer.getCurrentGameUid() == currentGame.getId();
        /*game.getTeams().entrySet().stream()
            .flatMap(stringListEntry -> stringListEntry.getValue().stream())
            .anyMatch(playerName -> playerName.equals(currentPlayer.getUsername()));*/

        /*
          Check if player left the game while it was open, in this case we don't care any longer
         */
        if (newStatus.isInProgress() && oldStatus.isOpen() && !playerStillInGame) {
          observable.removeListener(this);
          return;
        }

        if (oldStatus.isInProgress() && newStatus == GameStatus.ENDED) {
          GameService.this.notifyRecentlyPlayedGameEnded(game);
        }

        if (Objects.equals(currentGame, game) && currentGameStatusProperty.get() != newStatus) {
          currentGameStatusProperty.setValue(newStatus);
        }

        if (newStatus == GameStatus.BATTLEROOM && Objects.equals(currentGame, game)) {
          discordRichPresenceService.updatePlayedGameTo(currentGame, currentPlayer.getId(), currentPlayer.getUsername());
          if (currentPlayer.getStatus() == PlayerStatus.JOINING && preferencesService.getPreferences().getAutoLaunchOnJoinEnabled()) {
            GameService.this.startBattleRoom();
          }
        }

        if (preferencesService.getPreferences().getAutoRehostEnabled() &&
            newStatus == GameStatus.BATTLEROOM &&
            game.getGameType() != GameType.MATCHMAKER &&
            currentPlayer != null && currentPlayer.getUsername().equals(game.getHost())) {
          eventBus.post(new RehostRequestEvent(game));
        }
      }
    };
  }

  public ReadOnlyIntegerProperty runningGameUidProperty() {
    return runningGameUidProperty;
  }

  public CompletableFuture<Void> hostGame(NewGameInfo newGameInfo) {
    log.info("[hostGame] title={}", newGameInfo.getTitle());

    if (isGameRunning()) {
      log.debug("Game is running, ignoring host request");
      notificationService.addImmediateWarnNotification("game.gameRunning");
      return completedFuture(null);
    }

    String modTechnicalName = newGameInfo.getFeaturedMod().getTechnicalName();
    if (!preferencesService.isGameExeValid(modTechnicalName)) {
      CompletableFuture<Path> gameDirectoryFuture = postGameDirectoryChooseEvent(modTechnicalName);
      return gameDirectoryFuture.thenCompose(path -> hostGame(newGameInfo));
    }

    if (inMatchmakerQueue.get()) {
      addAlreadyInQueueNotification();
      return completedFuture(null);
    }
    String inGameChannel = getInGameIrcChannel(newGameInfo);
    String inGameIrcUrl = getInGameIrcUrl(inGameChannel);
    boolean autoLaunch = preferencesService.getPreferences().getAutoLaunchOnHostEnabled();

    autoJoinRequestedGameProperty.set(null);
    return updateGameIfNecessary(newGameInfo.getFeaturedMod(), null, Map.of(), newGameInfo.getSimMods())
        .thenCompose(aVoid -> fafService.requestHostGame(newGameInfo))
        .thenAccept(gameLaunchMessage -> startGame(gameLaunchMessage, inGameIrcUrl, autoLaunch, playerService.getCurrentPlayer().get().getUsername()));
  }

  private void addAlreadyInQueueNotification() {
    notificationService.addImmediateWarnNotification("teammatchmaking.notification.customAlreadyInQueue.message");
  }

  public CompletableFuture<Void> joinGame(Game game, String password) {
    if (isGameRunning()) {
      log.debug("Game is running, ignoring join request");
      notificationService.addImmediateWarnNotification("game.gameRunning");
      return completedFuture(null);
    }

    if (!preferencesService.isGameExeValid(game.getFeaturedMod())) {
      CompletableFuture<Path> gameDirectoryFuture = postGameDirectoryChooseEvent(game.getFeaturedMod());
      return gameDirectoryFuture.thenCompose(path -> joinGame(game, password));
    }

    if (inMatchmakerQueue.get()) {
      addAlreadyInQueueNotification();
      return completedFuture(null);
    }

    log.info("Joining game: '{}' ({})", game.getTitle(), game.getId());

    String inGameIrcChannel = getInGameIrcChannel(game);
    String inGameIrcUrl = getInGameIrcUrl(inGameIrcChannel);
    Map<String, Integer> featuredModVersions = game.getFeaturedModVersions();
    Set<String> simModUIds = game.getSimMods().keySet();
    autoJoinRequestedGameProperty.set(null);
    setRunningGameUid(game.getId());  // set it early so create-game button disabled during setup
    return
        modService.getFeaturedMod(game.getFeaturedMod())
        .thenCompose(featuredModBean -> updateGameIfNecessary(featuredModBean, null, featuredModVersions, simModUIds))
        .thenCompose(aVoid -> mapService.ensureMap(game.getFeaturedMod(), game.getMapName(), game.getMapCrc(), game.getMapArchiveName(), null, null))
        .exceptionally(throwable -> {
          log.warn("Exception preparing to join game", throwable);
          notificationService.addImmediateErrorNotification(throwable, "games.errorInPreparing");
          return null;
        })
        .thenCompose(aVoid -> {
          if (game.isPasswordProtected()) {
            setRunningGameUid(null);  // we don't get an exception or anything if password is wrong
          }
          return fafService.requestJoinGame(game.getId(), password);
          })
        .thenAccept(gameLaunchMessage -> {
            // Store password in case we rehost
            game.setPassword(password);
            setRunningGameUid(game.getId());
            boolean autoLaunch = preferencesService.getPreferences().getAutoLaunchOnJoinEnabled() && game.getStatus() == GameStatus.BATTLEROOM;
            startGame(gameLaunchMessage, inGameIrcUrl, autoLaunch, playerService.getCurrentPlayer().get().getUsername());
        })
        .exceptionally(throwable -> {
          log.warn("Game could not be joined", throwable);
          notificationService.addImmediateErrorNotification(throwable, "games.couldNotJoin");
          setRunningGameUid(null);
          return null;
        });
  }

  public CompletableFuture<Void> runWithReplay(DemoFileInfo demoFileInfo) {
    final FeaturedMod mod[] = new FeaturedMod[] {null};
    final String mapName[] = new String[] {null};
    final String mapCrc[] = new String[] {null};
    final String mapArchive[] = new String[] {null};

    return modService.findFeaturedModByTaDemoFileInfo(demoFileInfo)
        .thenAccept(featuredMod -> mod[0] = featuredMod)
        .thenCompose(aVoid -> fafService.findMapByTaDemoMapHash(demoFileInfo.getMapHash()))
        .thenAccept(mapBeanOptional -> mapBeanOptional.ifPresent(v -> {
          mapName[0] = v.getMapName();
          mapCrc[0] = v.getCrc();
          mapArchive[0] = v.getHpiArchiveName();
        }))
        .thenCompose(aVoid -> {
          if (mod[0] == null) {
            try {
              List<Action> actionList = fafService.getFeaturedMods().get().stream()
                  .filter(fm -> fm.isVisible())
                  .map(fm -> new Action(
                    fm.getDisplayName(), (a) -> runWithReplay(
                    demoFileInfo.getFilePath(), 0, fm.getTechnicalName(), mapName[0], mapCrc[0], mapArchive[0])))
                  .collect(Collectors.toList());

              notificationService.addNotification(new ImmediateNotification(
                  i18n.get("replay.selectMod.title"),
                  i18n.get("replay.selectMod.text"),
                  Severity.INFO,actionList));

            } catch (Exception e) {
              log.warn("Exception during mod enumeration: {}", e.getMessage());
            }
            return CompletableFuture.completedFuture(null);
          }
          else {
            return runWithReplay(
              demoFileInfo.getFilePath(), 0, mod[0].getTechnicalName(), mapName[0], mapCrc[0], mapArchive[0]);
          }
        });
  }

  public CompletableFuture<Void> runWithReplay(String replayFileOrUrl, Replay replay) {
    if (replay.getMap() != null) {
      return runWithReplay(
          replayFileOrUrl, replay.getId(), replay.getFeaturedMod().getTechnicalName(),
          replay.getMap().getMapName(), replay.getMap().getCrc(), replay.getMap().getHpiArchiveName());
    }
    else {
      return runWithReplay(replayFileOrUrl, replay.getId(), replay.getFeaturedMod().getTechnicalName(),
          null, null, null);
    }
  }

  public CompletableFuture<Void> runWithReplay(String replayFileOrUrl, Game game) {
    return runWithReplay(
        replayFileOrUrl, game.getId(), game.getFeaturedMod(),
        game.getMapName(), game.getMapCrc(), game.getMapArchiveName());
  }

  /**
   * @param replayFileOrUrl Either a path to a locally available file, or a url eg taforever.com:15000/1234
   */
  public CompletableFuture<Void> runWithReplay(
      String replayFileOrUrl, Integer replayId, String modTechnical,
      @Nullable String mapName, @Nullable String mapCrc, @Nullable String mapArchive) {

    if (!canStartReplay()) {
      return completedFuture(null);
    }

    if (!preferencesService.isGameExeValid(modTechnical)) {
      CompletableFuture<Path> gameDirectoryFuture = postGameDirectoryChooseEvent(modTechnical);
      gameDirectoryFuture.thenAccept(pathSet -> runWithReplay(
          replayFileOrUrl, replayId, modTechnical, mapName, mapCrc, mapArchive));
      return completedFuture(null);
    }

    onMatchmakerSearchStopped();

    return modService.getFeaturedMod(modTechnical)
        //.thenCompose(featuredModBean -> updateGameIfNecessary(featuredModBean, version, modVersions, simMods))
        .thenCompose(aVoid -> mapService.ensureMap(modTechnical, mapName, mapCrc, mapArchive, null, null))
        .thenAccept((aVoid) -> {
          try {
            if (isGameRunning()) {
              return;
            }

            Process launchServerProcess = noCatch(() -> totalAnnihilationService.startLaunchServer(modTechnical, replayId));
            spawnGenericTerminationListener(launchServerProcess);

            this.process = totalAnnihilationService.startReplay(modTechnical, replayFileOrUrl, replayId, getCurrentPlayer().getUsername());
            setRunningGameUid(replayId);

            BooleanProperty dismissTrigger = openProcessRunningDialog(this.process,
                i18n.get("replay.running.title", replayId, replayFileOrUrl),
                new File(replayFileOrUrl).exists() ? i18n.get("replay.running.text.local") : i18n.get("replay.running.text.live"));
            spawnGameTerminationListener(this.process, replayId, modTechnical, dismissTrigger);

          } catch (IOException e) {
            notifyCantPlayReplay(replayId, e);
          }
        })
        .exceptionally(throwable -> {
          notifyCantPlayReplay(replayId, throwable);
          return null;
        });
  }

  private BooleanProperty openProcessRunningDialog(Process process, String title, String text) {
    ImmediateNotification notification = new ImmediateNotification(
        title, text, Severity.INFO,
        asList(
            new Action(i18n.get("replay.running.terminate"), ev -> process.destroy())
        ));
    notification.setOverlayClose(false);
    notificationService.addNotification(notification);
    return notification.getDismissTrigger();
  }

  private boolean canStartReplay() {
    if (isGameRunning()) {
      log.warn("Total Annihilation is already running, not starting replay");
      notificationService.addImmediateWarnNotification("replay.gameRunning");
      return false;
    } else if (inMatchmakerQueue.get()) {
      log.warn("In matchmaker queue, not starting replay");
      notificationService.addImmediateWarnNotification("replay.inQueue");
      return false;
    }
//    else if (inOthersParty.get()) {
//      log.info("In party, not starting replay");
//      notificationService.addImmediateWarnNotification("replay.inParty");
//      return false;
//    }
    return true;
  }

  @NotNull
  public CompletableFuture<Path> postGameDirectoryChooseEvent(String modTechnicalName) {
    CompletableFuture<Path> gameDirectoryFuture = new CompletableFuture<>();
    eventBus.post(new GameDirectoryChooseEvent(modTechnicalName, gameDirectoryFuture));
    return gameDirectoryFuture;
  }

  @SneakyThrows
  private Void askWhetherToStartWithOutMap(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    JavaFxUtil.assertBackgroundThread();
    log.warn("Something went wrong loading map for replay", throwable);

    CountDownLatch userAnswered = new CountDownLatch(1);
    AtomicReference<Boolean> proceed = new AtomicReference<>(false);
    List<Action> actions = Arrays.asList(new Action(i18n.get("replay.ignoreMapNotFound"), event -> {
          proceed.set(true);
          userAnswered.countDown();
        }),
        new Action(i18n.get("replay.abortAfterMapNotFound"), event -> userAnswered.countDown()));
    notificationService.addNotification(new ImmediateNotification(i18n.get("replay.mapDownloadFailed"), i18n.get("replay.mapDownloadFailed.wannaContinue"), Severity.WARN, actions));
    userAnswered.await();
    if (!proceed.get()) {
      throw throwable;
    }
    return null;
  }

  private void notifyCantPlayReplay(@Nullable Integer replayId, Throwable throwable) {
    if (throwable.getCause() instanceof UnsupportedOperationException) {
      notificationService.addImmediateErrorNotification(throwable, "gameUpdate.error.gameNotWritableAllowMultiOn");
    } else {
      log.error("Could not play replay '" + replayId + "'", throwable);
      notificationService.addImmediateErrorNotification(throwable, "replayCouldNotBeStarted");
    }
  }

  @NotNull
  private Player getCurrentPlayer() {
    return playerService.getCurrentPlayer().orElseThrow(() -> new IllegalStateException("Player has not been set"));
  }

  public String getInGameIrcUserName(String playerName) {
    return playerName.replace(" ", "") + "[ingame]";
  }

  public String getInGameIrcChannel(String host, String title) {
    title = WordUtils.capitalizeFully(title, ' ', ',', ':').replaceAll("[ ,:]", "");
    host = String.format("[%s]", host);
    String channelName = "#"+title+host;
    if (channelName.length() > 32 && host.length() <= 32) {
      channelName = channelName.substring(0,32-host.length()) + host;
    }
    else if (channelName.length() > 32) {
      channelName = "#"+host;
    }
    return channelName;
  }

  public String getInGameIrcChannel(Game game) {
    return getInGameIrcChannel(game.getHost(), game.getTitle());
  }

  public String getInGameIrcChannel(NewGameInfo gameInfo) {
    return getInGameIrcChannel(getCurrentPlayer().getUsername(), gameInfo.getTitle());
  }

  public String getInGameIrcUrl(String channel) {
    if (preferencesService.getPreferences().getIrcIntegrationEnabled()) {
      return getInGameIrcUserName(getCurrentPlayer().getUsername()) + "@" + this.ircHostAndPort + "/" + channel;
    }
    else
    {
      return null;
    }
  }

  public ObservableList<Game> getGames() {
    return games;
  }

  public Game getByUid(int uid) {
    Game game = uidToGameInfoBean.get(uid);
    if (game == null) {
      log.warn("Can't find {} in gameInfoBean map", uid);
    }
    return game;
  }

  public CompletableFuture<Void> startSearchMatchmaker(String modTechnical) {
    if (isGameRunning()) {
      log.debug("Game is running, ignoring matchmaking search request");
      notificationService.addImmediateWarnNotification("game.gameRunning");
      return completedFuture(null);
    }

    if (!preferencesService.isGameExeValid(modTechnical)) {
      CompletableFuture<Path> gameDirectoryFuture = postGameDirectoryChooseEvent(modTechnical);
      return gameDirectoryFuture.thenCompose(path -> startSearchMatchmaker(modTechnical));
    }

    log.info("Matchmaking search has been started");
    inMatchmakerQueue.set(true);
    autoJoinRequestedGameProperty.set(null);
    return
        modService.getFeaturedMod(modTechnical)
        .thenAccept(featuredModBean -> updateGameIfNecessary(featuredModBean, null, emptyMap(), emptySet()))
        .thenCompose(aVoid -> fafService.startSearchMatchmaker())
        .thenAccept((gameLaunchMessage) ->
            mapService.ensureMap(gameLaunchMessage.getMod(), gameLaunchMessage.getMapname(), gameLaunchMessage.getMapCrc(), gameLaunchMessage.getMapArchive(), null, null)
            .thenRun(() -> {
              gameLaunchMessage.setArgs(new ArrayList<>(gameLaunchMessage.getArgs()));
              gameLaunchMessage.getArgs().add("/team " + gameLaunchMessage.getTeam());
              gameLaunchMessage.getArgs().add("/players " + gameLaunchMessage.getExpectedPlayers());
              gameLaunchMessage.getArgs().add("/startspot " + gameLaunchMessage.getMapPosition());
              startGame(gameLaunchMessage, null, true, playerService.getCurrentPlayer().get().getAlias());
            }))
        .exceptionally(throwable -> {
          if (throwable.getCause() instanceof CancellationException) {
            log.info("Matchmaking search has been cancelled");
          } else {
            log.warn("Matchmade game could not be started", throwable);
          }
          return null;
        });
  }

  public void onMatchmakerSearchStopped() {
    if (inMatchmakerQueue.get()) {
      fafService.stopSearchMatchmaker();
      inMatchmakerQueue.set(false);
      log.debug("Matchmaker search stopped");
    } else {
      log.debug("Matchmaker search has already been stopped, ignoring call");
    }
  }

  public BooleanProperty getInMatchmakerQueueProperty() {
    return inMatchmakerQueue;
  }

  public BooleanProperty getInOthersPartyProperty() {
    return inOthersParty;
  }

  /**
   * Returns the preferences the player is currently in. Returns {@code null} if not in a preferences.
   */
  @Nullable
  public Game getCurrentGame() {
    synchronized(currentGame) {
      return currentGame.get();
    }
  }

  public SimpleObjectProperty<Game> getCurrentGameProperty() {
    return currentGame;
  }

  public SimpleObjectProperty<GameStatus> getCurrentGameStatusProperty() {
    return currentGameStatusProperty;
  }

  public GameStatus getCurrentGameStatus() {
    return currentGameStatusProperty.get();
  }

  public boolean isGameRunning() {
    return process != null && process.isAlive();
  }

  private CompletableFuture<Void> updateGameIfNecessary(FeaturedMod featuredMod, @Nullable Integer version, @NotNull Map<String, Integer> featuredModVersions, @NotNull Set<String> simModUids) {
    return CompletableFuture.completedFuture(null);
    //return gameUpdater.update(featuredMod, version, featuredModVersions, simModUids);
  }

  public Integer getRunningGameUid() {
    return runningGameUidProperty.getValue();
  }

  private void setRunningGameUid(Integer uidOrNull) {
    try {
      noCatch(() -> runningGameUidProperty.setValue(uidOrNull));
    }
    catch(Exception e)
    {
      log.warn("[setRunningGameUid] {}", e.getMessage());
    }
  }

  public void startBattleRoom() {
    if (isGameRunning()) {
      this.totalAnnihilationService.sendToConsole("/launch");
    }
  }

  public void updateSettingsForStagingGame(String mapName, String ratingType) {
    Game currentGame = getCurrentGame();
    if (isGameRunning() && currentGame != null && currentGame.getStatus()==GameStatus.STAGING) {
      try {
        List<String[]> mapsDetails = MapTool.listMap(preferencesService.getTotalAnnihilation(currentGame.getFeaturedMod()).getInstalledPath(), mapName);
        final String UNIT_SEPARATOR = Character.toString((char)0x1f);
        String mapDetails = String.join(UNIT_SEPARATOR, mapsDetails.get(0));
        this.totalAnnihilationService.sendToConsole(String.format("/map %s", mapDetails));
        this.totalAnnihilationService.sendToConsole(String.format("/rating_type %s", ratingType));
      }
      catch (IOException e) {
        log.info("[setMapForStagingGame] unable to get details for map {}", mapName);
        notificationService.addImmediateErrorNotification(e, "maptool.error");
      }
    }
    else {
      log.info("[setMapForStagingGame] attempt to update settings while current game is not in STAGING state. ignoring");
    }
  }

  /**
   * Actually starts the game, including relay and replay server. Call this method when everything else is prepared
   * (mod/map download, connectivity check etc.)
   */
  private void startGame(GameLaunchMessage gameLaunchMessage, @Nullable String ircUrl, boolean autoLaunch, String playerAlias) {
    if (isGameRunning()) {
      log.warn("Total Annihilation is already running, not starting game");
      return;
    }
    String modTechnical = gameLaunchMessage.getMod();
    int uid = gameLaunchMessage.getUid();
    replayServer.start(uid, () -> getByUid(uid))
        .thenCompose(port ->  iceAdapter.start(playerAlias))
        .thenAccept(adapterPort -> {
          List<String> args = fixMalformedArgs(gameLaunchMessage.getArgs());

          Process launchServerProcess = noCatch(() -> totalAnnihilationService.startLaunchServer(modTechnical, uid));
          spawnGenericTerminationListener(launchServerProcess);

          String demoCompilerUrl = String.format("%s:%s/%s",
              clientProperties.getReplay().getRemoteHost(), clientProperties.getReplay().getCompilerPort(), uid);

          process = noCatch(() -> totalAnnihilationService.startGame(modTechnical, uid, args,
              adapterPort, getCurrentPlayer(), demoCompilerUrl, ircUrl, autoLaunch));
          setRunningGameUid(uid);
          currentGameStatusProperty.set(GameStatus.SPAWNING);
          spawnGameTerminationListener(process, uid,  modTechnical, null);
        })
        .exceptionally(throwable -> {
          log.warn("Game could not be started", throwable);
          notificationService.addImmediateErrorNotification(throwable, "game.start.couldNotStart");
          iceAdapter.stop();
          fafService.notifyGameEnded();
          this.killGame();
          setRunningGameUid(null);
          return null;
        });
  }

  private void notifyRecentlyPlayedGameEnded(Game game) {
    NotificationsPrefs notification = preferencesService.getPreferences().getNotification();
    if (notification.isAfterGameReviewEnabled() && notification.isTransientNotificationsEnabled()) {
      notificationService.addNotification(new PersistentNotification(i18n.get("game.ended", game.getTitle()),
          Severity.INFO,
          List.of(
              new Action(i18n.get("tada.upload"), actionEvent -> eventBus.post(new UploadToTadaEvent(game.getId()))),
              new Action(i18n.get("game.rate"), actionEvent -> eventBus.post(new ShowReplayEvent(game.getId())))
          )));
    }
  }

  /**
   * A correct argument list looks like ["/ratingcolor", "d8d8d8d8", "/numgames", "236"]. However, the FAF server sends
   * it as ["/ratingcolor d8d8d8d8", "/numgames 236"]. This method fixes this.
   */
  private List<String> fixMalformedArgs(List<String> gameLaunchMessage) {
    ArrayList<String> fixedArgs = new ArrayList<>();

    for (String combinedArg : gameLaunchMessage) {
      String[] split = combinedArg.split(" ");
      Collections.addAll(fixedArgs, split);
    }
    return fixedArgs;
  }

  Integer waitForTermination(Process process) {
    String command = process.info().command().orElse(null);
    String commandFileName = command == null ? null : Paths.get(command).getFileName().toString();
    int exitCode;

    try {
      exitCode = process.waitFor();
    } catch (InterruptedException e) {
      log.warn("[waitForTermination] interrupted waiting for termination of process '{}'. Destroying ...", command);
      process.destroy();
      return null;
    }

    log.info("[waitForTermination] '{}' terminated with exit code {}", commandFileName, exitCode);
    return exitCode;
  }

  void submitLogs(int gameId, String modTechnical) {
    if (preferencesService.getPreferences().getAutoUploadLogsOption() == AutoUploadLogsOption.ALLOW) {
      doSubmitLogs(gameId, modTechnical);
    }
    else if (preferencesService.getPreferences().getAutoUploadLogsOption() == AutoUploadLogsOption.ASK) {
      notificationService.addNotification(new ImmediateNotification(
          i18n.get("settings.autoLogsUpload"), i18n.get("settings.autoLogsUpload.description"),
          Severity.INFO, Arrays.asList(
          new Action(i18n.get("menu.revealLogFolder"), Action.Type.OK_STAY, event -> {
            Path logPath = preferencesService.getFafLogDirectory();
            this.platformService.reveal(logPath);
          }),
          new Action(i18n.get("settings.autoLogsUpload.allow"), Action.Type.OK_DONE, event -> {
            preferencesService.getPreferences().setAutoUploadLogsOption(AutoUploadLogsOption.ALLOW);
            preferencesService.storeInBackground();
            doSubmitLogs(gameId, modTechnical);
          }),
          new Action(i18n.get("settings.autoLogsUpload.deny"), Action.Type.OK_DONE, event -> {
            preferencesService.getPreferences().setAutoUploadLogsOption(AutoUploadLogsOption.DENY);
            preferencesService.storeInBackground();
          }))
      ));
    }
  }

  void doSubmitLogs(int gameId, String modTechnical) {
    log.info("[submitLogs] submitting logs to TAF for game ID={}", gameId);
    Path logClient = preferencesService.getFafLogDirectory().resolve("client.log");
    Path logIceAdapter = preferencesService.getIceAdapterLogDirectory().resolve("ice-adapter.log");
    Path logLauncher = preferencesService.getMostRecentLogFile("talauncher").orElse(Path.of(""));
    Path logGpgnet4ta = preferencesService.getMostRecentLogFile("game").orElse(Path.of(""));
    Path logReplay = preferencesService.getMostRecentLogFile("replay").orElse(Path.of(""));
    Path taErrorLog = preferencesService.getTotalAnnihilation(modTechnical).getInstalledPath().resolve("ErrorLog.txt");
    Path targetZipFile = preferencesService.getFafLogDirectory().resolve(String.format("game_logs_%d.zip", gameId));

    try {
      File files[] = {
          logClient.toFile(), logIceAdapter.toFile(), logLauncher.toFile(),
          logGpgnet4ta.toFile(), logReplay.toFile(), taErrorLog.toFile()};
      ZipUtil.zipFile(files, targetZipFile.toFile());
      ResourceLocks.acquireUploadLock();
      fafService.uploadGameLogs(targetZipFile, "game", gameId, (written, total) -> {});
    } catch (Exception e) {
      log.error("[submitLogs] unable to submit logs:", e.getMessage());
    } finally {
      ResourceLocks.freeUploadLock();
      try { Files.delete(targetZipFile); } catch(Exception e) {}
    }
  }

  @VisibleForTesting
  void spawnGameTerminationListener(Process process, int gameId, String modTechnical, @Nullable BooleanProperty triggerTerminationHandler) {
    if (process == null) {
      return;
    }

    rehostRequested = Optional.ofNullable(null);
    executorService.execute(() -> {
      String command = process.info().command().orElse("<unknown>");
      String commandFileName = Paths.get(command).getFileName().toString();
      Integer exitCode = waitForTermination(process);

      submitLogs(gameId, modTechnical);
      if (exitCode != null && exitCode != 0) {
        if (triggerTerminationHandler == null || !triggerTerminationHandler.get()) {
          String message = String.format("'%s' exited with code %d", command, exitCode);
          notificationService.addImmediateErrorNotification(new RuntimeException(message), "game.crash", commandFileName, gameId);
        }
      }

      JavaFxUtil.runLater(() -> {
        try {
          if (triggerTerminationHandler != null) {
            triggerTerminationHandler.setValue(true);
          }
          setRunningGameUid(null);
          replayServer.stop();
          iceAdapter.stop();
          fafService.notifyGameEnded();
        }
        catch (TaskRejectedException e) {
        }
      });
    });
  }

  @VisibleForTesting
  void spawnGenericTerminationListener(Process process) {
    if (process == null) {
      return;
    }
    executorService.execute(() -> {
      String command = process.info().command().orElse("<unknown>");
      String commandFileName = Paths.get(command).getFileName().toString();
      Integer exitCode = waitForTermination(process);
      if (exitCode != null && exitCode != 0) {
        String message = String.format("'%s' exited with code %d", command, exitCode);
        notificationService.addImmediateErrorNotification(new RuntimeException(message),"process.crash", commandFileName);
      }
    });
  }


  private void onLoggedIn() {
    if (isGameRunning()) {
      fafService.restoreGameSession(getCurrentGame().getId());
    }
  }

  public class RunAfterTimeout {
    private final int timeoutMillis;
    private final Timer timer;
    private final Runnable runnable;
    private TimerTask task;

    public RunAfterTimeout(Runnable runnable, int timeoutMillis) {
      this.runnable = runnable;
      this.timeoutMillis = timeoutMillis;
      this.timer = new Timer();
    }

    public void reset() {
      if (this.task != null) {
        this.task.cancel();
      }
      this.task = new TimerTask() {
        @Override public void run() { runnable.run(); }
      };
      timer.schedule(this.task, timeoutMillis);
    }
  }

  RunAfterTimeout rehostCheckTimer;
  @Subscribe
  public void onRehostRequest(RehostRequestEvent event) {
    rehostRequested = Optional.of(event.getGame());

    if (rehostCheckTimer == null) {
      rehostCheckTimer = new RunAfterTimeout(() -> checkRehost(), 300);
      InvalidationListener listener = (c) -> rehostCheckTimer.reset();
      playerService.getCurrentPlayer().get().statusProperty().addListener(listener);
      currentGame.addListener(listener);
      runningGameUidProperty.addListener(listener);
    }

    log.info("[onRehostRequest] will rehost {}", event.getGame());
    rehostCheckTimer.reset();
  }

  private void checkRehost() {
    Game currentGame = getCurrentGame();
    if (rehostRequested.isPresent() &&
        getCurrentPlayer().getStatus() == PlayerStatus.IDLE &&
        getRunningGameUid() == 0 &&       // local instance not running.  yeah its zero, not null :/
        currentGame == null          // server doesn't think we should be in a game
    ) {
      log.info("[checkRehost] rehosting ...");
      Game prototype = rehostRequested.get();
      rehostRequested = Optional.ofNullable(null);
      JavaFxUtil.runLater(() -> rehost(prototype));
    }
    else {
      log.info("[checkRehost] not yet ... isRequested={}, getRunningGameUid={}, currentGame={}, getCurrentPlayer={}",
          rehostRequested.isPresent(), getRunningGameUid(), currentGame, getCurrentPlayer().getStatus());
    }
  }

  private void rehost(Game prototype) {
    modService.getFeaturedMod(prototype.getFeaturedMod())
        .thenAccept(featuredModBean -> hostGame(new NewGameInfo(
            prototype.getTitle(),
            prototype.getPassword(),
            featuredModBean,
            prototype.getMapName(),
            new HashSet<>(prototype.getSimMods().values()),
            prototype.getVisibility(),
            prototype.getMinRating(), prototype.getMaxRating(),
            prototype.getEnforceRating(), prototype.getReplayDelaySeconds(),
            prototype.getRatingType())));
  }

  private ObjectProperty<Game> autoJoinRequestedGameProperty = new SimpleObjectProperty<>();
  public ObjectProperty<Game> getAutoJoinRequestedGameProperty() { return autoJoinRequestedGameProperty; };
  RunAfterTimeout autoJoinTimer;
  @Subscribe
  public void onAutoJoinRequest(AutoJoinRequestEvent event) {
    autoJoinRequestedGameProperty.set(event.getPrototype());
    if (event.getPrototype() == null) {
      log.info("[onAutoJoinRequest] cleared current auto-join request");
      return;
    }

    log.info("[onAutoJoinRequest] will autojoin {}'s next game", event.getPrototype().getHost());
    if (autoJoinTimer == null) {
      autoJoinTimer = new RunAfterTimeout(() -> checkAutoJoin(autoJoinRequestedGameProperty.get()), 300);
      InvalidationListener listener = c -> autoJoinTimer.reset();
      games.addListener(listener);
      playerService.getCurrentPlayer().get().statusProperty().addListener(listener);
      currentGame.addListener(listener);
      runningGameUidProperty.addListener(listener);
    }

    autoJoinTimer.reset();
  }

  public void checkAutoJoin(Game prototype) {
    if (prototype == null) {
      return;
    }
    Optional<Game> gameOptional = findMatchingAutoJoinable(prototype);
    Optional<Player> currentPlayerOptional = playerService.getCurrentPlayer();
    Game currentGame = getCurrentGame();

    if (gameOptional.isPresent() && currentPlayerOptional.isPresent() &&
        currentPlayerOptional.get().getStatus() == PlayerStatus.IDLE &&
        currentGame == null &&
        getRunningGameUid() == 0) {
      log.info("[checkAutoJoin] auto-joining {}!", gameOptional.get());
      JavaFxUtil.runLater(() -> this.joinGame(gameOptional.get(), prototype.getPassword()));
    }
    else {
      log.info("[checkAutoJoin] not yet joinableGame:{}, playerStatus:{}, currentGame{}, getRunningGameUid():{}",
          gameOptional.isPresent(), currentPlayerOptional.get().getStatus(), currentGame, getRunningGameUid());
    }
  }

  /// @note this means offline wrt IRC, not necessarily wrt taf-python-server
  @Subscribe
  public void onUserOffline(UserOfflineEvent event) {
    if (autoJoinRequestedGameProperty.get() != null &&
        autoJoinRequestedGameProperty.get().getHost().equals(event.getUsername())) {
      log.info("[onUserOffline] cancelling auto-join because player {} disconnected from IRC", event.getUsername());
      autoJoinRequestedGameProperty.set(null);
    }
  }

  private Optional<Game> findMatchingAutoJoinable(Game prototype) {
    return this.games.stream()
        .filter(g -> g.getGameType() != GameType.MATCHMAKER)
        .filter(g -> g.getId() != prototype.getId())
        .filter(g -> g.getHost() != null && g.getMapArchiveName() != null && g.getMapCrc() != null)
        .filter(g -> g.getHost().equals(prototype.getHost()))
        .filter(g -> Set.of(GameStatus.STAGING, GameStatus.BATTLEROOM).contains(g.getStatus()))
        .findFirst();
  }

  private void onGameInfo(GameInfoMessage gameInfoMessage) {
    JavaFxUtil.assertApplicationThread();
    if (gameInfoMessage.getGames() != null) {
      gameInfoMessage.getGames().forEach(this::onGameInfo);
      return;
    }

    // We may receive game info before we receive our player info
    Optional<Player> currentPlayerOptional = playerService.getCurrentPlayer();

    Game game = createOrUpdateGame(gameInfoMessage);
    // some control paths null out currentGame but we still need to remember this
    final boolean isGameCurrentGame = Objects.equals(currentGame.get(), game) ||
        Objects.equals(getRunningGameUid(), game.getId());

    if (GameStatus.ENDED == game.getStatus()) {
      removeGame(gameInfoMessage);
      if (!currentPlayerOptional.isPresent() || !isGameCurrentGame) {
        return;
      }
      synchronized (currentGame) {
        currentGame.set(null);
      }
    }

    if (currentPlayerOptional.isPresent()) {
      // TODO the following can be removed as soon as the server tells us which game a player is in.
      boolean currentPlayerInGame = gameInfoMessage.getUid() == currentPlayerOptional.get().getCurrentGameUid();

      if (currentPlayerInGame && gameInfoMessage.getState().isOpen()) {
        synchronized (currentGame) {
          currentGame.set(game);
        }
      } else if (isGameCurrentGame && !currentPlayerInGame) {
        synchronized (currentGame) {
          currentGame.set(null);
        }
      }
      if (preferencesService.getPreferences().getAutoJoinEnabled() &&
          game.getStatus() == GameStatus.BATTLEROOM &&
          game.getGameType() != GameType.MATCHMAKER &&
          !currentPlayerOptional.get().getUsername().equals(game.getHost()) &&
          currentPlayerInGame &&
          !game.equals(autoJoinRequestedGameProperty.get())
      ) {
        eventBus.post(new AutoJoinRequestEvent(game));
      }
    }

    JavaFxUtil.addListener(game.statusProperty(), (observable, oldValue, newValue) -> {
      if (oldValue.isOpen()
          && newValue.isInProgress()
          && game.getTeams().values().stream().anyMatch(team -> playerService.getCurrentPlayer().isPresent() && team.contains(playerService.getCurrentPlayer().get().getUsername()))
          && !platformService.isWindowFocused(faWindowTitle)) {
        platformService.focusWindow(faWindowTitle);
      }
    });
  }

  private Game createOrUpdateGame(GameInfoMessage gameInfoMessage) {
    JavaFxUtil.assertApplicationThread();
    Integer gameId = gameInfoMessage.getUid();
    log.debug("Updating Game {}", gameId);
    final Game game;
    synchronized (uidToGameInfoBean) {
      if (!uidToGameInfoBean.containsKey(gameId)) {
        game = new Game();
        uidToGameInfoBean.put(gameId, game);
        updateFromGameInfo(gameInfoMessage, game);
        eventBus.post(new GameAddedEvent(game));
      } else {
        game = uidToGameInfoBean.get(gameId);
        updateFromGameInfo(gameInfoMessage, game);
        eventBus.post(new GameUpdatedEvent(game));
      }
    }
    return game;
  }

  private double calcAverageRating(GameInfoMessage gameInfoMessage) {
    return gameInfoMessage.getTeams().values().stream()
        .flatMap(Collection::stream)
        .map(playerService::getPlayerForUsername)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .mapToInt(player -> RatingUtil.getLeaderboardRating(player, gameInfoMessage.getRatingType()))
        .average()
        .orElse(0.0);
  }

  private void updateFromGameInfo(GameInfoMessage gameInfoMessage, Game game) {
    game.setId(gameInfoMessage.getUid());
    game.setHost(gameInfoMessage.getHost());
    game.setTitle(StringEscapeUtils.unescapeHtml4(gameInfoMessage.getTitle()));
    game.setMapName(gameInfoMessage.getMapName());
    game.setFeaturedMod(gameInfoMessage.getFeaturedMod());
    game.setNumPlayers(gameInfoMessage.getNumPlayers());
    game.setMaxPlayers(gameInfoMessage.getMaxPlayers());
    Optional.ofNullable(gameInfoMessage.getLaunchedAt()).ifPresent(aDouble -> game.setStartTime(
        TimeUtil.fromPythonTime(aDouble.longValue()).toInstant()
    ));
    game.setStatus(gameInfoMessage.getState());
    game.setPasswordProtected(gameInfoMessage.getPasswordProtected());
    game.setGameType(gameInfoMessage.getGameType());
    game.setRatingType(gameInfoMessage.getRatingType());
    game.setVisibility(GameVisibility.fromString(gameInfoMessage.getVisibility()));

    //String UnitSeparator = Character.toString((char)0x1f);
    //String mapDetails[] = gameInfoMessage.getMapDetails().split(UnitSeparator); // determined by host: name,archive,crc,desc,size,numplayers,minwind-maxwind,tide,gravity
    String mapFilePath[] = gameInfoMessage.getMapFilePath().split("/");   // determined by faf db: archive/name/crc
    if (mapFilePath.length >= 3) {
      game.setMapArchiveName(mapFilePath[0]);
      game.setMapCrc(mapFilePath[2]);
    }

    game.setAverageRating(calcAverageRating(gameInfoMessage));

    synchronized (game.getSimMods()) {
      game.getSimMods().clear();
      if (gameInfoMessage.getSimMods() != null) {
        game.getSimMods().putAll(gameInfoMessage.getSimMods());
      }
    }

    synchronized (game.getTeams()) {
      game.getTeams().clear();
      if (gameInfoMessage.getTeams() != null) {
        game.getTeams().putAll(gameInfoMessage.getTeams());
      }
    }

    game.setMinRating(gameInfoMessage.getRatingMin());
    game.setMaxRating(gameInfoMessage.getRatingMax());
    game.setEnforceRating(gameInfoMessage.getEnforceRatingRange());
    game.setReplayDelaySeconds(gameInfoMessage.getReplayDelaySeconds());
  }

  private void removeGame(GameInfoMessage gameInfoMessage) {
    Game game;
    synchronized (uidToGameInfoBean) {
      game = uidToGameInfoBean.remove(gameInfoMessage.getUid());
    }

    if (gameInfoMessage.getUid().equals(getRunningGameUid())) {
      // getRunningGameUid() is determined immediately upon starting gpgnet4ta
      // getCurrentGameStatus()==SPAWNING indicates game has been started locally but server hasn't confirmed that fact
      if (GameStatus.SPAWNING.equals(getCurrentGameStatus())) {
        log.warn("Game cancelled while launching");
        killGame();
        notificationService.addImmediateInfoNotification("game.start.cancelledRemotely.title", "game.start.cancelledRemotely");
      }
    }
    eventBus.post(new GameRemovedEvent(game));
  }

  public void killGame() {
    if (process != null && process.isAlive()) {
      // If game is in progress, gpgnet4ta will only respond to two consecutive /quits
      this.totalAnnihilationService.sendToConsole("/quit");
    }
  }

  public void setMatchedQueueRatingType(String matchedQueueRatingType) {
    this.matchedQueueRatingType = matchedQueueRatingType;
  }

  @Subscribe
  public void onGameCloseRequested(CloseGameEvent event) {
    killGame();
  }

}
