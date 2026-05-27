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
import com.faforever.client.map.MapBean;
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
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
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
import java.util.Optional;
import java.util.Set;
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
  public Label mapDescription;
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
  public WatchButtonController watchButtonController;
  public VBox reservedPlayersContainer;
  public Button editReservedPlayersButton;
  public VBox reservedPlayersList;
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
    mapDescription.managedProperty().bind(mapDescription.visibleProperty());
    numberOfPlayersLabel.managedProperty().bind(numberOfPlayersLabel.visibleProperty());
    mapImageView.managedProperty().bind(mapImageView.visibleProperty());
    mapContainer.managedProperty().bind(mapContainer.visibleProperty());
    gameTypeLabel.managedProperty().bind(gameTypeLabel.visibleProperty());
    watchButton.managedProperty().bind(watchButton.visibleProperty());

    // make a bit more room for the autoJoin button's text
    leaveButton.managedProperty().bind(autoJoinButton.visibleProperty().not());
    startButton.managedProperty().bind(autoJoinButton.visibleProperty().not());
    joinButton.managedProperty().bind(autoJoinButton.visibleProperty().not());
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
    watchButton.setVisible(!isOwnGame && !isGameProcessRunning && isPlayerIdle && isLive && thisGame.getReplayDelaySeconds() >= 0);

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

    ChangeListener<String> mapNameListener = (obs,oldValue,newValue) -> {
      Optional<MapBean> knownMap = mapService.getMapLocallyFromName(game.getFeaturedMod(), game.getMapName());
      if (knownMap.isPresent()) {
        mapDescription.setVisible(true);
        mapDescription.textProperty().setValue(knownMap.get().getDescription());
      }
      else {
        mapDescription.textProperty().setValue(null);
        mapDescription.setVisible(false);
      }
    };
    game.mapNameProperty().addListener(mapNameListener);
    mapNameListener.changed(game.mapNameProperty(), game.mapNameProperty().get(), game.mapNameProperty().get());

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
          boolean hidePlayerRatings = leaderboards.stream().noneMatch(lb -> lb.getTechnicalName().equals(game.get().getRatingType()));
          teamListPane.getChildren().clear();
          TeamCardController.createAndAdd(game.get().getTeams(), game.get().getRatingType(),
              playerService, uiService, ratingService, galacticWarService,
              teamListPane, hidePlayerRatings, game.get().getGalacticWarPlanetName());
        }));
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
    openEditorPopup(null, null);
  }

  /**
   * Open the reserved-slots editor modal. Optionally prepopulates the add
   * field with a candidate name (used by the inline "tick to approve" button
   * on knocking rows so the host can confirm/edit the slot list in the same
   * dialog they'd use for any other reservation edit).
   */
  private void openEditorPopup(Integer prepopulateId, String prepopulateName) {
    Game g = game.get();
    if (g == null) return;

    ReservedPlayersEditorController editor = uiService.loadFxml("theme/play/reserved_players_editor.fxml");
    editor.maxPlayersProperty().set(g.getMaxPlayers());
    editor.setCurrentGameId(g.getId());
    // Seed from the server's parallel id/login arrays so we include offline
    // players too. Build the seed list and the id->login display fallback
    // map together so cells can show logins for offline players.
    Optional<Player> hostPlayer = playerService.getPlayerForUsername(g.getHost());
    List<Integer> seedIds = new ArrayList<>();
    Map<Integer, String> seedLogins = new HashMap<>();
    List<Integer> ids = g.getReservedPlayerIds();
    List<String> logins = g.getReservedPlayers();
    int n = Math.min(ids.size(), logins.size());
    Integer hostIdFromIds = null;
    for (int i = 0; i < n; i++) {
      Integer pid = ids.get(i);
      String login = logins.get(i);
      if (pid == null) continue;
      seedIds.add(pid);
      if (login != null) {
        seedLogins.put(pid, login);
      }
      if (login != null && login.equals(g.getHost())) {
        hostIdFromIds = pid;
      }
    }
    editor.setReservedPlayerIds(seedIds);
    // Add the prepopulated candidate's login to the display map so they
    // render correctly (with login, not "#id") if added.
    if (prepopulateId != null && prepopulateName != null) {
      seedLogins.put(prepopulateId, prepopulateName);
    }
    editor.setDisplayLogins(seedLogins);
    // Resolve host id: prefer the id from the server payload (in case the
    // local PlayerService doesn't know the host login), fall back to local.
    Integer hostId = hostIdFromIds != null
        ? hostIdFromIds
        : hostPlayer.map(Player::getId).orElse(null);
    if (hostId != null) {
      editor.setHost(hostId, g.getHost());
    }
    if (prepopulateName != null) {
      editor.prepopulateName(prepopulateName);
    }

    FxStage fxStage = FxStage.create(editor.getRoot())
        .initOwner(gameDetailRoot.getScene() != null ? gameDetailRoot.getScene().getWindow() : null)
        .initModality(Modality.WINDOW_MODAL)
        .withSceneFactory(uiService::createScene)
        .allowMinimize(false)
        .apply();
    Stage popup = fxStage.getStage();
    popup.setTitle(i18n.get("reservedSlots.editor.title"));
    popup.setOnHidden(e -> {
      // Server handles the "if a reserved id was in join_requests, promote
      // them and fire the invite notice" logic atomically inside
      // command_set_reserved_players. No separate approve call needed.
      gameService.sendReservedPlayers(new ArrayList<>(editor.getReservedPlayerIds()));
    });
    popup.show();
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
    approve.setOnAction(e -> openEditorPopup(req.getPlayerId(), req.getPlayerLogin()));
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

  public void onClickedMap(MouseEvent event) {
    mapContextMenuController.setGame(game.get());
    mapContextMenuController.getContextMenu().show(this.gameDetailRoot.getScene().getWindow(), event.getScreenX(), event.getScreenY());
  }
}
