package com.faforever.client.play;

import com.faforever.client.chat.ChatController;
import com.faforever.client.chat.ChatMessage;
import com.faforever.client.chat.MatchmakingChatController;
import com.faforever.client.chat.event.ChatMessageEvent;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.CustomGamesController;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameDetailController;
import com.faforever.client.game.GameService;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.theme.UiService;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class PlayController extends AbstractViewController<Node> {

  private final GameService gameService;
  private final UiService uiService;
  private final EventBus eventBus;

  public StackPane playRoot;
  public Node customGames;
  public Node mainChat;
  public TabPane gameChat;
  public SplitPane chatContainer;
  public SplitPane mainViewContainer;
  public Pane customGamesContainer;
  public HBox mainChatContainer;
  public HBox gameChatContainer;
  public VBox userListContainer;
  public ScrollPane gameDetailContainer;
  public Node mainChatUserListContainer;

  private CustomGamesController customGamesController;
  private ChatController mainChatController;
  private MatchmakingChatController gameChatController;
  private GameDetailController gameDetailController;

  public PlayController(GameService gameService, UiService uiService, EventBus eventBus) {
    this.gameService = gameService;
    this.uiService = uiService;
    this.eventBus = eventBus;

    eventBus.register(this);
  }

  @Override
  public void initialize() {
    gameChatController = uiService.loadFxml("theme/play/teammatchmaking/matchmaking_chat.fxml");
    gameChat.getTabs().add(gameChatController.getRoot());

    gameDetailController = uiService.loadFxml("theme/play/game_detail.fxml");
    gameDetailContainer.setContent(gameDetailController.getRoot());
    JavaFxUtil.bindManagedToVisible(gameDetailContainer);
    gameDetailContainer.setVisible(false);

    gameService.getCurrentGameProperty().addListener((obs, oldValue, newValue) -> {
      setCurrentGame(newValue);
      if (newValue != null) {
        setFocusedGame(newValue);
      }
    });
    setCurrentGame(gameService.getCurrentGame());
    setFocusedGame(gameService.getCurrentGame());

    customGamesController = CustomGamesController.getController(customGames);
    customGamesController.setOnSelectedListener(game -> setFocusedGame(game));
    customGamesController.setCreateGameDialogRoot(playRoot);

    mainChatController = ChatController.getController(mainChat);
    mainChatController.setUserListContainer(userListContainer);
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    customGamesController.display(navigateEvent);
    mainChatController.display(navigateEvent);
    gameChatController.display(navigateEvent);
  }

  @Override
  public void onHide() {
  }

  @Override
  public Node getRoot() {
    return playRoot;
  }

  @Subscribe
  public void onChatMessage(ChatMessageEvent event) {
    ChatMessage message = event.getMessage();
    if (message.getSource().equals(gameChatController.getReceiver())) {
      JavaFxUtil.runLater(() -> gameChatController.onChatMessage(message));
    }
  }

  private void setCurrentGame(Game game) {
    setGameChatBoxLayout(game);
    setGameChatBoxChannel(game);
  }

  private void setFocusedGame(Game game) {
    gameDetailContainer.setVisible(game != null);
    gameDetailController.setGame(game);
  }

  private void setGameChatBoxChannel(Game game) {
    if (game != null) {
      gameChatController.setChannel(gameService.getInGameIrcChannel(game));
      gameChatController.setTopic(String.format("%s's Game: %s", game.getHost(), game.getTitle()));
    }
    else {
      gameChatController.close();
    }
  }

  private void setGameChatBoxLayout(Game game) {
    double mainViewContainerWidth = mainViewContainer.getWidth();

    if (mainViewContainerWidth > 0.0 && game != null) {
      if (!gameChatContainer.isVisible()) {
        chatContainer.setDividerPositions(0.5);
        gameChatContainer.setVisible(true);
      }
      mainChatContainer.setMaxWidth(-1);
      gameChatContainer.setMaxWidth(-1);
      gameChat.setMaxWidth(-1);
    }
    else {
      mainChatContainer.setMaxWidth(-1);
      gameChatContainer.setMaxWidth(0.0);
      gameChat.setMaxWidth(0.0);
      gameChatContainer.setVisible(false);
    }
  }
}
