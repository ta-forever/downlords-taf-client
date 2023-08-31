package com.faforever.client.game;

import com.faforever.client.fx.Controller;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.GameStatus;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
@Slf4j
@RequiredArgsConstructor
public class TeamCardPlayerContextMenuController implements Controller<ContextMenu> {

  private final FafService fafService;
  private final PlayerService playerService;
  private Player player;
  public ContextMenu contextMenu;
  public MenuItem kickPlayerMenuItem;
  public MenuItem migrateHostMenuItem;

  public boolean setPlayer(Player player) {
    this.player = player;
    return isMenuAvailable(playerService.getCurrentPlayer().orElse(null), player);
  }

  static public boolean isMenuAvailable(Player user, Player target) {
    if (user == null || target == null) {
      return false;
    }
    Game targetPlayerGame = target.getGame();
    Game userPlayerGame = user.getGame();
    if (targetPlayerGame != null && userPlayerGame != null) {
      final boolean isUserInSameGame = targetPlayerGame.getId() == userPlayerGame.getId();
      final boolean isUserHost = targetPlayerGame.getHost().equals(user.getUsername());
      final boolean isGameStaging = targetPlayerGame.getStatus() == GameStatus.STAGING;
      final boolean isSamePlayer = user.getId() == target.getId();
      return isUserInSameGame && isUserHost && isGameStaging && !isSamePlayer;
    }
    else {
      return false;
    }
  }

  ContextMenu getContextMenu() {
    return contextMenu;
  }

  @Override
  public ContextMenu getRoot() {
    return contextMenu;
  }

  public void consumer(ActionEvent actionEvent) {
    actionEvent.consume();
  }

  public void onKickPlayer(ActionEvent actionEvent) {
    fafService.closePlayersGame(this.player.getId());
  }

  public void onMigrateHost(ActionEvent actionEvent) {
  }
}
