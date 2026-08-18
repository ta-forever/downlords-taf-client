package com.faforever.client.chat;

import com.faforever.client.audio.AudioService;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.WebViewConfigurer;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.NavigationItem;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.TransientNotification;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.SocialStatus;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.ToxicityAction;
import com.faforever.client.preferences.ToxicitySetting;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.StageHolder;
import com.faforever.client.uploader.ImageUploadService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.ConcurrentUtil;
import com.faforever.client.util.IdenticonUtil;
import com.faforever.client.util.TimeService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.eventbus.EventBus;
import com.google.common.io.CharStreams;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.MapChangeListener;
import javafx.concurrent.Worker;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.skin.TabPaneSkin;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.faforever.client.theme.UiService.CHAT_CONTAINER;
import static com.faforever.client.theme.UiService.CHAT_SECTION_COMPACT;
import static com.faforever.client.theme.UiService.CHAT_SECTION_EXTENDED;
import static com.faforever.client.theme.UiService.CHAT_TEXT_COMPACT;
import static com.faforever.client.theme.UiService.CHAT_TEXT_EXTENDED;
import static com.github.nocatch.NoCatch.noCatch;
import static com.google.common.html.HtmlEscapers.htmlEscaper;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static javafx.scene.AccessibleAttribute.ITEM_AT_INDEX;

/**
 * A chat tab displays messages in a {@link WebView}. The WebView is used since text on a JavaFX canvas isn't
 * selectable, but text within a WebView is. This comes with some ugly implications; some of the logic has to be
 * performed in interaction with JavaScript, like when the user clicks a link.
 */
public abstract class AbstractChatTabController implements Controller<Tab> {

  static final String CSS_CLASS_CHAT_ONLY = "chat_only";
  private static final String MESSAGE_CONTAINER_ID = "chat-container";
  private static final String MESSAGE_ITEM_CLASS = "chat-section";
  private static final String LAST_READ_DELIMITER_ID = "last-read-message";
  private static final PseudoClass UNREAD_PSEUDO_STATE = PseudoClass.getPseudoClass("unread");
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final org.springframework.core.io.Resource CHAT_JS_RESOURCE = new ClassPathResource("/js/chat_container.js");
  private static final org.springframework.core.io.Resource AUTOLINKER_JS_RESOURCE = new ClassPathResource("/js/Autolinker.min.js");
  private static final org.springframework.core.io.Resource JQUERY_JS_RESOURCE = new ClassPathResource("js/jquery-2.1.4.min.js");
  private static final org.springframework.core.io.Resource JQUERY_HIGHLIGHT_JS_RESOURCE = new ClassPathResource("js/jquery.highlight-5.closure.js");

  public static final String CHANNEL_NAME_GROUP_NAME = "channelName";
  /**
   * A pattern identifying all strings with a # in front and not starting with a number. Those are interpreted as
   * irc-channels.
   */
  private static final Pattern CHANNEL_USER_PATTERN = Pattern.compile("(^|\\s)(?<" + CHANNEL_NAME_GROUP_NAME + ">#[a-zA-Z]\\S+)", CASE_INSENSITIVE);

  private static final String ACTION_PREFIX = "/me ";
  private static final String JOIN_PREFIX = "/join ";
  private static final String WHOIS_PREFIX = "/whois ";
  private static final String SECRET_REPLAY_VAULT_ACTIVATE = "/opensesame";
  private static final String CHANSERV_USER = "ChanServ";
  /**
   * Added if a message is what IRC calls an "action".
   */
  private static final String ACTION_CSS_CLASS = "action";
  private static final String MESSAGE_CSS_CLASS = "message";
  protected final UserService userService;
  protected final ChatService chatService;
  protected final PreferencesService preferencesService;
  protected final PlayerService playerService;
  protected final AudioService audioService;
  protected final TimeService timeService;
  protected final I18n i18n;
  protected final NotificationService notificationService;
  protected final ReportingService reportingService;
  protected final UiService uiService;
  protected final com.faforever.client.ladder.LadderPointsService ladderPointsService;
  protected final EventBus eventBus;
  protected final WebViewConfigurer webViewConfigurer;
  protected final ChatUserService chatUserService;
  private final ImageUploadService imageUploadService;
  private final CountryFlagService countryFlagService;

  /**
   * Messages that arrived before the web view was ready. Those are appended as soon as it is ready.
   */
  private final List<ChatMessage> waitingMessages;
  private final IntegerProperty unreadMessagesCount;
  private final ChangeListener<Boolean> resetUnreadMessagesListener;
  private final ChangeListener<Number> unreadMessagesCountListener;

  private final ChangeListener<Number> zoomChangeListener;
  private final ChangeListener<Boolean> tabPaneFocusedListener;
  private final ChangeListener<Boolean> stageFocusedListener;
  private MapChangeListener<String, ChatChannelUser> usersChangeListener;
  private int lastEntryId;
  private boolean isChatReady;
  /**
   * Either a channel like "#coreprime" or a user like "Visionik".
   */
  private String receiver;
  private Pattern mentionPattern;
  private ChatMessage lastMessage;
  WebEngine engine;

