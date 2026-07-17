package com.faforever.client.game;

import com.faforever.client.chat.ChatService;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.discord.DiscordRichPresenceService;
import com.faforever.client.fa.CloseGameEvent;
import com.faforever.client.fa.DemoFileInfo;
import com.faforever.client.fa.MapTool;
import com.faforever.client.fa.TotalAnnihilationService;
import com.faforever.client.fa.relay.GpgGameMessage;
import com.faforever.client.fa.relay.event.AutoJoinRequestEvent;
import com.faforever.client.fa.relay.event.RehostRequestEvent;
import com.faforever.client.fa.relay.ice.IceAdapter;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.ladder.LadderPointsService;
import com.faforever.client.main.event.JoinChannelEvent;
import com.faforever.client.main.event.ShowScoreScreenEvent;
import com.faforever.client.map.MapService;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.FeaturedModVersion;
import com.faforever.client.mod.ModService;
import com.faforever.client.net.ConnectionState;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.DismissAction;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.PersistentNotification;
import com.faforever.client.notification.Severity;
import com.faforever.client.patch.GameUpdater;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.UserOfflineEvent;
import com.faforever.client.preferences.AskAlwaysOrNever;
import com.faforever.client.preferences.AutoUploadLogsOption;
import com.faforever.client.preferences.LastGamePrefs;
import com.faforever.client.preferences.NotificationsPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.rating.RatingService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.ReconnectTimerService;
import com.faforever.client.remote.domain.GameInfoMessage;
import com.faforever.client.remote.domain.GameLaunchMessage;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.remote.domain.GameType;
import com.faforever.client.remote.domain.LoginMessage;
import com.faforever.client.replay.UnhideReplayEvent;
import com.faforever.client.tada.event.UploadToTadaEvent;
import com.faforever.client.update.HotfixService;
import com.faforever.client.ui.preferences.event.GameDirectoryChooseEvent;
import com.faforever.client.util.RatingUtil;
import com.faforever.client.util.TimeUtil;
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
import java.net.ConnectException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_ARCHIVE;
import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_CRC;
import static com.faforever.client.fa.MapTool.MAP_DETAIL_COLUMN_NAME;
import static com.faforever.client.leaderboard.LeaderboardService.DEFAULT_RATING_TYPE;
import static com.github.nocatch.NoCatch.noCatch;
import static java.util.Arrays.asList;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Downloads necessary maps, mods and updates before starting
 */
@Lazy
@Service
@Slf4j
@RequiredArgsConstructor
public class GameService implements InitializingBean {

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
  private final GameUpdater gameUpdater;
  private final NotificationService notificationService;
  private final I18n i18n;
  private final ExecutorService executorService;
  private final PlayerService playerService;
  private final EventBus eventBus;
  private final IceAdapter iceAdapter;
  private final ModService modService;
  private final PlatformService platformService;
  private final DiscordRichPresenceService discordRichPresenceService;
  private final ReconnectTimerService reconnectTimerService;
  private final ChatService chatService;
  private final RatingService ratingService;
  private final HotfixService hotfixService;
  private final LadderPointsService ladderPointsService;

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
  private NewGameInfo recentHostGameRequest;  // rehostRequest is  missing a few bits of information (like password)
  // Titles the local player originally requested when hosting, keyed by game uid. The server may
  // rewrite a title that trips its badword filter; this map lets us show the host their ORIGINAL
  // wording on their own screen only, so they aren't tipped off that the filter fired (which would
  // just motivate them to work around it). Only ever populated on the hosting client, which is what
  // scopes the override to "host's screen only". Entries are dropped when the game is removed.
  private final Map<Integer, String> hostOriginalTitles = new ConcurrentHashMap<>();

  // Crash-resilient game-log upload: a marker is written when a game launches and cleared when its
  // logs are submitted at game end. A marker still present at next login means the client died
  // mid-game before uploading, so we upload those logs then. See GameLogUploadMarker.
  private static final String PENDING_LOG_UPLOAD_DIR = "pendingLogUploads";
  private static final long PENDING_LOG_UPLOAD_MAX_AGE_MS = java.time.Duration.ofDays(7).toMillis();
  private final java.util.concurrent.atomic.AtomicBoolean crashedLogUploadsRecovered =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  @Inject
  public GameService(ClientProperties clientProperties,
                     FafService fafService,
                     TotalAnnihilationService totalAnnihilationService,
                     MapService mapService,
                     PreferencesService preferencesService,
                     GameUpdater gameUpdater,
                     NotificationService notificationService,
                     I18n i18n,
                     ExecutorService executorService,
                     PlayerService playerService,
                     EventBus eventBus,
                     IceAdapter iceAdapter,
                     ModService modService,
                     PlatformService platformService,
                     DiscordRichPresenceService discordRichPresenceService,
                     ReconnectTimerService reconnectTimerService,
                     ChatService chatService,
                     RatingService ratingService,
                     HotfixService hotfixService,
                     LadderPointsService ladderPointsService) {

    this.clientProperties = clientProperties;
    this.fafService = fafService;
    this.totalAnnihilationService = totalAnnihilationService;
    this.mapService = mapService;
    this.preferencesService = preferencesService;
    this.gameUpdater = gameUpdater;
    this.notificationService = notificationService;
    this.i18n = i18n;
    this.executorService = executorService;
    this.playerService = playerService;
    this.eventBus = eventBus;
    this.iceAdapter = iceAdapter;
    this.modService = modService;
    this.platformService = platformService;
    this.discordRichPresenceService = discordRichPresenceService;
    this.reconnectTimerService = reconnectTimerService;
    this.chatService = chatService;
    this.ratingService = ratingService;
    this.hotfixService = hotfixService;
    this.ladderPointsService = ladderPointsService;

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
          log.info("[afterPropertiesSet][currentGame.listener] killGame because currentPlayer.getStatus != PLAYING");
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

      InvalidationListener listener = generateStartBattleRoomListener(newValue);
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
  private InvalidationListener generateStartBattleRoomListener(Game game) {
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

        if (currentPlayer.getStatus() == PlayerStatus.JOINING
            && currentGame.getStatus() == GameStatus.BATTLEROOM
            && preferencesService.getPreferences().getAutoLaunchOnJoinEnabled()
        ) {
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

        // Reserved-slots: the "Hold your spot?" prompt is triggered by the
        // server sending a `notice` with style `reserved_slot_auto_reserved`
        // (see ReservedSlotsNotificationService.onNotice). A status-listener
        // trigger here doesn't work for joiners because their disconnect
        // doesn't put the game itself into ENDED — the game stays in
        // BATTLEROOM for the other players.

        // Reserved-slots feature: snapshot the roster of any game we were
        // actually part of at launch and game-end, for the "Add players
        // from last game" button. Launch captures the at-launch roster
        // even if the game later crashes mid-flight without an ENDED
        // transition; end captures the final roster (which can change
        // due to mid-game drops/joins on some mods).
        if ((newStatus == GameStatus.LAUNCHING || newStatus == GameStatus.ENDED)
            && Objects.equals(currentGame, game)) {
          GameService.this.snapshotRosterForLastGamePref(game);
        }

        if (Objects.equals(currentGame, game) && currentGameStatusProperty.get() != newStatus) {
          currentGameStatusProperty.setValue(newStatus);
        }

        if (newStatus == GameStatus.BATTLEROOM && Objects.equals(currentGame, game)) {
          discordRichPresenceService.updatePlayedGameTo(currentGame, currentPlayer.getId(), currentPlayer.getUsername());
          if (currentPlayer.getStatus() == PlayerStatus.JOINING
              && preferencesService.getPreferences().getAutoLaunchOnJoinEnabled()
          ) {
            GameService.this.startBattleRoom();
          }
        }

        if (preferencesService.getPreferences().getAutoRehostEnabled()
            && newStatus == GameStatus.BATTLEROOM
            && game.getGameType() != GameType.MATCHMAKER
            && currentPlayer != null && currentPlayer.getUsername().equals(game.getHost())
        ) {
          eventBus.post(new RehostRequestEvent(game));
        }
      }
    };
  }

