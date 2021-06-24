package com.faforever.client.game;

import com.faforever.client.chat.ChatService;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.discord.DiscordRichPresenceService;
import com.faforever.client.fa.CloseGameEvent;
import com.faforever.client.fa.MapTool;
import com.faforever.client.fa.TotalAnnihilationService;
import com.faforever.client.fa.relay.event.RehostRequestEvent;
import com.faforever.client.fa.relay.ice.IceAdapter;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.HostGameEvent;
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
import com.faforever.client.player.SocialStatus;
import com.faforever.client.preferences.NotificationsPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.ReconnectTimerService;
import com.faforever.client.remote.domain.GameInfoMessage;
import com.faforever.client.remote.domain.GameLaunchMessage;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.remote.domain.GameType;
import com.faforever.client.remote.domain.LoginMessage;
import com.faforever.client.replay.ReplayServer;
import com.faforever.client.ui.preferences.event.GameDirectoryChooseEvent;
import com.faforever.client.util.RatingUtil;
import com.faforever.client.util.TimeUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import java.io.IOException;
import java.net.URI;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.nocatch.NoCatch.noCatch;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Downloads necessary maps, mods and updates before starting
 */
@Lazy
@Service
@Slf4j
@RequiredArgsConstructor
public class GameService implements InitializingBean {

  @VisibleForTesting
  static final String DEFAULT_RATING_TYPE = "global";

  public static final String GAME_CHANNEL_REGEX = "^#.+\\[.+\\]$";

  @VisibleForTesting
  final BooleanProperty gameRunning;

  /** TODO: Explain why access needs to be synchronized. */
  @VisibleForTesting
  final SimpleObjectProperty<Game> currentGame;
  final SimpleObjectProperty<GameStatus> currentGameStatusProperty;

