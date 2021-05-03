package com.faforever.client.game;

import com.faforever.client.fx.Controller;
import com.faforever.client.main.event.HostGameEvent;
import com.faforever.client.map.MapService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.domain.GameStatus;
import com.google.common.eventbus.EventBus;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
public class GameDetailMapContextMenuController implements Controller<ContextMenu> {

  private final GameService gameService;
  private final PlayerService playerService;
  private final MapService mapService;
  private final PreferencesService preferencesService;
  private final EventBus eventBus;
  private Game game;

  public ContextMenu mapContextMenu;
  public MenuItem createMenuItem;
  public MenuItem changeMenuItem;
  public MenuItem browseMenuItem;

  private ChangeListener<GameStatus> currentGameStatusChangeListener;

  public GameDetailMapContextMenuController(GameService gameService, PlayerService playerService, MapService mapService, PreferencesService preferencesService, EventBus eventBus) {
    this.gameService = gameService;
    this.playerService = playerService;
    this.mapService = mapService;
    this.preferencesService = preferencesService;
    this.eventBus = eventBus;
  }

  public void initialize() {
    currentGameStatusChangeListener = (obs, oldValue, newValue) -> updateMenuItemStatus();
    gameService.getCurrentGameStatusProperty().addListener(new WeakChangeListener<>(currentGameStatusChangeListener));
    updateMenuItemStatus();
  }

  public void setGame(Game game) {
    this.game = game;
    updateMenuItemStatus();
  }

  private void updateMenuItemStatus() {
    if (gameService.getCurrentGame() == null || !playerService.getCurrentPlayer().isPresent() || this.game == null) {
      createMenuItem.setVisible(true);
      changeMenuItem.setVisible(false);
      browseMenuItem.setVisible(false);
      return;
    }

    final String currentPlayer = playerService.getCurrentPlayer().get().getUsername();
    final boolean isPlayerInGame = gameService.getCurrentGame().getId() == game.getId();  // but is in another game
    final boolean isGameStaging = gameService.getCurrentGame().getStatus() == GameStatus.STAGING;
    final boolean isPlayerHost = gameService.getCurrentGame().getHost().equals(currentPlayer);
    if (isPlayerInGame && isPlayerHost && isGameStaging) {
      createMenuItem.setVisible(false);
      changeMenuItem.setVisible(true);
      browseMenuItem.setVisible(false);
    }
    else if (isPlayerInGame && !isPlayerHost) {
      createMenuItem.setVisible(false);
      changeMenuItem.setVisible(false);
      browseMenuItem.setVisible(true);
    }
    else {
      createMenuItem.setVisible(false);
      changeMenuItem.setVisible(false);
      browseMenuItem.setVisible(true);
    }
  }

  ContextMenu getContextMenu() {
    return mapContextMenu;
  }

  @Override
  public ContextMenu getRoot() {
    return mapContextMenu;
  }

  public void consumer(ActionEvent actionEvent) {
    actionEvent.consume();
  }

  public void onCreateNew(ActionEvent actionEvent) {
    if (game == null) {
      return;
    }
    final String modTechnical = preferencesService.getPreferences().getLastGame().getLastGameType();
    final String mapName = game.getMapName();
    final String mapCrc = game.getMapCrc();
    mapService.ensureMap(modTechnical, mapName, mapCrc, game.getMapArchiveName(), null, null)
        .thenRun(() -> eventBus.post(new HostGameEvent(mapName).setContextGame(game)));
  }

  public void onChangeMap(ActionEvent actionEvent) {
    eventBus.post(new HostGameEvent(game.getMapName()).setContextGame(game)); // createGameController is able to work out what to do
  }

  public void onBrowseMap(ActionEvent actionEvent) {
    onCreateNew(actionEvent); // createGameController is able to work out what to do
  }
}
