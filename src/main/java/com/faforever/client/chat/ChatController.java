package com.faforever.client.chat;

import com.faforever.client.chat.event.ChatMessageEvent;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.main.event.JoinChannelEvent;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.NavigationItem;
import com.faforever.client.net.ConnectionState;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.theme.UiService;
import com.faforever.client.user.UserService;
import com.faforever.client.user.event.LoggedOutEvent;
import com.faforever.client.util.ProgrammingError;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.faforever.client.chat.ChatService.PARTY_CHANNEL_SUFFIX;
import static com.faforever.client.chat.ChatService.GAME_CHANNEL_REGEX;

@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ChatController extends AbstractViewController<Node> {

  private final Map<String, AbstractChatTabController> nameToChatTabController;
  private final ChatService chatService;
  private final UiService uiService;
  private final UserService userService;
  private final NotificationService notificationService;
  private final EventBus eventBus;
  public Node chatRoot;
  public HBox chatContainer;
  public TabPane tabPane;
  public Pane connectingProgressPane;
  public VBox noOpenTabsContainer;
  public TextField channelNameTextField;
  public Label tabButtonSpacer;
  public Node userListNode; // stolen from ChannelTabControllers in tabPane
  public VBox userListContainer;

  public static ChatController getController(Node node) {
    Object controller;
    do {
      controller = node.getUserData();
      node = node.getParent();
    } while (controller == null && node != null);
    return (ChatController) controller;
  }

  public ChatController(ChatService chatService, UiService uiService, UserService userService, NotificationService notificationService, EventBus eventBus) {
    this.chatService = chatService;
    this.uiService = uiService;
    this.userService = userService;
    this.notificationService = notificationService;
    this.eventBus = eventBus;

    nameToChatTabController = new HashMap<>();
  }

  private void onChannelLeft(ChatChannel chatChannel) {
    JavaFxUtil.runLater(() -> removeTab(chatChannel.getName()));
  }

  private void onChannelJoined(ChatChannel chatChannel) {
    String channelName = chatChannel.getName();
    chatService.addUsersListener(channelName, change -> {
      if (change.wasRemoved()) {
        onChatUserLeftChannel(change.getValueRemoved(), channelName);
      }
      if (change.wasAdded()) {
        onUserJoinedChannel(change.getValueAdded(), channelName);
      }
    });
  }

  private void onDisconnected() {
    JavaFxUtil.runLater(() -> {
      connectingProgressPane.setVisible(true);
      tabPane.setVisible(false);
    });
  }

  private void onConnected() {
    JavaFxUtil.runLater(() -> {
      connectingProgressPane.setVisible(false);
      tabPane.setVisible(true);
    });
  }

  private void onConnecting() {
    JavaFxUtil.runLater(() -> {
      connectingProgressPane.setVisible(true);
      tabPane.setVisible(false);
    });
  }

  private void onLoggedOut() {
    JavaFxUtil.runLater(() -> tabPane.getTabs().clear());
  }

  private void removeTab(String playerOrChannelName) {
    JavaFxUtil.assertApplicationThread();
    AbstractChatTabController controller = nameToChatTabController.get(playerOrChannelName);
    if (controller != null) {
      tabPane.getTabs().remove(controller.getRoot());
    }
  }

  public void setUserListContainer(VBox node) {
    this.userListContainer = node;
    stealUserListNode(nameToChatTabController.get(tabPane.getSelectionModel().getSelectedItem().getId()));
  }

  private void stealUserListNode(AbstractChatTabController tab) {
    if (userListContainer == null) {
      return;
    }

    Optional<Boolean> isVisible = Optional.empty();
    if (userListNode != null) {
      userListContainer.getChildren().removeAll(userListNode);
      isVisible = Optional.of(userListNode.isVisible());
    }
    userListNode = tab.detachSidePanelNode();
    if (userListNode != null) {
      userListContainer.getChildren().add(0, userListNode);
      VBox.setVgrow(userListNode, Priority.ALWAYS);
      if (isVisible.isPresent()) {
        tab.setSidePaneEnabled(isVisible.get());
      }
    }
  }

  public void clearUserListNode(Event event) {
    if (this.userListNode != null && userListContainer != null) {
      userListContainer.getChildren().removeAll(this.userListNode);
    }
  }

  private AbstractChatTabController getOrCreateChannelTab(String channelName) {
    JavaFxUtil.assertApplicationThread();
    ChannelTabController tab;
    if (!nameToChatTabController.containsKey(channelName)) {
      tab = uiService.loadFxml("theme/chat/channel_tab.fxml");
      tab.setChatChannel(chatService.getOrCreateChannel(channelName));
      addTab(channelName, tab);
    }
    else {
      tab = (ChannelTabController) nameToChatTabController.get(channelName);
    }
    return tab;
  }

  private void addTab(String playerOrChannelName, AbstractChatTabController tabController) {
    JavaFxUtil.assertApplicationThread();
    nameToChatTabController.put(playerOrChannelName, tabController);
    Tab tab = tabController.getRoot();

    if (chatService.isDefaultChannel(playerOrChannelName)) {
      tabPane.getTabs().add(0, tab);
    } else {
      tabPane.getTabs().add(tabPane.getTabs().size() - 1, tab);
    }

    if (!(tabController instanceof ChannelTabController)) {
      tabController.getRoot().setOnSelectionChanged(event -> clearUserListNode(event));
    }

    tabPane.getSelectionModel().select(tab);
    nameToChatTabController.get(tab.getId()).onDisplay();

    tabController.setOnSelectedListener((obs, oldValue, newValue) -> {
      if (oldValue == false && newValue == true) {
        stealUserListNode(tabController);
      }});
    stealUserListNode(tabController);
  }

  @Override
  public void initialize() {
    super.initialize();
    eventBus.register(this);

    chatService.addChannelsListener(change -> {
      if (change.wasRemoved() && !isMatchmakerPartyMessage(change.getValueRemoved().getName())) {
        onChannelLeft(change.getValueRemoved());
      }
      if (change.wasAdded() && !isMatchmakerPartyMessage(change.getValueAdded().getName())) {
        onChannelJoined(change.getValueAdded());
      }
    });
    if (userService.getUsername() != null) {
      chatService.getUserChannels(userService.getUsername()).stream()
          .filter(channelName -> !isMatchmakerPartyMessage(channelName))
          .forEach(channelName -> getOrCreateChannelTab(channelName));
    }

    JavaFxUtil.addListener(chatService.connectionStateProperty(), (observable, oldValue, newValue) -> onConnectionStateChange(newValue));
    onConnectionStateChange(chatService.connectionStateProperty().get());

    JavaFxUtil.addListener(tabPane.getTabs(), (ListChangeListener<Tab>) change -> {
      while (change.next()) {
        change.getRemoved().forEach(tab -> nameToChatTabController.remove(tab.getId()));
      }
    });
 }

  @Subscribe
  public void onLoggedOutEvent(LoggedOutEvent event) {
    onLoggedOut();
  }

  private void onConnectionStateChange(ConnectionState newValue) {
    switch (newValue) {
      case DISCONNECTED:
        onDisconnected();
        break;
      case CONNECTED:
        onConnected();
        break;
      case CONNECTING:
        onConnecting();
        break;
      default:
        throw new ProgrammingError("Uncovered connection state: " + newValue);
    }
  }

  @Subscribe
  public void onChatMessage(ChatMessageEvent event) {
    ChatMessage message = event.getMessage();
    if (isMatchmakerPartyMessage(message)) {
      return;
    }
    JavaFxUtil.runLater(() -> {
      if (!message.isPrivate()) {
        getOrCreateChannelTab(message.getSource()).onChatMessage(message);
      } else {
        addAndGetPrivateMessageTab(message.getSource()).onChatMessage(message);
      }
    });
  }

  private boolean isMatchmakerPartyMessage(ChatMessage message) {
    return message.getSource() != null && isMatchmakerPartyMessage(message.getSource());
  }

  private boolean isMatchmakerPartyMessage(String channelName) {
    return channelName.endsWith(PARTY_CHANNEL_SUFFIX) || GAME_CHANNEL_REGEX.matcher(channelName).matches();
  }

  private AbstractChatTabController addAndGetPrivateMessageTab(String username) {
    JavaFxUtil.assertApplicationThread();
    if (!nameToChatTabController.containsKey(username)) {
      PrivateChatTabController tab = uiService.loadFxml("theme/chat/private_chat_tab.fxml");
      tab.setReceiver(username);
      addTab(username, tab);
    }

    return nameToChatTabController.get(username);
  }

  public Node getRoot() {
    return chatRoot;
  }

  @Subscribe
  public void onInitiatePrivateChatEvent(InitiatePrivateChatEvent event) {
    JavaFxUtil.runLater(() -> openPrivateMessageTabForUser(event.getUsername()));
  }

  private void openPrivateMessageTabForUser(String username) {
    JavaFxUtil.assertApplicationThread();
    if (username.equalsIgnoreCase(userService.getUsername())) {
      return;
    }
    AbstractChatTabController controller = addAndGetPrivateMessageTab(username);
    Tab tab = controller.getRoot();
    eventBus.post(new NavigateEvent(NavigationItem.PLAY));
    tabPane.getSelectionModel().select(tab);
    nameToChatTabController.get(tab.getId()).onDisplay();
  }

  public void onJoinChannelButtonClicked() {
    String channelName = channelNameTextField.getText();
    channelNameTextField.clear();
    if (!channelName.startsWith("#")) {
      log.info("Channel name {} does not start with #", channelName);
      notificationService.addImmediateWarnNotification("chat.error.noHashTag", channelName);
      return;
    }

    joinChannel(channelName);
  }

  private void joinChannel(String channelName) {
    chatService.joinChannel(channelName);
  }

  private void onChatUserLeftChannel(ChatChannelUser chatUser, String channelName) {
    if (isCurrentUser(chatUser)) {
      AbstractChatTabController chatTab = nameToChatTabController.get(channelName);
      if (chatTab != null) {
        JavaFxUtil.runLater(() -> tabPane.getTabs().remove(chatTab.getRoot()));
      }
    }
  }

  private void onUserJoinedChannel(ChatChannelUser chatUser, String channelName) {
    if (isCurrentUser(chatUser)) {
      JavaFxUtil.runLater(() -> {
        AbstractChatTabController tabController = getOrCreateChannelTab(channelName);
        onConnected();
        if (chatService.isDefaultChannel(channelName)) {
          Tab tab = tabController.getRoot();
          tabPane.getSelectionModel().select(tab);
          nameToChatTabController.get(tab.getId()).onDisplay();
        }
      });
    }
  }

  private boolean isCurrentUser(ChatChannelUser chatUser) {
    return chatUser.getUsername().equalsIgnoreCase(userService.getUsername());
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    if (navigateEvent instanceof JoinChannelEvent) {
      String channelName = ((JoinChannelEvent) navigateEvent).getChannel();
      chatService.joinChannel(channelName);
      AbstractChatTabController controller = nameToChatTabController.get(channelName);
      if (controller != null) {
        this.tabPane.getSelectionModel().select(controller.getRoot());
      }
    }

    else if (!tabPane.getTabs().isEmpty()) {
      Tab tab = tabPane.getSelectionModel().getSelectedItem();
      Optional.ofNullable(nameToChatTabController.get(tab.getId())).ifPresent(AbstractChatTabController::onDisplay);
    }
  }

  @Override
  public void onHide() {
    super.onHide();
    if (!tabPane.getTabs().isEmpty()) {
      Tab tab = tabPane.getSelectionModel().getSelectedItem();
      Optional.ofNullable(nameToChatTabController.get(tab.getId())).ifPresent(AbstractChatTabController::onHide);
    }
  }

}
