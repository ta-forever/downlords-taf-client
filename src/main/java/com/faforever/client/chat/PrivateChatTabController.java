package com.faforever.client.chat;

import com.faforever.client.audio.AudioService;
import com.faforever.client.chat.event.UnreadPrivateMessageEvent;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.WebViewConfigurer;
import com.faforever.client.game.PlayerStatus;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.theme.UiService;
import com.faforever.client.uploader.ImageUploadService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.TimeService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputControl;
import javafx.scene.web.WebView;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static com.faforever.client.player.SocialStatus.FOE;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PrivateChatTabController extends AbstractChatTabController {

  public Tab privateChatTabRoot;
  public WebView messagesWebView;
  public TextInputControl messageTextField;
  public PrivateUserInfoController privateUserInfoController;
  public ScrollPane gameDetailScrollPane;
  public SplitPane splitPane;

  private boolean userOffline;

  @Inject
  // TODO cut dependencies
  public PrivateChatTabController(UserService userService,
                                  PreferencesService preferencesService,
                                  PlayerService playerService,
                                  TimeService timeService,
                                  I18n i18n,
                                  ImageUploadService imageUploadService,
                                  NotificationService notificationService,
                                  ReportingService reportingService,
                                  UiService uiService,
                                  EventBus eventBus,
                                  AudioService audioService,
                                  ChatService chatService,
                                  WebViewConfigurer webViewConfigurer,
                                  CountryFlagService countryFlagService,
                                  ChatUserService chatUserService) {
    super(webViewConfigurer, userService, chatService, preferencesService, playerService, audioService,
        timeService, i18n, imageUploadService, notificationService, reportingService, uiService,
        eventBus, countryFlagService, chatUserService);
  }


  boolean isUserOffline() {
    return userOffline;
  }

  @Override
  public Tab getRoot() {
    return privateChatTabRoot;
  }

  @Override
  public void setReceiver(String username) {
    super.setReceiver(username);
    privateChatTabRoot.setId(username);
    privateChatTabRoot.setText(username);

    ChatChannelUser chatUser = chatService.getOrCreateChatUser(username, username, false);
    privateUserInfoController.setChatUser(chatUser);
  }

  public void initialize() {
    super.initialize();
    JavaFxUtil.fixScrollSpeed(gameDetailScrollPane);
    userOffline = false;
  }

  @Override
  protected TextInputControl messageTextField() {
    return messageTextField;
  }

  @Override
  protected WebView getMessagesWebView() {
    return messagesWebView;
  }

  @Override
  public void onChatMessage(ChatMessage chatMessage) {
    Optional<Player> playerOptional = playerService.getPlayerForUsername(chatMessage.getUsername());
    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();

    if (playerOptional.isPresent() && playerOptional.get().getSocialStatus() == FOE && chatPrefs.getHideFoeMessages()) {
      return;
    }

    PlayerStatus localPlayerStatus = PlayerStatus.IDLE;
    if (playerOptional.isPresent()) {
      localPlayerStatus = playerOptional.get().getStatus();
    }

    if (!hasFocus()) {
      if (Set.of(PlayerStatus.IDLE, PlayerStatus.HOSTING, PlayerStatus.JOINING).contains(localPlayerStatus)) {
        audioService.playPrivateMessageSound();
        showNotificationIfNecessary(chatMessage);
      }
      eventBus.post(new UnreadPrivateMessageEvent(chatMessage));
    }

    super.onChatMessage(chatMessage);
  }

  @VisibleForTesting
  void onPlayerDisconnected(ChatChannelUser user) {
    if (!user.getUsername().equals(getReceiver())) {
      return;
    }
    super.onPlayerDisconnected(user);
    userOffline = true;
    onChatMessage(new ChatMessage(user.getUsername(), Instant.now(), i18n.get("chat.operator") + ":", i18n.get("chat.privateMessage.playerLeft", user.getUsername()), true));
  }

  @VisibleForTesting
  void onPlayerConnected(ChatChannelUser user) {
    super.onPlayerConnected(user);
    if (!userOffline || !user.getUsername().equals(getReceiver())) {
      return;
    }
    userOffline = false;
    onChatMessage(new ChatMessage(user.getUsername(), Instant.now(), i18n.get("chat.operator") + ":", i18n.get("chat.privateMessage.playerReconnect", user.getUsername()), true));
  }

  @Override
  public Node detachSidePanelNode() {
    splitPane.getItems().remove(gameDetailScrollPane);
    return gameDetailScrollPane;
  }

  @Override
  public void setSidePaneEnabled(boolean enabled) {
    gameDetailScrollPane.setVisible(enabled);
    gameDetailScrollPane.setManaged(enabled);
  }

}
