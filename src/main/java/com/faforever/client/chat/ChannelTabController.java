package com.faforever.client.chat;

import com.faforever.client.audio.AudioService;
import com.faforever.client.chat.event.ChatUserCategoryChangeEvent;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.fx.WebViewConfigurer;
import com.faforever.client.galacticwar.GalacticWarService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.SocialStatus;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.domain.ChatBanNoticeMessage;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.theme.UiService;
import com.faforever.client.uploader.ImageUploadService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.TimeService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.faforever.client.fx.PlatformService.URL_REGEX_PATTERN;
import static com.faforever.client.player.SocialStatus.FOE;
import static java.util.Locale.US;

@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ChannelTabController extends AbstractChatTabController {
  @VisibleForTesting
  static final String CSS_CLASS_MODERATOR = "moderator";
  private static final String USER_CSS_CLASS_FORMAT = "user-%s";
  private static final Comparator<CategoryOrChatUserListItem> CHAT_USER_ITEM_COMPARATOR = (o1, o2) -> {
    ChatChannelUser left = o1.getUser();
    ChatChannelUser right = o2.getUser();

    Assert.state(left != null, "Only users must be compared");
    Assert.state(right != null, "Only users must be compared");

    if (isSelf(left)) {
      return 1;
    }
    if (isSelf(right)) {
      return -1;
    }
    return right.getUsername().compareToIgnoreCase(left.getUsername());
  };
  @VisibleForTesting
  /** Maps a chat user category to a list of all user items that belong to it. */
  final Map<ChatUserCategory, List<CategoryOrChatUserListItem>> categoriesToUserListItems;
  /** Maps a chat user category to the list items that represent the respective category within the chat user list. */
  private final Map<ChatUserCategory, CategoryOrChatUserListItem> categoriesToCategoryListItems;
  /** Maps usernames to all chat user list items that belong to that user. */
  private final Map<String, List<CategoryOrChatUserListItem>> userNamesToListItems;

  private final FilteredList<CategoryOrChatUserListItem> filteredChatUserList;

  /** The list of chat user (or category) items that backs the chat user list view. */
  private final ObservableList<CategoryOrChatUserListItem> chatUserListItems;

  private final AutoCompletionHelper autoCompletionHelper;
  private final PlatformService platformService;
  private final GalacticWarService galacticWarService;
  public SplitPane splitPane;
  public ToggleButton advancedUserFilter;
  public HBox searchFieldContainer;
  public Button closeSearchFieldButton;
  public TextField searchField;
  public VBox channelTabScrollPaneVBox;
  public Tab channelTabRoot;
  public WebView messagesWebView;
  public TextField userSearchTextField;
  public TextField messageTextField;
  public VBox chatUserListViewBox;
  public VBox topicPane;
  public TextFlow topicText;
  public ToggleButton toggleSidePaneButton;
  public Label userListTitleLabel;
  public javafx.scene.control.ComboBox<String> iconModeComboBox;
  private ChatChannel chatChannel;
  private ChangeListener<ChatBanNoticeMessage> chatBanNoticeListener;
  private ChangeListener<Boolean> hideFoeMessagesListener;
  private ChangeListener<ChatColorMode> chatColorModeListener;
  private ChangeListener<String> iconModeListener;
  private final InvalidationListener channelTopicListener = observable -> JavaFxUtil.runLater(this::updateChannelTopic);
  private Popup filterUserPopup;
  private UserFilterController userFilterController;

  public static ChannelTabController getController(Node node) {
    Object controller;
    do {
      controller = node.getUserData();
      node = node.getParent();
    } while (controller == null && node != null);
    return (ChannelTabController) controller;
  }

  // TODO cut dependencies
  public ChannelTabController(
      UserService userService, ChatService chatService, PreferencesService preferencesService,
      PlayerService playerService, AudioService audioService, TimeService timeService, I18n i18n,
      ImageUploadService imageUploadService, NotificationService notificationService, ReportingService reportingService,
      UiService uiService, EventBus eventBus, WebViewConfigurer webViewConfigurer,
      CountryFlagService countryFlagService, PlatformService platformService, ChatUserService chatUserService,
      GalacticWarService galacticWarService) {

    super(webViewConfigurer, userService, chatService, preferencesService, playerService, audioService,
        timeService, i18n, imageUploadService, notificationService, reportingService, uiService,
        eventBus, countryFlagService, chatUserService);
    this.platformService = platformService;
    this.galacticWarService = galacticWarService;

    categoriesToUserListItems = new HashMap<>();
    categoriesToCategoryListItems = new HashMap<>();
    userNamesToListItems = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    chatUserListItems = FXCollections.observableArrayList();
    filteredChatUserList = new FilteredList<>(chatUserListItems);

    autoCompletionHelper = new AutoCompletionHelper(
        currentWord -> userNamesToListItems.keySet().stream()
            .filter(playerName -> {
              // Case-insensitive matching: lowercase both name and search word
              String lowerName = playerName.toLowerCase(US);
              String lowerCurrent = currentWord.toLowerCase(US);

              if (lowerCurrent.contains("_")) {
                // If the user types an underscore, match the entire username directly
                return lowerName.startsWith(lowerCurrent);
              } else {
                // Split the username into parts at all underscores and match any part
                for (String part : lowerName.split("_")) {
                  if (part.startsWith(lowerCurrent)) {
                    return true;
                  }
                }
                // No part matched
                return false;
              }
            })
            .sorted()
            .collect(Collectors.toList())
    );

    List<CategoryOrChatUserListItem> categoryObjects = createCategoryTreeObjects();
    categoryObjects.forEach(categoryItem -> {
      categoriesToCategoryListItems.put(categoryItem.getCategory(), categoryItem);
      categoriesToUserListItems.put(categoryItem.getCategory(), new ArrayList<>());
    });
    chatUserListItems.addAll(categoryObjects);
  }

  private static boolean isSelf(ChatChannelUser chatUser) {
    return chatUser.getPlayer().isPresent() && chatUser.getPlayer().get().getSocialStatus() == SocialStatus.SELF;
  }

  private void onChatBanMessage(ChatBanNoticeMessage chatBanMessage) {
    if (chatBanMessage != null && chatBanMessage.getIsBanned()) {
      boolean thisChannel = chatBanMessage.getChannels() == null
          || this.getReceiver() != null && chatBanMessage.getChannels().contains(this.getReceiver());
      if (thisChannel) {
        messageTextField.setDisable(true);
        String banText = this.i18n.get("chat.banned.message", chatBanMessage.getExpiry(), chatBanMessage.getReason());
        messageTextField.setPromptText(banText);
        return;
      }
    }
    messageTextField.setDisable(false);
    messageTextField.setPromptText(this.i18n.get("chat.messagePrompt"));
  }

  @Override
  public void close() {
    // These listeners are registered on application-scoped singletons (chat service,
    // chat preferences, chat user service) that outlive this per-channel controller, so
    // they must be removed explicitly or the controller leaks and fires on dead tabs.
    if (this.chatBanNoticeListener != null) {
      this.chatService.getChatBanNoticeMessage().removeListener(this.chatBanNoticeListener);
      this.chatBanNoticeListener = null;
    }
    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();
    if (this.hideFoeMessagesListener != null) {
      chatPrefs.hideFoeMessagesProperty().removeListener(this.hideFoeMessagesListener);
      this.hideFoeMessagesListener = null;
    }
    if (this.chatColorModeListener != null) {
      chatPrefs.chatColorModeProperty().removeListener(this.chatColorModeListener);
      this.chatColorModeListener = null;
    }
    if (this.iconModeListener != null) {
      chatUserService.iconModeProperty().removeListener(this.iconModeListener);
      this.iconModeListener = null;
    }
    super.close();
  }

  public void setChatChannel(ChatChannel chatChannel) {
    Assert.state(this.chatChannel == null, "Channel has already been set");
    this.chatChannel = chatChannel;

    String channelName = chatChannel.getName();
    setReceiver(channelName);
    channelTabRoot.setId(channelName);
    channelTabRoot.setText(channelName);
    userListTitleLabel.setText(channelName);
    onPlayerCount(chatChannel.getUsers().size());

    this.onChatBanMessage(chatService.getChatBanNoticeMessage().get());
    this.chatBanNoticeListener = (obs, oldValue, newValue) -> this.onChatBanMessage(newValue);
    this.chatService.getChatBanNoticeMessage().addListener(this.chatBanNoticeListener);

    // Maybe there already were some users; fetch them
    chatChannel.getUsers().forEach(this::onPlayerConnected);

    searchFieldContainer.visibleProperty().bind(searchField.visibleProperty());
    closeSearchFieldButton.visibleProperty().bind(searchField.visibleProperty());
    addSearchFieldListener();
    topicPane.managedProperty().bind(topicPane.visibleProperty());
    updateChannelTopic();
    JavaFxUtil.addListener(chatChannel.topicProperty(), new WeakInvalidationListener(channelTopicListener));

    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();
    hideFoeMessagesListener = (observable, oldValue, newValue) -> {
      if (newValue) {
        chatChannel.getUsers().stream().filter(chatUser -> chatUser.getSocialStatus().stream().anyMatch(socialStatus -> socialStatus == FOE))
            .forEach(chatUser -> updateUserMessageDisplay(chatUser, "none"));
      } else {
        chatChannel.getUsers().stream().filter(chatUser -> chatUser.getSocialStatus().stream().anyMatch(socialStatus -> socialStatus == FOE))
            .forEach(chatUser -> updateUserMessageDisplay(chatUser, ""));
      }
    };
    JavaFxUtil.addListener(chatPrefs.hideFoeMessagesProperty(), hideFoeMessagesListener);

    chatColorModeListener = (observable, oldValue, newValue) -> chatChannel.getUsers().forEach(this::updateUserMessageColor);
    JavaFxUtil.addListener(chatPrefs.chatColorModeProperty(), chatColorModeListener);
  }

  private void updateChannelTopic() {
    JavaFxUtil.assertApplicationThread();
    boolean hasTopic = !Strings.isNullOrEmpty(chatChannel.getTopic());
    topicPane.setVisible(hasTopic);
    topicText.getChildren().clear();
    if (!hasTopic) {
      return;
    }
    String topic = chatChannel.getTopic();
    Arrays.stream(topic.split("\\s"))
        .forEach(word -> {
          if (URL_REGEX_PATTERN.matcher(word).matches()) {
            Hyperlink link = new Hyperlink(word);
            link.setOnAction(event -> platformService.showDocument(word));
            topicText.getChildren().add(link);
          } else {
            topicText.getChildren().add(new Label(word + " "));
          }
        });
  }

  void onPlayerCount(int count) {
    super.onPlayerCount(count);
    JavaFxUtil.runLater(() -> userSearchTextField.setPromptText(i18n.get("chat.userCount", count)));
  }

  @Override
  public void initialize() {
    super.initialize();

    initIconModeComboBox();
    userSearchTextField.textProperty().addListener((observable, oldValue, newValue) -> userFilterController.filterUsers());

    channelTabScrollPaneVBox.setMinWidth(preferencesService.getPreferences().getChat().getChannelTabScrollPaneWidth());
    channelTabScrollPaneVBox.setPrefWidth(preferencesService.getPreferences().getChat().getChannelTabScrollPaneWidth());
    addUserFilterPopup();


    VirtualFlow<CategoryOrChatUserListItem, ChatUserListCell> chatUserFlow = VirtualFlow.createVertical(filteredChatUserList, chatUserListItem -> new ChatUserListCell(chatUserListItem, uiService));
    VirtualizedScrollPane<VirtualFlow<CategoryOrChatUserListItem, ChatUserListCell>> chatUserScrollPane = new VirtualizedScrollPane<>(chatUserFlow);
    VBox.setVgrow(chatUserScrollPane, Priority.ALWAYS);
    chatUserListViewBox.getChildren().add(chatUserScrollPane);

    autoCompletionHelper.bindTo(messageTextField());

    initializeSideToggle();
  }

  @Override
  public Node detachSidePanelNode() {
    splitPane.getItems().removeAll(channelTabScrollPaneVBox);
    return channelTabScrollPaneVBox;
  }

  @Override
  public void setSidePaneEnabled(boolean enabled) {
    toggleSidePaneButton.setSelected(enabled);
  }

  private void initIconModeComboBox() {
    Map<String, String> galaxyNames = galacticWarService.getGalaxyDisplayNames();

    // Build value→display mapping
    Map<String, String> valueToDisplay = new java.util.LinkedHashMap<>();
    valueToDisplay.put(ChatUserService.ICON_MODE_NONE, i18n.get("chat.iconMode.none"));
    valueToDisplay.put(ChatUserService.ICON_MODE_AVATAR, i18n.get("chat.iconMode.avatar"));
    galaxyNames.forEach((techName, displayName) ->
        valueToDisplay.put(techName, i18n.get("chat.iconMode.gwRanks", displayName)));

    iconModeComboBox.getItems().addAll(valueToDisplay.keySet());
    iconModeComboBox.setConverter(new javafx.util.StringConverter<>() {
      @Override public String toString(String value) { return valueToDisplay.getOrDefault(value, value); }
      @Override public String fromString(String string) { return string; }
    });

    // Sync combobox ↔ chatUserService.iconMode
    iconModeComboBox.setValue(chatUserService.getIconMode());
    iconModeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null && !newVal.equals(chatUserService.getIconMode())) {
        chatUserService.setIconModeManual(newVal);
      }
    });
    iconModeListener = (obs, oldVal, newVal) -> {
      if (newVal != null && !newVal.equals(iconModeComboBox.getValue())) {
        JavaFxUtil.runLater(() -> iconModeComboBox.setValue(newVal));
      }
    };
    chatUserService.iconModeProperty().addListener(iconModeListener);
  }

  private void initializeSideToggle() {
    toggleSidePaneButton.setSelected(preferencesService.getPreferences().getChat().isPlayerListShown());
    JavaFxUtil.bind(channelTabScrollPaneVBox.visibleProperty(), toggleSidePaneButton.selectedProperty());
    JavaFxUtil.bind(channelTabScrollPaneVBox.managedProperty(), channelTabScrollPaneVBox.visibleProperty());
    JavaFxUtil.addListener(toggleSidePaneButton.selectedProperty(), (observable, oldValue, newValue) -> splitPane.setDividerPositions(newValue ? 0.15 : 0.0));
    JavaFxUtil.addListener(toggleSidePaneButton.selectedProperty(), (observable, oldValue, newValue) -> {
      preferencesService.getPreferences().getChat().setPlayerListShown(newValue);
      preferencesService.storeInBackground();
    });
  }

  @NotNull
  private List<CategoryOrChatUserListItem> createCategoryTreeObjects() {
    return Arrays.stream(ChatUserCategory.values())
        .map(CategoryOrChatUserListItem::new)
        .collect(Collectors.toList());
  }

  @VisibleForTesting
  boolean isUsernameMatch(ChatChannelUser user) {
    String lowerCaseSearchString = user.getUsername().toLowerCase(US);
    return lowerCaseSearchString.contains(userSearchTextField.getText().toLowerCase(US));
  }

  @Override
  public Tab getRoot() {
    return channelTabRoot;
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
  protected void onMention(ChatMessage chatMessage) {
    if (preferencesService.getPreferences().getNotification().getNotifyOnAtMentionOnlyEnabled()
        && !chatMessage.getMessage().contains("@" + userService.getUsername())) {
      return;
    }

    if (playerService.getPlayerForUsername(chatMessage.getUsername())
        .filter(player -> player.getSocialStatus() == FOE)
        .isPresent()) {
      log.debug("Ignored ping from {}", chatMessage.getUsername());
      return;
    }

    if (!hasFocus()) {
      audioService.playChatMentionSound();
      showNotificationIfNecessary(chatMessage);
      incrementUnreadMessagesCount(1);
      setUnread(true);
    }
  }

  @Override
  public void onChatMessage(ChatMessage chatMessage) {
    Optional<Player> playerOptional = playerService.getPlayerForUsername(chatMessage.getUsername());
    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();

    if (playerOptional.isPresent() && playerOptional.get().getSocialStatus() == FOE && chatPrefs.getHideFoeMessages()) {
      return;
    }

    super.onChatMessage(chatMessage);
  }

  @Override
  protected String getMessageCssClass(String login) {
    if (i18n.get("chat.operator").equals(login)) {
      return CSS_CLASS_MODERATOR;
    }

    ChatChannelUser chatUser = chatService.getOrCreateChatUser(login, chatChannel.getName());
    Optional<Player> currentPlayerOptional = playerService.getCurrentPlayer();

    if (currentPlayerOptional.isPresent()) {
      return "";
    }

    if (chatUser.isModerator()) {
      return CSS_CLASS_MODERATOR;
    }

    return super.getMessageCssClass(login);
  }

  private void addUserFilterPopup() {
    JavaFxUtil.assertApplicationThread();
    filterUserPopup = new Popup();
    filterUserPopup.setAutoFix(false);
    filterUserPopup.setAutoHide(true);
    filterUserPopup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_TOP_RIGHT);

    userFilterController = uiService.loadFxml("theme/chat/user_filter.fxml");
    userFilterController.setChannelController(this);
    userFilterController.filterAppliedProperty().addListener(((observable, oldValue, newValue) -> advancedUserFilter.setSelected(newValue)));
    filterUserPopup.getContent().setAll(userFilterController.getRoot());
  }

  private void removeUserMessageClass(ChatChannelUser chatUser, String cssClass) {
    //TODO: DOM Exception 12 when cssClass string is empty string, not sure why cause .remove in the js should be able to handle it
    if (cssClass.isEmpty()) {
      return;
    }
    //Workaround for issue #1080 https://github.com/FAForever/downlords-faf-client/issues/1080
    JavaFxUtil.runLater(() -> {
      try {
        engine.executeScript("removeUserMessageClass('" + String.format(USER_CSS_CLASS_FORMAT, chatUser.getUsername()) + "','" + cssClass + "');");
      } catch (Exception ignored) {
        //before with "getJsObject().call..." if the engine was not yet loaded the Exception was ignored and hence I know to the same
        //TODO: only accept calls after the engine loaded the page completely
      }
    });
  }

  private void addUserMessageClass(ChatChannelUser player, String cssClass) {
    JavaFxUtil.runLater(() -> getJsObject().call("addUserMessageClass", String.format(USER_CSS_CLASS_FORMAT, player.getUsername()), cssClass));
  }

  private void updateUserMessageDisplay(ChatChannelUser chatUser, String display) {
    JavaFxUtil.runLater(() -> getJsObject().call("updateUserMessageDisplay", chatUser.getUsername(), display));
  }

  private void associateChatUserWithPlayer(Player player, ChatChannelUser chatUser) {
    chatUserService.associatePlayerToChatUser(chatUser, player);

    updateCssClass(chatUser);
    updateChatUserListItemsForCategories(chatUser);
  }

  void onPlayerConnected(ChatChannelUser chatUser) {
    super.onPlayerConnected(chatUser);
    Optional<Player> playerOptional = playerService.getPlayerForUsername(chatUser.getUsername());
    playerOptional.ifPresentOrElse(player -> associateChatUserWithPlayer(player, chatUser), () -> updateChatUserListItemsForCategories(chatUser));
  }

  /**
   * Adds and removes chat user items from the chat user list depending on the user's categories. For instance, if the
   * user is a moderator, he'll be added to the moderator category (if missing) and if he's no longer a friend, he will
   * be removed from the friends category.
   */
  private void updateChatUserListItemsForCategories(ChatChannelUser chatUser) {
    List<CategoryOrChatUserListItem> userListItems;
    synchronized (userNamesToListItems) {
      userNamesToListItems.computeIfAbsent(chatUser.getUsername(), s -> new ArrayList<>());
      userListItems = userNamesToListItems.get(chatUser.getUsername());
    }
    Set<ChatUserCategory> chatUserCategorySet = chatUser.getChatUserCategories();
    Arrays.stream(ChatUserCategory.values())
        .forEach(category -> {
          List<CategoryOrChatUserListItem> categoryUserList = categoriesToUserListItems.get(category);
          if (chatUserCategorySet.contains(category) && userListItems.stream().noneMatch(categoryUserList::contains)) {
            CategoryOrChatUserListItem userItem = new CategoryOrChatUserListItem(chatUser, category);
            userListItems.add(userItem);
            categoryUserList.add(userItem);
            addToTreeItemSorted(userItem);
          } else if (!chatUserCategorySet.contains(category) && userListItems.stream().anyMatch(categoryUserList::contains)) {
            List<CategoryOrChatUserListItem> itemsToRemove = userListItems.stream().filter(categoryUserList::contains).collect(Collectors.toList());
            userListItems.removeAll(itemsToRemove);
            categoryUserList.removeAll(itemsToRemove);
            JavaFxUtil.runLater(() -> chatUserListItems.removeAll(itemsToRemove));
          }
        });
  }

  private void addToTreeItemSorted(CategoryOrChatUserListItem child) {
    ChatUserCategory category = child.getCategory();
    CategoryOrChatUserListItem parent = categoriesToCategoryListItems.get(category);
    JavaFxUtil.runLater(() -> {
      synchronized (chatUserListItems) {
        for (int index = chatUserListItems.indexOf(parent) + 1; index < chatUserListItems.size(); index++) {
          CategoryOrChatUserListItem otherItem = chatUserListItems.get(index);

          if (otherItem.getCategory() != category || CHAT_USER_ITEM_COMPARATOR.compare(child, otherItem) > 0) {
            chatUserListItems.add(index, child);
            return;
          }
        }
        chatUserListItems.add(child);
      }
    });
  }

  private void updateCssClass(ChatChannelUser chatUser) {
    JavaFxUtil.runLater(() -> {
      if (chatUser.getPlayer().isPresent()) {
        removeUserMessageClass(chatUser, CSS_CLASS_CHAT_ONLY);
      } else {
        addUserMessageClass(chatUser, CSS_CLASS_CHAT_ONLY);
      }
      if (chatUser.isModerator()) {
        addUserMessageClass(chatUser, CSS_CLASS_MODERATOR);
      } else {
        removeUserMessageClass(chatUser, CSS_CLASS_MODERATOR);
      }
    });
  }

  void onPlayerDisconnected(ChatChannelUser user) {
    super.onPlayerDisconnected(user);
    List<CategoryOrChatUserListItem> listItemsToBeRemoved = userNamesToListItems.remove(user.getUsername());

    if (listItemsToBeRemoved != null) {
      JavaFxUtil.runLater(() -> chatUserListItems.removeAll(listItemsToBeRemoved));
      Arrays.stream(ChatUserCategory.values())
          .filter(categoriesToUserListItems::containsKey)
          .map(categoriesToUserListItems::get)
          .forEach(categoryOrChatUserListItems -> listItemsToBeRemoved.forEach(categoryOrChatUserListItems::remove));
    }
  }

  // FIXME use this again
