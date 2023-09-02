package com.faforever.client.chat;

import com.faforever.client.audio.AudioService;
import com.faforever.client.chat.event.UnreadPartyMessageEvent;
import com.faforever.client.fx.WebViewConfigurer;
import com.faforever.client.game.PlayerStatus;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.theme.UiService;
import com.faforever.client.uploader.ImageUploadService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.TimeService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Locale.US;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MatchmakingChatController extends AbstractChatTabController {

  private final AutoCompletionHelper autoCompletionHelper;
  private final Set<ChatChannelUser> chatChannelUsers;

  public Tab matchmakingChatTabRoot;
  public WebView messagesWebView;
  public TextInputControl messageTextField;
  public TextFlow topicText;
  public Button dockButton;

  private ChatChannel channel;

  // TODO cut dependencies
  public MatchmakingChatController(UserService userService,
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

    chatChannelUsers = new HashSet<>();
    autoCompletionHelper = new AutoCompletionHelper(
        currentWord -> chatChannelUsers.stream()
            .map(ChatChannelUser::getUsername)
            .filter(playerName -> playerName.toLowerCase(US).startsWith(currentWord.toLowerCase()))
            .sorted()
            .collect(Collectors.toList())
    );
  }

  @Override
  public void initialize() {
    super.initialize();
    autoCompletionHelper.bindTo(messageTextField());
  }

  @Override
  public Tab getRoot() {
    return matchmakingChatTabRoot;
  }

  public void setTopic(String topic) {
    topicText.getChildren().clear();
    Arrays.stream(topic.split("\\s"))
        .forEach(word -> {
          Label label = new Label(word + " ");
          label.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
          topicText.getChildren().add(label);
        });
  }

  public void setChannel(String partyName) {
    channel = chatService.getOrCreateChannel(partyName);
    chatService.joinChannel(partyName);
    setReceiver(partyName);
    matchmakingChatTabRoot.setId(partyName);
    matchmakingChatTabRoot.setText(partyName);
    setTopic(i18n.get("teammatchmaking.chat.topic"));
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
    PlayerStatus localPlayerStatus = PlayerStatus.IDLE;
    if (playerService.getCurrentPlayer().isPresent()) {
      localPlayerStatus = playerService.getCurrentPlayer().get().getStatus();
    }

    if (!hasFocus() && !playerService.isCurrentPlayer(chatMessage.getSubject())) {
      if (Set.of(PlayerStatus.IDLE, PlayerStatus.HOSTING, PlayerStatus.JOINING).contains(localPlayerStatus)) {
        audioService.playPrivateMessageSound();
      }
      eventBus.post(new UnreadPartyMessageEvent(chatMessage));
    }

    super.onChatMessage(chatMessage);
  }

  @VisibleForTesting
  void onPlayerDisconnected(ChatChannelUser user) {
    super.onPlayerDisconnected(user);
    chatChannelUsers.remove(user);
    onChatMessage(new ChatMessage(getReceiver(), Instant.now(),
        i18n.get("chat.operator") + ":", i18n.get("chat.groupChat.playerDisconnect", user.getUsername()),true)
            .setSubject(user.getUsername()));
  }

  @VisibleForTesting
  void onPlayerConnected(ChatChannelUser user) {
    super.onPlayerConnected(user);
    chatChannelUsers.add(user);
    onChatMessage(new ChatMessage(getReceiver(), Instant.now(),
        i18n.get("chat.operator") + ":", i18n.get("chat.groupChat.playerConnect", user.getUsername()), true)
        .setSubject(user.getUsername()));
  }

  public Button getDockButton() {
    return this.dockButton;
  }
}
