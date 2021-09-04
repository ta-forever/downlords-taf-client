package com.faforever.client.game;

import com.faforever.client.chat.ChatService;
import com.faforever.client.fa.relay.event.AutoJoinRequestEvent;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.mod.ModService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.event.CurrentPlayerInfo;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.vault.replay.WatchButtonController;
import com.google.common.base.Joiner;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static javafx.beans.binding.Bindings.createObjectBinding;
import static javafx.beans.binding.Bindings.createStringBinding;

@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
@RequiredArgsConstructor
@Slf4j
public class GameTileController implements Controller<Node> {

  private final MapService mapService;
  private final I18n i18n;
  private final JoinGameHelper joinGameHelper;
  private final ModService modService;
  private final GameService gameService;
  private final PlayerService playerService;
  private final ChatService chatService;
  private final EventBus eventBus;
  public Node lockIconLabel;
  public Label gameTypeLabel;
  public Node gameCardRoot;
  public Label gameMapLabel;
  public Label gameTitleLabel;
  public Label gameStatusLabel;
  public Label numberOfPlayersLabel;
  public Label avgRatingLabel;
  public Label hostLabel;
  public Label modsLabel;
  public ImageView mapImageView;
  private Consumer<Game> onSelectedListener;
  private Game game;
  private Timeline gameTimeSinceStartUpdater;
  public Label gameTimeSinceStartLabel;

  public Button joinButton;
  public Button autoJoinButton;
  public Button leaveButton;
  public Button startButton;
  public Node watchButton;

  public WatchButtonController watchButtonController;
  private InvalidationListener thisGameStatusInvalidationListener;
  private WeakInvalidationListener weakThisGameStatusListener;

  private ChangeListener<GameStatus> currentGameStatusListener;
  private ChangeListener<Number> gameRunningListener;
  private ChangeListener<Game> autoJoinRequestedGameListener;

  public void setOnSelectedListener(Consumer<Game> onSelectedListener) {
    this.onSelectedListener = onSelectedListener;
  }

  /* sever ties to external objects so that this instance can be garbage collected */
  public void sever() {
    if (gameTimeSinceStartUpdater !=null) {
      gameTimeSinceStartUpdater.stop();
    }
    eventBus.unregister(this);
  }

