package com.faforever.client.chat;

import com.faforever.client.api.dto.GroupPermission;
import com.faforever.client.chat.avatar.AvatarBean;
import com.faforever.client.chat.avatar.AvatarService;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.StringListCell;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameService;
import com.faforever.client.game.JoinGameHelper;
import com.faforever.client.game.PlayerStatus;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.ShowUserReplaysEvent;
import com.faforever.client.moderator.BanDialogController;
import com.faforever.client.moderator.ModeratorService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.domain.GameType;
import com.faforever.client.replay.ReplayService;
import com.faforever.client.reporting.ReportDialogController;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.alert.Alert;
import com.faforever.client.ui.alert.animation.AlertAnimation;
import com.faforever.client.util.ClipboardUtil;
import com.google.common.eventbus.EventBus;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.faforever.client.chat.ChatColorMode.RANDOM;
import static com.faforever.client.player.SocialStatus.FOE;
import static com.faforever.client.player.SocialStatus.FRIEND;
import static com.faforever.client.player.SocialStatus.SELF;
import static java.util.Locale.US;

@Slf4j
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
public class ChatUserContextMenuController implements Controller<ContextMenu> {

  private final PreferencesService preferencesService;
  private final PlayerService playerService;
  private final ReplayService replayService;
  private final com.faforever.client.replay.BrowserWatchService browserWatchService;
  private final NotificationService notificationService;
  private final I18n i18n;
  private final EventBus eventBus;
  private final JoinGameHelper joinGameHelper;
  private final AvatarService avatarService;
  private final UiService uiService;
  private final ModeratorService moderatorService;
  private final ChatGameInviteService chatGameInviteService;
  private final GameService gameService;
  private final com.faforever.client.ladder.LadderPointsService ladderPointsService;
  public MenuItem reserveSlotItem;
  public ComboBox<AvatarBean> avatarComboBox;
  public CustomMenuItem avatarPickerMenuItem;
  public MenuItem sendPrivateMessageItem;
  public SeparatorMenuItem socialSeparator;
  public CustomMenuItem colorPickerMenuItem;
  public ColorPicker colorPicker;
  public MenuItem joinGameItem;
  public MenuItem addFriendItem;
  public MenuItem removeFriendItem;
  public MenuItem addFoeItem;
  public MenuItem removeFoeItem;
  public MenuItem watchGameItem;
  public MenuItem viewReplaysItem;
  public MenuItem inviteItem;
  public MenuItem reportItem;
  public SeparatorMenuItem moderatorActionSeparator;
  public MenuItem banItem;
  public MenuItem broadcastMessage;
  public ContextMenu chatUserContextMenuRoot;
  public MenuItem showUserInfo;
  public Button removeCustomColorButton;
  private ChatChannelUser chatUser;
  public MenuItem kickGameItem;
  public MenuItem kickLobbyItem;
  public MenuItem watchGameInBrowserItem;

  @SuppressWarnings("FieldCanBeLocal")
  private ChangeListener<Player> playerChangeListener;

  // The avatar/medal combo box is populated imperatively (not via a live binding), so a reused menu
  // (see ChatUserItemController's WeakReference cache) must be refreshed on show to reflect a featured
  // medal the user just changed elsewhere (e.g. the User Info dialog). These guard re-population so the
  // selection listener is installed exactly once and doesn't fire for programmatic re-selection.
  private boolean avatarSelectionListenerInstalled;
  private boolean suppressAvatarSelectionListener;