  /**
   * An observable copy of {@link #uidToGameInfoBean}. <strong>Do not modify its content directly</strong>.
   */
  private final ObservableMap<Integer, Game> uidToGameInfoBean;

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
    inMatchmakerQueue = new SimpleBooleanProperty();
    inOthersParty = new SimpleBooleanProperty();
    gameRunning = new SimpleBooleanProperty();
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
          log.info("[currentGameListener] killGame() because new currentGame==null && currentPlayer.status() != PLAYING");
          killGame();
        }

        return;
      }

      if (newValue.getGameType() != GameType.MATCHMAKER) {
        String newGameChannel = getInGameIrcChannel(newValue);
        Set<String> userChannels = chatService.getUserChannels(currentPlayer.getUsername());
        userChannels.stream()
            .filter(channel -> channel.matches(GAME_CHANNEL_REGEX) && !channel.equals(newGameChannel))
            .forEach(channel -> chatService.leaveChannel(channel));
        userChannels.stream()
            .filter(channel -> channel.matches(GAME_CHANNEL_REGEX) && channel.equals(newGameChannel))
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
        gameRunning,
        (observable, oldValue, newValue) -> reconnectTimerService.setGameRunning(newValue)
    );

    eventBus.register(this);

    fafService.addOnMessageListener(GameInfoMessage.class, message -> JavaFxUtil.runLater(() -> onGameInfo(message)));
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
        if (currentGame.get() == null || !Objects.equals(game, currentGame.get())) {
          observable.removeListener(this);
          return;
        }
        final Player currentPlayer = playerService.getCurrentPlayer().orElseThrow(() -> new IllegalStateException("Player must be set"));
        discordRichPresenceService.updatePlayedGameTo(currentGame.get(), currentPlayer.getId(), currentPlayer.getUsername());

        if (currentPlayer.getStatus() == PlayerStatus.JOINING && currentGame.get().getStatus() == GameStatus.BATTLEROOM && preferencesService.getPreferences().getAutoLaunchOnJoinEnabled()) {
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
        boolean playerStillInGame = currentPlayer != null && currentGame != null && currentGame.get() != null && currentPlayer.getCurrentGameUid() == currentGame.get().getId();
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

        if (Objects.equals(currentGame.get(), game) && currentGameStatusProperty.get() != newStatus) {
          currentGameStatusProperty.setValue(newStatus);
        }

        if (newStatus == GameStatus.BATTLEROOM && Objects.equals(currentGame.get(), game)) {
          discordRichPresenceService.updatePlayedGameTo(currentGame.get(), currentPlayer.getId(), currentPlayer.getUsername());
          if (currentPlayer.getStatus() == PlayerStatus.JOINING && preferencesService.getPreferences().getAutoLaunchOnJoinEnabled()) {
            GameService.this.startBattleRoom();
          }
        }

        if (preferencesService.getPreferences().getAutoRehostEnabled() &&
            newStatus == GameStatus.BATTLEROOM &&
            game.getGameType() != GameType.MATCHMAKER &&
            getCurrentPlayer() != null && getCurrentPlayer().getUsername().equals(game.getHost())) {
          log.info("[generateGameStatusListener.changed] posting RehostRequestEvent");
          eventBus.post(new RehostRequestEvent(game));
        }
      }
    };
  }

  public ReadOnlyBooleanProperty gameRunningProperty() {
    return gameRunning;
  }

  public CompletableFuture<Void> hostGame(NewGameInfo newGameInfo) {
    log.info("[hostGame] title={}", newGameInfo.getTitle());

    if (isRunning()) {
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

    return updateGameIfNecessary(newGameInfo.getFeaturedMod(), null, Map.of(), newGameInfo.getSimMods())
        .thenCompose(aVoid -> fafService.requestHostGame(newGameInfo))
        .thenAccept(gameLaunchMessage -> startGame(gameLaunchMessage, inGameIrcUrl, autoLaunch, playerService.getCurrentPlayer().get().getUsername()));
  }

  private void addAlreadyInQueueNotification() {
    notificationService.addImmediateWarnNotification("teammatchmaking.notification.customAlreadyInQueue.message");
  }

  public CompletableFuture<Void> joinGame(Game game, String password) {
    if (isRunning()) {
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
    return
        modService.getFeaturedMod(game.getFeaturedMod())
        .thenCompose(featuredModBean -> updateGameIfNecessary(featuredModBean, null, featuredModVersions, simModUIds))
        .thenCompose(aVoid -> mapService.ensureMap(game.getFeaturedMod(), game.getMapName(), game.getMapCrc(), game.getMapArchiveName(), null, null))
        .thenCompose(aVoid -> fafService.requestJoinGame(game.getId(), password))
        .thenAccept(gameLaunchMessage -> {
          JavaFxUtil.runLater(() -> { // some UI elements are bound to currentGame property
              synchronized (currentGame) {
                // Store password in case we rehost
                game.setPassword(password);
                log.info("[joinGame] currentGame.set(game)");
                currentGame.set(game);
              }
            });

            boolean autoLaunch = preferencesService.getPreferences().getAutoLaunchOnJoinEnabled() && game.getStatus() == GameStatus.BATTLEROOM;
            startGame(gameLaunchMessage, inGameIrcUrl, autoLaunch, playerService.getCurrentPlayer().get().getUsername());
        })
        .exceptionally(throwable -> {
          log.warn("Game could not be joined", throwable);
          notificationService.addImmediateErrorNotification(throwable, "games.couldNotJoin");
          return null;
        });
  }

  /**
   * @param path a replay file that is readable by the preferences without any further conversion
   */
  public CompletableFuture<Void> runWithReplay(Path path, @Nullable Integer replayId, String featuredMod, Integer version, Map<String, Integer> modVersions, Set<String> simMods, String mapName) {
    if (!canStartReplay()) {
      return completedFuture(null);
    }

    if (!preferencesService.isGameExeValid(featuredMod)) {
      CompletableFuture<Path> gameDirectoryFuture = postGameDirectoryChooseEvent(featuredMod);
      gameDirectoryFuture.thenAccept(pathSet -> runWithReplay(path, replayId, featuredMod, version, modVersions, simMods, mapName));
      return completedFuture(null);
    }

    onMatchmakerSearchStopped();

    return modService.getFeaturedMod(featuredMod)
        .thenCompose(featuredModBean -> updateGameIfNecessary(featuredModBean, version, modVersions, simMods))
        .thenRun(() -> {
          try {
            Process processForReplay = totalAnnihilationService.startReplay(featuredMod, path, replayId);
            if (isRunning()) {
              return;
            }
            this.process = processForReplay;
            setGameRunning(true);
            spawnGameTerminationListener(this.process);
          } catch (IOException e) {
            notifyCantPlayReplay(replayId, e);
          }
        })
        .exceptionally(throwable -> {
          notifyCantPlayReplay(replayId, throwable);
          return null;
        });
  }

  private boolean canStartReplay() {
    if (isRunning()) {
      log.warn("Total Annihilation is already running, not starting replay");
      notificationService.addImmediateWarnNotification("replay.gameRunning");
      return false;
    } else if (inMatchmakerQueue.get()) {
      log.warn("In matchmaker queue, not starting replay");
      notificationService.addImmediateWarnNotification("replay.inQueue");
      return false;
    } else if (inOthersParty.get()) {
      log.info("In party, not starting replay");
      notificationService.addImmediateWarnNotification("replay.inParty");
      return false;
    }
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

  public CompletableFuture<Void> runWithLiveReplay(URI replayUrl, Integer gameId, String gameType, String mapName) {
    if (!canStartReplay()) {
      return completedFuture(null);
    }

    String modTechnicalName = modService.getFeaturedMod(gameType).join().getTechnicalName();
    if (!preferencesService.isGameExeValid(modTechnicalName)) {
      CompletableFuture<Path> gameDirectoryFuture = postGameDirectoryChooseEvent(modTechnicalName);
      return gameDirectoryFuture.thenCompose(path -> runWithLiveReplay(replayUrl, gameId, gameType, mapName));
    }

    onMatchmakerSearchStopped();

    Game gameBean = getByUid(gameId);

    Map<String, Integer> modVersions = gameBean.getFeaturedModVersions();
    Set<String> simModUids = gameBean.getSimMods().keySet();

    return modService.getFeaturedMod(gameType)
        .thenCompose(featuredModBean -> updateGameIfNecessary(featuredModBean, null, modVersions, simModUids))
        .thenCompose(aVoid -> mapService.ensureMap(modTechnicalName, mapName, gameBean.getMapCrc(), gameBean.getMapArchiveName(), null, null))
        .thenRun(() -> noCatch(() -> {
          Process processCreated = totalAnnihilationService.startReplay(modTechnicalName, replayUrl, gameId, getCurrentPlayer());
          if (isRunning()) {
            return;
          }
          this.process = processCreated;
          setGameRunning(true);
          spawnGameTerminationListener(this.process);
        }))
        .exceptionally(throwable -> {
          notifyCantPlayReplay(gameId, throwable);
          return null;
        });
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
    if (isRunning()) {
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
    return currentGame.get();
  }

  public SimpleObjectProperty<Game> getCurrentGameProperty() {
    return currentGame;
  }

  public SimpleObjectProperty<GameStatus> getCurrentGameStatusProperty() {
    return currentGameStatusProperty;
  }

  private boolean isRunning() {
    return process != null && process.isAlive();
  }

  private CompletableFuture<Void> updateGameIfNecessary(FeaturedMod featuredMod, @Nullable Integer version, @NotNull Map<String, Integer> featuredModVersions, @NotNull Set<String> simModUids) {
    return CompletableFuture.completedFuture(null);
    //return gameUpdater.update(featuredMod, version, featuredModVersions, simModUids);
  }

  public boolean isGameRunning() {
    return gameRunning.get();
  }

  private void setGameRunning(boolean running) {
    gameRunning.set(running);
  }

  public void startBattleRoom() {
    if (isRunning()) {
      this.totalAnnihilationService.sendToConsole("/launch");
    }
  }

  public void setMapForStagingGame(String mapName) {
    if (isRunning() && currentGame.get() != null && currentGame.get().getStatus()==GameStatus.STAGING) {
      try {
        List<String[]> mapsDetails = MapTool.listMap(preferencesService.getTotalAnnihilation(currentGame.get().getFeaturedMod()).getInstalledPath(), mapName);
        final String UNIT_SEPARATOR = Character.toString((char)0x1f);
        String mapDetails = String.join(UNIT_SEPARATOR, mapsDetails.get(0));
        String command = String.format("/map %s", mapDetails);
        log.info("[setMapForStagingGame] Sending '{}' to game console", command);
        this.totalAnnihilationService.sendToConsole(command);
      }
      catch (IOException e) {
        log.info("[setMapForStagingGame] unable to get details for map {}", mapName);
        notificationService.addImmediateErrorNotification(e, "maptool.error");
      }
    }
    else {
      log.info("[setMapForStagingGame] attempt to set map while current game is not in STAGING state. ignoring");
    }
  }

  /**
   * Actually starts the game, including relay and replay server. Call this method when everything else is prepared
   * (mod/map download, connectivity check etc.)
   */
  private void startGame(GameLaunchMessage gameLaunchMessage, String ircUrl, boolean autoLaunch, String playerAlias) {
    if (isRunning()) {
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

          process = noCatch(() -> totalAnnihilationService.startGame(modTechnical, gameLaunchMessage.getUid(), args,
              adapterPort, getCurrentPlayer(), ircUrl, autoLaunch));
          setGameRunning(true);
          spawnGameTerminationListener(process);
        })
        .exceptionally(throwable -> {
          log.warn("Game could not be started", throwable);
          notificationService.addImmediateErrorNotification(throwable, "game.start.couldNotStart");
          iceAdapter.stop();
          fafService.notifyGameEnded();
          setGameRunning(false);
          return null;
        });
  }

  private void notifyRecentlyPlayedGameEnded(Game game) {
    NotificationsPrefs notification = preferencesService.getPreferences().getNotification();
    if (false && notification.isAfterGameReviewEnabled() && notification.isTransientNotificationsEnabled()) {
      notificationService.addNotification(new PersistentNotification(i18n.get("game.ended", game.getTitle()),
          Severity.INFO,
          singletonList(new Action(i18n.get("game.rate"), actionEvent -> eventBus.post(new ShowReplayEvent(game.getId()))))));
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

  void waitForTermination(Process process) {
    String command = process.info().command().orElse("<unknown>");
    String commandFileName = Paths.get(command).getFileName().toString();
    int exitCode;

    try {
      exitCode = process.waitFor();
    } catch (InterruptedException e) {
      log.warn("[waitForTermination] interrupted waiting for termination of process '{}'. Destroying ...", command);
      process.destroy();
      return;
    }

    log.info("[waitForTermination] '{}' terminated with exit code {}", commandFileName, exitCode);
    if (exitCode != 0) {
      String logPath = preferencesService.getFafLogDirectory().toString();
      String message = String.format("'%s' exited with code %d. See '%s' for further information", command, exitCode, logPath);
      notificationService.addImmediateErrorNotification(new RuntimeException(message),"game.crash", commandFileName, logPath);
    }
  }

  @VisibleForTesting
  void spawnGameTerminationListener(Process process) {
    if (process == null) {
      return;
    }

    rehostRequested = Optional.ofNullable(null);
    executorService.execute(() -> {
      waitForTermination(process);
      JavaFxUtil.runLater(() -> {
        try {
          gameRunning.set(false);
          replayServer.stop();
          iceAdapter.stop();
          fafService.notifyGameEnded();
          if (rehostRequested.isPresent()) {
            rehost(rehostRequested.get());
          }
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
    executorService.execute(() -> waitForTermination(process));
  }

  private void rehost(Game prototype) {
    modService.getFeaturedMod(prototype.getFeaturedMod())
        .thenAccept(featuredModBean -> hostGame(new NewGameInfo(
            prototype.getTitle(),
            prototype.getPassword(),
            featuredModBean,
            prototype.getMapName(),
            new HashSet<>(prototype.getSimMods().values()),
            GameVisibility.PUBLIC,
            prototype.getMinRating(), prototype.getMaxRating(), prototype.getEnforceRating())));
  }

  @Subscribe
  public void onRehostRequest(RehostRequestEvent event) {
    if (!gameRunning.get()) {
      log.info("[onRehostRequest] rehost immediately");
      rehost(event.getGame());
    }
    else {
      log.info("[onRehostRequest] rehost deferred");
      rehostRequested = Optional.of(event.getGame());
    }
  }

  private void onLoggedIn() {
    if (isGameRunning()) {
      fafService.restoreGameSession(currentGame.get().getId());
    }
  }

  private boolean isAutoJoinable(Game game) {
    if (game.getStatus() != GameStatus.STAGING && game.getStatus() != GameStatus.BATTLEROOM) {
      return false;
    }

    // can't join until this information is available
    if (game.getMapArchiveName() == null || game.getMapCrc() == null) {
      return false;
    }

    if (game.isPasswordProtected()) {
      return false;
    }

    if (game.getGameType() == GameType.MATCHMAKER) {
      return false;
    }

    Player hostPlayer = playerService.getPlayerForUsername(game.getHost()).get();
    if (hostPlayer.getSocialStatus() == SocialStatus.SELF || hostPlayer.getSocialStatus() == SocialStatus.FOE) {
      return false;
    }

    if (hostPlayer.getStatus() != PlayerStatus.HOSTING && hostPlayer.getStatus() != PlayerStatus.HOSTED) {
      return false;
    }

    Optional<Player> currentPlayerOptional = playerService.getCurrentPlayer();
    if (currentPlayerOptional.isEmpty()) {
      return false;
    }

    // auto-join is only for if we're already in the chat channel
    String newGameIrcChannel = getInGameIrcChannel(game);
    Player currentPlayer = currentPlayerOptional.get();
    Set<String> currentPlayerChannels = chatService.getUserChannels(currentPlayer.getUsername());
    if (!currentPlayerChannels.stream().anyMatch(channel -> channel.equals(newGameIrcChannel))) {
      return false;
    }

    return true;
  }

  private void conditionallyDoOrDeferAutojoin(Game game) {
    log.info("[conditionallyDoOrDeferAutojoin]", game);
    Optional<Player> currentPlayerOptional = playerService.getCurrentPlayer();
    if (currentPlayerOptional.isPresent() && preferencesService.getPreferences().getAutoJoinEnabled() && isAutoJoinable(game)) {
      Player currentPlayer = currentPlayerOptional.get();
      if (currentPlayer.getStatus() == PlayerStatus.IDLE && getCurrentGame() == null) {
        log.info("[conditionallyDoOrDeferAutojoin] auto-joining {}", game);
        this.joinGame(game, null);
      }
      else if (currentPlayer.getStatus() != PlayerStatus.IDLE) {
        log.info("[conditionallyDoOrDeferAutojoin] will auto-join {} when player becomes IDLE", game);
        ChangeListener<PlayerStatus> listener = new ChangeListener<>() {
          @Override
          public void changed(ObservableValue<? extends PlayerStatus> observable, PlayerStatus oldValue, PlayerStatus newValue) {
            currentPlayer.statusProperty().removeListener(this);
            conditionallyDoOrDeferAutojoin(game);
          }
        };
        currentPlayer.statusProperty().addListener(listener);
      } else /* getCurrentGame() != null */ {
        log.info("[conditionallyDoOrDeferAutojoin] will auto-join {} when current game terminates", game);
        ChangeListener<Game> listener = new ChangeListener<>() {
          @Override
          public void changed(ObservableValue<? extends Game> observable, Game oldValue, Game newValue) {
            currentGame.removeListener(this);
            conditionallyDoOrDeferAutojoin(game);
          }
        };
        currentGame.addListener(listener);
      }
    }
    log.info("[conditionallyDoOrDeferAutojoin] done", game);
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
    final boolean isGameCurrentGame = Objects.equals(currentGame.get(), game);  // some control paths null out currentGame but we still need to remember this

    if (GameStatus.ENDED == game.getStatus()) {
      removeGame(gameInfoMessage);
      if (!currentPlayerOptional.isPresent() || !isGameCurrentGame) {
        return;
      }
      synchronized (currentGame) {
        log.info("[onGameInfo] currentGame.set(null) because GameStatus.ENDED");
        currentGame.set(null);
      }
    }

    if (currentPlayerOptional.isPresent()) {
      // TODO the following can be removed as soon as the server tells us which game a player is in.
      boolean currentPlayerInGame = gameInfoMessage.getUid() == currentPlayerOptional.get().getCurrentGameUid();

      if (currentPlayerInGame && gameInfoMessage.getState().isOpen()) {
        synchronized (currentGame) {
          log.info("[onGameInfo] currentGame(game) because currentPlayerInGame && game.isOpen()");
          currentGame.set(game);
          log.info("[onGameInfo] done");
        }
        log.info("[onGameInfo] done and synced");
      } else if (isGameCurrentGame && !currentPlayerInGame) {
        synchronized (currentGame) {
          log.info("[onGameInfo] currentGame(null) because !currentPlayerInGame");
          currentGame.set(null);
        }
      }

      log.info("[onGameInfo] isGameCurrentGame ...");
      if (!isGameCurrentGame) {
        log.info("[onGameInfo] conditionallyDoOrDeferAutojoin() ...");
        conditionallyDoOrDeferAutojoin(game);
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
  }

  private void removeGame(GameInfoMessage gameInfoMessage) {
    Game game;
    synchronized (uidToGameInfoBean) {
      game = uidToGameInfoBean.remove(gameInfoMessage.getUid());
    }
    eventBus.post(new GameRemovedEvent(game));
  }

  public void killGame() {
    if (process != null && process.isAlive()) {
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