  public void initialize() {
    watchButton = watchButtonController.getRoot();

    modsLabel.managedProperty().bind(modsLabel.visibleProperty());
    modsLabel.visibleProperty().bind(modsLabel.textProperty().isNotEmpty());
    gameTypeLabel.managedProperty().bind(gameTypeLabel.visibleProperty());
    lockIconLabel.managedProperty().bind(lockIconLabel.visibleProperty());

    // make a bit more room for the autoJoin button's text
    leaveButton.managedProperty().bind(autoJoinButton.visibleProperty().not());
    startButton.managedProperty().bind(autoJoinButton.visibleProperty().not());
    joinButton.managedProperty().bind(autoJoinButton.visibleProperty().not());
    autoJoinButton.managedProperty().bind(autoJoinButton.visibleProperty());
    watchButton.managedProperty().bind(autoJoinButton.visibleProperty());

    // getStyle.contains doesn't work.  so we'll use this user data to track whether "activated" style has been applied
    autoJoinButton.setUserData(Boolean.FALSE);

    thisGameStatusInvalidationListener = observable -> onGameStatusChanged();
    weakThisGameStatusListener = new WeakInvalidationListener(thisGameStatusInvalidationListener);

    currentGameStatusListener = (obs, newValue, oldValue) -> updateButtonsVisibility(gameService.getCurrentGame(), gameService.getAutoJoinRequestedGameProperty().get(), playerService.getCurrentPlayer().get());
    gameRunningListener = (obs, newValue, oldValue) -> updateButtonsVisibility(gameService.getCurrentGame(), gameService.getAutoJoinRequestedGameProperty().get(), playerService.getCurrentPlayer().get());
    autoJoinRequestedGameListener = (obs, newValue, oldValue) -> updateButtonsVisibility(gameService.getCurrentGame(), gameService.getAutoJoinRequestedGameProperty().get(), playerService.getCurrentPlayer().get());

    JavaFxUtil.addListener(gameService.getCurrentGameStatusProperty(), new WeakChangeListener<>(currentGameStatusListener));
    JavaFxUtil.addListener(gameService.runningGameUidProperty(), new WeakChangeListener<>(gameRunningListener));
    JavaFxUtil.addListener(gameService.getAutoJoinRequestedGameProperty(), new WeakChangeListener<>(autoJoinRequestedGameListener));

    gameTimeSinceStartLabel.setVisible(false);
    gameTimeSinceStartUpdater = new Timeline(1,new KeyFrame(javafx.util.Duration.seconds(0), (ActionEvent event) -> {
      if (this.game == null) {
        gameTimeSinceStartUpdater.stop();
        gameTimeSinceStartLabel.setVisible(false);
        return;
      }
      if (this.game.getStartTime() != null) {
        Duration timeSinceStart = Duration.between(this.game.getStartTime(), Instant.now());
        gameTimeSinceStartLabel.setText(String.format("%d:%s", timeSinceStart.toMinutes(), StringUtils.leftPad(String.valueOf(timeSinceStart.toSecondsPart()),2,"0")));
        gameTimeSinceStartLabel.setVisible(!timeSinceStart.isNegative());
      }
      else {
        gameTimeSinceStartLabel.setVisible(false);
      }
      if (this.game.getStatus().equals(GameStatus.ENDED)) {
        gameTimeSinceStartUpdater.stop();
      }
    }), new KeyFrame(javafx.util.Duration.seconds(1)));
    gameTimeSinceStartUpdater.setCycleCount(Timeline.INDEFINITE);

    eventBus.register(this);
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
    boolean isCurrentGame = game != null && currentGame != null && Objects.equals(game, currentGame);
    boolean isOwnGame = game != null && currentPlayer != null && currentPlayer.getUsername().equals(game.getHost());
    boolean isGameProcessRunning = gameService.isGameRunning();
    boolean isPlayerIdle = currentPlayer != null && currentPlayer.getStatus() == PlayerStatus.IDLE;
    boolean isPlayerHosting = currentPlayer != null && currentPlayer.getStatus() == PlayerStatus.HOSTING;
    boolean isPlayerJoining = currentPlayer != null && currentPlayer.getStatus() == PlayerStatus.JOINING;
    boolean isStagingRoomOpen = game != null && game.getStatus() == GameStatus.STAGING;
    boolean isBattleRoomOpen = game != null && game.getStatus() == GameStatus.BATTLEROOM;

    joinButton.setVisible(!isGameProcessRunning && isPlayerIdle && (isStagingRoomOpen || isBattleRoomOpen));
    autoJoinButton.setVisible(!isOwnGame && !isGameProcessRunning && isPlayerIdle && !isStagingRoomOpen && !isBattleRoomOpen);
    leaveButton.setVisible(isGameProcessRunning && isCurrentGame);
    startButton.setVisible(isGameProcessRunning && isCurrentGame && (isPlayerHosting && isStagingRoomOpen || isPlayerJoining && isBattleRoomOpen));
    watchButton.setVisible(!isOwnGame && !isGameProcessRunning && isPlayerIdle && !isStagingRoomOpen && !isBattleRoomOpen);

    final String activatedStyleClass = "autojoin-game-button-active";
    if (autoJoinPrototype != null && this.game != null && autoJoinPrototype.getId() == this.game.getId()) {
      if ((Boolean)autoJoinButton.getUserData() == false) {
        autoJoinButton.setUserData(Boolean.TRUE);
        autoJoinButton.getStyleClass().add(activatedStyleClass);
      }
    }
    else {
      autoJoinButton.setUserData(Boolean.FALSE);
      autoJoinButton.getStyleClass().remove(activatedStyleClass);
    }
  }

  public Node getRoot() {
    return gameCardRoot;
  }

