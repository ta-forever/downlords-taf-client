package com.faforever.client.game;

import com.faforever.client.api.dto.GroupPermission;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.moderator.BanDialogController;
import com.faforever.client.moderator.ModeratorService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.alert.Alert;
import com.faforever.client.ui.alert.animation.AlertAnimation;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
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
  private final ModeratorService moderatorService;
  private final UiService uiService;
  private Player player;
  public ContextMenu contextMenu;
  public MenuItem kickPlayerMenuItem;
  public MenuItem reserveSlotMenuItem;
  public SeparatorMenuItem moderatorActionSeparator;
  public MenuItem kickGameItem;
  public MenuItem kickLobbyItem;
  public MenuItem banItem;

  public boolean setPlayer(Player player) {
    this.player = player;
    Player currentPlayer = playerService.getCurrentPlayer().orElse(null);
    boolean baseAvailable = isMenuAvailable(currentPlayer, player);
    boolean reserveAvailable = isReserveSlotAvailable(currentPlayer, player);
    kickPlayerMenuItem.setVisible(baseAvailable);
    reserveSlotMenuItem.setVisible(reserveAvailable);
    moderatorService.getPermissions()
        .thenAccept(permissions -> setModeratorOptions(permissions, player, currentPlayer));
    boolean modAvailable = kickGameItem != null && (kickGameItem.isVisible() || (kickLobbyItem != null && kickLobbyItem.isVisible()) || (banItem != null && banItem.isVisible()));
    return baseAvailable || reserveAvailable || modAvailable;
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

  private void setModeratorOptions(java.util.Set<String> permissions, Player target, Player current) {
    if (target == null) {
      return;
    }
    boolean notSelf = current == null || current.getId() != target.getId();
    boolean canKick = permissions != null && permissions.contains(GroupPermission.ADMIN_KICK_SERVER);
    boolean canBan = permissions != null && permissions.contains(GroupPermission.ROLE_ADMIN_ACCOUNT_BAN);
    boolean hasHostActions = kickPlayerMenuItem != null && kickPlayerMenuItem.isVisible()
        || reserveSlotMenuItem != null && reserveSlotMenuItem.isVisible();
    boolean showKickGame = canKick && notSelf && !(kickPlayerMenuItem != null && kickPlayerMenuItem.isVisible());
    JavaFxUtil.runLater(() -> {
      if (kickGameItem != null) kickGameItem.setVisible(showKickGame);
      if (kickLobbyItem != null) kickLobbyItem.setVisible(canKick && notSelf);
      if (banItem != null) banItem.setVisible(canBan && notSelf);
      boolean hasAnyMod = (kickGameItem != null && kickGameItem.isVisible())
          || (kickLobbyItem != null && kickLobbyItem.isVisible())
          || (banItem != null && banItem.isVisible());
      if (moderatorActionSeparator != null) {
        moderatorActionSeparator.setVisible(hasAnyMod && hasHostActions);
      }
    });
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

  public void onKickGame() {
    if (this.player != null) {
      moderatorService.closePlayersGame(this.player.getId());
    }
  }

  public void onKickLobby() {
    if (this.player != null) {
      moderatorService.closePlayersLobby(this.player.getId());
    }
  }

  public void onBan(ActionEvent actionEvent) {
    actionEvent.consume();
    if (this.player == null) {
      return;
    }
    Alert<?> dialog = new Alert<>(getRoot().getOwnerWindow());

    BanDialogController controller = uiService.<BanDialogController>loadFxml("theme/moderator/ban_dialog.fxml")
        .setPlayer(this.player)
        .setCloseListener(dialog::close);

    dialog.setContent(controller.getDialogLayout());
    dialog.setAnimation(AlertAnimation.TOP_ANIMATION);
    dialog.show();
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