  /**
   * Push a new reserved-players list to the server for the player's current
   * hosted game. Caller is responsible for being the host of a STAGING/BATTLEROOM
   * game — server-side checks gate this regardless.
   */
  public void sendReservedPlayers(List<Integer> playerIds) {
    fafService.setReservedPlayers(playerIds);
  }

  /**
   * Leave a game while reserving the slot for a short return window. The
   * server side ({@code command_leave_and_reserve}) records a 5-minute TTL
   * against the player so the slot is held if they re-join in that window.
   * The caller is responsible for the actual game-process termination (via
   * {@link #killGame()} or the existing leave flow) — this method only
   * tells the server to record the reservation intent first.
   */
  public void leaveAndReserve(int gameId) {
    fafService.leaveAndReserve();
  }

  /** Race-defense for the leave dialog: if the user picks Reserve / Just-leave
   *  client-side, we send {@code cancel_reservation} (or {@code leave_and_reserve})
   *  before killing TA. The server normally processes that command and sets its
   *  {@code auto_reserve_opt_out} flag before the disconnect lands — in which
   *  case it skips the auto-reserve + notice entirely and this set is unused.
   *  But if the disconnect overtakes the command on the network, the server
   *  fires {@code reserved_slot_auto_reserved} anyway and the client gets a
   *  redundant prompt. The set absorbs that race. One-shot per gameId. */
  private final Set<Integer> suppressedAutoReservePromptGameIds = ConcurrentHashMap.newKeySet();

  /**
   * Mark a game's leave-dialog as already answered so the follow-up
   * {@code reserved_slot_auto_reserved} notice (if any wins the race against
   * our outbound {@code cancel_reservation} / {@code leave_and_reserve})
   * doesn't re-prompt.
   */
  public void markBattleroomExitHandled(int gameId) {
    suppressedAutoReservePromptGameIds.add(gameId);
  }

  /**
   * Prompts the player after they exit TA from battleroom to hold their slot
   * for 5 minutes. Triggered from the game-status listener when their own
   * game transitions BATTLEROOM -> ENDED. The server has already auto-reserved
   * the player's slot with a 30-second TTL at disconnect time, so:
   *   - "Reserve my spot" upgrades the TTL to 5 minutes.
   *   - "Don't reserve" explicitly cancels the auto-reservation.
   *   - Ignoring the prompt lets the 30s TTL auto-expire.
   * No Cancel option — the game already exited; only the reservation choice
   * is meaningful here.
   */
  void offerReserveOnBattleroomExit(int gameId) {
    if (suppressedAutoReservePromptGameIds.remove(gameId)) {
      return;
    }
    notificationService.addNotification(new ImmediateNotification(
        i18n.get("reservedSlots.leaveDialog.title"),
        i18n.get("reservedSlots.exitedDialog.message"),
        Severity.INFO,
        List.of(
            new Action(i18n.get("reservedSlots.leaveDialog.reserveAndLeave"),
                ev -> leaveAndReserve(gameId)),
            new Action(i18n.get("reservedSlots.exitedDialog.dontReserve"),
                ev -> fafService.cancelReservation())
        )
    ));
  }

  /**
   * Apply the reserved-slots config to the just-hosted game once it has
   * entered STAGING/BATTLEROOM. Idempotent and safe to call before the
   * hostGame future resolves: if the game is already there, applies
   * immediately; otherwise installs a one-shot listener that fires on
   * first STAGING/BATTLEROOM transition and cancels itself if the game
   * is abandoned before reaching that state.
   */
  public void applyReservedSlotsOnceStaging(boolean enabled, List<Integer> playerIds) {
    if (!enabled) {
      return;
    }
    Runnable apply = () -> {
      fafService.setReservedSlotsEnabled(true);
      if (playerIds != null && !playerIds.isEmpty()) {
        fafService.setReservedPlayers(playerIds);
      }
    };
    Game game = getCurrentGame();
    if (game != null
        && (game.getStatus() == GameStatus.STAGING || game.getStatus() == GameStatus.BATTLEROOM)) {
      apply.run();
      return;
    }
    ChangeListener<GameStatus> oneShot = new ChangeListener<>() {
      @Override
      public void changed(javafx.beans.value.ObservableValue<? extends GameStatus> obs,
                          GameStatus oldStatus, GameStatus newStatus) {
        if (newStatus == GameStatus.STAGING || newStatus == GameStatus.BATTLEROOM) {
          currentGameStatusProperty.removeListener(this);
          apply.run();
        } else if (newStatus == GameStatus.ENDED) {
          currentGameStatusProperty.removeListener(this);
        }
      }
    };
    currentGameStatusProperty.addListener(oneShot);
  }

