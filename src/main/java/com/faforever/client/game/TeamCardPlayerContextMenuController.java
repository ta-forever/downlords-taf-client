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
  private final GameService gameService;
  private Player player;
  public ContextMenu contextMenu;
  public MenuItem kickPlayerMenuItem;
  public MenuItem reserveSlotMenuItem;

  public boolean setPlayer(Player player) {
    this.player = player;
    Player currentPlayer = playerService.getCurrentPlayer().orElse(null);
    boolean baseAvailable = isMenuAvailable(currentPlayer, player);
    boolean reserveAvailable = isReserveSlotAvailable(currentPlayer, player);
    reserveSlotMenuItem.setVisible(reserveAvailable);
    return baseAvailable || reserveAvailable;
  }

  private boolean isReserveSlotAvailable(Player user, Player target) {
    if (user == null || target == null) {
      return false;
    }
    Game myGame = gameService.getCurrentGame();
    if (myGame == null || !myGame.isReservedSlotsEnabled()) {
      return false;
    }
    if (!myGame.getHost().equals(user.getUsername())) {
      return false;
    }
    if (user.getId() == target.getId()) {
      return false;
    }
    return !myGame.getReservedPlayers().contains(target.getUsername());
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

  public void onReserveSlot(ActionEvent actionEvent) {
    Game myGame = gameService.getCurrentGame();
    if (myGame == null || !myGame.isReservedSlotsEnabled() || this.player == null) {
      return;
    }
    java.util.List<Integer> ids = new java.util.ArrayList<>();
    for (String login : myGame.getReservedPlayers()) {
      if (login.equals(myGame.getHost())) continue;
      playerService.getPlayerForUsername(login).ifPresent(p -> ids.add(p.getId()));
    }
    if (!ids.contains(this.player.getId())) {
      ids.add(this.player.getId());
    }
    gameService.sendReservedPlayers(ids);
  }
}
