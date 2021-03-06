package com.faforever.client.game;

import com.faforever.client.chat.ChatService;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.JoinChannelEvent;
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
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
  private final JoinGameHelper joinGameHelper;
  private final EventBus eventBus;

  public Pane gameDetailRoot;
  public Label gameTypeLabel;
  public Label mapLabel;
  public Label numberOfPlayersLabel;
  public Label gameStatusLabel;
  public Label hostLabel;
  public VBox teamListPane;
  public ImageView mapImageView;
  public Label gameTitleLabel;
  public Button joinButton;
  public Button chatButton;
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
  public Node watchButton;
  private Timeline gameTimeSinceStartUpdater;
  public Label gameTimeSinceStartLabel;
  public GameDetailMapContextMenuController mapContextMenuController;

  @SuppressWarnings("FieldCanBeLocal")
  private InvalidationListener featuredModInvalidationListener;
  private ChangeListener<GameStatus> currentGameStatusListener;
  private ChangeListener<Boolean> gameRunningListener;

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
                              JoinGameHelper joinGameHelper, EventBus eventBus) {
    this.i18n = i18n;
    this.mapService = mapService;
    this.modService = modService;
    this.gameService = gameService;
    this.playerService = playerService;
    this.uiService = uiService;
    this.chatService = chatService;
    this.joinGameHelper = joinGameHelper;
    this.eventBus = eventBus;

    game = new ReadOnlyObjectWrapper<>();

    thisGameStatusInvalidationListener = observable -> onGameStatusChanged();
    thisGameTeamsInvalidationListener = observable -> createTeams();
    weakThisGameTeamsListener = new WeakInvalidationListener(thisGameTeamsInvalidationListener);
    weakThisGameStatusListener = new WeakInvalidationListener(thisGameStatusInvalidationListener);

    currentGameStatusListener = (obs, newValue, oldValue) -> updateButtonsVisibility(gameService.getCurrentGame(), playerService.getCurrentPlayer().get());
    gameRunningListener = (obs, newValue, oldValue) -> updateButtonsVisibility(gameService.getCurrentGame(), playerService.getCurrentPlayer().get());
    gameService.getCurrentGameStatusProperty().addListener(new WeakChangeListener<>(currentGameStatusListener));
    gameService.gameRunningProperty().addListener(new WeakChangeListener<>(gameRunningListener));
    eventBus.register(this);
  }

  public void initialize() {

    mapContextMenuController = uiService.loadFxml("theme/play/game_detail_map_context_menu.fxml");

    JavaFxUtil.addLabelContextMenus(uiService, gameTitleLabel, mapLabel, gameTypeLabel);
    gameDetailRoot.parentProperty().addListener(observable -> {
      if (!(gameDetailRoot.getParent() instanceof Pane)) {
        return;
      }
      gameDetailRoot.maxWidthProperty().bind(((Pane) gameDetailRoot.getParent()).widthProperty());
    });
    watchButton = watchButtonController.getRoot();

    watchButton.managedProperty().bind(watchButton.visibleProperty());
    gameTitleLabel.managedProperty().bind(gameTitleLabel.visibleProperty());
    hostLabel.managedProperty().bind(hostLabel.visibleProperty());
    mapLabel.managedProperty().bind(mapLabel.visibleProperty());
    numberOfPlayersLabel.managedProperty().bind(numberOfPlayersLabel.visibleProperty());
    mapImageView.managedProperty().bind(mapImageView.visibleProperty());
    gameTypeLabel.managedProperty().bind(gameTypeLabel.visibleProperty());

    gameTitleLabel.visibleProperty().bind(game.isNotNull());
    hostLabel.visibleProperty().bind(game.isNotNull());
    mapLabel.visibleProperty().bind(game.isNotNull());
    numberOfPlayersLabel.visibleProperty().bind(game.isNotNull());
    mapImageView.visibleProperty().bind(game.isNotNull());
    gameTypeLabel.visibleProperty().bind(game.isNotNull());

    if (playerService.getCurrentPlayer().isPresent()) {
      updateButtonsVisibility(gameService.getCurrentGame(), playerService.getCurrentPlayer().get());
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
      updateButtonsVisibility(gameService.getCurrentGame(), player.getCurrentPlayer());
    }
  }

  private void onGameStatusChanged() {
    updateButtonsVisibility(gameService.getCurrentGame(), playerService.getCurrentPlayer().get());
  }

  private void updateButtonsVisibility(Game currentGame, Player currentPlayer) {
    Game thisGame = this.game.get();
    boolean isCurrentGame = thisGame != null && currentGame != null && Objects.equals(thisGame, currentGame);
    boolean isGameProcessRunning = gameService.isGameRunning();
    boolean isPlayerIdle = currentPlayer != null && currentPlayer.getStatus() == PlayerStatus.IDLE;
    boolean isPlayerHosting = currentPlayer != null && currentPlayer.getStatus() == PlayerStatus.HOSTING;
    boolean isPlayerJoining = currentPlayer != null && currentPlayer.getStatus() == PlayerStatus.JOINING;
    boolean isStagingRoomOpen = thisGame != null && thisGame.getStatus() == GameStatus.STAGING;
    boolean isBattleRoomOpen = thisGame != null && thisGame.getStatus() == GameStatus.BATTLEROOM;

    joinButton.setVisible(!isGameProcessRunning && isPlayerIdle && (isStagingRoomOpen || isBattleRoomOpen));
    chatButton.setVisible(thisGame != null);
    leaveButton.setVisible(isGameProcessRunning && isCurrentGame);
    startButton.setVisible(isGameProcessRunning && isCurrentGame && (isPlayerHosting && isStagingRoomOpen || isPlayerJoining && isBattleRoomOpen));
    watchButton.setVisible(false);
  }

  public void setGame(Game game) {
    if (game == null) {
      return;
    }

    Optional.ofNullable(this.game.get()).ifPresent(oldGame -> {
      Optional.ofNullable(weakThisGameTeamsListener).ifPresent(listener -> oldGame.getTeams().removeListener(listener));
      Optional.ofNullable(weakThisGameStatusListener).ifPresent(listener -> oldGame.statusProperty().removeListener(listener));
    });

    this.game.set(game);

    gameTitleLabel.textProperty().bind(game.titleProperty());
    hostLabel.textProperty().bind(game.hostProperty());
    mapLabel.textProperty().bind(game.mapNameProperty());
    gameStatusLabel.textProperty().bind(game.statusProperty().asString());
    gameTimeSinceStartUpdater.play();

    numberOfPlayersLabel.textProperty().bind(createStringBinding(
        () -> i18n.get("game.detail.players.format", game.getNumPlayers(), game.getMaxPlayers()),
        game.numPlayersProperty(),
        game.maxPlayersProperty()
    ));
    mapImageView.imageProperty().bind(createObjectBinding(
        () -> mapService.loadPreview(game.getFeaturedMod(), game.getMapName(), PreviewType.MINI, 10),
        game.mapNameProperty()
    ));

    featuredModInvalidationListener = observable -> modService.getFeaturedMod(game.getFeaturedMod())
        .thenAccept(featuredMod -> JavaFxUtil.runLater(() -> {
          gameTypeLabel.setText(i18n.get("loading"));
          String fullName = featuredMod != null ? featuredMod.getDisplayName() : null;
          gameTypeLabel.setText(StringUtils.defaultString(fullName));
        }));
    game.featuredModProperty().addListener(new WeakInvalidationListener(featuredModInvalidationListener));
    featuredModInvalidationListener.invalidated(game.featuredModProperty());

    JavaFxUtil.addListener(game.getTeams(), weakThisGameTeamsListener);
    thisGameTeamsInvalidationListener.invalidated(game.getTeams());

    JavaFxUtil.addListener(game.statusProperty(), weakThisGameStatusListener);
    thisGameStatusInvalidationListener.invalidated(game.statusProperty());
  }

  public Game getGame() {
    return game.get();
  }

  public ReadOnlyObjectProperty<Game> gameProperty() {
    return game.getReadOnlyProperty();
  }

  private void createTeams() {
    JavaFxUtil.assertApplicationThread();
    teamListPane.getChildren().clear();
    ObservableMap<String, List<String>> teams = game.get().getTeams();
    synchronized (teams) {
      TeamCardController.createAndAdd(teams, game.get().getRatingType(), playerService, uiService, teamListPane);
    }
  }

  @Override
  public Pane getRoot() {
    return gameDetailRoot;
  }

  public void onJoinButtonClicked(ActionEvent event) {
    joinGameHelper.join(game.get());
  }

  public void onChatButtonClicked(ActionEvent event)
  {
    if (game.get() != null) {
      String gameChannel = gameService.getInGameIrcChannel(game.get());
      eventBus.post(new JoinChannelEvent(gameChannel));
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