  public void setGame(Game game) {
    Assert.isNull(this.game, "Game has already been set");
    this.game = game;
    if (game.getStartTime() == null) {
      game.startTimeProperty().addListener((obs, oldValue, newValue) -> this.watchButtonController.setGame(game));
    }
    else {
      this.watchButtonController.setGame(game);
    }

    modService.getFeaturedMod(game.getFeaturedMod())
        .thenAccept(featuredModBean -> JavaFxUtil.runLater(() -> gameTypeLabel.setText(StringUtils.defaultString(featuredModBean.getDisplayName()))));

    gameTitleLabel.textProperty().bind(game.titleProperty());
    hostLabel.setText(game.getHost());

    JavaFxUtil.bind(gameMapLabel.textProperty(), game.mapNameProperty());

    numberOfPlayersLabel.textProperty().bind(createStringBinding(
        () -> i18n.get("game.players.format", game.getNumPlayers(), game.getMaxPlayers()),
        game.numPlayersProperty(),
        game.maxPlayersProperty()
    ));

    gameStatusLabel.textProperty().bind(game.statusProperty().asString());
    gameTimeSinceStartUpdater.play();

    avgRatingLabel.textProperty().bind(createStringBinding(
        () -> i18n.get("game.avgRating.format", Math.round(game.getAverageRating() / 100.0) * 100.0),
        game.teamsProperty()
    ));

    ObservableMap<String, String> simMods = game.getSimMods();
    modsLabel.textProperty().bind(createStringBinding(() -> getSimModsLabelContent(simMods), simMods));

    // TODO display "unknown map" image first since loading may take a while
    mapImageView.imageProperty().bind(createObjectBinding(
        () -> mapService.loadPreview(game.getFeaturedMod(), game.getMapName(), PreviewType.MINI, 10),
        game.mapNameProperty()
    ));

    lockIconLabel.visibleProperty().bind(game.passwordProtectedProperty());
    updateButtonsVisibility(gameService.getCurrentGame(), gameService.getAutoJoinRequestedGameProperty().get(), playerService.getCurrentPlayer().get());

    JavaFxUtil.addListener(game.statusProperty(), weakThisGameStatusListener);
    thisGameStatusInvalidationListener.invalidated(game.statusProperty());
  }

  private String getSimModsLabelContent(ObservableMap<String, String> simMods) {
    List<String> modNames = simMods.entrySet().stream()
        .limit(2)
        .map(Entry::getValue)
        .collect(Collectors.toList());

    if (simMods.size() > 2) {
      return i18n.get("game.mods.twoAndMore", modNames.get(0), simMods.size() - 1);
    }
    return Joiner.on(i18n.get("textSeparator")).join(modNames);
  }

  public void onMousePressed(MouseEvent mouseEvent) {
    Objects.requireNonNull(onSelectedListener, "onSelectedListener has not been set");
    Objects.requireNonNull(game, "gameInfoBean has not been set");
    gameCardRoot.requestFocus();
    onSelectedListener.accept(this.game);
  }

  public void onMouseReleased(MouseEvent mouseEvent) {
    Objects.requireNonNull(onSelectedListener, "onSelectedListener has not been set");
    Objects.requireNonNull(game, "gameInfoBean has not been set");
    gameCardRoot.requestFocus();

    Game currentGame = gameService.getCurrentGame();
    Game autoJoinGame = gameService.getAutoJoinRequestedGameProperty().get();
    if (currentGame != null) {
      onSelectedListener.accept(currentGame);
    }
    else if (autoJoinGame != null) {
      onSelectedListener.accept(autoJoinGame);
    }
  }

  public void onJoinButtonClicked(ActionEvent event) {
    joinGameHelper.join(game);
  }

  public void onAutoJoinButtonClicked(ActionEvent event) {
    if (this.game != null) {
      Game autoJoinPrototype = gameService.getAutoJoinRequestedGameProperty().get();
      if (autoJoinPrototype == null || autoJoinPrototype.getId() != this.game.getId()) {
        eventBus.post(new AutoJoinRequestEvent(game));
      } else {
        eventBus.post(new AutoJoinRequestEvent(null));
      }
    }
  }

  public void onLeaveButtonClicked(ActionEvent event) {
    gameService.killGame();
    String gameChannel = gameService.getInGameIrcChannel(game);
    this.chatService.leaveChannel(gameChannel);
  }

  public void onStartButtonClicked(ActionEvent event) {
    gameService.startBattleRoom();
  }
}
