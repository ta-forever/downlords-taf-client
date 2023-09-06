package com.faforever.client.game;

import com.faforever.client.chat.ChatService;
import com.faforever.client.fa.relay.event.AutoJoinRequestEvent;
import com.faforever.client.fx.DefaultImageView;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.mod.ModService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.event.CurrentPlayerInfo;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.theme.UiService;
import com.faforever.client.vault.replay.WatchButtonController;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.event.ActionEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
  private final JoinGameHelper joinGameHelper;
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
  private Timeline gameTimeSinceStartUpdater;
  public Label gameTimeSinceStartLabel;
  public GameDetailMapContextMenuController mapContextMenuController;

  private final Tooltip pingTableTooltip;

  @SuppressWarnings("FieldCanBeLocal")
  private InvalidationListener featuredModInvalidationListener;
  private InvalidationListener gameRatingTypeInvalidationListener;
  private InvalidationListener thisGamePingsInvalidationListener;
  private ChangeListener<GameStatus> currentGameStatusListener;
  private ChangeListener<Number> gameRunningListener;
  private ChangeListener<Game> autoJoinRequestedGameListener;

  /* sever ties to external objects so that this instance can be garbage collected */
  public void sever() {
    if (gameTimeSinceStartUpdater !=null) {
      gameTimeSinceStartUpdater.stop();
    }
    eventBus.unregister(this);
  }

  public GameDetailController(I18n i18n, MapService mapService, ModService modService,
                              GameService gameService, PlayerService playerService,
                              UiService uiService, ChatService chatService,
                              LeaderboardService leaderboardService,
                              JoinGameHelper joinGameHelper, EventBus eventBus) {
    this.i18n = i18n;
    this.mapService = mapService;
    this.modService = modService;
    this.gameService = gameService;
    this.playerService = playerService;
    this.uiService = uiService;
    this.chatService = chatService;
    this.leaderboardService = leaderboardService;
    this.joinGameHelper = joinGameHelper;
    this.eventBus = eventBus;
    this.pingTableTooltip = new Tooltip();

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
    });

    this.game.set(game);
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

    gameTitleLabel.textProperty().bind(game.titleProperty());
    hostLabel.textProperty().bind(game.hostProperty());
    mapLabel.textProperty().bind(game.mapNameProperty());
    gameStatusLabel.textProperty().bind(game.statusProperty().asString());
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
        return game.getReplayDelaySeconds().toString() + " secs";
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

    gameRatingTypeInvalidationListener = observable -> JavaFxUtil.runLater(() ->
        gameRatingTypeLabel.setText(i18n.get(String.format("leaderboard.%s.name", game.getRatingType()))));
    game.ratingTypeProperty().addListener(gameRatingTypeInvalidationListener);
    gameRatingTypeInvalidationListener.invalidated(game.ratingTypeProperty());

    gameTypeLabel.visibleProperty().bind(game.ratingTypeProperty().isEqualTo(DEFAULT_RATING_TYPE));
    gameRatingTypeGlobalLabel.visibleProperty().bind(gameTypeLabel.visibleProperty());
    gameRatingTypeLabel.visibleProperty().bind(gameTypeLabel.visibleProperty().not());

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
              TeamCardController.createAndAdd(game.get().getTeams(), game.get().getRatingType(), playerService, uiService,
                  teamListPane, hidePlayerRatings);
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
        Integer playerOrdinal = playerOrdinalsById.getOrDefault(playerId, -1);

        if (playerOrdinal >= 0) {
          for (List<Integer> peerPingPair : entry.getValue()) {
            Integer peerId = peerPingPair.get(0);
            Integer peerOrdinal = playerOrdinalsById.getOrDefault(peerId, -1);
            if (peerOrdinal >= 0) {
              Integer ping = peerPingPair.get(1);
              double red = min(1.0, (double) ping / (double) 1000);
              double green = 1.0 - red;
              double blue = 0.0;
              double opacity = 1.0;

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
              cell.setMinSize(10, 10); // Set cell size as needed
              cell.setPrefSize(20, 20);

              String playerUsername = playersInGameById.get(playerId).getUsername();
              String peerUsername = playersInGameById.get(peerId).getUsername();
              if (ping < 2000) {
                cell.setUserData(String.format("%s\n%s\n%dms", playerUsername, peerUsername, ping));
              }
              else {
                cell.setUserData(String.format("%s\n%s\n(timeout)", playerUsername, peerUsername));
              }
              pingTableGridPane.add(cell, peerOrdinal, playerOrdinal);
            }
          }
        }
      }
      pingTableContainer.setVisible(true);
    });
  }

  public void setPingTableTooltip(MouseEvent mouseEvent) {
    try {
      Region cell = (Region) mouseEvent.getTarget();
      String text = (String) cell.getUserData();
      if (text != null) {
        pingTableTooltip.setText(text);
        pingTableTooltip.show(pingTableGridPane, mouseEvent.getScreenX(), mouseEvent.getScreenY() + 20);
      }
    }
    catch (java.lang.ClassCastException ignored)
    { }
  }

  public void hidePingTableTooltip(MouseEvent mouseEvent) {
    pingTableTooltip.hide();
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

  public void onLeaveButtonClicked(ActionEvent event) {
    log.info("[onLeaveButtonClicked] killGame()");
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