  public ChatUserContextMenuController(PreferencesService preferencesService,
                                       PlayerService playerService, ReplayService replayService,
                                       com.faforever.client.replay.BrowserWatchService browserWatchService,
                                       NotificationService notificationService, I18n i18n, EventBus eventBus,
                                       JoinGameHelper joinGameHelper, AvatarService avatarService, UiService uiService,
                                       ModeratorService moderatorService, ChatGameInviteService chatGameInviteService,
                                       GameService gameService,
                                       com.faforever.client.ladder.LadderPointsService ladderPointsService) {
    this.preferencesService = preferencesService;
    this.playerService = playerService;
    this.replayService = replayService;
    this.browserWatchService = browserWatchService;
    this.notificationService = notificationService;
    this.i18n = i18n;
    this.eventBus = eventBus;
    this.joinGameHelper = joinGameHelper;
    this.avatarService = avatarService;
    this.uiService = uiService;
    this.moderatorService = moderatorService;
    this.chatGameInviteService = chatGameInviteService;
    this.gameService = gameService;
    this.ladderPointsService = ladderPointsService;
  }

  public void initialize() {
    avatarComboBox.setCellFactory(param -> avatarCell());
    avatarComboBox.setButtonCell(avatarCell());
    removeCustomColorButton.managedProperty().bind(removeCustomColorButton.visibleProperty());

    avatarPickerMenuItem.visibleProperty().bind(Bindings.createBooleanBinding(() -> !avatarComboBox.getItems().isEmpty(), avatarComboBox.getItems()));
  }

  @NotNull
  private StringListCell<AvatarBean> avatarCell() {
    return new StringListCell<>(
        AvatarBean::getDescription,
        avatarBean -> {
          if (avatarBean.getMedalCode() != null) {
            ImageView medalView = new ImageView(uiService.getThemeImageOrDefault(
                com.faforever.client.ladder.LadderUiUtil.medalIconPath(avatarBean.getMedalCode()),
                UiService.DEFAULT_MEDAL_IMAGE));
            // Square medal art scaled 1:1 to fit within a 120x60 box (-> ~60x60), then centred in a
            // reserved 120x60 slot so medal rows line up like a regular (120x60) avatar.
            medalView.setPreserveRatio(true);
            medalView.setFitWidth(120);
            medalView.setFitHeight(60);
            javafx.scene.layout.StackPane slot = new javafx.scene.layout.StackPane(medalView);
            slot.setMinSize(120, 60);
            slot.setPrefSize(120, 60);
            slot.setMaxSize(120, 60);
            return slot;
          }
          URL url = avatarBean.getUrl();
          if (url == null) {
            return null;
          }
          return new ImageView(avatarService.loadAvatar(url.toString()));
        });
  }

  ContextMenu getContextMenu() {
    return chatUserContextMenuRoot;
  }

