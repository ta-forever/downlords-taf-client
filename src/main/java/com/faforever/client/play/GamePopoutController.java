package com.faforever.client.play;

import ch.micheljung.fxwindow.FxStage;
import com.faforever.client.chat.MatchmakingChatController;
import com.faforever.client.fx.Controller;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameDetailController;
import com.faforever.client.game.GameService;
import com.faforever.client.theme.UiService;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class GamePopoutController implements Controller<Node> {

  private final GameService gameService;
  private final UiService uiService;
  public TabPane chatTabPane;
  public ScrollPane gameDetailScrollPane;
  public StackPane root;
  public FxStage fxStage;
  public Label headingLabel;

  private MatchmakingChatController chatController;
  private GameDetailController gameDetailController;

  public GamePopoutController(GameService gameService, UiService uiService) {
    this.gameService = gameService;
    this.uiService = uiService;
  }

  @Override
  public void initialize() {
    gameDetailController = uiService.loadFxml("theme/play/game_detail.fxml");
    gameDetailScrollPane.setContent(gameDetailController.getRoot());

    gameService.getCurrentGameProperty().addListener((obs, oldValue, newValue) -> {
      if (newValue != null) {
        setFocusedGame(newValue);
      }
    });
    gameService.getAutoJoinRequestedGameProperty().addListener((obs, oldValue, newValue) -> {
      if (newValue != null) {
        setFocusedGame(newValue);
      }
    });
    setFocusedGame(gameService.getCurrentGame());
  }

  public MatchmakingChatController undockChatController() {
    if (chatController != null) {
      chatTabPane.getTabs().remove(chatController.getRoot());
    }
    return chatController;
  }

  public void dockChatController(MatchmakingChatController chatController) {
    if (chatController != null) {
      this.chatController = chatController;
      chatTabPane.getTabs().add(chatController.getRoot());
    }
  }

  public void show() {
    fxStage = FxStage.create(root)
        .initModality(Modality.NONE)
        .withSceneFactory(uiService::createScene)
        .apply();

    Stage stage = fxStage.getStage();
    stage.show();
  }

  public Stage getStage() {
    if (fxStage != null) {
      return fxStage.getStage();
    }
    else {
      return null;
    }
  }

  @Override
  public Node getRoot() {
    return this.root;
  }

  private void setFocusedGame(Game game) {
    gameDetailScrollPane.setVisible(game != null);
    gameDetailController.setGame(game);
  }

}