  /**
   * Snapshot the player roster of the given game into {@link LastGamePrefs}
   * for the reserved-slots "Add players from last game" button. Players that
   * aren't in {@link PlayerService}'s cache are skipped (we can't resolve
   * them to an id, which is what the reserved-list needs).
   */
  private void snapshotRosterForLastGamePref(Game game) {
    if (game == null || game.getTeams() == null) {
      return;
    }
    List<Integer> ids = new ArrayList<>();
    List<String> logins = new ArrayList<>();
    for (List<String> teamMembers : game.getTeams().values()) {
      if (teamMembers == null) continue;
      for (String login : teamMembers) {
        playerService.getPlayerForUsername(login).ifPresent(p -> {
          ids.add(p.getId());
          logins.add(login);
        });
      }
    }
    LastGamePrefs prefs = preferencesService.getPreferences().getLastGame();
    prefs.setLastGameRosterPlayerIds(ids);
    prefs.setLastGameRosterPlayerLogins(logins);
    preferencesService.storeInBackground();
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
    boolean autoLaunch = preferencesService.getPreferences().getAutoLaunchOnHostEnabled();

    autoJoinRequestedGameProperty.set(null);
    recentHostGameRequest = newGameInfo;
    return updateGameIfNecessary(newGameInfo.getFeaturedMod(), newGameInfo.getFeaturedModVersionKey())
        .thenCompose(modVersionKey -> {
          newGameInfo.setFeaturedModVersionKey(modVersionKey);
          return fafService.requestHostGame(newGameInfo);
        })
        .thenAccept(gameLaunchMessage -> {
          // Remember the host's original title so their own screen keeps showing it even if the
          // server rewrote it via the badword filter. Populated only here, on the hosting client,
          // which is what scopes the override to "host's screen only".
          if (newGameInfo.getTitle() != null) {
            hostOriginalTitles.put(gameLaunchMessage.getUid(), newGameInfo.getTitle());
            // A GAME_INFO carrying the (possibly rewritten) title may already have created the Game;
            // re-apply the original now so the host never briefly sees the rewritten wording.
            Game hostedGame = getByUid(gameLaunchMessage.getUid());
            if (hostedGame != null) {
              JavaFxUtil.runLater(() -> hostedGame.setTitle(newGameInfo.getTitle()));
            }
          }
          // Join the channel the server assigned to this game (decoupled from the title). Fall back
          // to deriving it from the host's name + the server's returned game name for older servers
          // that don't send a channel. Crucially this uses gameLaunchMessage.getName() (the server's
          // possibly-rewritten title), NOT newGameInfo.getTitle() (our original), so the host lands
          // in the same channel the joiners derive from the broadcast title.
          String channel = gameLaunchMessage.getChatChannel() != null && !gameLaunchMessage.getChatChannel().isBlank()
              ? gameLaunchMessage.getChatChannel()
              : getInGameIrcChannel(getCurrentPlayer().getUsername(), gameLaunchMessage.getName());
          String inGameIrcUrl = getInGameIrcUrl(channel);
          startGame(gameLaunchMessage, inGameIrcUrl, autoLaunch, playerService.getCurrentPlayer().get().getUsername());
        });
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
    autoJoinRequestedGameProperty.set(null);
    setRunningGameUid(game.getId());  // set it early so create-game button disabled during setup

    return
        modService.getFeaturedMod(game.getFeaturedMod())
        .thenCompose(featuredModBean -> updateGameIfNecessary(featuredModBean, game.getFeaturedModVersion() != null
            ? game.getFeaturedModVersion() : featuredModBean.getGitBranch()))
        .thenCompose(aVoid -> mapService.optionalEnsureMap(game.getFeaturedMod(), game.getMapName(), game.getMapCrc(), game.getMapArchiveName(), null, null))
        .exceptionally(throwable -> {
          log.warn("Exception preparing to join game", throwable);
          notificationService.addImmediateErrorNotification(throwable, "games.errorInPreparing");
          return null;
        })
        .thenCompose(ensuredMap -> fafService.requestJoinGame(game.getId(), password))
        .thenAccept(gameLaunchMessage -> {
          if (gameLaunchMessage != null) {
            // Store password in case we rehost
            game.setPassword(password);
            setRunningGameUid(game.getId());
            boolean autoLaunch = preferencesService.getPreferences().getAutoLaunchOnJoinEnabled() && game.getStatus() == GameStatus.BATTLEROOM;
            startGame(gameLaunchMessage, inGameIrcUrl, autoLaunch, playerService.getCurrentPlayer().get().getUsername());
          }
          else {
            setRunningGameUid(null);
          }
        })
        .exceptionally(throwable -> {
          log.warn("Game could not be joined", throwable);
          notificationService.addImmediateErrorNotification(throwable, "games.couldNotJoin");
          setRunningGameUid(null);
          return null;
        });
  }

  public CompletableFuture<Void> runWithReplay(DemoFileInfo demoFileInfo) {
    final FeaturedMod[] mod = new FeaturedMod[] {null};
    final String[] mapName = new String[] {null};
    final String[] mapCrc = new String[] {null};
    final String[] mapArchive = new String[] {null};

    return modService.findFeaturedModByTaDemoFileInfo(demoFileInfo)
        .thenAccept(featuredMod -> mod[0] = featuredMod)
        .thenCompose(aVoid -> fafService.findMapByTaDemoMapHash(demoFileInfo.getMapHash()))
        .thenAccept(mapBeanOptional -> mapBeanOptional.ifPresent(v -> {
          mapName[0] = v.getMapName();
          mapCrc[0] = v.getCrcValue();
          mapArchive[0] = v.getHpiArchiveName();
        }))
        .thenCompose(aVoid -> {
          if (mod[0] == null || mod[0].getVersions().isEmpty()) {
            return fafService.getFeaturedMods()
                .thenCompose(featuredMods -> promptMod(featuredMods, demoFileInfo))
                .thenCompose(fm -> {
                  mod[0] = fm;
                  if (preferencesService.getTotalAnnihilation(fm.getTechnicalName()).getAutoUpdateEnable() == AskAlwaysOrNever.NEVER) {
                    return CompletableFuture.completedFuture(fm.getGitBranch());
                  }
                  else {
                    return promptModVersion(fm, demoFileInfo).thenApply(FeaturedModVersion::getGitBranch);
                  }
                })
                .thenCompose(userSelectedVersion ->
                    runWithReplay(demoFileInfo.getFilePath(), 0, mod[0].getTechnicalName(),
                        userSelectedVersion, mapName[0], mapCrc[0], mapArchive[0]));
          }
          else {
            return runWithReplay(demoFileInfo.getFilePath(), 0, mod[0].getTechnicalName(),
                mod[0].getVersions().get(0).getGitBranch(),
                mapName[0], mapCrc[0], mapArchive[0]);
          }
        });
  }

  private CompletableFuture<FeaturedMod> promptMod(List<FeaturedMod> featuredMods, DemoFileInfo demoFileInfo) {
    CompletableFuture<FeaturedMod> future = new CompletableFuture<>();
    List<Action> selectModActionList = featuredMods.stream()
        .filter(FeaturedMod::isVisible)
        .map(fm -> new Action(fm.getDisplayName(), (a) -> future.complete(fm)))
        .collect(Collectors.toList());

    notificationService.addNotification(new ImmediateNotification(
        i18n.get("replay.selectMod.title"),
        i18n.get("replay.selectMod.text", demoFileInfo.getModHash(),
            demoFileInfo.getTaVersionMajor(), demoFileInfo.getTaVersionMinor()),
        Severity.INFO, selectModActionList));

    return future;
  }

  private CompletableFuture<FeaturedModVersion> promptModVersion(FeaturedMod fm, DemoFileInfo demoFileInfo) {
    CompletableFuture<FeaturedModVersion> future = new CompletableFuture<>();
    List<Action> selectModActionList = fm.getVersions().stream()
        .map(fmv -> new Action(fmv.getDisplayName(), (a) -> future.complete(fmv)))
        .collect(Collectors.toList());

    notificationService.addNotification(new ImmediateNotification(
        i18n.get("replay.selectModVersion.title", fm.getDisplayName()),
        i18n.get("replay.selectModVersion.text", demoFileInfo.getModHash(),
            demoFileInfo.getTaVersionMajor(), demoFileInfo.getTaVersionMinor()),
        Severity.INFO, selectModActionList));

    return future;
  }

  public CompletableFuture<Void> runWithReplay(String replayFileOrUrl, Game game) {
    if (game.getFeaturedModVersion() != null) {
      return runWithReplay(
          replayFileOrUrl, game.getId(), game.getFeaturedMod(), game.getFeaturedModVersion(),
          game.getMapName(), game.getMapCrc(), game.getMapArchiveName());
    }
    else {
      return modService.getFeaturedMod(game.getFeaturedMod())
          .thenCompose(featuredModBean -> runWithReplay(
              replayFileOrUrl, game.getId(), game.getFeaturedMod(), featuredModBean.getGitBranch(),
              game.getMapName(), game.getMapCrc(), game.getMapArchiveName()));
    }
  }

