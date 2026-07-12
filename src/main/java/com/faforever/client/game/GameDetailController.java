package com.faforever.client.game;

import com.faforever.client.chat.ChatService;
import com.faforever.client.fa.relay.event.AutoJoinRequestEvent;
import com.faforever.client.fx.DefaultImageView;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.galacticwar.GalacticWarService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.Leaderboard;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.event.CurrentPlayerInfo;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.rating.RatingService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.theme.UiService;
import com.faforever.client.vault.replay.WatchButtonController;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.css.PseudoClass;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ch.micheljung.fxwindow.FxStage;

import static com.faforever.client.leaderboard.LeaderboardService.DEFAULT_RATING_TYPE;
import static java.lang.Math.min;
import static javafx.beans.binding.Bindings.createObjectBinding;
import static javafx.beans.binding.Bindings.createStringBinding;

@Component
@Slf4j
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GameDetailController implements Controller<Pane> {

  private final I18n i18n;
  private final MapService mapService;
  private final ModService modService;
  private final GameService gameService;
  private final PlayerService playerService;
  private final UiService uiService;
  private final ChatService chatService;
  private final LeaderboardService leaderboardService;
  private final PreferencesService preferencesService;
  private final RatingService ratingService;
  private final GalacticWarService galacticWarService;
  private final JoinGameHelper joinGameHelper;
  private final NotificationService notificationService;
  private final FafService fafService;
  private final EventBus eventBus;

  public Pane gameDetailRoot;
  public Label gameTypeLabel;
  public Label mapLabel;
  public Label numberOfPlayersLabel;
  public Label gameStatusLabel;
  public Label hostLabel;
  public Label liveReplayDelayLabel;
  public VBox teamListPane;
  public DefaultImageView mapImageView;
  public Label gameTitleLabel;
  public Button joinButton;
  public Button autoJoinButton;
  public Button leaveButton;
  public Button startButton;
  public Button manageGameButton;
  public WatchButtonController watchButtonController;
  public VBox reservedPlayersContainer;
  public Button editReservedPlayersButton;
  public VBox reservedPlayersList;
  /** Position preselection box (title / host pill / instructions) shown below the map.
   *  The pickable controls themselves are the numbered markers on {@link #positionMarkerPane}. */
  public VBox positionRequestContainer;
  /** Host-only pill toggling the game between fixed (preselectable) and random
   *  start positions. Drives whether the picker is shown at all. */
  public ToggleButton fixedPositionsPill;
  /** Brief usage note shown in the picker box (fixed-mode only). */
  public Label positionRequestInstructions;
  /** Transparent layer over the map preview onto which numbered, clickable start-position
   *  markers are drawn from maptool coordinates. */
  public Pane positionMarkerPane;
  /** Cache key (mod + map + position count) of the markers currently built, so repeated
   *  picker refreshes don't re-run maptool or rebuild nodes. */
  private String loadedMarkersKey;
  /** The current start-position markers, kept so we can reposition them when the map preview
   *  resizes and restyle them when the selection changes. */
  private final List<PositionMarker> positionMarkers = new ArrayList<>();
  /** Fixed on-screen diameter of a start-position marker button. */
  private static final double POSITION_MARKER_SIZE = 22.0;
  /** Pseudo-class toggled on a marker when it belongs to the current player's requested pair. */
  private static final PseudoClass MARKER_SELECTED = PseudoClass.getPseudoClass("selected");
  /** Pseudo-class toggled on a marker whose pair has exactly one requester (half-filled). */
  private static final PseudoClass MARKER_HALF = PseudoClass.getPseudoClass("half");
  /** Pseudo-class toggled on a marker whose pair has two or more requesters (fully filled). */
  private static final PseudoClass MARKER_FULL = PseudoClass.getPseudoClass("full");

  /** A numbered start-position marker button plus its normalised map position and role (pair). */
  private static final class PositionMarker {
    final Button node;
    final double nx;
    final double ny;
    final int role;
    PositionMarker(Button node, double nx, double ny, int role) {
      this.node = node;
      this.nx = nx;
      this.ny = ny;
      this.role = role;
    }
  }
  /** Guards against re-sending a request while the picker is being synced to
   *  the server-broadcast state (setSelected fires no action event, but be
   *  defensive against future refactors that might). */
  private boolean syncingPositionPicker;
  /** Listener that re-renders the reserved-players list when the current
   *  game's reserved_players changes. Re-attached in setGame(). */
  private javafx.collections.ListChangeListener<String> reservedPlayersListener;
  /** Listener that re-renders the reserved-players list when the current
   *  game's join_requests changes (host-side join-request inline rows). */
  private javafx.collections.ListChangeListener<Game.JoinRequest> joinRequestsListener;
  /** Listener on PlayerService's online-players map: re-renders the reserved
   *  list when a reserved login goes online/offline. Installed once in
   *  initialize() and kept for the controller's lifetime. */
  private javafx.collections.MapChangeListener<String, Player> playersByNameListener;
  /** Listener on PlayerService's id-keyed map. {@code playersByName} survives
   *  a FAF-server logout (cleared only on IRC-offline), so we also listen on
   *  {@code playersById} which IS updated promptly on FAF logout — that's
   *  what triggers the "greyed out" treatment in the reserved-players card. */
  private javafx.collections.MapChangeListener<Integer, Player> playersByIdListener;
  /** Per-render cleanup callbacks that detach the per-Player status /
   *  currentGameUid listeners attached during the last rebuild. Run at the
   *  start of every rebuild so we don't accumulate stale listeners. */
  private final List<Runnable> perPlayerStatusListenerCleanups = new ArrayList<>();
  private final ReadOnlyObjectWrapper<Game> game;
  @SuppressWarnings("FieldCanBeLocal")
  private final InvalidationListener thisGameTeamsInvalidationListener;
  @SuppressWarnings("FieldCanBeLocal")
  private final InvalidationListener thisGameStatusInvalidationListener;
  private final WeakInvalidationListener weakThisGameTeamsListener;
  private final WeakInvalidationListener weakThisGameStatusListener;
  private final WeakInvalidationListener weakThisGamePingsListener;
  public Node watchButton;
  public Label gameRatingTypeLabel;
  public Label gameRatingTypeGlobalLabel;
  public StackPane mapContainer;
  public GridPane pingTableGridPane;
  public VBox pingTableContainer;
  public Label gameRatingRangeLabel;
  public Label pingTableValue;
  private Timeline gameTimeSinceStartUpdater;
  public Label gameTimeSinceStartLabel;
  public GameDetailMapContextMenuController mapContextMenuController;

  @SuppressWarnings("FieldCanBeLocal")
  private InvalidationListener featuredModInvalidationListener;
  private InvalidationListener gameRatingTypeInvalidationListener;
  private InvalidationListener thisGamePingsInvalidationListener;
  private ChangeListener<GameStatus> currentGameStatusListener;
  private ChangeListener<Number> gameRunningListener;
  private ChangeListener<Game> autoJoinRequestedGameListener;
  /** Re-renders the team cards (rating ⇄ ladder rank) when the global display-metric pill flips. */
  private ChangeListener<com.faforever.client.preferences.DisplayMetric> displayMetricListener;
  private boolean severed;

  /* sever ties to external objects so that this instance can be garbage collected */
  public void sever() {
    if (severed) {
      return;
    }
    severed = true;
    if (gameTimeSinceStartUpdater !=null) {
      gameTimeSinceStartUpdater.stop();
    }
    eventBus.unregister(this);
  }

  public GameDetailController(I18n i18n, MapService mapService, ModService modService,
                              GameService gameService, PlayerService playerService,
                              UiService uiService, ChatService chatService,
                              LeaderboardService leaderboardService, RatingService ratingService,
                              GalacticWarService galacticWarService,
                              PreferencesService preferencesService, JoinGameHelper joinGameHelper,
                              NotificationService notificationService, FafService fafService,
                              EventBus eventBus) {
    this.i18n = i18n;
    this.mapService = mapService;
    this.modService = modService;
    this.gameService = gameService;
    this.playerService = playerService;
    this.uiService = uiService;
    this.chatService = chatService;
    this.leaderboardService = leaderboardService;
    this.ratingService = ratingService;
    this.preferencesService = preferencesService;
    this.galacticWarService = galacticWarService;
    this.joinGameHelper = joinGameHelper;
    this.notificationService = notificationService;
    this.fafService = fafService;
    this.eventBus = eventBus;

    game = new ReadOnlyObjectWrapper<>();

    thisGameStatusInvalidationListener = observable -> onGameStatusChanged();
    thisGameTeamsInvalidationListener = observable -> createTeams();
    thisGamePingsInvalidationListener = observable -> createPingTable();
    weakThisGameTeamsListener = new WeakInvalidationListener(thisGameTeamsInvalidationListener);
    weakThisGameStatusListener = new WeakInvalidationListener(thisGameStatusInvalidationListener);
    weakThisGamePingsListener = new WeakInvalidationListener(thisGamePingsInvalidationListener);

    currentGameStatusListener = (obs, newValue, oldValue) -> JavaFxUtil.runLater(() -> updateButtonsVisibility(
        gameService.getCurrentGame(), gameService.getAutoJoinRequestedGameProperty().get(),
        playerService.getCurrentPlayer().get()));
    gameRunningListener = (obs, newValue, oldValue) -> JavaFxUtil.runLater(() -> updateButtonsVisibility(
        gameService.getCurrentGame(), gameService.getAutoJoinRequestedGameProperty().get(),
        playerService.getCurrentPlayer().get()));
    autoJoinRequestedGameListener = (obs, newValue, oldValue) -> JavaFxUtil.runLater(() -> updateButtonsVisibility(
        gameService.getCurrentGame(), gameService.getAutoJoinRequestedGameProperty().get(),
        playerService.getCurrentPlayer().get()));
    gameService.getCurrentGameStatusProperty().addListener(new WeakChangeListener<>(currentGameStatusListener));
    gameService.runningGameUidProperty().addListener(new WeakChangeListener<>(gameRunningListener));
    gameService.getAutoJoinRequestedGameProperty().addListener(new WeakChangeListener<>(autoJoinRequestedGameListener));
    eventBus.register(this);
  }

  public void initialize() {
    mapImageView.setDefaultImage(uiService.getThemeImage(UiService.UNKNOWN_MAP_IMAGE));
    mapContextMenuController = uiService.loadFxml("theme/play/game_detail_map_context_menu.fxml");
    pingTableContainer.managedProperty().bind(pingTableContainer.visibleProperty());
    positionRequestContainer.managedProperty().bind(positionRequestContainer.visibleProperty());

    // Keep the start-position marker layer exactly over the rendered map image (which is
    // scaled to fit while preserving aspect ratio), so markers land at the right spots.
    // pickOnBounds=false lets clicks on empty areas fall through to the map; only the marker
    // buttons capture input. Reposition markers whenever the rendered size changes.
    positionMarkerPane.setPickOnBounds(false);
    positionMarkerPane.maxWidthProperty().bind(Bindings.createDoubleBinding(
        () -> mapImageView.getLayoutBounds().getWidth(), mapImageView.layoutBoundsProperty()));
    positionMarkerPane.prefWidthProperty().bind(positionMarkerPane.maxWidthProperty());
    positionMarkerPane.maxHeightProperty().bind(Bindings.createDoubleBinding(
        () -> mapImageView.getLayoutBounds().getHeight(), mapImageView.layoutBoundsProperty()));
    positionMarkerPane.prefHeightProperty().bind(positionMarkerPane.maxHeightProperty());
    positionMarkerPane.widthProperty().addListener((obs, oldV, newV) -> repositionMarkers());
    positionMarkerPane.heightProperty().addListener((obs, oldV, newV) -> repositionMarkers());

    // The team cards show either a skill rating or a ladder rank depending on the global pill;
    // rebuild them live when it flips so the displayed metric stays in sync.
    displayMetricListener = (obs, oldValue, newValue) -> {
      if (game.get() != null) {
        createTeams();
      }
    };
    JavaFxUtil.addListener(preferencesService.getPreferences().displayMetricProperty(),
        new WeakChangeListener<>(displayMetricListener));

    // React to players going online/offline so the reserved-players card's
    // status dots update without needing the whole game_info to change.
    // We listen on BOTH playersByName (catches IRC-driven changes) AND
    // playersById (catches the actual FAF login/logout — see the doc on
    // PlayerService.getPlayersByName for why both are needed).
    playersByNameListener = change -> {
      Game g = game.get();
      if (g == null || !g.isReservedSlotsEnabled()) {
        return;
      }
      if (!g.getReservedPlayers().contains(change.getKey())) {
        return;
      }
      JavaFxUtil.runLater(this::rebuildReservedPlayersList);
    };
    playerService.getPlayersByName().addListener(playersByNameListener);

    playersByIdListener = change -> {
      Game g = game.get();
      if (g == null || !g.isReservedSlotsEnabled()) {
        return;
      }
      Player p = change.wasAdded() ? change.getValueAdded() : change.getValueRemoved();
      if (p == null) {
        return;
      }
      if (!g.getReservedPlayers().contains(p.getUsername())) {
        return;
      }
      JavaFxUtil.runLater(this::rebuildReservedPlayersList);
    };
    playerService.getPlayersById().addListener(playersByIdListener);

    JavaFxUtil.addLabelContextMenus(uiService, gameTitleLabel, hostLabel);
    gameDetailRoot.parentProperty().addListener(observable -> {
      if (!(gameDetailRoot.getParent() instanceof Pane)) {
        return;
      }
      gameDetailRoot.maxWidthProperty().bind(((Pane) gameDetailRoot.getParent()).widthProperty());
    });
    watchButton = watchButtonController.getRoot();

    gameTitleLabel.managedProperty().bind(gameTitleLabel.visibleProperty());
    hostLabel.managedProperty().bind(hostLabel.visibleProperty());
    mapLabel.managedProperty().bind(mapLabel.visibleProperty());
    numberOfPlayersLabel.managedProperty().bind(numberOfPlayersLabel.visibleProperty());
    mapImageView.managedProperty().bind(mapImageView.visibleProperty());
    mapContainer.managedProperty().bind(mapContainer.visibleProperty());
    gameTypeLabel.managedProperty().bind(gameTypeLabel.visibleProperty());
    watchButton.managedProperty().bind(watchButton.visibleProperty());

    // Each button only takes layout space when it's actually visible, so the
    // visible buttons get the full row width to show their labels.
    leaveButton.managedProperty().bind(leaveButton.visibleProperty());
    startButton.managedProperty().bind(startButton.visibleProperty());
    manageGameButton.managedProperty().bind(manageGameButton.visibleProperty());
    joinButton.managedProperty().bind(joinButton.visibleProperty());
    autoJoinButton.managedProperty().bind(autoJoinButton.visibleProperty());

    // getStyle.contains doesn't work.  so we'll use this user data to track whether "activated" style has been applied
    autoJoinButton.setUserData(Boolean.FALSE);

    gameTitleLabel.visibleProperty().bind(game.isNotNull());
    hostLabel.visibleProperty().bind(game.isNotNull());
    mapLabel.visibleProperty().bind(game.isNotNull());
    numberOfPlayersLabel.visibleProperty().bind(game.isNotNull());
    mapImageView.visibleProperty().bind(game.isNotNull());
    gameTypeLabel.visibleProperty().bind(game.isNotNull());

    if (playerService.getCurrentPlayer().isPresent()) {
      updateButtonsVisibility(gameService.getCurrentGame(), gameService.getAutoJoinRequestedGameProperty().get(), playerService.getCurrentPlayer().get());
    }

    gameTimeSinceStartLabel.setVisible(false);
    gameTimeSinceStartUpdater = new Timeline(1,new KeyFrame(javafx.util.Duration.seconds(0), (ActionEvent event) -> {
      if (this.game.get() == null) {
        gameTimeSinceStartUpdater.stop();
        gameTimeSinceStartLabel.setVisible(false);
        return;
      }
      if (this.game.get().getStartTime() != null) {
        Duration timeSinceStart = Duration.between(this.game.get().getStartTime(), Instant.now());
        gameTimeSinceStartLabel.setText(String.format("%d:%s", timeSinceStart.toMinutes(), StringUtils.leftPad(String.valueOf(timeSinceStart.toSecondsPart()),2,"0")));
        gameTimeSinceStartLabel.setVisible(!timeSinceStart.isNegative());
      }
      else {
        gameTimeSinceStartLabel.setVisible(false);
      }
      if (this.game.get().getStatus().equals(GameStatus.ENDED)) {
        gameTimeSinceStartUpdater.stop();
      }
    }), new KeyFrame(javafx.util.Duration.seconds(1)));
    gameTimeSinceStartUpdater.setCycleCount(Timeline.INDEFINITE);
  }

  @Subscribe
  public void onPlayerInfo(CurrentPlayerInfo player) {
    if (player.getCurrentPlayer() != null) {
      updateButtonsVisibility(gameService.getCurrentGame(), gameService.getAutoJoinRequestedGameProperty().get(), player.getCurrentPlayer());
    }
  }

  private void onGameStatusChanged() {
    updateButtonsVisibility(gameService.getCurrentGame(), gameService.getAutoJoinRequestedGameProperty().get(), playerService.getCurrentPlayer().get());
  }

  private void updateButtonsVisibility(Game currentGame, Game autoJoinPrototype, Player currentPlayer) {
    JavaFxUtil.assertApplicationThread();
    Game thisGame = this.game.get();
    boolean isCurrentGame = thisGame != null && thisGame.getId() == gameService.getRunningGameUid();
    boolean isOwnGame = thisGame != null && currentPlayer != null && currentPlayer.getUsername().equals(thisGame.getHost());
    boolean isGameProcessRunning = gameService.isGameRunning() || gameService.getRunningGameUid() != 0;
    boolean isPlayerIdle = currentPlayer != null && currentPlayer.getStatus() == PlayerStatus.IDLE;
    boolean isPlayerHosting = currentPlayer != null && currentPlayer.getStatus() == PlayerStatus.HOSTING;
    boolean isPlayerJoining = currentPlayer != null && currentPlayer.getStatus() == PlayerStatus.JOINING;
    boolean isStagingRoomOpen = thisGame != null && thisGame.getStatus() == GameStatus.STAGING;
    boolean isBattleRoomOpen = thisGame != null && thisGame.getStatus() == GameStatus.BATTLEROOM;
    boolean isLive = thisGame != null && Set.of(GameStatus.LAUNCHING, GameStatus.LIVE).contains(thisGame.getStatus());

    joinButton.setVisible(!isGameProcessRunning && isPlayerIdle && (isStagingRoomOpen || isBattleRoomOpen));
    autoJoinButton.setVisible(!isOwnGame && !isGameProcessRunning && isPlayerIdle && !isStagingRoomOpen && !isBattleRoomOpen);
    leaveButton.setVisible(isGameProcessRunning && isCurrentGame);
    startButton.setVisible(isGameProcessRunning && isCurrentGame && (isPlayerHosting && isStagingRoomOpen || isPlayerJoining && isBattleRoomOpen));
    // Host-only: manage teams (+autoteam) and reserved slots while the lobby/battleroom is open.
    manageGameButton.setVisible(isGameProcessRunning && isCurrentGame && isOwnGame && (isStagingRoomOpen || isBattleRoomOpen));
    watchButton.setVisible(!isOwnGame && !isGameProcessRunning && isPlayerIdle && isLive && thisGame.getReplayDelaySeconds() >= 0);

    updatePositionPicker();

    final String activatedStyleClass = "autojoin-game-button-active";
    if (autoJoinPrototype != null && this.game.get() != null && autoJoinPrototype.getId() == this.game.get().getId()) {
      if (!((Boolean) autoJoinButton.getUserData())) {
        autoJoinButton.setUserData(Boolean.TRUE);
        autoJoinButton.getStyleClass().add(activatedStyleClass);
      }
    }
    else {
      autoJoinButton.setUserData(Boolean.FALSE);
      autoJoinButton.getStyleClass().remove(activatedStyleClass);
    }
  }

  public void setGame(Game game) {
    if (game == null) {
      return;
    }

    Optional.ofNullable(this.game.get()).ifPresent(oldGame -> {
      Optional.ofNullable(weakThisGameTeamsListener).ifPresent(listener -> oldGame.getTeams().removeListener(listener));
      Optional.ofNullable(weakThisGameTeamsListener).ifPresent(listener -> oldGame.getPinnedTeams().removeListener(listener));
      Optional.ofNullable(weakThisGameTeamsListener).ifPresent(listener -> oldGame.getPositionRequests().removeListener(listener));
      Optional.ofNullable(weakThisGameTeamsListener).ifPresent(listener -> oldGame.fixedPositionsEnabledProperty().removeListener(listener));
      Optional.ofNullable(weakThisGameTeamsListener).ifPresent(listener -> oldGame.maxPlayersProperty().removeListener(listener));
      Optional.ofNullable(weakThisGamePingsListener).ifPresent(listener -> oldGame.pingsProperty().removeListener(listener));
      Optional.ofNullable(weakThisGameStatusListener).ifPresent(listener -> oldGame.statusProperty().removeListener(listener));
      Optional.ofNullable(featuredModInvalidationListener).ifPresent(listener -> oldGame.featuredModProperty().removeListener(listener));
      Optional.ofNullable(gameRatingTypeInvalidationListener).ifPresent(listener -> oldGame.ratingTypeProperty().removeListener(listener));
      Optional.ofNullable(reservedPlayersListener).ifPresent(listener -> oldGame.reservedPlayersProperty().removeListener(listener));
      Optional.ofNullable(joinRequestsListener).ifPresent(listener -> oldGame.joinRequestsProperty().removeListener(listener));
      if (oldGame.getId() != game.getId()) {
        teamListPane.getChildren().clear();
      }
    });

    this.game.set(game);

    // Reserved-slots: show only while the game is still open to joiners
    // (STAGING / BATTLEROOM). Reservations don't apply once the game has
    // launched, gone live, or ended — surfacing the list in those states
    // is misleading.
    reservedPlayersContainer.visibleProperty().bind(Bindings.createBooleanBinding(
        () -> game.isReservedSlotsEnabled() && game.getStatus() != null && game.getStatus().isOpen(),
        game.reservedSlotsEnabledProperty(), game.statusProperty()));
    reservedPlayersContainer.managedProperty().bind(reservedPlayersContainer.visibleProperty());
    reservedPlayersListener = c -> JavaFxUtil.runLater(this::rebuildReservedPlayersList);
    game.reservedPlayersProperty().addListener(reservedPlayersListener);
    joinRequestsListener = c -> JavaFxUtil.runLater(this::rebuildReservedPlayersList);
    game.joinRequestsProperty().addListener(joinRequestsListener);
    rebuildReservedPlayersList();
    if (game.getStartTime() == null) {
      game.startTimeProperty().addListener((obs, oldValue, newValue) -> this.watchButtonController.setGame(game));
    }
    else {
      this.watchButtonController.setGame(game);
    }

    Optional<Player> host = playerService.getPlayerForUsername(game.getHost());
    if (host.isPresent() && playerService.isFoe(host.get().getId())) {
      gameTitleLabel.setText(String.format("%s's Game", game.getHost()));
    }
    else {
      gameTitleLabel.textProperty().bind(game.titleProperty());
    }

    hostLabel.textProperty().bind(game.hostProperty());
    mapLabel.textProperty().bind(game.mapNameProperty());
    gameStatusLabel.textProperty().bind(createObjectBinding(() ->
            i18n.getWithDefault(game.getStatus().getString(), game.getStatus().getI18nKey()),
        game.statusProperty()
    ));
    gameStatusLabel.graphicProperty().bind(createObjectBinding(() -> {
      String themeImageFileName = game.getStatus().getThemeImageFileName();
      if (themeImageFileName != null) {
        return new ImageView(uiService.getThemeImage(game.getStatus().getThemeImageFileName()));
      }
      else {
        return null;
      }}, game.statusProperty()
    ));
    gameTimeSinceStartUpdater.play();

    numberOfPlayersLabel.textProperty().bind(createStringBinding(
        () -> i18n.get("game.detail.players.format", game.getNumPlayers(), game.getMaxPlayers()),
        game.numPlayersProperty(),
        game.maxPlayersProperty()
    ));
    liveReplayDelayLabel.textProperty().bind(createStringBinding(() -> {
      if (game.getReplayDelaySeconds() > 0) {
        return i18n.get("duration.seconds", game.getReplayDelaySeconds());
      } else if (game.getReplayDelaySeconds() == 0) {
        return i18n.get("liveReplay.zeroDelay");
      } else {
        return i18n.get("liveReplay.disabled");
      }}, game.replayDelaySecondsProperty()
    ));

    mapImageView.backgroundLoadingImageProperty().bind(createObjectBinding(
        () -> mapService.loadPreview(game.getFeaturedMod(), game.getMapName(), PreviewType.MINI, 10),
        game.mapNameProperty()
    ));
    mapContainer.visibleProperty().bind(
        game.replayDelaySecondsProperty().greaterThanOrEqualTo(0).or(
            gameService.getCurrentGameProperty().isEqualTo(game)));

    featuredModInvalidationListener = observable -> modService.getFeaturedMod(game.getFeaturedMod())
        .thenAccept(featuredMod -> JavaFxUtil.runLater(() -> {
          gameTypeLabel.setText(i18n.get("loading"));
          String fullName = featuredMod != null ? featuredMod.getDisplayName() : null;
          gameTypeLabel.setText(StringUtils.defaultString(fullName));
        }));
    game.featuredModProperty().addListener(featuredModInvalidationListener);
    featuredModInvalidationListener.invalidated(game.featuredModProperty());

    gameRatingTypeInvalidationListener = observable ->
        leaderboardService.getLeaderboards()
            .thenCombine(modService.getFeaturedMod(game.getFeaturedMod()), (leaderboards, featuredMod) -> leaderboards.stream()
                .filter(lb -> lb.getTechnicalName().equals(game.getRatingType()))
                .findAny()
                .map(Leaderboard::getNameKey)
                .orElse(featuredMod.getDisplayName()))
            .thenAccept(text -> JavaFxUtil.runLater(() -> gameRatingTypeLabel.setText(text)));

    game.ratingTypeProperty().addListener(gameRatingTypeInvalidationListener);
    gameRatingTypeInvalidationListener.invalidated(game.ratingTypeProperty());

    gameRatingRangeLabel.textProperty().bind(Bindings.createObjectBinding(
        () -> game.getRatingRangeString(i18n),
        game.statusProperty(),
        game.minRatingProperty(),
        game.maxRatingProperty()
    ));

    gameTypeLabel.visibleProperty().bind(game.ratingTypeProperty().isEqualTo(DEFAULT_RATING_TYPE));
    gameRatingTypeGlobalLabel.visibleProperty().bind(gameTypeLabel.visibleProperty());
    gameRatingTypeLabel.visibleProperty().bind(gameTypeLabel.visibleProperty().not());
    gameRatingRangeLabel.visibleProperty().bind(gameTypeLabel.visibleProperty().not());

    JavaFxUtil.addListener(game.getTeams(), weakThisGameTeamsListener);
    JavaFxUtil.addListener(game.getPinnedTeams(), weakThisGameTeamsListener);
    JavaFxUtil.addListener(game.getPositionRequests(), weakThisGameTeamsListener);
    // The host's fixed/random pill gates the picker's visibility on every
    // client, so re-render when the broadcast flag flips.
    JavaFxUtil.addListener(game.fixedPositionsEnabledProperty(), weakThisGameTeamsListener);
    // The picker's role-button count and visibility depend on max players,
    // which the host can change mid-staging via Manage Game.
    JavaFxUtil.addListener(game.maxPlayersProperty(), weakThisGameTeamsListener);
    thisGameTeamsInvalidationListener.invalidated(game.getTeams());

    JavaFxUtil.addListener(game.statusProperty(), weakThisGameStatusListener);
    thisGameStatusInvalidationListener.invalidated(game.statusProperty());

    JavaFxUtil.addListener(game.pingsProperty(), weakThisGamePingsListener);
    thisGamePingsInvalidationListener.invalidated(game.pingsProperty());
  }

  public Game getGame() {
    return game.get();
  }

  public ReadOnlyObjectProperty<Game> gameProperty() {
    return game.getReadOnlyProperty();
  }

  private void createTeams() {

    this.leaderboardService.getLeaderboards()
        .thenAccept(leaderboards -> JavaFxUtil.runLater(() -> {
          // Hide ratings (and rank) for unknown rating types AND for the hidden global
          // "just for fun" board, which has a leaderboard but is flagged hidden.
          boolean hidePlayerRatings = leaderboards.stream()
              .filter(lb -> lb.getTechnicalName().equals(game.get().getRatingType()))
              .findFirst()
              .map(lb -> lb.getLeaderboardHidden())
              .orElse(true);
          teamListPane.getChildren().clear();
          // Show the host's +autoteam pins for the full lifetime of the game
          // (staging, live, ended) so it's always plain to everyone that the
          // host arranged the teams — including when that arrangement turns out
          // badly imbalanced. Suppress only when the host is the lone pin (noise);
          // surface pins once the host has pinned at least one other player.
          Map<Integer, Integer> pinnedTeams = Map.of();
          Map<Integer, Integer> allPins = game.get().getPinnedTeams();
          if (!allPins.isEmpty()) {
            Integer hostId = playerService.getPlayerForUsername(game.get().getHost())
                .map(Player::getId).orElse(null);
            boolean hasNonHostPin = allPins.keySet().stream()
                .anyMatch(id -> hostId == null || !id.equals(hostId));
            if (hasNonHostPin) {
              pinnedTeams = allPins;
            }
          }
          Map<Integer, Integer> positionRequests;
          synchronized (game.get().getPositionRequests()) {
            positionRequests = new HashMap<>(game.get().getPositionRequests());
          }
          TeamCardController.createAndAdd(game.get().getTeams(), game.get().getRatingType(),
              playerService, uiService, ratingService, galacticWarService,
              teamListPane, hidePlayerRatings, game.get().getGalacticWarPlanetName(), pinnedTeams,
              positionRequests);
          updatePositionPicker();
        }));
  }

  /**
   * Show/refresh the position-preselection picker.
   *
   * <p>The box is only relevant while the current player is in this open game
   * with more than two slots. Within that, the host always sees the box — it
   * carries the fixed/random pill they use to enable preselection — while
   * joiners only see it once the host has switched the pill to fixed. The
   * per-role picker buttons (and the map overlay + instructions) appear only in
   * fixed mode.</p>
   *
   * <p>Selecting a toggle sends the request to the server; the authoritative
   * state comes back via GAME_INFO and re-syncs the toggles (and the badges in
   * the team cards).</p>
   */
  private void updatePositionPicker() {
    JavaFxUtil.assertApplicationThread();
    Game g = game.get();
    Optional<Player> currentPlayer = playerService.getCurrentPlayer();
    boolean inThisOpenGame = g != null && currentPlayer.isPresent()
        && g.getId() == gameService.getRunningGameUid()
        && g.getStatus() != null && g.getStatus().isOpen()
        && g.getMaxPlayers() > 2;
    boolean isHost = inThisOpenGame && currentPlayer.get().getUsername().equals(g.getHost());
    boolean fixed = inThisOpenGame && g.isFixedPositionsEnabled();
    // Host always sees the box (to reach the pill); joiners only in fixed mode.
    boolean containerVisible = inThisOpenGame && (isHost || fixed);
    // The picker proper (on-map markers + instructions) is fixed-mode only.
    boolean pickerVisible = containerVisible && fixed;

    positionRequestContainer.setVisible(containerVisible);
    fixedPositionsPill.setVisible(isHost);
    fixedPositionsPill.setManaged(isHost);
    positionRequestInstructions.setVisible(pickerVisible);
    positionRequestInstructions.setManaged(pickerVisible);

    // Keep the host's pill in sync with the authoritative server state without
    // re-firing its action handler.
    if (isHost) {
      syncingPositionPicker = true;
      try {
        fixedPositionsPill.setSelected(fixed);
        fixedPositionsPill.setText(i18n.get(fixed
            ? "game.positionMode.fixed" : "game.positionMode.random"));
      } finally {
        syncingPositionPicker = false;
      }
    }

    updateStartPositionMarkers(pickerVisible ? g : null);
  }

  /**
   * Build / refresh the numbered start-position markers drawn over the map preview. When
   * {@code g} is null the markers are cleared. Coordinates come (cached) from maptool's
   * {@code positions-coords} side-car; a reload only happens when the map or position count
   * changes. Highlights are refreshed every call so they track the player's selection.
   */
  private void updateStartPositionMarkers(Game g) {
    if (g == null) {
      positionMarkerPane.getChildren().clear();
      positionMarkers.clear();
      loadedMarkersKey = null;
      return;
    }
    int positions = min(10, g.getMaxPlayers());
    String key = g.getFeaturedMod() + "/" + g.getMapName() + "/" + positions;
    if (key.equals(loadedMarkersKey)) {
      refreshMarkerHighlights();
      return;
    }
    loadedMarkersKey = key;
    final String mod = g.getFeaturedMod();
    final String map = g.getMapName();
    // maptool may run on a cache miss, so load off the JavaFX thread; apply on it.
    CompletableFuture
        .supplyAsync(() -> mapService.loadStartPositions(mod, map, positions))
        .thenAccept(coords -> JavaFxUtil.runLater(() -> {
          if (key.equals(loadedMarkersKey)) {   // still the same map/count
            buildPositionMarkers(coords, positions);
            refreshMarkerHighlights();
          }
        }));
  }

  private void buildPositionMarkers(List<MapService.StartPosition> coords, int maxPositions) {
    positionMarkerPane.getChildren().clear();
    positionMarkers.clear();
    for (MapService.StartPosition sp : coords) {
      // Only show the positions actually in play for this game's max-player count.
      // maptool may return a larger fallback schema when the map has no exact one.
      if (sp.number() > maxPositions) {
        continue;
      }
      // Server roles are pairs 0..4 (map positions 2r+1 / 2r+2); ignore anything past position 10.
      int role = (sp.number() - 1) / 2;
      if (role >= 5) {
        continue;
      }
      Button marker = new Button(String.valueOf(sp.number()));
      marker.getStyleClass().add("position-marker");
      marker.setFocusTraversable(false);
      marker.setMinSize(POSITION_MARKER_SIZE, POSITION_MARKER_SIZE);
      marker.setPrefSize(POSITION_MARKER_SIZE, POSITION_MARKER_SIZE);
      marker.setMaxSize(POSITION_MARKER_SIZE, POSITION_MARKER_SIZE);
      marker.setTooltip(biggerTooltip(
          i18n.get("game.positionRequest.button.tooltip", 2 * role + 1, 2 * role + 2)));
      marker.setOnAction(e -> onPositionMarkerClicked(role));
      positionMarkers.add(new PositionMarker(marker, sp.x(), sp.y(), role));
      positionMarkerPane.getChildren().add(marker);
    }
    repositionMarkers();
  }

  /**
   * Place each marker near its true start position, but nudged apart so overlapping markers
   * stay clickable. A small relaxation balances two forces per marker: a <em>weak</em> spring
   * pulling it back to its real map coordinate (the anchor), and a <em>strong</em> short-range
   * repulsion from every other marker that is intense when they overlap and decays rapidly past
   * a marker's width. Deterministic (fixed iterations from the anchors) so it doesn't jitter
   * between reposition calls.
   */
  private void repositionMarkers() {
    double w = positionMarkerPane.getWidth();
    double h = positionMarkerPane.getHeight();
    int n = positionMarkers.size();
    if (n == 0) {
      return;
    }
    double[] ax = new double[n];   // anchor (true position) in pane pixels
    double[] ay = new double[n];
    double[] x = new double[n];
    double[] y = new double[n];
    for (int i = 0; i < n; i++) {
      PositionMarker pm = positionMarkers.get(i);
      ax[i] = pm.nx * w;
      ay[i] = pm.ny * h;
      // Seed off the anchor with a tiny index-based offset so exactly-coincident
      // positions still separate (a symmetric pair has no net direction otherwise).
      x[i] = ax[i] + 0.01 * (i + 1);
      y[i] = ay[i] - 0.01 * (i + 1);
    }
    if (w <= 0 || h <= 0) {
      applyMarkerPositions(x, y);
      return;
    }

    final double range = POSITION_MARKER_SIZE / 3.0;  // repulsion length scale ≈ a marker radius
    final double kAttract = 0.15;                     // weak pull back to the true position
    final double kRepel = POSITION_MARKER_SIZE * 0.8; // strong short-range push
    final double maxStep = 4.0;                        // clamp per-iteration move for stability
    final double margin = POSITION_MARKER_SIZE / 2.0;
    for (int iter = 0; iter < 120; iter++) {
      double[] fx = new double[n];
      double[] fy = new double[n];
      for (int i = 0; i < n; i++) {
        fx[i] += kAttract * (ax[i] - x[i]);
        fy[i] += kAttract * (ay[i] - y[i]);
      }
      for (int i = 0; i < n; i++) {
        for (int j = i + 1; j < n; j++) {
          double dx = x[i] - x[j];
          double dy = y[i] - y[j];
          double dist = Math.hypot(dx, dy);
          if (dist < 1e-4) {
            dx = i - j;
            dy = j - i;
            dist = Math.hypot(dx, dy);
          }
          double mag = kRepel * Math.exp(-dist / range);   // strong when close, rapid falloff past range
          double ux = dx / dist;
          double uy = dy / dist;
          fx[i] += mag * ux;
          fy[i] += mag * uy;
          fx[j] -= mag * ux;
          fy[j] -= mag * uy;
        }
      }
      for (int i = 0; i < n; i++) {
        double len = Math.hypot(fx[i], fy[i]);
        if (len > maxStep) {
          fx[i] *= maxStep / len;
          fy[i] *= maxStep / len;
        }
        x[i] = Math.max(margin, Math.min(w - margin, x[i] + fx[i]));
        y[i] = Math.max(margin, Math.min(h - margin, y[i] + fy[i]));
      }
    }
    applyMarkerPositions(x, y);
  }

  private void applyMarkerPositions(double[] x, double[] y) {
    for (int i = 0; i < positionMarkers.size(); i++) {
      Button node = positionMarkers.get(i).node;
      node.setLayoutX(x[i] - POSITION_MARKER_SIZE / 2);
      node.setLayoutY(y[i] - POSITION_MARKER_SIZE / 2);
    }
  }

  /**
   * Highlight the markers belonging to the current player's requested pair (role) and shade
   * every marker by how many players have requested its pair: empty (0), half-filled (1) or
   * fully filled (2+). Since a pair straddles both teams it has exactly two physical slots, so
   * two or more requesters means the pair is full.
   */
  private void refreshMarkerHighlights() {
    Integer myRole = requestedRoleOfCurrentPlayer();
    Map<Integer, Long> requestsPerRole = requestCountByRole();
    for (PositionMarker pm : positionMarkers) {
      long count = requestsPerRole.getOrDefault(pm.role, 0L);
      boolean mine = myRole != null && myRole == pm.role;
      pm.node.pseudoClassStateChanged(MARKER_SELECTED, mine);
      pm.node.pseudoClassStateChanged(MARKER_HALF, count == 1);
      pm.node.pseudoClassStateChanged(MARKER_FULL, count >= 2);
      // A pair I don't already hold is unclickable once it's full or the host's pins
      // rule it out for me; my own pick stays clickable so I can release it.
      pm.node.setDisable(!mine && !canRequestRole(pm.role));
    }
  }

  /**
   * Whether the current player could claim pair {@code role} right now. A pair straddles
   * both teams, so it has only two slots: it's full once two other players hold it. It is
   * also blocked when the host has pinned this player and another requester of the same pair
   * to the <em>same</em> team, since only one of them can occupy the pair's single slot on
   * that team. Mirrors the authoritative check in the server's {@code command_set_position_request}.
   */
  private boolean canRequestRole(int role) {
    Game g = game.get();
    Optional<Player> me = playerService.getCurrentPlayer();
    if (g == null || me.isEmpty()) {
      return false;
    }
    // A claimable pair needs both mirrored positions (2r+1, 2r+2) in play. With an odd
    // position count the last, unmirrored position is shown but can't be claimed.
    int numPairs = min(10, g.getMaxPlayers()) / 2;
    if (role < 0 || role >= numPairs) {
      return false;
    }
    int myId = me.get().getId();
    List<Integer> others;
    synchronized (g.getPositionRequests()) {
      others = g.getPositionRequests().entrySet().stream()
          .filter(e -> e.getValue() != null && e.getValue() == role && e.getKey() != myId)
          .map(Entry::getKey)
          .collect(Collectors.toList());
    }
    if (others.size() >= 2) {
      return false;
    }
    Integer myPin = g.getPinnedTeams().get(myId);
    if (myPin != null && others.stream().anyMatch(pid -> myPin.equals(g.getPinnedTeams().get(pid)))) {
      return false;
    }
    return true;
  }

  /** How many players have requested each pair (role), from the authoritative GAME_INFO state. */
  private Map<Integer, Long> requestCountByRole() {
    Game g = game.get();
    if (g == null) {
      return Map.of();
    }
    synchronized (g.getPositionRequests()) {
      return g.getPositionRequests().values().stream()
          .filter(Objects::nonNull)
          .collect(Collectors.groupingBy(role -> role, Collectors.counting()));
    }
  }

  private Integer requestedRoleOfCurrentPlayer() {
    Game g = game.get();
    Optional<Player> currentPlayer = playerService.getCurrentPlayer();
    if (g == null || currentPlayer.isEmpty()) {
      return null;
    }
    synchronized (g.getPositionRequests()) {
      return g.getPositionRequests().get(currentPlayer.get().getId());
    }
  }

  /** Host flipped the fixed/random pill. Push the new mode to the server; the
   *  GAME_INFO broadcast re-syncs the pill and shows/hides the picker on every
   *  client (including this one, via {@link #updatePositionPicker()}). */
  public void onFixedPositionsToggled(ActionEvent event) {
    if (syncingPositionPicker) {
      return;
    }
    Game g = game.get();
    if (g == null) {
      return;
    }
    gameService.setFixedPositionsEnabled(g, fixedPositionsPill.isSelected());
  }

  /**
   * A start-position marker was clicked: toggle a request for its pair (role). Clicking the
   * pair the player already holds clears it. The selection is reflected optimistically and
   * re-synced authoritatively when the server echoes it back in GAME_INFO.
   */
  private void onPositionMarkerClicked(int role) {
    if (syncingPositionPicker) {
      return;
    }
    Integer myRole = requestedRoleOfCurrentPlayer();
    boolean select = myRole == null || myRole != role;
    // Selecting a pair I can't claim (full, or ruled out by host pins) is a no-op; the
    // marker is disabled too, but guard the handler in case of a stale click.
    if (select && !canRequestRole(role)) {
      return;
    }
    Integer optimisticRole = select ? role : null;
    // Reflect the selection and the pair-occupancy shading immediately; the authoritative
    // GAME_INFO echo re-syncs it (and may drop the request if the server rejects a full pair).
    Map<Integer, Long> counts = new HashMap<>(requestCountByRole());
    if (myRole != null) {
      counts.merge(myRole, -1L, Long::sum);
    }
    if (optimisticRole != null) {
      counts.merge(optimisticRole, 1L, Long::sum);
    }
    for (PositionMarker pm : positionMarkers) {
      long count = counts.getOrDefault(pm.role, 0L);
      pm.node.pseudoClassStateChanged(MARKER_SELECTED, optimisticRole != null && optimisticRole == pm.role);
      pm.node.pseudoClassStateChanged(MARKER_HALF, count == 1);
      pm.node.pseudoClassStateChanged(MARKER_FULL, count >= 2);
    }
    fafService.setPositionRequest(select ? role : null);
  }

  private void createPingTable() {
    Map<Integer, Player> playersInGameById = game.get().getTeams().values().stream()
        .flatMap(List::stream)
        .map(playerService::getPlayerForUsername)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toMap(Player::getId, player -> player));

    Map<Integer, Integer> playerOrdinalsById = new HashMap<>();
    Map<Integer, Player> playersByOrdinal = new HashMap<>();
    for (Map.Entry<Integer, List<List<Integer>>> entry : game.get().getPings().entrySet()) {
      Integer playerId = entry.getKey();
      if (!playerOrdinalsById.containsKey(playerId) && playersInGameById.containsKey(playerId)) {
        Integer nextOrdinal = playersByOrdinal.size();
        playersByOrdinal.put(nextOrdinal, playersInGameById.get(playerId));
        playerOrdinalsById.put(playerId, nextOrdinal);
      }
      for (List<Integer> peerPingPair : entry.getValue()) {
        Integer peerId = peerPingPair.get(0);
        if (!playerOrdinalsById.containsKey(peerId) && playersInGameById.containsKey(peerId)) {
          Integer nextOrdinal = playersByOrdinal.size();
          playersByOrdinal.put(nextOrdinal, playersInGameById.get(peerId));
          playerOrdinalsById.put(peerId, nextOrdinal);
        }
      }
    }

    if (playerOrdinalsById.size() < 2) {
      JavaFxUtil.runLater(() -> pingTableContainer.setVisible(false));
      return;
    }

    JavaFxUtil.runLater(() -> {
      pingTableGridPane.getChildren().clear();
      for (Map.Entry<Integer, List<List<Integer>>> entry : game.get().getPings().entrySet()) {
        Integer playerId = entry.getKey();
        if(!playerOrdinalsById.containsKey(playerId) || !playersInGameById.containsKey(playerId)) {
          continue;
        }

        Integer playerOrdinal = playerOrdinalsById.get(playerId);
        String playerUsername = playersInGameById.get(playerId).getUsername();
        Label playerLabel = new Label(playerUsername);
        playerLabel.setAlignment(Pos.CENTER_LEFT);
        pingTableGridPane.add(playerLabel, 0, playerOrdinal);

        for (List<Integer> peerPingPair : entry.getValue()) {
          Integer peerId = peerPingPair.get(0);
          if(!playerOrdinalsById.containsKey(peerId) || !playersInGameById.containsKey(peerId)) {
            continue;
          }

          Integer peerOrdinal = playerOrdinalsById.get(peerId);
          String peerUsername = playersInGameById.get(peerId).getUsername();
          Integer ping = peerPingPair.get(1);

          double red, green, blue;
          if (preferencesService.getPreferences().getColorBlindFriend()) {
            double t = Math.min(1.0, (double) ping / 1000.0);
            red = (float) t;
            green = (float) (1.0 - t);
            blue = (float) (1.0 - t);
          }
          else {
            double t = Math.min(1.0, (double) ping / 1000.0);
            red = (float) t;
            green = (float) (1.0 - t);
            blue = (float) 0.0;
          }

          Region cell = new Region();
          cell.setStyle(
              "-fx-background-color: rgba(" +
                  (int) (red * 255) + "," +
                  (int) (green * 255) + "," +
                  (int) (blue * 255) + "," +
                  "1);" +
              "-fx-border-color: black;" +
              "-fx-border-width: 1px;" +
              "-fx-border-style: solid;"
          );
          cell.setMinSize(10, 20); // Set cell size as needed
          cell.setPrefSize(20, 20);

          if (ping < 2000) {
            cell.setUserData(String.format("%s / %s\n%s", playerUsername, peerUsername, i18n.get("duration.milliseconds", ping)));
          }
          else {
            cell.setUserData(String.format("%s / %s\n%s", playerUsername, peerUsername, i18n.get("duration.timeout")));
          }
          pingTableGridPane.add(cell, 1+peerOrdinal, playerOrdinal);
        }
      }
      pingTableContainer.setVisible(true);
    });
  }

  public void setPingTableTooltip(MouseEvent mouseEvent) {
    pingTableValue.setVisible(false);
    try {
      Region cell = (Region) mouseEvent.getTarget();
      String text = (String) cell.getUserData();
      if (text != null) {
        pingTableValue.setText(text);
        pingTableValue.setVisible(true);
      }
    }
    catch (java.lang.ClassCastException ignored)
    { }
  }

  public void hidePingTableTooltip(MouseEvent mouseEvent) {
    pingTableValue.setVisible(false);
  }

  @Override
  public Pane getRoot() {
    return gameDetailRoot;
  }

  public void onJoinButtonClicked(ActionEvent event) {
    joinGameHelper.join(game.get());
  }

  public void onAutoJoinButtonClicked(ActionEvent event) {
    if (this.game.get() != null) {
      Game autoJoinPrototype = gameService.getAutoJoinRequestedGameProperty().get();
      if (autoJoinPrototype == null || autoJoinPrototype.getId() != this.game.get().getId()) {
        eventBus.post(new AutoJoinRequestEvent(game.get()));
      } else {
        eventBus.post(new AutoJoinRequestEvent(null));
      }
    }
  }

  private void rebuildReservedPlayersList() {
    // Detach the per-Player status/currentGame listeners from the previous
    // render so they don't pile up. Map-change listener for online/offline
    // is installed once in initialize() and stays.
    perPlayerStatusListenerCleanups.forEach(Runnable::run);
    perPlayerStatusListenerCleanups.clear();

    reservedPlayersList.getChildren().clear();
    Game g = game.get();
    if (g == null) {
      return;
    }
    Optional<Player> currentPlayerOpt = playerService.getCurrentPlayer();
    boolean isHost = currentPlayerOpt.isPresent()
        && currentPlayerOpt.get().getUsername().equals(g.getHost());
    editReservedPlayersButton.setVisible(isHost);
    editReservedPlayersButton.setManaged(isHost);

    // Attach status/currentGame listeners to every reserved player — even
    // ones we end up filtering out — so the card auto-refreshes when any
    // of them transitions in or out of "this game". Track the cleanup so
    // we can detach them on the next rebuild.
    // Also build the "pending" list: reserved players who are NOT already
    // in this game. Players who are in-game are visible in the team cards
    // below; re-listing them here just adds noise. (Edit dialog keeps them
    // — host needs the full list to manage it.)
    List<String> pending = new ArrayList<>();
    for (String login : g.getReservedPlayers()) {
      Optional<Player> p = playerService.getPlayerForUsername(login);
      if (p.isPresent()) {
        attachReservedPlayerStateListener(p.get());
        // Only suppress when the player is actually FAF-online AND currently
        // sitting in this game. A stale cached Player whose FAF session has
        // ended should appear as "offline pending", not "in this game".
        if (playerService.isOnline(login) && p.get().getCurrentGameUid() == g.getId()) {
          continue;  // in this game — hide from the "pending" list
        }
      }
      pending.add(login);
    }

    // Host-only "knocking" rows for incoming join requests. ListProperty is
    // empty for non-hosts (server only pushes host_game_state to the host).
    List<Game.JoinRequest> knocking = isHost
        ? new ArrayList<>(g.getJoinRequests())
        : Collections.emptyList();

    if (pending.isEmpty() && knocking.isEmpty()) {
      Label empty = new Label(i18n.get("reservedSlots.detail.empty"));
      empty.getStyleClass().add("reserved-players-empty");
      reservedPlayersList.getChildren().add(empty);
      return;
    }
    for (String login : pending) {
      reservedPlayersList.getChildren().add(buildReservedPlayerRow(login, isHost));
    }
    for (Game.JoinRequest req : knocking) {
      reservedPlayersList.getChildren().add(buildKnockingRow(req));
    }
  }

  /**
   * Subscribe to a single Player's status + currentGameUid properties so
   * that the reserved-players card rebuilds on any transition (joins/leaves
   * this game, joins another game, etc). The cleanup is added to
   * {@link #perPlayerStatusListenerCleanups} and runs at the start of the
   * next rebuild.
   */
  private void attachReservedPlayerStateListener(Player p) {
    Runnable rebuild = () -> JavaFxUtil.runLater(this::rebuildReservedPlayersList);
    javafx.beans.value.ChangeListener<PlayerStatus> statusListener = (obs, oldV, newV) -> rebuild.run();
    javafx.beans.value.ChangeListener<Number> gameUidListener = (obs, oldV, newV) -> rebuild.run();
    p.statusProperty().addListener(statusListener);
    p.currentGameUidProperty().addListener(gameUidListener);
    perPlayerStatusListenerCleanups.add(() -> {
      p.statusProperty().removeListener(statusListener);
      p.currentGameUidProperty().removeListener(gameUidListener);
    });
  }

  private Node buildReservedPlayerRow(String login, boolean isHost) {
    javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(6);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("reserved-player-row");

    Optional<Player> playerOpt = playerService.getPlayerForUsername(login);
    // Treat as offline if the Player object is missing OR the player is no
    // longer registered on the FAF server (the name-cache lingers while IRC
    // still has them — that's not the same as "available to take a slot").
    if (playerOpt.isPresent() && !playerService.isOnline(login)) {
      playerOpt = Optional.empty();
    }
    Game g = game.get();

    // Decide what status indicator (if any) to show for this row:
    //   offline   -> no dot; name greyed via .reserved-player-offline-name
    //   idle      -> no dot; name normal
    //   in-this-game STAGING/BATTLEROOM -> green ring
    //   in-this-game LIVE/LAUNCHING     -> green filled
    //   in-other-game STAGING/BATTLEROOM -> amber ring
    //   in-other-game LIVE               -> amber filled
    // The "other game's state" is inferred from the player's own PlayerStatus
    // since we don't have a Game handle for it.
    String statusClass = null;
    String statusText;
    if (playerOpt.isEmpty()) {
      statusText = i18n.get("reservedSlots.editor.statusOffline");
    } else {
      Player p = playerOpt.get();
      PlayerStatus pstatus = p.getStatus();
      boolean idle = pstatus == null || pstatus == PlayerStatus.IDLE;
      boolean inThis = g != null && p.getCurrentGameUid() == g.getId();
      if (idle) {
        statusText = i18n.get("reservedSlots.editor.statusOnline");
      } else if (inThis) {
        boolean live = g.getStatus() == GameStatus.LIVE
            || g.getStatus() == GameStatus.LAUNCHING;
        statusClass = live ? "status-in-this-game-live" : "status-in-this-game-staging";
        statusText = i18n.get("reservedSlots.editor.statusInThisGame");
      } else {
        // In some other game. PlayerStatus.PLAYING means the other game has
        // launched (LIVE); HOSTING/HOSTED/JOINING/JOINED mean lobby/BR.
        boolean live = pstatus == PlayerStatus.PLAYING;
        statusClass = live ? "status-in-other-game-live" : "status-in-other-game-staging";
        statusText = i18n.get("reservedSlots.editor.statusInGame");
      }
    }
    // Always insert a 10x10 placeholder so the name column stays aligned
    // across rows regardless of whether the player has a visible status
    // indicator. The .reserved-player-status-dot base class is invisible
    // on its own; the .status-* sibling class draws the circle.
    Region dot = new Region();
    dot.getStyleClass().add("reserved-player-status-dot");
    dot.setPrefSize(10, 10);
    dot.setMinSize(10, 10);
    dot.setMaxSize(10, 10);
    if (statusClass != null) {
      dot.getStyleClass().add(statusClass);
      Tooltip.install(dot, biggerTooltip(statusText));
    }
    row.getChildren().add(dot);

    Node nameNode;
    if (playerOpt.isPresent()) {
      // Use the same PlayerCardTooltipController as the team cards so we get
      // country flag / friend-foe icons / consistent rendering. No rating
      // shown (reserved slots don't have a leaderboard context — they're
      // just a list of "who's reserved").
      // Per-Player listeners for re-render-on-status-change are attached in
      // rebuildReservedPlayersList for every reserved player (including
      // filtered-out in-this-game ones), so we don't attach here.
      PlayerCardTooltipController tooltip = uiService.loadFxml("theme/player_card_tooltip.fxml");
      tooltip.setPlayer(playerOpt.get(), null, null, null);
      nameNode = tooltip.getRoot();
    } else {
      // Offline / unknown player — simple label with the login. Status dot
      // already conveys the offline state.
      Label name = new Label(login);
      name.getStyleClass().add("reserved-player-offline-name");
      nameNode = name;
    }
    javafx.scene.layout.HBox.setHgrow(nameNode, javafx.scene.layout.Priority.ALWAYS);
    if (nameNode instanceof javafx.scene.layout.Region nr) {
      nr.setMaxWidth(Double.MAX_VALUE);
    }
    row.getChildren().add(nameNode);

    // Host can remove anyone except themselves. (Server-side: host id stays
    // in regardless, so even if we sent the request without the host, the
    // server would put them back.)
    if (isHost && g != null && !login.equals(g.getHost())) {
      Button remove = new Button("x");
      remove.getStyleClass().add("reserved-player-remove");
      remove.setOnAction(e -> removeReservedPlayer(login));
      row.getChildren().add(remove);
    }
    return row;
  }

  private void removeReservedPlayer(String login) {
    Game g = game.get();
    if (g == null) return;
    List<Integer> updated = new ArrayList<>();
    for (String existing : g.getReservedPlayers()) {
      if (existing.equals(login)) continue;
      if (existing.equals(g.getHost())) continue;  // host is implicit on server
      playerService.getPlayerForUsername(existing).ifPresent(p -> updated.add(p.getId()));
    }
    gameService.sendReservedPlayers(updated);
  }

  public void onEditReservedPlayersClicked(ActionEvent event) {
    openManageGame(true, null);
  }

  /**
   * Render an inline "knocking on door" row for a pending join request.
   * Only ever called when the current player is the host (the server only
   * sends join_requests to the host via host_game_state). The tick opens
   * the editor with the requester's name prepopulated; the cross fires
   * dismiss_join_request to drop the request silently.
   */
  private Node buildKnockingRow(Game.JoinRequest req) {
    javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(6);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().addAll("reserved-player-row", "reserved-player-knocking-row");

    // Knocking icon: door silhouette + arrow glyph via .knock-icon (SVG
    // shape painted in -fx-text-background-color by the .icon base class).
    // Distinct from the status dots so the host immediately reads "this
    // is a pending request, not an established reservation".
    Region knock = new Region();
    knock.getStyleClass().addAll("icon", "knock-icon");
    Tooltip.install(knock, biggerTooltip(i18n.get("reservedSlots.knocking.tooltip", req.getPlayerLogin())));
    row.getChildren().add(knock);

    // Name display — use PlayerCardTooltipController when the requester is
    // online (we get country flag / friend-foe icons / consistent rendering),
    // fall back to a plain Label otherwise.
    Optional<Player> playerOpt = playerService.getPlayerForUsername(req.getPlayerLogin());
    if (playerOpt.isPresent() && !playerService.isOnline(req.getPlayerLogin())) {
      playerOpt = Optional.empty();
    }
    Node nameNode;
    if (playerOpt.isPresent()) {
      PlayerCardTooltipController tooltip = uiService.loadFxml("theme/player_card_tooltip.fxml");
      tooltip.setPlayer(playerOpt.get(), null, null, null);
      nameNode = tooltip.getRoot();
    } else {
      Label name = new Label(req.getPlayerLogin());
      name.getStyleClass().add("reserved-player-offline-name");
      nameNode = name;
    }
    javafx.scene.layout.HBox.setHgrow(nameNode, javafx.scene.layout.Priority.ALWAYS);
    if (nameNode instanceof javafx.scene.layout.Region nr) {
      nr.setMaxWidth(Double.MAX_VALUE);
    }
    row.getChildren().add(nameNode);

    Button approve = new Button("✓");
    approve.getStyleClass().add("reserved-player-knock-approve");
    approve.setTooltip(biggerTooltip(i18n.get("reservedSlots.knocking.approve.tooltip")));
    approve.setOnAction(e -> openManageGame(true, req.getPlayerLogin()));
    row.getChildren().add(approve);

    Button dismiss = new Button("✗");
    dismiss.getStyleClass().add("reserved-player-knock-dismiss");
    dismiss.setTooltip(biggerTooltip(i18n.get("reservedSlots.knocking.dismiss.tooltip")));
    dismiss.setOnAction(e -> fafService.dismissJoinRequest(req.getPlayerId()));
    row.getChildren().add(dismiss);
    return row;
  }

  /** Build a Tooltip with the .reserved-slots-tooltip style class applied so
   *  the text isn't shown at the default tiny font size. */
  private Tooltip biggerTooltip(String text) {
    Tooltip t = new Tooltip(text);
    t.getStyleClass().add("reserved-slots-tooltip");
    return t;
  }

  public void onLeaveButtonClicked(ActionEvent event) {
    Game g = game.get();
    Optional<Player> currentPlayer = playerService.getCurrentPlayer();
    boolean offerReserveAndLeave = g != null
        && g.isReservedSlotsEnabled()
        && currentPlayer.isPresent()
        && !g.getReservedPlayers().contains(currentPlayer.get().getUsername());
    if (!offerReserveAndLeave) {
      log.info("[onLeaveButtonClicked] killGame()");
      leaveGameAndChannel();
      return;
    }
    // Use ImmediateNotification (themed modal) rather than a raw Alert so
    // the dialog chrome matches the rest of the app.
    int gameId = g.getId();
    notificationService.addNotification(new ImmediateNotification(
        i18n.get("reservedSlots.leaveDialog.title"),
        i18n.get("reservedSlots.leaveDialog.message"),
        Severity.INFO,
        List.of(
            new Action(i18n.get("reservedSlots.leaveDialog.reserveAndLeave"), ev -> {
              log.info("[onLeaveButtonClicked] reserve-and-leave for game {}", gameId);
              // User already picked their reservation outcome — suppress the
              // server-driven auto-reserve prompt that will follow disconnect.
              gameService.markBattleroomExitHandled(gameId);
              gameService.leaveAndReserve(gameId);
              leaveGameAndChannel();
            }),
            new Action(i18n.get("reservedSlots.leaveDialog.justLeave"), ev -> {
              log.info("[onLeaveButtonClicked] just-leave for game {}", gameId);
              // Suppress the follow-up prompt AND tell the server to opt out
              // of the disconnect auto-reserve BEFORE we kill TA — that way
              // the player never even briefly appears on the reserved list
              // when the server processes the disconnect.
              gameService.markBattleroomExitHandled(gameId);
              fafService.cancelReservation();
              leaveGameAndChannel();
            }),
            new Action(i18n.get("cancel"))
        )
    ));
  }

  private void leaveGameAndChannel() {
    gameService.killGame();
    String gameChannel = gameService.getInGameIrcChannel(game.get());
    this.chatService.leaveChannel(gameChannel);
  }

  public void onStartButtonClicked(ActionEvent event) {
    log.info("[onStartButtonClicked] startBattleRoom()");
    gameService.startBattleRoom();
  }

  public void onManageGameClicked(ActionEvent event) {
    openManageGame(false, null);
  }

  /**
   * Open the single host-only "Manage Game" modal (Teams + Reserved Slots tabs).
   *
   * @param focusReserved select the Reserved Slots tab on open
   * @param prepopulateReservedName pre-fill the reserved add field (approve flow)
   */
  private void openManageGame(boolean focusReserved, String prepopulateReservedName) {
    Game g = game.get();
    if (g == null) {
      return;
    }
    ManageGameController controller = uiService.loadFxml("theme/play/manage_game.fxml");
    controller.setGame(g, focusReserved, prepopulateReservedName);
    FxStage fxStage = FxStage.create(controller.getRoot())
        .initOwner(gameDetailRoot.getScene() != null ? gameDetailRoot.getScene().getWindow() : null)
        .initModality(Modality.WINDOW_MODAL)
        .withSceneFactory(uiService::createScene)
        .allowMinimize(false)
        .apply();
    Stage popup = fxStage.getStage();
    popup.setTitle(i18n.get("manageGame.title"));
    controller.setOnClose(popup::close);
    popup.show();
  }

  public void onClickedMap(MouseEvent event) {
    mapContextMenuController.setGame(game.get());
    mapContextMenuController.getContextMenu().show(this.gameDetailRoot.getScene().getWindow(), event.getScreenX(), event.getScreenY());
  }
}