  public void setChatUser(ChatChannelUser chatUser) {
    this.chatUser = chatUser;
    showUserInfo.visibleProperty().bind(chatUser.playerProperty().isNotNull());
    viewReplaysItem.visibleProperty().bind(chatUser.playerProperty().isNotNull());

    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();

    String lowerCaseUsername = chatUser.getUsername().toLowerCase(US);
    colorPicker.setValue(chatPrefs.getUserToColor().getOrDefault(lowerCaseUsername, null));

    colorPicker.valueProperty().addListener((observable, oldValue, newValue) -> {
      String lowerUsername = chatUser.getUsername().toLowerCase(US);
      ChatUserCategory userCategory;
      if (chatUser.isModerator()) {
        userCategory = ChatUserCategory.MODERATOR;
      } else {
        userCategory = chatUser.getSocialStatus().map(status -> switch (status) {
          case FRIEND -> ChatUserCategory.FRIEND;
          case FOE -> ChatUserCategory.FOE;
          default -> ChatUserCategory.OTHER;
        }).orElse(ChatUserCategory.OTHER);
      }
      if (newValue == null) {
        chatPrefs.getUserToColor().remove(lowerUsername);
        chatUser.setColor(chatPrefs.getGroupToColor().getOrDefault(userCategory, null));
      } else {
        chatPrefs.getUserToColor().put(lowerUsername, newValue);
        chatUser.setColor(newValue);
      }
    });

    removeCustomColorButton.visibleProperty().bind(chatPrefs.chatColorModeProperty().isNotEqualTo(RANDOM)
        .and(colorPicker.valueProperty().isNotNull()));
    colorPickerMenuItem.visibleProperty().bind(chatPrefs.chatColorModeProperty().isNotEqualTo(RANDOM));


    playerChangeListener = (observable, oldValue, newValue) -> {
      if (newValue == null) {
        inviteItem.visibleProperty().unbind();
        inviteItem.setVisible(false);
        inviteItem.setDisable(true);
        return;
      }

      if (newValue.getSocialStatus() == SELF) {
        loadAvailableAvatars(newValue);
      }

      moderatorService.getPermissions()
          .thenAccept(permissions -> setModeratorOptions(permissions, newValue));

      sendPrivateMessageItem.visibleProperty().bind(newValue.socialStatusProperty().isNotEqualTo(SELF));
      addFriendItem.visibleProperty().bind(
          newValue.socialStatusProperty().isNotEqualTo(FRIEND).and(newValue.socialStatusProperty().isNotEqualTo(SELF))
      );
      removeFriendItem.visibleProperty().bind(newValue.socialStatusProperty().isEqualTo(FRIEND));
      addFoeItem.visibleProperty().bind(newValue.socialStatusProperty().isNotEqualTo(FOE).and(newValue.socialStatusProperty().isNotEqualTo(SELF)));
      removeFoeItem.visibleProperty().bind(newValue.socialStatusProperty().isEqualTo(FOE));
      reportItem.visibleProperty().bind(newValue.socialStatusProperty().isNotEqualTo(SELF));

      joinGameItem.visibleProperty().bind(newValue.socialStatusProperty().isNotEqualTo(SELF)
          .and(newValue.statusProperty().isEqualTo(PlayerStatus.JOINING)
              .or(newValue.statusProperty().isEqualTo(PlayerStatus.HOSTING)
              .or(newValue.statusProperty().isEqualTo(PlayerStatus.JOINED)
              .or(newValue.statusProperty().isEqualTo(PlayerStatus.HOSTED)))))
          .and(Bindings.createBooleanBinding(() -> {
                return newValue.getGame() != null
                    && newValue.getGame().getGameType() != GameType.MATCHMAKER;
              }, newValue.gameProperty())
          ));
      watchGameItem.visibleProperty().bind(newValue.statusProperty().isEqualTo(PlayerStatus.PLAYING));
      watchGameInBrowserItem.visibleProperty().bind(newValue.statusProperty().isEqualTo(PlayerStatus.PLAYING)
          .and(Bindings.createBooleanBinding(browserWatchService::isAvailable)));
      inviteItem.visibleProperty().bind(Bindings.createBooleanBinding(() -> isGameInviteVisible(newValue),
          newValue.socialStatusProperty(), gameService.getCurrentGameProperty(), gameService.getCurrentGameStatusProperty()));

      // "Reserve a slot for this player" — visible only when:
      //   - target is not me
      //   - I am currently hosting a game
      //   - that game has reserved-slots enabled
      //   - the target isn't already on the reserved list
      reserveSlotItem.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
        if (newValue.getSocialStatus() == SELF) return false;
        Game myGame = gameService.getCurrentGame();
        if (myGame == null || !myGame.isReservedSlotsEnabled()) return false;
        Player self = playerService.getCurrentPlayer().orElse(null);
        if (self == null || !self.getUsername().equals(myGame.getHost())) return false;
        return !myGame.getReservedPlayers().contains(newValue.getUsername());
      }, newValue.socialStatusProperty(), gameService.getCurrentGameStatusProperty()));

      refreshInviteItemState();
    };
    JavaFxUtil.addListener(chatUser.playerProperty(), new WeakChangeListener<>(playerChangeListener));
    playerChangeListener.changed(chatUser.playerProperty(), null, chatUser.getPlayer().orElse(null));

    socialSeparator.visibleProperty().bind(addFriendItem.visibleProperty().or(
        removeFriendItem.visibleProperty().or(
            addFoeItem.visibleProperty().or(
                removeFoeItem.visibleProperty()))));
  }

  /**
   * Re-syncs the avatar/medal picker with the current featured-medal selection. Called when a cached
   * menu is re-shown (see ChatUserItemController) so a medal chosen elsewhere (e.g. the User Info
   * dialog) is reflected instead of the stale selection captured when the menu was first built.
   */
  public void refreshAvatarSelection() {
    refreshInviteItemState();
    chatUser.getPlayer()
        .filter(player -> player.getSocialStatus() == SELF)
        .ifPresent(this::loadAvailableAvatars);
  }

  private void refreshInviteItemState() {
    if (chatUser == null || inviteItem == null) {
      return;
    }

    Player player = chatUser.getPlayer().orElse(null);
    Game game = gameService.getCurrentGame();
    inviteItem.setDisable(player == null
        || !isGameInviteVisible(player)
        || !chatGameInviteService.canInvite(player.getUsername(), game));
  }

  private boolean isGameInviteVisible(Player targetPlayer) {
    if (targetPlayer == null || targetPlayer.getSocialStatus() == SELF) {
      return false;
    }

    Game myGame = gameService.getCurrentGame();
    Player currentPlayer = playerService.getCurrentPlayer().orElse(null);
    return myGame != null
        && myGame.getStatus() != null
        && myGame.getStatus().isOpen()
        && myGame.getGameType() != GameType.MATCHMAKER
        && currentPlayer != null
        && currentPlayer.getUsername().equals(myGame.getHost());
  }

  private void setModeratorOptions(Set<String> permissions, Player newValue) {
    boolean notSelf = !newValue.getSocialStatus().equals(SELF);

    kickGameItem.setVisible(notSelf & permissions.contains(GroupPermission.ADMIN_KICK_SERVER));
    kickLobbyItem.setVisible(notSelf & permissions.contains(GroupPermission.ADMIN_KICK_SERVER));
    banItem.setVisible(notSelf & permissions.contains(GroupPermission.ROLE_ADMIN_ACCOUNT_BAN));
    broadcastMessage.setVisible(notSelf & permissions.contains(GroupPermission.ROLE_WRITE_MESSAGE));
    moderatorActionSeparator.setVisible(kickGameItem.isVisible() || kickLobbyItem.isVisible() || banItem.isVisible() || broadcastMessage.isVisible());
  }

  private void loadAvailableAvatars(Player player) {
    // The picker offers the regular server avatars AND the player's own medals (CL-7), so a medal
    // can be chosen as the avatar seamlessly alongside avatars.
    avatarService.getAvailableAvatars()
        .thenCombine(ladderPointsService.getEarnedMedals(player.getId()),
            java.util.AbstractMap.SimpleImmutableEntry::new)
        .thenCombine(ladderPointsService.getFeaturedMedal(player.getId()), (avatarsAndMedals, featured) -> {
      List<AvatarBean> avatars = avatarsAndMedals.getKey();
      List<com.faforever.client.ladder.FeaturedMedalDisplay> medals = avatarsAndMedals.getValue();

      ObservableList<AvatarBean> items = FXCollections.observableArrayList(avatars);
      items.add(0, new AvatarBean(null, i18n.get("chat.userContext.noAvatar")));
      for (com.faforever.client.ladder.FeaturedMedalDisplay medal : medals) {
        items.add(new AvatarBean(null,
            com.faforever.client.ladder.LadderUiUtil.medalAvatarTooltip(i18n, medal.getCode(), medal.getCount()),
            medal.getCode()));
      }

      String currentAvatarUrl = player.getAvatarUrl();
      JavaFxUtil.runLater(() -> {
        // Suppress the selection listener while we (re)build and preselect programmatically, so a
        // refresh of a reused menu doesn't post a spurious featured-medal change back to the server.
        suppressAvatarSelectionListener = true;
        avatarComboBox.getItems().setAll(items);
        // Preselect the current choice: the featured medal if one is set, else the current avatar.
        AvatarBean selected = featured
            .flatMap(code -> items.stream().filter(b -> code.equals(b.getMedalCode())).findFirst())
            .orElseGet(() -> items.stream()
                .filter(b -> b.getMedalCode() == null
                    && Objects.equals(Objects.toString(b.getUrl(), null), currentAvatarUrl))
                .findFirst().orElse(null));
        avatarComboBox.getSelectionModel().select(selected);
        suppressAvatarSelectionListener = false;

        // Add the listener only once (the menu can be re-populated on reuse), and only after the
        // initial selection so it doesn't fire a spurious change.
        if (avatarSelectionListenerInstalled) {
          return;
        }
        avatarSelectionListenerInstalled = true;
        avatarComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
          if (suppressAvatarSelectionListener) {
            return;
          }
          if (newValue != null && newValue.getMedalCode() != null) {
            // A medal occupies the avatar slot (overrides the regular avatar) via the featured medal.
            ladderPointsService.setFeaturedMedal(player.getId(), newValue.getMedalCode())
                .thenRun(() -> eventBus.post(
                    new com.faforever.client.ladder.FeaturedMedalChangedEvent(player.getId())));
          } else {
            // A regular avatar (or "no avatar") clears any medal-as-avatar so the avatar shows.
            ladderPointsService.setFeaturedMedal(player.getId(), null)
                .thenRun(() -> eventBus.post(
                    new com.faforever.client.ladder.FeaturedMedalChangedEvent(player.getId())));
            player.setAvatarTooltip(newValue == null ? null : newValue.getDescription());
            player.setAvatarUrl(newValue == null ? null : Objects.toString(newValue.getUrl(), null));
            avatarService.changeAvatar(newValue);
          }
        });
      });
      return null;
    });
  }

  public void onShowUserInfoSelected() {
    UserInfoWindowController userInfoWindowController = uiService.loadFxml("theme/user_info_window.fxml");
    userInfoWindowController.setPlayer(chatUser.getPlayer().orElseThrow(() -> new IllegalStateException("No player for chat user: " + chatUser)));
    userInfoWindowController.setOwnerWindow(chatUserContextMenuRoot.getOwnerWindow());
    userInfoWindowController.show();
  }

  public void onSendPrivateMessageSelected() {
    eventBus.post(new InitiatePrivateChatEvent(chatUser.getUsername()));
  }

  public void onCopyUsernameSelected() {
    ClipboardUtil.copyToClipboard(chatUser.getUsername());
  }

  public void onReserveSlotSelected() {
    Game myGame = gameService.getCurrentGame();
    if (myGame == null || !myGame.isReservedSlotsEnabled()) {
      return;
    }
    Player targetPlayer = chatUser.getPlayer().orElse(null);
    if (targetPlayer == null) {
      return;
    }
    // Build the new id list = current logins (resolved to ids, skipping host
    // since the server keeps them implicitly) + the new target.
    java.util.List<Integer> ids = new java.util.ArrayList<>();
    for (String login : myGame.getReservedPlayers()) {
      if (login.equals(myGame.getHost())) continue;
      playerService.getPlayerForUsername(login).ifPresent(p -> ids.add(p.getId()));
    }
    if (!ids.contains(targetPlayer.getId())) {
      ids.add(targetPlayer.getId());
    }
    gameService.sendReservedPlayers(ids);
  }

  public void onAddFriendSelected() {
    Player player = getPlayer();
    if (player.getSocialStatus() == FOE) {
      playerService.removeFoe(player);
    }
    playerService.addFriend(player);
  }

  public void onRemoveFriendSelected() {
    Player player = getPlayer();
    playerService.removeFriend(player);
  }

  public void onReport() {
    ReportDialogController reportDialogController = uiService.loadFxml("theme/reporting/report_dialog.fxml");
    chatUser.getPlayer().ifPresentOrElse(reportDialogController::setOffender,
        () -> reportDialogController.setOffender(chatUser.getUsername()));
    reportDialogController.setOwnerWindow(chatUserContextMenuRoot.getOwnerWindow());
    reportDialogController.show();
  }

  public void onAddFoeSelected() {
    Player player = getPlayer();
    if (player.getSocialStatus() == FRIEND) {
      playerService.removeFriend(player);
    }
    playerService.addFoe(player);
  }

  public void onRemoveFoeSelected() {
    Player player = getPlayer();
    playerService.removeFoe(player);
  }

  public void onWatchGameSelected() {
    Player player = getPlayer();
    try {
      replayService.runLiveReplay(player.getGame());
    } catch (Exception e) {
      log.error("Cannot display live replay", e.getCause());
      notificationService.addImmediateErrorNotification(e, "replays.live.loadFailure.message");
    }
  }

  public void onWatchGameInBrowserSelected() {
    Player player = getPlayer();
    try {
      browserWatchService.watchInBrowser(player.getGame());
    } catch (Exception e) {
      log.error("Cannot open browser live viewer", e.getCause());
      notificationService.addImmediateErrorNotification(e, "game.watch.browserFailed");
    }
  }

  public void onViewReplaysSelected() {
    Player player = getPlayer();
    eventBus.post(new ShowUserReplaysEvent(player.getId()));
  }

  public void onInviteToGameSelected() {
    Player player = getPlayer();
    Game myGame = gameService.getCurrentGame();
    if (!isGameInviteVisible(player) || !chatGameInviteService.canInvite(player.getUsername(), myGame)) {
      refreshInviteItemState();
      return;
    }

    chatGameInviteService.inviteToGame(player.getUsername(), myGame);
    refreshInviteItemState();
  }

  public void onBan(ActionEvent actionEvent) {
    actionEvent.consume();
    Alert<?> dialog = new Alert<>(getRoot().getOwnerWindow());

    BanDialogController controller = uiService.<BanDialogController>loadFxml("theme/moderator/ban_dialog.fxml")
        .setPlayer(getPlayer())
        .setCloseListener(dialog::close);

    dialog.setContent(controller.getDialogLayout());
    dialog.setAnimation(AlertAnimation.TOP_ANIMATION);
    dialog.show();
  }

  public void onBroadcastMessage(ActionEvent actionEvent) {
    actionEvent.consume();

    TextInputDialog broadcastMessageInputDialog = new TextInputDialog();
    broadcastMessageInputDialog.setTitle(i18n.get("chat.userContext.broadcast"));

    broadcastMessageInputDialog.showAndWait()
        .ifPresent(broadcastMessage -> {
              if (broadcastMessage.isBlank()) {
                log.error("Broadcast message is empty: {}", broadcastMessage);
              } else {
                log.info("Sending broadcast message: {}", broadcastMessage);
                moderatorService.broadcastMessage(broadcastMessage);
              }
            }
        );
  }

  public void onJoinGameSelected() {
    Player player = getPlayer();
    joinGameHelper.join(player.getGame());
  }

  @NotNull
  private Player getPlayer() {
    return chatUser.getPlayer().orElseThrow(() -> new IllegalStateException("No player for chat user:" + chatUser));
  }

  public void onRemoveCustomColor() {
    colorPicker.setValue(null);
  }

  @Override
  public ContextMenu getRoot() {
    return chatUserContextMenuRoot;
  }

  public void onKickGame() {
    moderatorService.closePlayersGame(getPlayer().getId());
  }

  public void onKickLobby() {
    moderatorService.closePlayersLobby(getPlayer().getId());
  }


  public void consumer(ActionEvent actionEvent) {
    actionEvent.consume();
  }
}