  /**
   * @param replayFileOrUrl Either a path to a locally available file, or a url eg taforever.com:15000/1234
   */
  public CompletableFuture<Void> runWithReplay(
      String replayFileOrUrl, Integer replayId, String modTechnical, @Nullable String modVersion,
      @Nullable String mapName, @Nullable String mapCrc, @Nullable String mapArchive) {

    if (!canStartReplay()) {
      return completedFuture(null);
    }

    if (!preferencesService.isGameExeValid(modTechnical)) {
      CompletableFuture<Path> gameDirectoryFuture = postGameDirectoryChooseEvent(modTechnical);
      gameDirectoryFuture.thenAccept(pathSet -> runWithReplay(
          replayFileOrUrl, replayId, modTechnical, modVersion, mapName, mapCrc, mapArchive));
      return completedFuture(null);
    }

    onMatchmakerSearchStopped();

    return modService.getFeaturedMod(modTechnical)
        .thenCompose(featuredModBean -> updateGameIfNecessary(featuredModBean, modVersion))
        .thenCompose(aVoid -> mapService.optionalEnsureMap(modTechnical, mapName, mapCrc, mapArchive, null, null))
        .thenAccept((ensuredMap) -> {
          try {
            if (isGameRunning()) {
              return;
            }
            if (!hotfixService.applyModFileHotfixes(modTechnical)) {
              log.warn("[runWithReplay] aborting replay: mandatory mod-file hotfix failed for {}", modTechnical);
              notificationService.addImmediateWarnNotification("hotfix.modFileFailed", modTechnical);
              return;
            }

            Process launchServerProcess = noCatch(() -> totalAnnihilationService.startLaunchServer(modTechnical, replayId));
            spawnGenericTerminationListener(launchServerProcess);

            this.process = totalAnnihilationService.startReplay(modTechnical, replayFileOrUrl, replayId, getCurrentPlayer().getUsername());
            setRunningGameUid(-1);

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
    ImmediateNotification notification = new ImmediateNotification(title, text, Severity.INFO, new ArrayList<>());
    notification.getActions().addAll(asList(
        new Action(i18n.get("replay.running.terminate"), ev -> process.destroy()),
        new DismissAction(i18n)
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
    // Prefer the channel the server assigned (decoupled from the title). Only fall back to
    // deriving it from host+title for older servers that don't send one. NOTE: on the hosting
    // client game.getTitle() may have been overridden to the host's original wording, so the
    // fallback must not be relied on to agree with joiners when the title was rewritten — that
    // agreement is exactly what the server-sent channel provides.
    String serverChannel = game.getChatChannel();
    if (serverChannel != null && !serverChannel.isBlank()) {
      return serverChannel;
    }
    // Fallback for older servers: derive from the server's (rewritten) title, NOT the displayed
    // title, which on the hosting client is the host's original wording. Deriving from serverTitle
    // keeps host and joiners on the same channel.
    return getInGameIrcChannel(game.getHost(), game.getServerTitle());
  }

  public String getInGameIrcChannel(NewGameInfo gameInfo) {
    return getInGameIrcChannel(getCurrentPlayer().getUsername(), gameInfo.getTitle());
  }

  /** Finds the game whose chat channel is {@code channelName}, matching against the server-assigned
   *  channel (or the host+title fallback for older servers). Used by the chat UI to label a game-room
   *  channel tab with the game's title instead of the raw channel name. */
  public Optional<Game> findGameByChatChannel(String channelName) {
    if (channelName == null) {
      return Optional.empty();
    }
    synchronized (uidToGameInfoBean) {
      return uidToGameInfoBean.values().stream()
          .filter(game -> channelName.equals(getInGameIrcChannel(game)))
          .findFirst();
    }
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

  /**
   * Quiet {@link #getByUid(int)} that returns empty instead of logging when the game is unknown.
   * Used by {@code PlayerService}'s association back-fill, where a miss is an expected transient
   * (the player's game may simply not be in the client's game list) and must not spam the log.
   */
  public Optional<Game> findByUid(int uid) {
    return Optional.ofNullable(uidToGameInfoBean.get(uid));
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
        .thenAccept(featuredModBean -> updateGameIfNecessary(featuredModBean, null))
        .thenCompose(aVoid -> fafService.startSearchMatchmaker())
        .thenAccept((gameLaunchMessage) ->
            mapService.optionalEnsureMap(gameLaunchMessage.getMod(), gameLaunchMessage.getMapname(), gameLaunchMessage.getMapCrc(), gameLaunchMessage.getMapArchive(), null, null)
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

  private CompletableFuture<String> updateGameIfNecessary(FeaturedMod featuredMod, @Nullable String version) {
    return gameUpdater.update(featuredMod, version);
  }

  public CompletableFuture<Void> proactiveUpdateCurrentVersions() {
    return gameUpdater.proactiveUpdateCurrentVersions();
  }

  private void setRunningGameUid(Integer uidOrNull) {
    try {
      noCatch(() -> runningGameUidProperty.setValue(uidOrNull));
    }
    catch(Exception e) {
      log.warn("[setRunningGameUid] {}", e.getMessage());
    }
  }

  public Integer getRunningGameUid() {
    return runningGameUidProperty.getValue();
  }

  public ReadOnlyIntegerProperty runningGameUidProperty() {
    return runningGameUidProperty;
  }

  public void startBattleRoom() {
    if (isGameRunning()) {
      Game game = getCurrentGame();
      mapService.optionalEnsureMap(game.getFeaturedMod(), game.getMapName(), game.getMapCrc(), game.getMapArchiveName(), null, null)
          .thenRun(() -> {
            List<Player> joinOrder = computeStartOrder(game);
            this.totalAnnihilationService.sendToConsole("/set_hash_api_token " + this.fafService.getApiAccessToken());
            boolean sequenced = joinOrder.size() > 2 && preferencesService.getPreferences().getSequencedLaunchEnabled();
            // TA's start-position mode maps directly off the host's fixed/random
            // pill — no longer inferred from whether positions happen to be
            // managed (requests / pins / sequenced launch). The host decides;
            // if they want preselection, pins or a sequenced order to land on
            // specific positions they set the pill to fixed (the picker is
            // already gated on it). gpgnet4ta writes this straight to
            // TAForever.ini location (1=fixed, 2=random).
            boolean fixedPositions = game.isFixedPositionsEnabled();
            this.totalAnnihilationService.sendToConsole("/fixed_positions " + (fixedPositions ? "1" : "0"));
            if (sequenced) {
              this.totalAnnihilationService.sendToConsole("/launch " +
                  String.join(",", joinOrder.stream().map(p -> String.valueOf(p.getId())).toList()));
            } else {
              this.totalAnnihilationService.sendToConsole("/launch");
            }
          })
          .exceptionally(throwable -> {
            log.error("[startBattleRoom] error starting battleroom", throwable);
            return null;
          });
    }
  }

  public void setStartPositions() {
    Game game = getCurrentGame();
    if (game != null) {
      setStartPositions(game);
    }
  }

  public void setStartPositions(Game game) {
    // A manual arrangement or a player's position request is an explicit
    // action, so honour it even when the auto-team-balance preference is off.
    // Otherwise fall back to the preference gate for the auto-balanced
    // solution.
    if (!hasManualTeams(game) && !hasPositionRequests(game)
        && !preferencesService.getPreferences().getAutoTeamBalanceEnabled()) {
      return;
    }
    List<Player> positions = computeStartOrder(game);
    if (isGameRunning() && getCurrentGame() == game) {
      if (positions != null && !positions.isEmpty()) {
        this.totalAnnihilationService.sendToConsole("/startpositions " +
            String.join(",", positions.stream().map(p -> String.valueOf(p.getId())).toList()));
      }
    }
  }

  /** The host's pinned team constraints, keyed by player id -> team index
   *  (0 or 1), and the game id they apply to. Only the pinned players are
   *  stored; everyone else is auto-balanced around them. Overrides the
   *  unconstrained auto-balanced {@code /startpositions} solution while set. */
  private Integer manualTeamGameId;
  private Map<Integer, Integer> manualTeamByPlayerId;

  /**
   * Store the host's pinned team constraints for {@code game} and immediately
   * push the resulting start-position order to the running game so a
   * subsequent {@code +autoteam} uses it. An empty map clears the constraints
   * (pure auto-balance).
   *
   * @param pinnedTeamByPlayerId pinned player id -> team index (0 or 1)
   */
  public void setManualTeams(Game game, Map<Integer, Integer> pinnedTeamByPlayerId) {
    if (game == null) {
      return;
    }
    if (pinnedTeamByPlayerId == null || pinnedTeamByPlayerId.isEmpty()) {
      clearManualTeams();
    } else {
      this.manualTeamGameId = game.getId();
      this.manualTeamByPlayerId = new HashMap<>(pinnedTeamByPlayerId);
      log.info("[setManualTeams] host pinned {} players to teams for game {}",
          pinnedTeamByPlayerId.size(), game.getId());
    }
    // Propagate the host's pins to the server so every client can show them in
    // the team cards (parallel id/team lists; empty clears them server-side).
    List<Integer> pinIds = new ArrayList<>();
    List<Integer> pinTeams = new ArrayList<>();
    if (pinnedTeamByPlayerId != null) {
      pinnedTeamByPlayerId.forEach((id, team) -> {
        pinIds.add(id);
        pinTeams.add(team);
      });
    }
    fafService.setPinnedTeams(pinIds, pinTeams);
    setStartPositions(game);
  }

  /**
   * Host toggle: enable/disable start-position preselection for {@code game}.
   * Propagated to the server, which rebroadcasts it in GAME_INFO so every
   * client shows or hides the position picker accordingly. Disabling clears any
   * outstanding position requests server-side.
   */
  public void setFixedPositionsEnabled(Game game, boolean enabled) {
    if (game == null) {
      return;
    }
    fafService.setFixedPositionsEnabled(enabled);
  }

  /**
   * Host control: change the maximum number of players for a staging game.
   * Sends the same {@code /max_players} console command used by the Create Game
   * dialog's update mode; the server rebroadcasts the new cap in GAME_INFO.
   * No-op unless we're the host of the current staging game and the value is 2-10.
   */
  public void setMaxPlayers(Game game, int maxPlayers) {
    if (game == null || maxPlayers < 2 || maxPlayers > 10) {
      return;
    }
    Game currentGame = getCurrentGame();
    if (isGameRunning() && currentGame != null && currentGame.getStatus() == GameStatus.STAGING) {
      this.totalAnnihilationService.sendToConsole(String.format("/max_players %d", maxPlayers));
    }
  }

  public void clearManualTeams() {
    this.manualTeamGameId = null;
    this.manualTeamByPlayerId = null;
  }

  /** @return a copy of the pinned constraints for {@code gameId}, or {@code null}
   *  if none are in effect for that game. */
  public Map<Integer, Integer> getManualTeams(int gameId) {
    if (manualTeamByPlayerId != null && manualTeamGameId != null && manualTeamGameId == gameId) {
      return new HashMap<>(manualTeamByPlayerId);
    }
    return null;
  }

  private boolean hasManualTeams(Game game) {
    return game != null && manualTeamByPlayerId != null && !manualTeamByPlayerId.isEmpty()
        && manualTeamGameId != null && manualTeamGameId == game.getId();
  }

  private boolean hasPositionRequests(Game game) {
    if (game == null) {
      return false;
    }
    synchronized (game.getPositionRequests()) {
      return !game.getPositionRequests().isEmpty();
    }
  }

  /**
   * The ordered player list to send via {@code /startpositions}, honouring (in
   * priority order) the host's manual team pins, then the players' start-position
   * preselections, then rating balance. A start-position pair straddles both
   * teams, so any pair two players both requested forces them onto opposite
   * teams; the balancer then optimises rating balance around those constraints.
   * The result is an equal (or off-by-one) two-team partition interleaved so
   * tadr-ddraw's {@code allyTeam = i % teamCount} mapping reproduces it on
   * {@code +autoteam}.
   *
   * <p>Host pins win over preselection: a preselection the pins can't satisfy is
   * dropped by the balancer. If the pins themselves have become infeasible after
   * roster churn the balancer falls back to pure auto-balance rather than send
   * nothing. Within-team seating ({@link #applyPositionRequests}) then places each
   * requester at their pair's slot.</p>
   */
  private List<Player> computeStartOrder(Game game) {
    Map<Integer, Integer> positionRequests;
    synchronized (game.getPositionRequests()) {
      positionRequests = new LinkedHashMap<>(game.getPositionRequests());
    }
    Map<Integer, Integer> hostPins = hasManualTeams(game) ? manualTeamByPlayerId : Map.of();
    List<int[]> oppositeTeamPairs = oppositeTeamPairsFromRequests(positionRequests, game.getMaxPlayers());
    List<Player> order = this.ratingService.getBalancedTeams(game, hostPins, oppositeTeamPairs);
    return order == null ? List.of() : applyPositionRequests(positionRequests, order);
  }

  /**
   * A pair (role) requested by two players must place those two on opposite
   * teams, since a pair occupies one mirrored map position on each side. Returns
   * one {@code [playerIdA, playerIdB]} constraint per such pair, in request
   * order. Roles beyond the game's available pairs are ignored, and a third-or-
   * later requester of an already-full pair is dropped (the server rejects those,
   * but be defensive against races).
   */
  static List<int[]> oppositeTeamPairsFromRequests(Map<Integer, Integer> requests, int maxPlayers) {
    int numPairs = Math.min(10, maxPlayers) / 2;
    Map<Integer, List<Integer>> byRole = new LinkedHashMap<>();
    for (Entry<Integer, Integer> e : requests.entrySet()) {
      Integer role = e.getValue();
      if (role == null || role < 0 || role >= numPairs) {
        continue;
      }
      byRole.computeIfAbsent(role, r -> new ArrayList<>()).add(e.getKey());
    }
    List<int[]> edges = new ArrayList<>();
    for (List<Integer> requesters : byRole.values()) {
      if (requesters.size() >= 2) {
        edges.add(new int[]{requesters.get(0), requesters.get(1)});
      }
    }
    return edges;
  }

  /**
   * Reorder the interleaved start order within each team so players' position
   * role requests are honoured where possible. The interleave convention maps
   * list index i to map position i+1 and team i%2, so a player placed at index
   * 2r (team A) or 2r+1 (team B) receives role r — one of the two mirrored map
   * start positions of that pair. Team membership is never changed, only the
   * order of players within a team, so balance and pins are unaffected.
   *
   * <p>Requests are granted in {@code requests} iteration order (the server's
   * request order, i.e. first-come-first-served on same-role conflicts). A
   * request for role r is only satisfiable when r is a valid role for the
   * requester's team (r &lt; team size). Players without a granted request
   * keep their relative order in the leftover roles.</p>
   */
  static List<Player> applyPositionRequests(Map<Integer, Integer> requests, List<Player> order) {
    if (requests == null || requests.isEmpty() || order.size() < 2) {
      return order;
    }

    List<List<Player>> teams = List.of(new ArrayList<>(), new ArrayList<>());
    for (int i = 0; i < order.size(); i++) {
      teams.get(i % 2).add(order.get(i));
    }

    for (List<Player> team : teams) {
      Player[] byRole = new Player[team.size()];
      Set<Integer> placedIds = new HashSet<>();
      for (Entry<Integer, Integer> request : requests.entrySet()) {
        int role = request.getValue();
        if (role < 0 || role >= team.size() || byRole[role] != null) {
          continue;
        }
        team.stream()
            .filter(p -> p.getId() == request.getKey() && !placedIds.contains(p.getId()))
            .findFirst()
            .ifPresent(p -> {
              byRole[role] = p;
              placedIds.add(p.getId());
            });
      }
      // Everyone else fills the leftover roles in their existing order.
      int nextRole = 0;
      for (Player player : team) {
        if (placedIds.contains(player.getId())) {
          continue;
        }
        while (byRole[nextRole] != null) {
          nextRole++;
        }
        byRole[nextRole] = player;
      }
      team.clear();
      team.addAll(Arrays.asList(byRole));
    }

    List<Player> result = new ArrayList<>(order.size());
    for (int r = 0; r < teams.get(0).size(); r++) {
      result.add(teams.get(0).get(r));
      if (r < teams.get(1).size()) {
        result.add(teams.get(1).get(r));
      }
    }
    return result;
  }

  /**
   * Change just the maximum player count of the current staging game. Only
   * meaningful while the game is in STAGING (the staging lobby owns the slot
   * count); ignored otherwise.
   */
  public void setMaxPlayersForStagingGame(int maxPlayers) {
    Game currentGame = getCurrentGame();
    if (isGameRunning() && currentGame != null && currentGame.getStatus() == GameStatus.STAGING
        && maxPlayers > 0 && maxPlayers <= 10) {
      this.totalAnnihilationService.sendToConsole(String.format("/max_players %d", maxPlayers));
    }
  }

  public void updateSettingsForStagingGame(String title, String mapName, String ratingType, LiveReplayOption liveReplayOption, String password, int maxPlayers, boolean enforceRating, Integer minRating, Integer maxRating) {
    Game currentGame = getCurrentGame();
    if (isGameRunning() && currentGame != null && currentGame.getStatus()==GameStatus.STAGING) {
      try {
        List<String[]> mapsDetails = MapTool.listMap(preferencesService.getTotalAnnihilation(currentGame.getFeaturedMod()).getInstalledPath(), mapName);
        if (mapsDetails.size() > 0) {
          final String UNIT_SEPARATOR = Character.toString((char) 0x1f);
          String mapDetails = String.join(UNIT_SEPARATOR, mapsDetails.get(0));
          this.totalAnnihilationService.sendToConsole(String.format("/map %s", mapDetails));
        }
        if (ratingType != null && ratingType.length() > 0) {
          this.totalAnnihilationService.sendToConsole(String.format("/rating_type %s", ratingType));
          if (ratingType.equals(DEFAULT_RATING_TYPE)) {
            this.totalAnnihilationService.sendToConsole("/disable_game_file_version_verify");
          }
          else {
            this.totalAnnihilationService.sendToConsole("/enable_game_file_version_verify");
          }
        }
        if (liveReplayOption != null) {
          this.totalAnnihilationService.sendToConsole(String.format("/replay_delay_seconds %s", liveReplayOption.getDelaySeconds()));
        }
        if (password != null) {
          this.fafService.setGamePassword(password);
        }
        if (maxPlayers > 0 && maxPlayers <= 10) {
          this.totalAnnihilationService.sendToConsole(String.format("/max_players %d", maxPlayers));
        }

        if (minRating != null && maxRating != null) {
          this.fafService.sendGpgGameMessage(new GpgGameMessage("SetGameRatingRange", List.of(minRating, maxRating, enforceRating)));
        }
        else if (minRating != null) {
          this.fafService.sendGpgGameMessage(new GpgGameMessage("SetGameRatingMin", List.of(minRating, enforceRating)));
        }
        else if (maxRating != null) {
          this.fafService.sendGpgGameMessage(new GpgGameMessage("SetGameRatingMax", List.of(maxRating, enforceRating)));
        }
        else {
          this.fafService.sendGpgGameMessage(new GpgGameMessage("ClearGameRatingRange", List.of()));
        }
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
    final Integer [] adapterPort = {null};
    iceAdapter.start(playerAlias, uid)
        .thenAccept(ap -> adapterPort[0] = ap)
        .thenCompose((aVoid) -> this.fafService.getLeaderboards())
        .thenAccept(availableLeaderboards -> {
          if (!hotfixService.applyModFileHotfixes(modTechnical)) {
            log.warn("[startGame] aborting launch: mandatory mod-file hotfix failed for {}", modTechnical);
            notificationService.addImmediateWarnNotification("hotfix.modFileFailed", modTechnical);
            iceAdapter.stop();
            fafService.notifyGameEnded();
            return;
          }
          List<String> args = fixMalformedArgs(gameLaunchMessage.getArgs());

          Process launchServerProcess = noCatch(() -> totalAnnihilationService.startLaunchServer(modTechnical, uid));
          spawnGenericTerminationListener(launchServerProcess);

          String demoCompilerUrl = String.format("%s:%s/%s",
              clientProperties.getReplay().getRemoteHost(), clientProperties.getReplay().getCompilerPort(), uid);

          boolean isRated = availableLeaderboards.stream().anyMatch(
              lb -> lb.getTechnicalName().equals(gameLaunchMessage.getRatingType()));

          log.info("[startGame] ratingType={}, isRated={}", gameLaunchMessage.getRatingType(), isRated);

          process = noCatch(() -> totalAnnihilationService.startGame(modTechnical, uid, args,
              adapterPort[0], getCurrentPlayer(), demoCompilerUrl, ircUrl, autoLaunch, isRated));
          setRunningGameUid(uid);
          if (process != null) {
            // Mark this game as "logs not yet uploaded" so that if the client dies before the
            // termination listener runs, the next login uploads them. Cleared in that listener.
            GameLogUploadMarker.mark(
                preferencesService.getFafLogDirectory().resolve(PENDING_LOG_UPLOAD_DIR), uid, modTechnical);
          }
          currentGameStatusProperty.set(GameStatus.SPAWNING);
          spawnGameTerminationListener(process, uid,  modTechnical, null);
        })
        .exceptionally(throwable -> {
          log.warn("Game could not be started", throwable);
          if (throwable.getCause() instanceof ConnectException) {
            notificationService.addImmediateErrorNotification(throwable, "game.start.noConnectIce");
          }
          else {
            notificationService.addImmediateErrorNotification(throwable, "game.start.couldNotStart");
          }
          iceAdapter.stop();
          fafService.notifyGameEnded();
          log.info("[startGame] killGame because start process completed exceptionally");
          this.killGame();
          setRunningGameUid(null);
          return null;
        });
  }

  private void notifyRecentlyPlayedGameEnded(Game game) {
    NotificationsPrefs notification = preferencesService.getPreferences().getNotification();
    if (notification.isAfterGameReviewEnabled() && notification.isTransientNotificationsEnabled()) {
      List<Action> actions = new ArrayList<>();
      if (game.getReplayDelaySeconds() < 0) {
        actions.add(new Action(i18n.get("replay.unhide"), null, Action.Type.OK_ONCE,
            actionEvent -> eventBus.post(new UnhideReplayEvent(game.getId()))));
      }
      actions.add(new Action(i18n.get("tada.upload"), null, Action.Type.OK_ONCE,
          actionEvent -> eventBus.post(new UploadToTadaEvent(game.getId()))));
      actions.add(new Action(i18n.get("scorescreen.viewResult"), null, Action.Type.OK_ONCE,
          actionEvent -> eventBus.post(new ShowScoreScreenEvent(game.getId()))));
      notificationService.addNotification(new PersistentNotification(
          i18n.get("game.ended", game.getTitle()), Severity.INFO, actions));
    }
    // Auto-open the Battle Report unless the user has switched it off (its "Don't show again"
    // button, re-enabled in Settings > Notifications) — and only once the game actually has
    // results to show, so the user is never greeted with "Results unavailable".
    if (notification.isBattleReportEnabled()) {
      autoShowBattleReportWhenReady(game.getId(), 0);
    }
  }

  /** Poll cadence for a just-ended game's results before giving up on auto-opening
   *  the Battle Report (the combat service compiles them asynchronously). */
  private static final int BATTLE_REPORT_POLL_DELAY_SECONDS = 15;
  private static final int BATTLE_REPORT_POLL_MAX_ATTEMPTS = 12;   // ~3 minutes

  /**
   * Auto-open the Battle Report only once {@code gameId} actually has results
   * (the same emptiness check the score screen itself uses). Results are
   * compiled asynchronously by the combat service after game end (demo download
   * + parse + rate), so poll for a few minutes; if nothing ever appears (unrated
   * game type, invalid launch codes, service down) don't open at all. Also skips
   * auto-opening if the user has since started another game — the persistent
   * notification's "View result" action remains as the manual path.
   */
  private void autoShowBattleReportWhenReady(int gameId, int attempt) {
    ladderPointsService.getGameResult(gameId)
        .thenAccept(result -> {
          if (result != null && !result.isEmpty()) {
            JavaFxUtil.runLater(() -> {
              if (isGameRunning()) {
                log.info("[autoShowBattleReport] results for game {} ready but user is in a game; not auto-opening", gameId);
                return;
              }
              eventBus.post(new ShowScoreScreenEvent(gameId));
            });
          } else if (attempt < BATTLE_REPORT_POLL_MAX_ATTEMPTS) {
            CompletableFuture.delayedExecutor(BATTLE_REPORT_POLL_DELAY_SECONDS, TimeUnit.SECONDS, executorService)
                .execute(() -> autoShowBattleReportWhenReady(gameId, attempt + 1));
          } else {
            log.info("[autoShowBattleReport] no results for game {} after {} checks; not opening the battle report",
                gameId, attempt + 1);
          }
        })
        .exceptionally(throwable -> {
          log.warn("[autoShowBattleReport] failed to check results for game {}", gameId, throwable);
          return null;
        });
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
      fafService.uploadGameLogs(gameId, "game", modTechnical);
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
            fafService.uploadGameLogs(gameId, "game", modTechnical);
          }),
          new Action(i18n.get("settings.autoLogsUpload.deny"), Action.Type.OK_DONE, event -> {
            preferencesService.getPreferences().setAutoUploadLogsOption(AutoUploadLogsOption.DENY);
            preferencesService.storeInBackground();
          }))
      ));
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
          iceAdapter.stop();
          fafService.notifyGameEnded();
        }
        catch (TaskRejectedException e) {
        }
      });

      submitLogs(gameId, modTechnical);
      // Reaching here means we observed the game terminate and handled its logs, so this game did
      // not crash the client out from under us: drop its pending-upload marker.
      GameLogUploadMarker.clear(preferencesService.getFafLogDirectory().resolve(PENDING_LOG_UPLOAD_DIR), gameId);
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
    recoverCrashedGameLogUploads();
    if (isGameRunning()) {
      if (getCurrentGame() != null) {
        fafService.restoreGameSession(getCurrentGame().getId());
      }
      else {
        log.info("[onLoggedIn] killGame because currentGame() == null");
        killGame();
      }
    }
  }

  /**
   * Uploads logs for any game whose previous session ended (crash/kill) before it could submit them.
   * Runs once per process on first login, off the calling thread, and before any new game launch can
   * overwrite the fixed-name logs. Reuses {@link #submitLogs} so the user's auto-upload preference
   * (ALLOW/ASK/DENY) is honoured exactly as for a normal game end.
   */
  private void recoverCrashedGameLogUploads() {
    if (!crashedLogUploadsRecovered.compareAndSet(false, true)) {
      return;
    }
    executorService.execute(() -> {
      java.nio.file.Path markerDir = preferencesService.getFafLogDirectory().resolve(PENDING_LOG_UPLOAD_DIR);
      List<GameLogUploadMarker.Pending> pending =
          GameLogUploadMarker.listAndPrune(markerDir, PENDING_LOG_UPLOAD_MAX_AGE_MS);
      int currentUid = runningGameUidProperty.get();
      for (GameLogUploadMarker.Pending p : pending) {
        if (p.gameId() == currentUid) {
          // The game we're currently in will upload its logs normally at game end; leave its marker.
          continue;
        }
        log.info("[recoverCrashedGameLogUploads] previous session ended during game {} before its "
            + "logs were uploaded; uploading now", p.gameId());
        try {
          submitLogs(p.gameId(), p.modTechnical());
        } catch (Exception e) {
          log.warn("[recoverCrashedGameLogUploads] failed to upload recovered logs for game {}: {}",
              p.gameId(), e.toString());
        } finally {
          GameLogUploadMarker.clear(markerDir, p.gameId());
        }
      }
    });
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
    final String password = prototype.isPasswordProtected() && recentHostGameRequest != null
        ? recentHostGameRequest.getPassword()
        : null;

    modService.getFeaturedMod(prototype.getFeaturedMod())
        .thenAccept(featuredModBean -> hostGame(new NewGameInfo(
            prototype.getTitle(),
            password,
            featuredModBean,
            prototype.getFeaturedModVersion(),
            prototype.getMapName(),
            new HashSet<>(prototype.getSimMods().values()),
            prototype.getVisibility(),
            prototype.getMinRating(), prototype.getMaxRating(),
            prototype.getEnforceRating(), prototype.getReplayDelaySeconds(),
            prototype.getRatingType(),
            prototype.getGalacticWarPlanetName(),
            prototype.getMaxPlayers())));
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
    com.faforever.client.StartupProfiler.markOnce("first game_info received");
    if (gameInfoMessage.getGames() != null) {
      gameInfoMessage.getGames().forEach(this::onGameInfo);
      return;
    }

    // We may receive game info before we receive our player info
    Optional<Player> currentPlayerOptional = playerService.getCurrentPlayer();

    boolean isCurrentGameAndPlayersChanged = false;
    if (currentGame.get() != null) {
      isCurrentGameAndPlayersChanged = currentGame.get().getId() == gameInfoMessage.getUid();
      if (isCurrentGameAndPlayersChanged) {
        java.util.Map<?, List<String>> currentGameTeams = currentGame.get().getTeams();
        java.util.Map<?, List<String>> gameInfoTeams = gameInfoMessage.getTeams();
        if (currentGameTeams != null && gameInfoTeams != null) {
          List<String> currentGamePlayers = currentGameTeams.entrySet().stream()
              .filter(entry -> Integer.parseInt(entry.getKey().toString()) >= 2)
              .map(Entry::getValue)
              .flatMap(List::stream)
              .sorted().toList();
          List<String> gameInfoGamePlayers = gameInfoTeams.entrySet().stream()
              .filter(entry -> Integer.parseInt(entry.getKey().toString()) >= 2)
              .map(Entry::getValue)
              .flatMap(List::stream)
              .sorted().toList();
          isCurrentGameAndPlayersChanged = !gameInfoGamePlayers.equals(currentGamePlayers);
        } else {
          isCurrentGameAndPlayersChanged = currentGameTeams != null || gameInfoTeams != null;
        }
      }
    }

    // Detect a position-request change for the current game BEFORE
    // createOrUpdateGame overwrites the model, so the host can re-push
    // /startpositions below just like on a roster change.
    boolean positionRequestsChanged = false;
    if (currentGame.get() != null && currentGame.get().getId() == gameInfoMessage.getUid()) {
      positionRequestsChanged = !new ArrayList<>(currentGame.get().getPositionRequests().entrySet())
          .equals(new ArrayList<>(parsePositionRequests(gameInfoMessage).entrySet()));
    }

    String currentGameRatingType = null;
    if (currentGame.get() != null) {
      currentGameRatingType = currentGame.get().getRatingType();
    }

    Game game = createOrUpdateGame(gameInfoMessage);
    // some control paths null out currentGame but we still need to remember this
    final boolean isGameCurrentGame = Objects.equals(currentGame.get(), game) ||
        Objects.equals(getRunningGameUid(), game.getId());

    if (isGameCurrentGame && currentGameRatingType != null && !currentGameRatingType.equals(game.getRatingType())) {
      if (DEFAULT_RATING_TYPE.equals(game.getRatingType())) {
        this.totalAnnihilationService.sendToConsole("/disable_game_file_version_verify");
      }
      else if (game.getRatingType() != null){
        this.totalAnnihilationService.sendToConsole("/enable_game_file_version_verify");
      }
    }

    if (GameStatus.ENDED == game.getStatus()) {
      if (manualTeamGameId != null && manualTeamGameId == game.getId()) {
        clearManualTeams();
      }
      removeGame(gameInfoMessage);
      if (currentPlayerOptional.isEmpty() || !isGameCurrentGame) {
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
        if (gameInfoMessage.getHost() != null && currentPlayerOptional.get().getUsername().equals(gameInfoMessage.getHost())) {
          if (isCurrentGameAndPlayersChanged || positionRequestsChanged) {
            setStartPositions();
          }
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
        JavaFxUtil.addListener(game.statusProperty(), (observable, oldValue, newValue) -> {
          if (oldValue.isOpen()
              && newValue.isInProgress()
              && game.getTeams().values().stream().anyMatch(team -> playerService.getCurrentPlayer().isPresent() && team.contains(playerService.getCurrentPlayer().get().getUsername()))
              && !platformService.isWindowFocused(faWindowTitle)) {
            platformService.focusWindow(faWindowTitle);
          }
        });
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

    synchronized (game.getPings()) {
      if (gameInfoMessage.getPings() != null) {
        game.pingsProperty().set(FXCollections.observableMap(gameInfoMessage.getPings()));
      }
      else {
        log.info("[updateFromGameInfo] gameInfoMessage.getPings() is null");
      }
    }

    if (gameInfoMessage.getHost() != null) {
      game.setMinRating(gameInfoMessage.getRatingMin());
      game.setMaxRating(gameInfoMessage.getRatingMax());
      game.setEnforceRating(gameInfoMessage.getEnforceRatingRange());
      game.setReplayDelaySeconds(gameInfoMessage.getReplayDelaySeconds());

      game.setHost(gameInfoMessage.getHost());
      // The server assigns the chat channel explicitly; keep it verbatim so host and joiners share
      // one channel regardless of what the title got rewritten to.
      game.setChatChannel(gameInfoMessage.getChatChannel());
      // Always record the server's title verbatim. This is the value the channel is derived from
      // (in the fallback path), so it must NOT be the host's original wording — otherwise the host
      // would derive a different channel than the joiners.
      String serverTitle = StringEscapeUtils.unescapeHtml4(gameInfoMessage.getTitle());
      game.setServerTitle(serverTitle);
      // If we're the host of this game, DISPLAY the title we originally requested over whatever the
      // server broadcast (which may be a badword-rewritten generic title). hostOriginalTitles is
      // only populated on the hosting client, so joiners always see the server's title. Note this
      // only affects the displayed title, never the channel (which uses serverTitle above).
      String hostOriginalTitle = hostOriginalTitles.get(gameInfoMessage.getUid());
      game.setTitle(hostOriginalTitle != null ? hostOriginalTitle : serverTitle);
      game.setMapName(gameInfoMessage.getMapName());
      game.setFeaturedMod(gameInfoMessage.getFeaturedMod());
      game.setGalacticWarPlanetName(gameInfoMessage.getGalacticWarPlanetName());
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
      String[] mapFilePath = gameInfoMessage.getMapFilePath().split("/");   // determined by faf db: archive/name/crc
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

      game.setReservedSlotsEnabled(Boolean.TRUE.equals(gameInfoMessage.getReservedSlotsEnabled()));
      game.setFixedPositionsEnabled(Boolean.TRUE.equals(gameInfoMessage.getFixedPositionsEnabled()));
      synchronized (game.getReservedPlayers()) {
        game.getReservedPlayers().setAll(
            gameInfoMessage.getReservedPlayers() != null
                ? gameInfoMessage.getReservedPlayers()
                : List.of());
      }
      synchronized (game.getReservedPlayerIds()) {
        game.getReservedPlayerIds().setAll(
            gameInfoMessage.getReservedPlayerIds() != null
                ? gameInfoMessage.getReservedPlayerIds()
                : List.of());
      }

      // Host's manual +autoteam pins (parallel id/team lists) -> id->team map.
      synchronized (game.getPinnedTeams()) {
        Map<Integer, Integer> pins = new HashMap<>();
        List<Integer> pinIds = gameInfoMessage.getPinnedPlayerIds();
        List<Integer> pinTeams = gameInfoMessage.getPinnedTeams();
        if (pinIds != null && pinTeams != null) {
          int count = Math.min(pinIds.size(), pinTeams.size());
          for (int i = 0; i < count; i++) {
            pins.put(pinIds.get(i), pinTeams.get(i));
          }
        }
        if (!game.getPinnedTeams().equals(pins)) {
          game.getPinnedTeams().clear();
          game.getPinnedTeams().putAll(pins);
        }
      }

      // Players' position requests (parallel id/role lists) -> id->role map.
      // Insertion order is request order (first-come tie-break on same-role
      // conflicts), so replace the content whenever the mapping OR the order
      // changed. The backing map is a LinkedHashMap, and putAll of another
      // LinkedHashMap preserves its iteration order.
      synchronized (game.getPositionRequests()) {
        Map<Integer, Integer> requests = parsePositionRequests(gameInfoMessage);
        if (!new ArrayList<>(game.getPositionRequests().entrySet())
            .equals(new ArrayList<>(requests.entrySet()))) {
          game.getPositionRequests().clear();
          game.getPositionRequests().putAll(requests);
        }
      }
    }
  }

  /** The message's parallel position-request lists as an id->role map whose
   *  iteration order is the server's request order. */
  private static Map<Integer, Integer> parsePositionRequests(GameInfoMessage gameInfoMessage) {
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    List<Integer> ids = gameInfoMessage.getPositionRequestPlayerIds();
    List<Integer> roles = gameInfoMessage.getPositionRequests();
    if (ids != null && roles != null) {
      int count = Math.min(ids.size(), roles.size());
      for (int i = 0; i < count; i++) {
        requests.put(ids.get(i), roles.get(i));
      }
    }
    return requests;
  }

  private void removeGame(GameInfoMessage gameInfoMessage) {
    Game game;
    synchronized (uidToGameInfoBean) {
      game = uidToGameInfoBean.remove(gameInfoMessage.getUid());
    }
    hostOriginalTitles.remove(gameInfoMessage.getUid());

    if (gameInfoMessage.getUid().equals(getRunningGameUid())) {
      // getRunningGameUid() is determined immediately upon starting gpgnet4ta
      // getCurrentGameStatus()==SPAWNING indicates game has been started locally but server hasn't confirmed that fact
      if (GameStatus.SPAWNING.equals(getCurrentGameStatus())) {
        log.warn("[removeGame] Game cancelled while launching");
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
    log.info("[onGameCloseRequested] killGame because onGameCLoseRequested");
    killGame();
  }

}