//  private void updateRandomColorsAllowed(ChatUserHeader parent, ChatChannelUser chatUser, ChatUserItemController chatUserItemController) {
//    chatUserItemController.setRandomColorAllowed(
//        (parent == othersTreeItem || parent == chatOnlyTreeItem)
//            && chatUser.getPlayer().isPresent()
//            && chatUser.getPlayer().get().getSocialStatus() != SELF
//    );
//  }

  public void onKeyReleased(KeyEvent event) {
    if (event.getCode() == KeyCode.ESCAPE) {
      onSearchFieldClose();
    } else if (event.isControlDown() && event.getCode() == KeyCode.F) {
      searchField.clear();
      searchField.setVisible(!searchField.isVisible());
      searchField.requestFocus();
    }
  }

  public void onSearchFieldClose() {
    searchField.setVisible(false);
    searchField.clear();
  }

  private void addSearchFieldListener() {
    searchField.textProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue.trim().isEmpty()) {
        getJsObject().call("removeHighlight");
      } else {
        getJsObject().call("highlightText", newValue);
      }
    });
  }

  public void onAdvancedUserFilter(ActionEvent actionEvent) {
    advancedUserFilter.setSelected(userFilterController.isFilterApplied());
    if (filterUserPopup.isShowing()) {
      filterUserPopup.hide();
      return;
    }

    ToggleButton button = (ToggleButton) actionEvent.getSource();

    Bounds screenBounds = advancedUserFilter.localToScreen(advancedUserFilter.getBoundsInLocal());
    filterUserPopup.show(button.getScene().getWindow(), screenBounds.getMinX(), screenBounds.getMaxY());
  }

  @Override
  protected String getInlineStyle(String username) {
    if (i18n.get("chat.operator").equals(username)) {
      return "";
    }
    ChatChannelUser chatUser = chatService.getOrCreateChatUser(username, chatChannel.getName());

    Optional<Player> playerOptional = playerService.getPlayerForUsername(username);

    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();
    String color = "";
    String display = "";

    if (chatPrefs.getHideFoeMessages() && playerOptional.isPresent() && playerOptional.get().getSocialStatus() == FOE) {
      display = "display: none;";
    } else {
      if (chatUser.getColor().isPresent()) {
        color = createInlineStyleFromColor(chatUser.getColor().get());
      }
    }

    return String.format("%s%s", color, display);
  }

  void setUserFilter(Predicate<CategoryOrChatUserListItem> predicate) {
    filteredChatUserList.setPredicate(predicate);
  }

  @Subscribe
  public void onChatUserCategoryChange(ChatUserCategoryChangeEvent event) {
    // We could add a listener on chatChannelUser.socialStatusProperty() but this would result in thousands of mostly idle
    // listeners which we're trying to avoid.
    ChatChannelUser chatUser = event.getChatUser();
    if (chatChannel.getUsers().contains(chatUser)) {
      if (chatUser.getSocialStatus().stream().anyMatch(socialStatus -> socialStatus == FOE)) {
        updateUserMessageDisplay(chatUser, "none");
      } else {
        updateUserMessageDisplay(chatUser, "");
      }
      updateCssClass(chatUser);
      updateUserMessageColor(chatUser);
      updateChatUserListItemsForCategories(chatUser);
    }
  }

  @VisibleForTesting
  List<CategoryOrChatUserListItem> getChatUserItemsByCategory(ChatUserCategory category) {
    CategoryOrChatUserListItem categoryItem = categoriesToCategoryListItems.get(category);
    if (categoryItem == null) {
      return Collections.emptyList();
    }
    return filteredChatUserList.stream().filter(item -> item.getUser() != null && item.getCategory() == category).collect(Collectors.toList());
  }

  @VisibleForTesting
  boolean checkUsersAreInList(ChatUserCategory category, String... usernames) {
    List<String> names = Arrays.asList(usernames);
    long foundItems = getChatUserItemsByCategory(category).stream()
        .map(userItem -> userItem.getUser().getUsername()).filter(names::contains).count();
    return foundItems == names.size();
  }
}