  @Inject
  // TODO cut dependencies
  public AbstractChatTabController(
      WebViewConfigurer webViewConfigurer, UserService userService, ChatService chatService,
      PreferencesService preferencesService, PlayerService playerService, AudioService audioService,
      TimeService timeService, I18n i18n, ImageUploadService imageUploadService,
      NotificationService notificationService, ReportingService reportingService, UiService uiService,
      EventBus eventBus, CountryFlagService countryFlagService, ChatUserService chatUserService,
      com.faforever.client.ladder.LadderPointsService ladderPointsService) {

    this.webViewConfigurer = webViewConfigurer;
    this.uiService = uiService;
    this.ladderPointsService = ladderPointsService;
    this.chatService = chatService;
    this.userService = userService;
    this.preferencesService = preferencesService;
    this.playerService = playerService;
    this.audioService = audioService;
    this.timeService = timeService;
    this.i18n = i18n;
    this.imageUploadService = imageUploadService;
    this.notificationService = notificationService;
    this.reportingService = reportingService;
    this.eventBus = eventBus;
    this.countryFlagService = countryFlagService;
    this.chatUserService = chatUserService;

    waitingMessages = new ArrayList<>();
    unreadMessagesCount = new SimpleIntegerProperty();
    resetUnreadMessagesListener = (observable, oldValue, newValue) -> setUnread(false);
    unreadMessagesCountListener = (observable, oldValue, newValue) -> {
      if (lastEntryId > 0 && oldValue.intValue()==0 && newValue.intValue()>0) {
        removeMessageId(LAST_READ_DELIMITER_ID);
        insertIntoContainer(String.format("<hr id='%s'>", LAST_READ_DELIMITER_ID), "chat-section-" + lastEntryId);
      }
      chatService.incrementUnreadMessagesCount(newValue.intValue() - oldValue.intValue());
    };

    zoomChangeListener = (observable, oldValue, newValue) -> {
      preferencesService.getPreferences().getChat().setZoom(newValue.doubleValue());
      preferencesService.storeInBackground();
    };
    stageFocusedListener = (window, windowFocusOld, windowFocusNew) -> {
      if (getRoot() != null &&
          getRoot().getTabPane() != null &&
          getRoot().getTabPane().isVisible()
      ) {
        try {
          messageTextField().requestFocus();
        }
        catch (IllegalStateException e) { } // during shutdown after "view has already been closed"
      }
    };
    tabPaneFocusedListener = (focusedTabPane, oldTabPaneFocus, newTabPaneFocus) -> {
      if (newTabPaneFocus) {
        messageTextField().requestFocus();
      }
    };
  }

  /**
   * Returns true if this chat tab is currently focused by the user. Returns false if a different tab is selected, the
   * user is not in "chat" or if the window has no focus.
   */
  protected boolean hasFocus() {
    if (!getRoot().isSelected()) {
      return false;
    }

    TabPane tabPane = getRoot().getTabPane();
    return tabPane != null
        && JavaFxUtil.isVisibleRecursively(tabPane)
        && tabPane.getScene().getWindow().isFocused()
        && tabPane.getScene().getWindow().isShowing();
  }

  protected void setUnread(boolean unread) {
    TabPane tabPane = getRoot().getTabPane();
    if (tabPane == null) {
      return;
    }
    TabPaneSkin skin = (TabPaneSkin) tabPane.getSkin();
    if (skin == null) {
      return;
    }
    int tabIndex = tabPane.getTabs().indexOf(getRoot());
    if (tabIndex == -1) {
      // Tab has been closed
      return;
    }
    Node tab = (Node) skin.queryAccessibleAttribute(ITEM_AT_INDEX, tabIndex);
    tab.pseudoClassStateChanged(UNREAD_PSEUDO_STATE, unread);

    if (!unread) {
      synchronized (unreadMessagesCount) {
        if (unreadMessagesCount.get() == 0) {
          removeMessageId(LAST_READ_DELIMITER_ID);
        }
        unreadMessagesCount.setValue(0);
      }
    }
  }

  public abstract Tab getRoot();

  protected void incrementUnreadMessagesCount(int delta) {
    synchronized (unreadMessagesCount) {
      unreadMessagesCount.set(unreadMessagesCount.get() + delta);
    }
  }

  public String getReceiver() {
    return receiver;
  }

  public void setReceiver(String receiver) {
    if (this.receiver != null) {
      this.removeUsersChangeListener();
      eventBus.unregister(this);
      setUnread(false);
      unreadMessagesCount.removeListener(unreadMessagesCountListener);
    }

    this.receiver = receiver;
    usersChangeListener = change -> JavaFxUtil.runLater(() -> {
      if (change.wasAdded() && !change.getValueAdded().getUsername().equals(CHANSERV_USER)) {
        onPlayerConnected(change.getValueAdded());
      } else if (change.wasRemoved() && !change.getValueRemoved().getUsername().equals(CHANSERV_USER)) {
        onPlayerDisconnected(change.getValueRemoved());
      }
      onPlayerCount(change.getMap().size());
    });
    if (receiver.startsWith("#")) {
      chatService.addUsersListener(receiver, usersChangeListener);
    } else {
      chatService.addChatUsersByNameListener(usersChangeListener);
    }

    eventBus.register(this);
    unreadMessagesCount.addListener(unreadMessagesCountListener);
  }

  private void removeUsersChangeListener() {
    if (receiver == null || usersChangeListener == null) {
      return;
    }
    if (receiver.startsWith("#")) {
      chatService.removeUsersListener(receiver, usersChangeListener);
    }
    else {
      chatService.removeChatUsersByNameListener(usersChangeListener);
    }
  }

  public void close() {
    if (receiver == null || usersChangeListener == null || unreadMessagesCountListener==null) {
      return;
    }

    eventBus.unregister(this);
    setUnread(false);
    unreadMessagesCount.removeListener(unreadMessagesCountListener);

    if (receiver.startsWith("#")) {
      chatService.leaveChannel(receiver);
      chatService.removeUsersListener(receiver, usersChangeListener);
    }
    else {
      chatService.removeChatUsersByNameListener(usersChangeListener);
    }

    this.receiver = null;
  }

  public void onClosed(Event event) {
    close();
  }

  public void initialize() {
    mentionPattern = Pattern.compile("\\b(" + Pattern.quote(userService.getUsername()) + ")\\b", CASE_INSENSITIVE);

    initChatView();

    addFocusListeners();
    addImagePasteListener();

    JavaFxUtil.addListener(StageHolder.getStage().focusedProperty(), new WeakChangeListener<>(resetUnreadMessagesListener));
    JavaFxUtil.addListener(getRoot().selectedProperty(), new WeakChangeListener<>(resetUnreadMessagesListener));

    preferencesService.getPreferences().getChat().showToxicityProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue) {
        JavaFxUtil.runLater(() -> callJs("enableToxicityVisibility"));
      } else {
        JavaFxUtil.runLater(() -> callJs("disableToxicityVisibility"));
      }
    });

    getRoot().setOnClosed(this::onClosed);
  }

  /**
   * Registers listeners necessary to focus the message input field when changing to another message tab, changing from
   * another tab to the "chat" tab or re-focusing the window.
   */
  private void addFocusListeners() {
    JavaFxUtil.addListener(getRoot().selectedProperty(), (observable, oldValue, newValue) -> {
      if (newValue) {
        // Since a tab is marked as "selected" before it's rendered, the text field can't be selected yet.
        // So let's schedule the focus to be executed afterwards
        JavaFxUtil.runLater(messageTextField()::requestFocus);
      }
    });

    JavaFxUtil.addListener(getRoot().tabPaneProperty(), (tabPane, oldTabPane, newTabPane) -> {
      if (newTabPane == null) {
        return;
      }
      JavaFxUtil.addListener(StageHolder.getStage().focusedProperty(), new WeakChangeListener<>(stageFocusedListener));
      JavaFxUtil.addListener(newTabPane.focusedProperty(), new WeakChangeListener<>(tabPaneFocusedListener));
    });
  }

  private void addImagePasteListener() {
    TextInputControl messageTextField = messageTextField();
    messageTextField.setOnKeyReleased(event -> {
      if (isPaste(event)
          && Clipboard.getSystemClipboard().hasImage()) {
        pasteImage();
      }
    });
  }

  protected abstract TextInputControl messageTextField();

  private boolean isPaste(KeyEvent event) {
    return (event.getCode() == KeyCode.V && event.isShortcutDown())
        || (event.getCode() == KeyCode.INSERT && event.isShiftDown());
  }

  private void pasteImage() {
    TextInputControl messageTextField = messageTextField();
    int currentCaretPosition = messageTextField.getCaretPosition();

    messageTextField.setDisable(true);

    Clipboard clipboard = Clipboard.getSystemClipboard();
    Image image = clipboard.getImage();

    imageUploadService.uploadImageInBackground(image).thenAccept(url -> {
      messageTextField.insertText(currentCaretPosition, url);
      messageTextField.setDisable(false);
      messageTextField.requestFocus();
      messageTextField.positionCaret(messageTextField.getLength());
    }).exceptionally(throwable -> {
      messageTextField.setDisable(false);
      return null;
    });
  }

  private void initChatView() {
    WebView messagesWebView = getMessagesWebView();
    webViewConfigurer.configureWebView(messagesWebView)
        .addOpenTadaPageDispatcher();

    messagesWebView.zoomProperty().addListener(new WeakChangeListener<>(zoomChangeListener));

    configureBrowser(messagesWebView);
    loadChatContainer();
  }

  private void loadChatContainer() {
    try (Reader reader = new InputStreamReader(uiService.getThemeFileUrl(CHAT_CONTAINER).openStream())) {
      String chatContainerHtml = CharStreams.toString(reader)
          .replace("{chat-container-js}", CHAT_JS_RESOURCE.getURL().toExternalForm())
          .replace("{auto-linker-js}", AUTOLINKER_JS_RESOURCE.getURL().toExternalForm())
          .replace("{jquery-js}", JQUERY_JS_RESOURCE.getURL().toExternalForm())
          .replace("{jquery-highlight-js}", JQUERY_HIGHLIGHT_JS_RESOURCE.getURL().toExternalForm());

      engine.loadContent(chatContainerHtml);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void configureBrowser(WebView messagesWebView) {
    engine = messagesWebView.getEngine();

    configureZoomLevel();
    configureLoadListener();
  }

  private void configureZoomLevel() {
    Double zoom = preferencesService.getPreferences().getChat().getZoom();
    if (zoom != null) {
      getMessagesWebView().setZoom(zoom);
    }
  }

  private void configureLoadListener() {
    JavaFxUtil.addListener(engine.getLoadWorker().stateProperty(), (observable, oldValue, newValue) -> {
      if (newValue != Worker.State.SUCCEEDED) {
        return;
      }
      synchronized (waitingMessages) {
        // Set BEFORE draining: the page is loaded by now, so the document is callable, and the
        // drain itself renders messages. Leaving the flag false until afterwards would make
        // callJs skip anything the replay path triggers. Holding the lock still keeps a
        // concurrent onChatMessage from interleaving with the drain.
        isChatReady = true;
        waitingMessages.forEach(AbstractChatTabController.this::addMessage);
        waitingMessages.clear();
        onWebViewLoaded();
      }
    });
  }

  protected abstract WebView getMessagesWebView();

  /**
   * Calls a global function of the chat document, passing each argument as a JS string literal.
   * <p>
   * Deliberately <em>not</em> {@code ((JSObject) engine.executeScript("window")).call(fn, args)}:
   * {@code JSObject.call} routes through {@code JSObjectCallAsFunction} in jfxwebkit, whose
   * argument array is not protected from JavaScriptCore's garbage collector while the Java
   * arguments are being converted. If a GC runs part-way through that conversion an already
   * converted argument is collected, and the next argument read decodes a null {@code Structure*}
   * — an {@code EXCEPTION_ACCESS_VIOLATION} that kills the whole JVM (no Java exception, no
   * recovery). The likelihood scales with the number and size of the arguments, which is why the
   * four-string medal-avatar call was by far the biggest single crash source on the fleet.
   * {@link WebEngine#executeScript(String)} uses {@code JSEvaluateScript} instead and has no such
   * unrooted array.
   * <p>
   * This is the same workaround FAForever applied for
   * <a href="https://github.com/FAForever/downlords-faf-client/issues/1080">issue #1080</a>; back
   * then it was only applied to a single call site, and every site added since re-introduced the
   * crash. Do not reintroduce {@code JSObject.call} here.
   */
  protected void callJs(String function, String... args) {
    JavaFxUtil.assertApplicationThread();
    if (!isChatReady) {
      // The document defines these functions, so calling one before the page has loaded throws
      // ReferenceError and kills whatever was in flight — e.g. joining a channel styles every user
      // already in it (setChatChannel -> onPlayerConnected -> updateUserMessageColor) long before
      // the WebView finishes loading, which aborted the rest of the member loop.
      //
      // Dropping the call is correct rather than merely safe: every function in chat_container.js
      // only restyles ALREADY-RENDERED message DOM, and before load nothing is rendered, so there
      // is nothing to lose. Messages that arrive meanwhile are queued in waitingMessages and
      // replayed on load, and renderHtml bakes the user's colour, css classes and toxicity
      // visibility into the markup at render time — so the state these calls would have applied
      // is already correct on the other side of the load.
      logger.trace("Chat document not ready; skipping {}()", function);
      return;
    }
    StringBuilder script = new StringBuilder(function).append('(');
    for (int i = 0; i < args.length; i++) {
      if (i > 0) {
        script.append(',');
      }
      script.append(jsQuote(args[i]));
    }
    engine.executeScript(script.append(");").toString());
  }

  /**
   * Renders {@code value} as a single-quoted JS string literal. Every argument that reaches the
   * chat document goes through here: chat text, user names and tooltips are attacker controlled,
   * so a naive {@code replace("'", "\\'")} is escapable via a trailing backslash.
   */
  protected static String jsQuote(@Nullable String value) {
    String text = StringUtils.defaultString(value);
    StringBuilder result = new StringBuilder(text.length() + 16).append('\'');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\\' -> result.append("\\\\");
        case '\'' -> result.append("\\'");
        case '"' -> result.append("\\\"");
        case '\n' -> result.append("\\n");
        case '\r' -> result.append("\\r");
        case '\t' -> result.append("\\t");
        case '\b' -> result.append("\\b");
        case '\f' -> result.append("\\f");
        // Line/paragraph separators terminate a JS line but not a Java one.
        case 0x2028 -> result.append("\\u2028");
        case 0x2029 -> result.append("\\u2029");
        default -> {
          if (c < 0x20) {
            result.append(String.format("\\u%04x", (int) c));
          } else {
            result.append(c);
          }
        }
      }
    }
    return result.append('\'').toString();
  }

  protected void onWebViewLoaded() {
    // Default implementation does nothing, can be overridden by subclass.
  }

  public void onSendMessage() {
    TextInputControl messageTextField = messageTextField();

    String text = messageTextField.getText();
    if (StringUtils.isEmpty(text)) {
      return;
    }

    if (text.startsWith(ACTION_PREFIX)) {
      sendAction(messageTextField, text);
    } else if (text.startsWith(JOIN_PREFIX)) {
      chatService.joinChannel(text.replaceFirst(Pattern.quote(JOIN_PREFIX), ""));
      messageTextField.clear();
    } else if (text.startsWith(WHOIS_PREFIX)) {
      chatService.whois(text.replaceFirst(Pattern.quote(JOIN_PREFIX), ""));
      messageTextField.clear();
    } else if (text.startsWith(SECRET_REPLAY_VAULT_ACTIVATE)) {
      eventBus.post(new NavigateEvent(NavigationItem.REPLAY));
      messageTextField.clear();
    } else {
      sendMessage();
    }
  }

  private void sendMessage() {
    TextInputControl messageTextField = messageTextField();
    messageTextField.setDisable(true);

    final String text = messageTextField.getText();
    chatService.sendMessageInBackground(receiver, text).thenAccept(message -> JavaFxUtil.runLater(() -> {
      messageTextField.clear();
      messageTextField.setDisable(false);
      messageTextField.requestFocus();
    })).exceptionally(throwable -> {
      throwable = ConcurrentUtil.unwrapIfCompletionException(throwable);
      logger.warn("Message could not be sent: {}", text, throwable);
      notificationService.addImmediateErrorNotification(throwable, "chat.sendFailed");

      messageTextField.setDisable(false);
      messageTextField.requestFocus();
      return null;
    });
  }

  private void sendAction(final TextInputControl messageTextField, final String text) {
    messageTextField.setDisable(true);

    chatService.sendActionInBackground(receiver, text.replaceFirst(Pattern.quote(ACTION_PREFIX), ""))
        .thenAccept(message -> {
          messageTextField.clear();
          messageTextField.setDisable(false);
          messageTextField.requestFocus();
          onChatMessage(new ChatMessage(userService.getUsername(), Instant.now(), userService.getUsername(), message, 0.0, true));
        })
        .exceptionally(throwable -> {
          throwable = ConcurrentUtil.unwrapIfCompletionException(throwable);
          // TODO onDisplay error to user somehow
          logger.warn("Message could not be sent: {}", text, throwable);
          messageTextField.setDisable(false);
          return null;
        });
  }

  protected void onChatMessage(ChatMessage chatMessage) {
    synchronized (waitingMessages) {
      if (!isChatReady) {
        waitingMessages.add(chatMessage);
      } else {
        JavaFxUtil.runLater(() -> {
          addMessage(chatMessage);
          removeTopmostMessages();
          scrollToBottomIfDesired();
        });
      }
    }
  }

  private void scrollToBottomIfDesired() {
    JavaFxUtil.assertApplicationThread();
    engine.executeScript("scrollToBottomIfDesired()");
  }

  private void removeTopmostMessages() {
    JavaFxUtil.assertApplicationThread();
    int maxMessageItems = preferencesService.getPreferences().getChat().getMaxMessages();

    int numberOfMessages = (int) engine.executeScript("document.getElementsByClassName('" + MESSAGE_ITEM_CLASS + "').length");
    while (numberOfMessages > maxMessageItems) {
      engine.executeScript("document.getElementsByClassName('" + MESSAGE_ITEM_CLASS + "')[0].remove()");
      numberOfMessages--;
    }
  }

  private void removeMessageId(String id) {
    String script = String.format("var x=document.getElementById('%s'); if (x!=null) x.remove();", id);
    engine.executeScript(script);
  }

  protected void updateUserMessageColor(ChatChannelUser chatUser) {
    String color;
    if (chatUser.getColor().isPresent()) {
      color = JavaFxUtil.toRgbCode(chatUser.getColor().get());
    } else {
      color = "";
    }
    JavaFxUtil.runLater(() -> callJs("updateUserMessageColor", chatUser.getUsername(), color));
  }

  private void setUserMessageColor(ChatChannelUser chatUser, String jsColorString) {
    JavaFxUtil.runLater(() -> callJs("updateUserMessageColor", chatUser.getUsername(), jsColorString));
  }

  /**
   * Either inserts a new chat entry or, if the same user as before sent another message, appends it do the previous
   * entry.
   */
  private void addMessage(ChatMessage chatMessage) {
    JavaFxUtil.assertApplicationThread();
    noCatch(() -> {
      if (!hasFocus() && !playerService.isCurrentPlayer(chatMessage.getSubject())) {
        setUnread(true);
        incrementUnreadMessagesCount(1);
      }

      boolean appended;
      if (requiresNewChatSection(chatMessage)) {
        appended = appendChatMessageSection(chatMessage);
      } else {
        appended = appendMessage(chatMessage);
      }
      if (appended) {
        lastMessage = chatMessage;
      }
    });
  }

  private boolean requiresNewChatSection(ChatMessage chatMessage) {
    return lastMessage == null
        || !lastMessage.getUsername().equals(chatMessage.getUsername())
        || lastMessage.getTime().isBefore(chatMessage.getTime().minus(1, MINUTES))
        || lastMessage.isAction();
  }

  int nextTextLineId = 0;
  private boolean appendMessage(ChatMessage chatMessage) throws IOException {
    URL themeFileUrl;
    if (preferencesService.getPreferences().getChat().getChatFormat() == ChatFormat.COMPACT) {
      themeFileUrl = uiService.getThemeFileUrl(CHAT_TEXT_COMPACT);
    } else {
      themeFileUrl = uiService.getThemeFileUrl(CHAT_TEXT_EXTENDED);
    }

    String html = renderHtml(++nextTextLineId, chatMessage, themeFileUrl, null);
    if (html != null) {
      insertIntoContainer(html, "chat-section-" + lastEntryId);
      return true;
    }
    else {
      return false;
    }
  }

  private boolean appendChatMessageSection(ChatMessage chatMessage) throws IOException {
    URL themeFileURL;
    if (preferencesService.getPreferences().getChat().getChatFormat() == ChatFormat.COMPACT) {
      themeFileURL = uiService.getThemeFileUrl(CHAT_SECTION_COMPACT);
    } else {
      themeFileURL = uiService.getThemeFileUrl(CHAT_SECTION_EXTENDED);
    }

    String html = renderHtml(++nextTextLineId, chatMessage, themeFileURL, ++lastEntryId);
    if (html != null) {
      insertIntoContainer(html, MESSAGE_CONTAINER_ID);
      appendMessage(chatMessage);
      // renderHtml only shows a medal-as-avatar if it was already cached (peek). On a cold miss the
      // regular avatar was rendered; resolve the medal in the background and swap it in, so message
      // avatars match how the chat user list resolves them.
      scheduleMedalAvatarRefreshIfNeeded(chatMessage.getUsername());
      return true;
    }
    else {
      return false;
    }
  }

  /** Async-resolve the player's chosen medal-as-avatar and, if present, swap it into the sections
   * just rendered with the fallback avatar. No-op when the medal was already rendered synchronously
   * (warm cache) or the player has no featured medal. */
  private void scheduleMedalAvatarRefreshIfNeeded(String username) {
    Optional<Player> playerOptional = playerService.getPlayerForUsername(username);
    if (playerOptional.isEmpty()) {
      return;
    }
    Player player = playerOptional.get();
    // Gate on "is it resolved", not "did peek return a medal": peek answers empty both for a
    // player with no medal and for a cache miss, so the latter test spawned a future (and a
    // continuation, per open tab) for every message from every medal-less player — the common
    // case — only to discover there was nothing to swap in.
    if (player.getId() <= 0 || ladderPointsService.isFeaturedMedalCached(player.getId())) {
      return; // no id, or renderHtml's peek already settled this player, medal or not
    }
    applyMedalAvatar(player, false);
  }

  /** Inline style that letterboxes the square medal art into the rectangular avatar box (see the
   * matching note in {@link #renderHtml}). */
  private static String medalAvatarStyle(boolean compact) {
    return compact
        ? "width:60px;height:30px;object-fit:contain;"
        : "width:120px;height:60px;object-fit:contain;";
  }

  /** Resolve the player's featured medal and, on the FX thread, swap the avatar image on every
   * already-rendered chat section for that player. When the medal is absent: revert to the regular
   * avatar if {@code revertIfAbsent}, otherwise leave the rendered (fallback) avatar untouched. */
  private void applyMedalAvatar(Player player, boolean revertIfAbsent) {
    String username = player.getUsername();
    boolean compact = preferencesService.getPreferences().getChat().getChatFormat() == ChatFormat.COMPACT;
    ladderPointsService.getFeaturedMedalCached(player.getId()).thenAccept(medalOptional -> {
      String avatarUrl;
      String avatarTitle;
      String avatarStyle;
      if (medalOptional.isPresent()) {
        com.faforever.client.ladder.FeaturedMedalDisplay medal = medalOptional.get();
        avatarUrl = uiService.getThemeFileUrlOrDefault(
            com.faforever.client.ladder.LadderUiUtil.medalIconPath(medal.getCode()),
            UiService.DEFAULT_MEDAL_IMAGE).toString();
        avatarTitle = com.faforever.client.ladder.LadderUiUtil.medalAvatarTooltip(
            i18n, medal.getCode(), medal.getCount());
        avatarStyle = medalAvatarStyle(compact);
      } else if (revertIfAbsent) {
        avatarUrl = StringUtils.defaultString(player.getAvatarUrl());
        avatarTitle = StringUtils.defaultString(player.getAvatarTooltip());
        avatarStyle = "";
      } else {
        return;
      }
      JavaFxUtil.runLater(() -> {
        if (isChatReady) {
          callJs("updateUserAvatarMedal", username, avatarUrl, avatarTitle, avatarStyle);
        }
      });
    });
  }

  /** A player changed (set or cleared) their chosen medal-as-avatar: swap the avatar image on every
   * already-rendered chat section for that player, mirroring how the chat user list refreshes. On a
   * clear we revert to the player's regular avatar. */
  @com.google.common.eventbus.Subscribe
  public void onFeaturedMedalChanged(com.faforever.client.ladder.FeaturedMedalChangedEvent event) {
    if (event.getPlayerId() <= 0) {
      return;
    }
    Player player = playerService.getPlayersById().get(event.getPlayerId());
    if (player != null) {
      applyMedalAvatar(player, true);
    }
  }

  private String renderHtml(int textLineId, ChatMessage chatMessage, URL themeFileUrl, @Nullable Integer sectionId) throws IOException {
    String html;
    try (Reader reader = new InputStreamReader(themeFileUrl.openStream())) {
      html = CharStreams.toString(reader);
    }

    String login = chatMessage.getUsername();
    String avatarUrl = "";
    String avatarTitle = "";
    String avatarStyle = "";
    String clanTag = "";
    String decoratedClanTag = "";
    String countryFlagUrl = "";

    Optional<Player> playerOptional = playerService.getPlayerForUsername(chatMessage.getUsername());
    if (playerOptional.isPresent()) {
      Player player = playerOptional.get();
      avatarUrl = player.getAvatarUrl();
      avatarTitle = StringUtils.defaultString(player.getAvatarTooltip());
      // A chosen medal-as-avatar takes the avatar slot here too (never the flag). Cached read; on a
      // cold miss the regular avatar shows and the next message renders the medal.
      Optional<com.faforever.client.ladder.FeaturedMedalDisplay> medal =
          player.getId() > 0 ? ladderPointsService.peekFeaturedMedal(player.getId()) : Optional.empty();
      if (medal.isPresent()) {
        avatarUrl = uiService.getThemeFileUrlOrDefault(
            com.faforever.client.ladder.LadderUiUtil.medalIconPath(medal.get().getCode()),
            UiService.DEFAULT_MEDAL_IMAGE).toString();
        avatarTitle = com.faforever.client.ladder.LadderUiUtil.medalAvatarTooltip(
            i18n, medal.get().getCode(), medal.get().getCount());
        // Medal art is square. Reserve the same avatar box a regular avatar uses (60x30 compact,
        // 120x60 extended) but letterbox the square medal inside it via object-fit:contain, so it
        // keeps a 1:1 ratio without changing the row layout.
        boolean compact = preferencesService.getPreferences().getChat().getChatFormat() == ChatFormat.COMPACT;
        avatarStyle = medalAvatarStyle(compact);
      }
      countryFlagUrl = countryFlagService.getCountryFlagUrl(player.getCountry())
          .map(URL::toString)
          .orElse("");

      if (StringUtils.isNotEmpty(player.getClan())) {
        clanTag = player.getClan();
        decoratedClanTag = i18n.get("chat.clanTagFormat", clanTag);
      }
    }

    String timeString = timeService.asShortTime(chatMessage.getTime());
    html = html.replace("{time}", timeString)
        .replace("{avatar}", StringUtils.defaultString(avatarUrl))
        .replace("{avatar-title}", avatarTitle)
        .replace("{avatar-style}", avatarStyle)
        .replace("{username}", login)
        .replace("{clan-tag}", clanTag)
        .replace("{decorated-clan-tag}", decoratedClanTag)
        .replace("{country-flag}", StringUtils.defaultString(countryFlagUrl))
        .replace("{section-id}", String.valueOf(sectionId));

    Collection<String> cssClasses = new ArrayList<>();
    cssClasses.add(String.format("user-%s", chatMessage.getUsername()));
    if (chatMessage.isAction()) {
      cssClasses.add(ACTION_CSS_CLASS);
    } else {
      cssClasses.add(MESSAGE_CSS_CLASS);
    }

    html = html.replace("{css-classes}", Joiner.on(' ').join(cssClasses));

    Optional.ofNullable(getMessageCssClass(login)).ifPresent(cssClasses::add);

    String text = htmlEscaper().escape(chatMessage.getMessage()).replace("\\", "\\\\");
    text = convertUrlsToHyperlinks(text);
    text = replaceChannelNamesWithHyperlinks(text);

    Matcher matcher = mentionPattern.matcher(text);
    if (matcher.find()) {
      text = matcher.replaceAll("<span class='self'>" + matcher.group(1) + "</span>");
      onMention(chatMessage);
    }

    SocialStatus senderSocialStatus = playerOptional.map(Player::getSocialStatus).orElse(SocialStatus.OTHER);
    ToxicityAction toxicityAction = chatMessage.getToxicityScore() > 0.0
        ? preferencesService.getPreferences().getChat().getToxicitySettings3()
        .stream()
        .filter(ts -> ts.getSocialStatus() == senderSocialStatus)
        .filter(ts -> chatMessage.getToxicityScore() > ts.getToxicityThreshold())
        .map(ToxicitySetting::getAction)
        .findAny()
        .orElse(ToxicityAction.ALLOW)
        : ToxicityAction.ALLOW;

    if (toxicityAction == ToxicityAction.HIDE) {
      return null;
    }

    return html
        .replace("{css-classes}", Joiner.on(' ').join(cssClasses))
        .replace("{inline-style}", getInlineStyle(login))
        .replace("{message-id}", String.format("%d", textLineId))
        .replace("{text_redacted}", i18n.get("chat.toxicity.filter.redactedFormat", chatMessage.getToxicityScore()))
        .replace("{text-content-display}", toxicityAction == ToxicityAction.CENSOR ? "none" : "block")
        .replace("{toxicity-display}", preferencesService.getPreferences().getChat().getShowToxicity() ? "inline" : "none")
        .replace("{text-redacted-display}", toxicityAction == ToxicityAction.CENSOR ? "block" : "none")
        .replace("{toxicity}", String.format("%.2f", chatMessage.getToxicityScore()))
        // Always replace text last in case the message contains one of the placeholders.
        .replace("{text}", text);
  }

  @VisibleForTesting
  protected String replaceChannelNamesWithHyperlinks(String text) {
    Matcher channelMatcher = CHANNEL_USER_PATTERN.matcher(text);
    while (channelMatcher.find()) {
      String channelName = channelMatcher.group(CHANNEL_NAME_GROUP_NAME);
      text = text.replace(channelName, "<a href=\"javascript:void(0);\" onClick=\"java.openChannel('" + channelName + "')\">" + channelName + "</a>");
    }
    return text;
  }

  protected void onMention(ChatMessage chatMessage) {
    // Default implementation does nothing
  }

  protected void showNotificationIfNecessary(ChatMessage chatMessage) {
    Stage stage = StageHolder.getStage();
    if (stage.isFocused() && stage.isShowing()) {
      return;
    }

    Optional<Player> playerOptional = playerService.getPlayerForUsername(chatMessage.getUsername());
    String identIconSource = playerOptional.map(player -> String.valueOf(player.getId())).orElseGet(chatMessage::getUsername);

    if (preferencesService.getPreferences().getNotification().isPrivateMessageToastEnabled()) {
      notificationService.addNotification(new TransientNotification(
          chatMessage.getUsername(),
          chatMessage.getMessage(),
          IdenticonUtil.createIdenticon(identIconSource),
          event -> {
            eventBus.post(new NavigateEvent(NavigationItem.PLAY));
            stage.toFront();
            getRoot().getTabPane().getSelectionModel().select(getRoot());
          })
      );
    }
  }

  protected String getMessageCssClass(String login) {
    Optional<Player> playerOptional = playerService.getPlayerForUsername(login);
    if (!playerOptional.isPresent()) {
      return CSS_CLASS_CHAT_ONLY;
    }

    return playerOptional.get().getSocialStatus().getCssClass();
  }

  protected String getInlineStyle(String username) {
    // To be overridden by subclasses
    return "";
  }

  @VisibleForTesting
  String createInlineStyleFromColor(Color messageColor) {
    return String.format("color: %s;", JavaFxUtil.toRgbCode(messageColor));
  }

  protected String convertUrlsToHyperlinks(String text) {
    JavaFxUtil.assertApplicationThread();
    // jsQuote, not a bare quote-escape: chat text is attacker controlled and a trailing backslash
    // escapes out of a replace("'", "\\'") into arbitrary script.
    return (String) engine.executeScript("link(" + jsQuote(text) + ")");
  }

  private void insertIntoContainer(String html, String containerId) {
    // See callJs: never JSObject.call — the message HTML is the largest argument the chat document
    // ever receives, so this was the second biggest JVM-crash source after the medal avatars.
    JavaFxUtil.assertApplicationThread();
    engine.executeScript("document.getElementById(" + jsQuote(containerId) + ")"
        + ".insertAdjacentHTML('beforeend'," + jsQuote(html) + ");");
    getMessagesWebView().requestLayout();
  }

  public final void display(NavigateEvent navigateEvent) {
    onDisplay();
  }

  /**
   * Subclasses may override in order to perform actions when the view is being displayed.
   */
  protected void onDisplay() {
    JavaFxUtil.runLater(() -> {
      setUnread(false);
      messageTextField().requestFocus();
    });
  }

  /**
   * Subclasses may override in order to perform actions when the view is no longer being displayed.
   */
  protected void onHide() {

  }

  void onPlayerDisconnected(ChatChannelUser user) {
    setUserMessageColor(user, "#666");
  }

  void onPlayerConnected(ChatChannelUser user) {
    updateUserMessageColor(user);
  }

  void onPlayerCount(int count) {
  }

  // Detach some Node (eg channel user list) from the concrete Controller's layout so it can be docked with another controllers layout.
  // The concrete Controller still retains ownership and responsibility for Node's contents. It just gives up responsibility for layout.
  public Node detachSidePanelNode() {
    return null;
  }

  public void setSidePaneEnabled(boolean enabled) {
  }

  ChangeListener<Boolean> onSelectedListener;
  public void setOnSelectedListener(ChangeListener<Boolean> listener) {
    onSelectedListener = listener;
    getRoot().selectedProperty().addListener(new WeakChangeListener<>(listener));
  }
}
