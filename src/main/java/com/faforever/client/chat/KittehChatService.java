package com.faforever.client.chat;

import com.faforever.client.FafClientApplication;
import com.faforever.client.chat.event.ChatMessageEvent;
import com.faforever.client.chat.event.ChatUserCategoryChangeEvent;
import com.faforever.client.chat.messagetags.Toxicity;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.config.ClientProperties.Irc;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.net.ConnectionState;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerOnlineEvent;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.SocialStatus;
import com.faforever.client.player.UserOfflineEvent;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.ChatBanNoticeMessage;
import com.faforever.client.remote.domain.NewTadaReplayMessage;
import com.faforever.client.remote.domain.SocialMessage;
import com.faforever.client.ui.tray.event.UpdateApplicationBadgeEvent;
import com.faforever.client.user.UserService;
import com.faforever.client.user.event.LoggedOutEvent;
import com.faforever.client.user.event.LoginSuccessEvent;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.hash.Hashing;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.engio.mbassy.listener.Handler;
import org.jetbrains.annotations.NotNull;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.Client.Builder.Server.SecurityType;
import org.kitteh.irc.client.library.defaults.DefaultClient;
import org.kitteh.irc.client.library.element.Channel;
import org.kitteh.irc.client.library.element.MessageTag;
import org.kitteh.irc.client.library.element.User;
import org.kitteh.irc.client.library.element.mode.ChannelUserMode;
import org.kitteh.irc.client.library.element.mode.Mode;
import org.kitteh.irc.client.library.element.mode.ModeStatus.Action;
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent;
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent;
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent;
import org.kitteh.irc.client.library.event.channel.ChannelModeEvent;
import org.kitteh.irc.client.library.event.channel.ChannelNamesUpdatedEvent;
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent;
import org.kitteh.irc.client.library.event.channel.ChannelTopicEvent;
import org.kitteh.irc.client.library.event.client.ClientNegotiationCompleteEvent;
import org.kitteh.irc.client.library.event.connection.ClientConnectionEndedEvent;
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent;
import org.kitteh.irc.client.library.event.user.PrivateNoticeEvent;
import org.kitteh.irc.client.library.event.user.UserNickChangeEvent;
import org.kitteh.irc.client.library.event.user.UserQuitEvent;
import org.kitteh.irc.client.library.feature.auth.NickServ;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.faforever.client.chat.ChatColorMode.DEFAULT;
import static com.faforever.client.chat.ChatUserCategory.MODERATOR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.US;
import static javafx.collections.FXCollections.observableHashMap;
import static javafx.collections.FXCollections.observableMap;

@Lazy
@Service
@Slf4j
@Profile("!" + FafClientApplication.PROFILE_OFFLINE)
@RequiredArgsConstructor
public class KittehChatService implements ChatService, InitializingBean, DisposableBean {

  private static final Set<Character> MODERATOR_PREFIXES = Set.of('~', '&', '@', '%');
  /** How long to give NickServ RECOVER before falling back to a reconnect. */
  private static final int NICK_RECLAIM_CHECK_SECONDS = 6;
  /** How long to let a retried NICK be accepted before giving up and reconnecting. */
  private static final int NICK_RETRY_SECONDS = 3;
  /** Let our own QUIT land before re-registering, so we do not collide with ourselves. */
  private static final int RELOG_SETTLE_SECONDS = 2;
  private final ChatUserService chatUserService;
  private final PreferencesService preferencesService;
  private final UserService userService;
  private final FafService fafService;
  private final EventBus eventBus;
  private final ClientProperties clientProperties;
  private final PlayerService playerService;
  private final NotificationService notificationService;
  private final I18n i18n;
  /**
   * Maps channels by name.
   */
  private final ObservableMap<String, ChatChannel> channels = observableHashMap();
  /** Key is the result of {@link #mapKey(String, String)}. */
  private final ObservableMap<String, ChatChannelUser> chatChannelUsersByChannelAndName = observableMap(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
  private final SimpleIntegerProperty unreadMessagesCount = new SimpleIntegerProperty();
  @VisibleForTesting
  ObjectProperty<ConnectionState> connectionState = new SimpleObjectProperty<>(ConnectionState.DISCONNECTED);
  @VisibleForTesting
  DefaultClient client;
  private NickServ nickServ;
  /** One reclaim-reconnect per connection, so a nick we can never win cannot loop us. */
  private final AtomicBoolean nickReconnectUsed = new AtomicBoolean();
  private final AtomicBoolean nickCheckScheduled = new AtomicBoolean();
  /**
   * Where we were when the IRC connection dropped. Channels are otherwise joined only from
   * {@link #onSocialMessage}, which the lobby sends at login — so an IRC-only reconnect (the
   * lobby socket untouched) brought the client back online in no channels at all.
   */
  private final Set<String> channelsToRejoin = ConcurrentHashMap.newKeySet();
  /**
   * Indicates whether the "auto channels" already have been joined. This is needed because we don't want to auto join
   * channels after a reconnect that the user left before the reconnect.
   */
  private boolean autoChannelsJoined;
  private boolean newbieChannelJoined;
  private ObjectProperty<ChatBanNoticeMessage> chatBanNoticeMessage = new SimpleObjectProperty<ChatBanNoticeMessage>(null);

  @Override
  public void afterPropertiesSet() {
    eventBus.register(this);
    fafService.addOnMessageListener(SocialMessage.class, this::onSocialMessage);
    fafService.addOnMessageListener(NewTadaReplayMessage.class, this::onNewTadaReplayMessage);
    fafService.addOnMessageListener(ChatBanNoticeMessage.class, this::onChatBanNotification);

    connectionState.addListener((observable, oldValue, newValue) -> {
      switch (newValue) {
        case DISCONNECTED, CONNECTING -> onDisconnected();
      }
    });

    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();
    JavaFxUtil.addListener(chatPrefs.userToColorProperty(),
        (MapChangeListener<? super String, ? super Color>) change -> preferencesService.storeInBackground()
    );
    JavaFxUtil.addListener(chatPrefs.groupToColorProperty(),
        (MapChangeListener<? super ChatUserCategory, ? super Color>) change -> {
          preferencesService.storeInBackground();
          updateUserColors(chatPrefs.getChatColorMode());
        }
    );
    JavaFxUtil.addListener(chatPrefs.chatColorModeProperty(), (observable, oldValue, newValue) -> updateUserColors(newValue));
  }

  private void updateUserColors(ChatColorMode chatColorMode) {
    if (chatColorMode == null) {
      chatColorMode = DEFAULT;
    }
    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();
    synchronized (chatChannelUsersByChannelAndName) {
      if (chatColorMode == ChatColorMode.RANDOM) {
        chatChannelUsersByChannelAndName.values()
            .forEach(chatUser -> chatUser.setColor(ColorGeneratorUtil.generateRandomColor(chatUser.getUsername().hashCode())));
      } else {
        chatChannelUsersByChannelAndName.values()
            .forEach(chatUser -> {
              if (chatPrefs.getUserToColor().containsKey(userToColorKey(chatUser.getUsername()))) {
                chatUser.setColor(chatPrefs.getUserToColor().get(userToColorKey(chatUser.getUsername())));
              } else {
                if (chatUser.isModerator() && chatPrefs.getGroupToColor().containsKey(MODERATOR)) {
                  chatUser.setColor(chatPrefs.getGroupToColor().get(MODERATOR));
                } else {
                  chatUser.setColor(chatUser.getSocialStatus()
                      .map(status -> chatPrefs.getGroupToColor().getOrDefault(groupToColorKey(status), null))
                      .orElse(null));
                }
              }
            });
      }
    }
  }

  @NotNull
  private String userToColorKey(String username) {
    return username.toLowerCase(US);
  }

  @NotNull
  private ChatUserCategory groupToColorKey(SocialStatus socialStatus) {
    return switch (socialStatus) {
      case FRIEND -> ChatUserCategory.FRIEND;
      case FOE -> ChatUserCategory.FOE;
      default -> ChatUserCategory.OTHER;
    };
  }

  @Override
  public ChatChannelUser getOrCreateChatUser(String username, String channelName) {
    Channel channel = client.getChannel(channelName).orElseThrow(() -> new IllegalArgumentException("Channel '" + channelName + "' is unknown"));
    User user = channel.getUser(username).orElseThrow(() -> new IllegalArgumentException("Chat user '" + username + "' is unknown for channel '" + channelName + "'"));
    return getOrCreateChatUser(user, channel);
  }

  private ChatChannelUser getOrCreateChatUser(User user, Channel channel) {
    String username = user.getNick();

    boolean isModerator = channel.getUserModes(user).stream().flatMap(Collection::stream)
        .map(ChannelUserMode::getNickPrefix)
        .anyMatch(MODERATOR_PREFIXES::contains);

    return getOrCreateChatUser(username, channel.getName(), isModerator);
  }

  public Set<String> getUserChannels(String username) {
    Set<String> channelNames = new HashSet<>();
    channels.forEach((channelName, channel) -> {
      if (channel.getUser(username) != null) {
        channelNames.add(channelName);
      }
    });
    return channelNames;
  }

  @Subscribe
  public void onLoginSuccessEvent(LoginSuccessEvent event) {
    log.debug("[onLoginSuccessEvent]");
    connect();
  }

  @Subscribe
  public void onLoggedOutEvent(LoggedOutEvent event) {
    log.debug("[onLoggedOutEvent]");
    disconnect();
    eventBus.post(UpdateApplicationBadgeEvent.ofNewValue(0));
  }

  @Subscribe
  public void onPlayerOnline(PlayerOnlineEvent event) {
    Player player = event.getPlayer();

    synchronized (channels) {
      channels.values().parallelStream()
          .map(channel -> chatChannelUsersByChannelAndName.get(mapKey(player.getUsername(), channel.getName())))
          .filter(Objects::nonNull)
          .forEach(chatChannelUser -> {
            chatUserService.associatePlayerToChatUser(chatChannelUser, player);
            eventBus.post(new ChatUserCategoryChangeEvent(chatChannelUser));
          });
    }
  }

  @Handler
  public void onConnect(ClientNegotiationCompleteEvent event) {
    log.debug("[onConnect]");
    connectionState.set(ConnectionState.CONNECTED);
    // One line that says whether the nick machinery is healthy: with TafNickListener in place
    // these agree, and a mismatch means the swap did not take effect.
    log.info("[onConnect] registered as '{}' (wanted '{}')", client.getNick(), userService.getUsername());
    rejoinChannels();
    identifyIrc();
    nickReconnectUsed.set(false);
    recoverOwnNick();
  }

  /**
   * Swap out the two stock listeners that cannot cope with this ircd. See
   * {@link TafNickListener} and {@link TafNickRejectedListener} for what each gets wrong;
   * between them they are why {@code client.getNick()} lies and why a reclaimed nick is given
   * straight back.
   */
  private void replaceBrokenNickListeners() {
    int removed = 0;
    for (Object listener : new ArrayList<>(client.getEventManager().getRegisteredEventListeners())) {
      String name = listener.getClass().getSimpleName();
      if ("DefaultNickListener".equals(name) || "DefaultNickRejectedListener".equals(name)) {
        client.getEventManager().unregisterEventListener(listener);
        removed++;
      }
    }
    client.getEventManager().registerEventListener(new TafNickListener(client));
    client.getEventManager().registerEventListener(
        new TafNickRejectedListener(client, userService::getUsername,
            () -> connectionState.get() == ConnectionState.CONNECTED));
    log.info("[replaceBrokenNickListeners] replaced {} stock nick listener(s)", removed);
  }


  private void registerIrc() {
    String nick = client.getNick();
    if (!nick.endsWith("`")) {
      String password = getPassword();
      String email = String.format("%s@users.taforever.com", nick);

      log.info("[registerIrc] registering ...");
      client.sendMessage("NickServ", String.format("REGISTER %s %s", password, email));
    }
  }

  private void identifyIrc() {
    log.info("[identifyIrc] identifying ...");
    nickServ.startAuthentication();
  }

  @Handler
  private void onJoinEvent(ChannelJoinEvent event) {
    User user = event.getActor();
    log.debug("[onJoinEvent] User joined channel: {}", user);
    addUserToChannel(event.getChannel().getName(), getOrCreateChatUser(user, event.getChannel()));
  }

  @Handler
  public void onChatUserList(ChannelNamesUpdatedEvent event) {
    log.debug("[onChatUserList]");
    Channel channel = event.getChannel();
    List<ChatChannelUser> users = channel.getUsers().stream().map(user -> getOrCreateChatUser(user, channel)).collect(Collectors.toList());
    ChatChannel chatChannel = getOrCreateChannel(channel.getName());
    chatChannel.addUsers(users);

    // NAMES is the whole truth for this channel, so drop anyone it doesn't list. Renames
    // otherwise linger forever: Kitteh fires no event for our own nick change outside a
    // tracked channel (DefaultNickListener returns early for isSelf), so "Nick`" would
    // stay in the list after services moved us back to "Nick".
    Set<String> present = users.stream().map(ChatChannelUser::getUsername).collect(Collectors.toSet());
    chatChannel.getUsers().stream()
        .map(ChatChannelUser::getUsername)
        .filter(name -> !present.contains(name))
        .collect(Collectors.toList())
        .forEach(name -> {
          log.debug("[onChatUserList] dropping stale user {} from {}", name, channel.getName());
          chatChannel.removeUser(name);
          synchronized (chatChannelUsersByChannelAndName) {
            chatChannelUsersByChannelAndName.remove(mapKey(name, channel.getName()));
          }
        });
  }

  @Handler
  private void onPartEvent(ChannelPartEvent event) {
    log.debug("[onPartEvent]");
    User user = event.getActor();
    boolean weLeft = user.getNick().equalsIgnoreCase(userService.getUsername());
    onChatUserLeftChannel(event.getChannel().getName(), user.getNick(), weLeft);
  }

  /**
   * Re-key a renamed user in every channel they are in.
   *
   * A rename produces no PART and no QUIT, so nothing else would ever drop the old name. This
   * only became reachable once {@link TafNickListener} replaced Kitteh's own nick listener —
   * upstream threw on this ircd's bare-prefix NICK and never fired the event at all.
   */
  @Handler
  private void onUserNickChange(UserNickChangeEvent event) {
    String oldNick = event.getOldUser().getNick();
    String newNick = event.getNewUser().getNick();
    log.debug("[onUserNickChange] {} -> {}", oldNick, newNick);
    if (oldNick.equals(newNick)) {
      return;
    }

    for (ChatChannel channel : new ArrayList<>(channels.values())) {
      String channelName = channel.getName();
      ChatChannelUser oldUser = channel.removeUser(oldNick);
      if (oldUser == null) {
        continue;
      }
      synchronized (chatChannelUsersByChannelAndName) {
        chatChannelUsersByChannelAndName.remove(mapKey(oldNick, channelName));
      }
      // Recreated, not renamed, so the Player association is resolved for the new nick.
      addUserToChannel(channelName, getOrCreateChatUser(newNick, channelName, oldUser.isModerator()));
    }
  }

  @Handler
  private void onChatUserQuit(UserQuitEvent event) {
    log.debug("[onChatUserQuit]");
    String nick = event.getUser().getNick();

    // A stale session of ours holds our own name, and services rename us onto that name
    // as they kill it — so this quit can carry our current nick without being us.
    // Treating it as our own departure dropped us from the user list and, via
    // ChatController's channels listener, parted the channel.
    // Never act on a quit carrying a name that is, or is about to be, ours. Comparing only
    // against the nick we hold *right now* loses a race: the ghost's quit and our rename onto
    // its name arrive on different threads about a millisecond apart (22:03:45.774 vs .775),
    // and deleting that entry takes us out of the channel.
    String desired = userService.getUsername();
    if (desired == null || !desired.equalsIgnoreCase(nick)) {
      new ArrayList<>(channels.values())
          .forEach(channel -> onChatUserLeftChannel(channel.getName(), nick, false));
    }
  }

  @Handler
  private void onTopicChange(ChannelTopicEvent event) {
    log.debug("[onTopicChange]");
    Channel channel = event.getChannel();
    getOrCreateChannel(channel.getName()).setTopic(event.getNewTopic().getValue().orElse(""));
  }

  @Handler
  private void onChannelMessage(ChannelMessageEvent event) {
    log.debug("[onChannelMessage]");

    User user = event.getActor();
    String source = event.getChannel().getName();

    double toxicity = event.getSource().getTag("taforever.com/toxicity")
        .map(MessageTag::getValue)
        .map(value -> value.orElse("0"))
        .map(Double::parseDouble)
        .orElse(0.0);

    eventBus.post(new ChatMessageEvent(new ChatMessage(source, Instant.now(), user.getNick(), event.getMessage(), toxicity, false)));
  }

  @Handler
  private void onChannelCTCP(ChannelCtcpEvent event) {
    log.debug("[onChannelCTCP]");
    User user = event.getActor();

    Channel channel = event.getChannel();
    String source = channel.getName();

    eventBus.post(new ChatMessageEvent(new ChatMessage(source, Instant.now(), user.getNick(), event.getMessage().replace("ACTION", user.getNick()), 0.0, true)));
  }

  @Handler
  private void onChannelModeChanged(ChannelModeEvent event) {
    log.debug("[onChannelModeChanged]");
    ChatChannel channel = getOrCreateChannel(event.getChannel().getName());
    event.getStatusList().getAll().forEach(channelModeStatus ->
        channelModeStatus.getParameter().ifPresent(username -> {
          Mode changedMode = channelModeStatus.getMode();
          Action modeAction = channelModeStatus.getAction();
          if (changedMode instanceof ChannelUserMode) {
            if (MODERATOR_PREFIXES.contains(((ChannelUserMode) changedMode).getNickPrefix())) {
              ChatChannelUser chatChannelUser = getOrCreateChatUser(username, channel.getName(), false);
              if (modeAction == Action.ADD) {
                chatChannelUser.setModerator(true);
              } else if (modeAction == Action.REMOVE) {
                chatChannelUser.setModerator(false);
              }
              eventBus.post(new ChatUserCategoryChangeEvent(chatChannelUser));
            }
          }
        }));
  }

  @Handler
  private void onPrivateMessage(PrivateMessageEvent event) {
    User user = event.getActor();
    log.debug("[onPrivateMessage] Received private message: {}", event);

    if ("NickServ".equals(user.getNick())) {
      log.info("[onPrivateMessage] Suppressed NickServ private message: {}", event.getMessage());
      if (event.getMessage().contains("choose a different nick")) {
        this.identifyIrc();
      } else if (event.getMessage().contains("isn't registered")) {
        this.registerIrc();
      }
      return;
    }

    ChatChannelUser sender = getOrCreateChatUser(user.getNick(), user.getNick(), false);
    if (sender.getPlayer().map(Player::getSocialStatus).filter(status -> status == SocialStatus.FOE).isPresent()
        && preferencesService.getPreferences().getChat().getHideFoeMessages()) {
      log.debug("[onPrivateMessage] Suppressing chat message from foe '{}'", user.getNick());
      return;
    }

    double toxicity = event.getSource().getTag("taforever.com/toxicity")
        .map(MessageTag::getValue)
        .map(value -> value.orElse("0"))
        .map(Double::parseDouble)
        .orElse(0.0);

    eventBus.post(new ChatMessageEvent(new ChatMessage(user.getNick(), Instant.now(), user.getNick(), event.getMessage(), toxicity)));
  }

  @Handler
  private void onNotice(PrivateNoticeEvent event) {
    String message = event.getMessage();
    log.info("[onNotice] {}", message);

    if (message.contains("choose a different nick")) {
      this.identifyIrc();
    } else if (message.contains("isn't registered")) {
      this.registerIrc();
    }
  }

  /**
   * Restore the channels we were in before the connection dropped.
   *
   * Deliberately the channels we actually occupied, not the configured auto-join list: a user
   * who had left a channel must stay out of it. Marks the auto-join as done for the same
   * reason — a later SocialMessage must not drag those channels back in.
   */
  /**
   * Take our own nick back when a stale session of ours is holding it.
   *
   * RECOVER kills that session; if it died on its own first the nick is simply free and
   * setNick takes it. Either way we may still be left on "Nick`", because Kitteh answers the
   * 433 by claiming the suffix and then renames us back off the nick whenever we reclaim it —
   * from a thread we cannot get ahead of. So {@link #scheduleNickReclaimCheck()} reconnects
   * instead: registering afresh once the nick is free is the only sequence that holds, and it
   * needs no rename, which is the part this ircd and Kitteh cannot agree on.
   */
  void recoverOwnNick() {
    String desiredNick = userService.getUsername();
    String actualNick = client.getNick();
    if (desiredNick == null || desiredNick.equalsIgnoreCase(actualNick)) {
      return;
    }

    log.info("[recoverOwnNick] connected as '{}' instead of '{}', recovering", actualNick, desiredNick);
    client.setNick(desiredNick);
    // The password is stripped from the log by onMessage.
    client.sendMessage("NickServ", String.format("RECOVER %s %s", desiredNick, getPassword()));
    scheduleNickReclaimCheck();
  }

  /**
   * After RECOVER, take the nick back — by rename if we can, by reconnecting only if we must.
   *
   * RECOVER renames us when it actually kills a ghost. When the ghost has already died of its
   * own accord it reports "No one is using your nick", nothing renames us, and we are left on
   * the suffix holding a request that was refused while the ghost was still up. A plain retry
   * claims it then, with none of the churn of a reconnect; observed needing this on two of four
   * cycles on 2026-08-22.
   */
  private void scheduleNickReclaimCheck() {
    if (!nickCheckScheduled.compareAndSet(false, true)) {
      return;
    }
    CompletableFuture.runAsync(() -> {
      nickCheckScheduled.set(false);
      String desired = userService.getUsername();
      if (!needsNick(desired)) {
        return;
      }
      // A refused retry is harmless: TafNickRejectedListener's fallback is the suffix we are
      // already on, so this cannot push us further out.
      log.info("[nickReclaim] still '{}', asking for '{}' again before reconnecting",
          client.getNick(), desired);
      client.setNick(desired);
      scheduleRelogIfStillNotOurs();
    }, CompletableFuture.delayedExecutor(NICK_RECLAIM_CHECK_SECONDS, TimeUnit.SECONDS));
  }

  private void scheduleRelogIfStillNotOurs() {
    CompletableFuture.runAsync(() -> {
      String desired = userService.getUsername();
      if (!needsNick(desired)) {
        log.info("[nickReclaim] reclaimed '{}' without reconnecting", client.getNick());
        return;
      }
      if (!nickReconnectUsed.compareAndSet(false, true)) {
        return;
      }
      log.info("[nickReclaim] '{}' still not ours, reconnecting to register as '{}'",
          client.getNick(), desired);
      disconnect();
      // Let our own QUIT land first: re-registering on top of a socket that still holds the
      // suffix collides with ourselves and yields "Nick``" (seen 2026-08-22 12:10:13).
      CompletableFuture.runAsync(this::connect,
          CompletableFuture.delayedExecutor(RELOG_SETTLE_SECONDS, TimeUnit.SECONDS));
    }, CompletableFuture.delayedExecutor(NICK_RETRY_SECONDS, TimeUnit.SECONDS));
  }

  /** True when we are connected and the server has us on something other than our own nick. */
  private boolean needsNick(String desired) {
    return desired != null
        && connectionState.get() == ConnectionState.CONNECTED
        && !desired.equalsIgnoreCase(client.getNick());
  }

  private void rejoinChannels() {
    if (channelsToRejoin.isEmpty()) {
      return;
    }
    List<String> toJoin = new ArrayList<>(channelsToRejoin);
    channelsToRejoin.clear();
    autoChannelsJoined = true;
    log.info("[rejoinChannels] restoring {} channel(s) after reconnect: {}", toJoin.size(), toJoin);
    joinChannels(toJoin, true);
  }

  private void joinChannels(List<String> channels, boolean reverseOrder) {
    if (channels == null) {
      return;
    }

    if (reverseOrder) {
      ListIterator<String> iterator = channels.listIterator(channels.size());
      while (iterator.hasPrevious()) {
        joinChannel(iterator.previous());
      }
    } else {
      channels.forEach(this::joinChannel);
    }
  }

  private void onDisconnected() {
    log.debug("[onDisconnected]");
    synchronized (channels) {
      // Remember where we were; nothing else would rejoin us.
      channelsToRejoin.clear();
      channelsToRejoin.addAll(channels.keySet());
      // Keep the entries. Removing one closes its tab, which discards the message history and
      // parts the channel for real via AbstractChatTabController.close(). Users are refilled
      // from NAMES on rejoin.
      channels.values().forEach(ChatChannel::clearUsers);
    }
    synchronized (chatChannelUsersByChannelAndName) {
      chatChannelUsersByChannelAndName.clear();
    }
    newbieChannelJoined = false;
    autoChannelsJoined = false;
  }

  private void addUserToChannel(String channelName, ChatChannelUser chatUser) {
    getOrCreateChannel(channelName).addUser(chatUser);
    if (chatUser.isModerator()) {
      onModeratorSet(channelName, chatUser.getUsername());
    }
  }

  /**
   * @param weLeft drop the channel itself, not just the user. Passed in rather than inferred
   *     from the name: a stale session of ours quits under our own nick, and treating that as
   *     our own departure closed the tab, which parts the channel for real.
   */
  private void onChatUserLeftChannel(String channelName, String username, boolean weLeft) {
    log.debug("[onChatUserLeftChannel] {} {} weLeft={}", channelName, username, weLeft);
    if (!channels.containsKey(channelName) || channels.get(channelName).removeUser(username) == null) {
      return;
    }
    if (weLeft) {
      synchronized (channels) {
        channels.remove(channelName);
      }
    }
    synchronized (chatChannelUsersByChannelAndName) {
      chatChannelUsersByChannelAndName.remove(mapKey(username, channelName));
    }
    // The server doesn't yet tell us when a user goes offline, so we have to rely on the user leaving IRC.
    if (preferencesService.getClientRemoteConfiguration().getAllChatChannels().stream()
        .noneMatch(chan -> this.chatChannelUsersByChannelAndName.containsKey(mapKey(username, chan)))) {
      eventBus.post(new UserOfflineEvent(username));
    }
  }

  private void onMessage(String message) {
    message = message.replace(getPassword(), "*****");
    log.debug("[onMessage] {}", message);
  }

  @Handler
  private void onDisconnect(ClientConnectionEndedEvent event) {
    log.debug("[onDisconnect] event.getReconnectionDelay()={}, event.getCause={}", event.getReconnectionDelay(),
        event.getCause().isPresent() ? event.getCause().get().toString() : "unknown");
    connectionState.set(ConnectionState.DISCONNECTED);
  }

  @NotNull
  private String getPassword() {
    return Hashing.md5().hashString(Hashing.sha256().hashString(userService.getPassword(), UTF_8).toString(), UTF_8).toString();
  }

  private void onSocialMessage(SocialMessage socialMessage) {
    log.debug("[onSocialMessage]");
    if (!autoChannelsJoined && socialMessage.getChannels() != null) {
      autoChannelsJoined = true;
      List<String> autoChannels = new ArrayList<>(socialMessage.getChannels());
      autoChannels.removeAll(preferencesService.getPreferences().getChat().getAutoJoinChannels2());
      joinChannels(autoChannels, true);
      joinChannels(preferencesService.getPreferences().getChat().getAutoJoinChannels2(), true);
    }
  }

  @Override
  public void connect() {
    if (isChatBannedAllChannels()) {
      return;
    }

    String username = userService.getUsername();

    Irc irc = clientProperties.getIrc();

    client = (DefaultClient) Client.builder()
        .user(String.valueOf(userService.getUserId()))
        .realName(username)
        .nick(username)
        .server()
        .host(irc.getHost())
        .port(irc.getPort(), SecurityType.SECURE)
        .secureTrustManagerFactory(new TrustEveryoneFactory())
        .then()
        .listeners()
        .input(this::onMessage)
        .output(this::onMessage)
        // Kitteh's default exception listener dumps the full Netty
        // stack trace to stderr. That makes otherwise-informative
        // server rejections (Z-line / session-limit / plain-text reply
        // before TLS handshake) flood the IDE console with unreadable
        // NotSslRecordException payloads. Downgrade to a single-line
        // DEBUG entry; the user-visible effect is the same (the chat
        // client reconnects anyway) and genuine errors are still in
        // client.log at DEBUG level if investigation is needed.
        .exception(t -> log.debug("IRC client I/O exception: {}", t.toString()))
        .then()
        .build();

    nickServ = NickServ.builder(client)
        .account(username)
        .password(getPassword())
        .build();

    client.getMessageTagManager().registerTagCreator("message-tags", "+taforever.com/toxicity", Toxicity.FUNCTION);
    replaceBrokenNickListeners();
    client.getEventManager().registerEventListener(this);
    client.getActorTracker().setQueryChannelInformation(false);
    client.connect();
  }

  @Override
  public void disconnect() {
    if (client != null) {
      log.info("Disconnecting from IRC");
      client.shutdown("Goodbye");
    }
  }

  @Override
  public CompletableFuture<String> sendMessageInBackground(String target, String message) {
    eventBus.post(new ChatMessageEvent(new ChatMessage(target, Instant.now(), userService.getUsername(), message, 0.0)));
    return CompletableFuture.supplyAsync(() -> {
      client.sendMessage(target, message);
      return message;
    });
  }

  @Override
  public ChatChannel getOrCreateChannel(String channelName) {
    synchronized (channels) {
      if (!channels.containsKey(channelName)) {
        channels.put(channelName, new ChatChannel(channelName));
      }
      return channels.get(channelName);
    }
  }

  @Override
  public ChatChannelUser getOrCreateChatUser(String username, String channel, boolean isModerator) {
    synchronized (chatChannelUsersByChannelAndName) {
      String key = mapKey(username, channel);
      if (!chatChannelUsersByChannelAndName.containsKey(key)) {
        Optional<Player> optionalPlayer = playerService.getPlayerForUsername(username);

        ChatChannelUser chatChannelUser = new ChatChannelUser(username, isModerator);
        chatChannelUsersByChannelAndName.put(key, chatChannelUser);
        chatUserService.associatePlayerToChatUser(chatChannelUser, optionalPlayer.orElse(null));
      }
      return chatChannelUsersByChannelAndName.get(key);
    }
  }

  @Override
  public void addUsersListener(String channelName, MapChangeListener<String, ChatChannelUser> listener) {
    getOrCreateChannel(channelName).addUsersListeners(listener);
  }

  @Override
  public void addChatUsersByNameListener(MapChangeListener<String, ChatChannelUser> listener) {
    synchronized (chatChannelUsersByChannelAndName) {
      JavaFxUtil.addListener(chatChannelUsersByChannelAndName, listener);
    }
  }

  @Override
  public void removeChatUsersByNameListener(MapChangeListener<String, ChatChannelUser> listener) {
    synchronized (chatChannelUsersByChannelAndName) {
      JavaFxUtil.removeListener(chatChannelUsersByChannelAndName, listener);
    }
  }

  @Override
  public void addChannelsListener(MapChangeListener<String, ChatChannel> listener) {
    JavaFxUtil.addListener(channels, listener);
  }

  @Override
  public void removeUsersListener(String channelName, MapChangeListener<String, ChatChannelUser> listener) {
    if (channels.containsKey(channelName)) {
      channels.get(channelName).removeUserListener(listener);
    }
  }

  @Override
  public void leaveChannel(String channelName) {
    log.debug("[leaveChannel] {}", channelName);
    if (connectionState.get() != ConnectionState.CONNECTED) {
      // Kitteh would hold the PART and send it on the next connection, parting a channel we
      // had just rejoined. Reached when a tab is closed while disconnected.
      log.debug("[leaveChannel] not connected, ignoring {}", channelName);
      return;
    }
    if (client != null) {
      client.removeChannel(channelName);
    }
  }

  @Override
  public CompletableFuture<String> sendActionInBackground(String target, String action) {
    return CompletableFuture.supplyAsync(() -> {
      client.sendCtcpMessage(target, "ACTION " + action);
      return action;
    });
  }

  @Override
  public void joinChannel(String channelName) {
    if (client != null) {
      log.debug("[joinChannel] {}", channelName);
      client.addChannel(channelName);
    }
  }

  @Override
  public boolean isDefaultChannel(String channelName) {
    return preferencesService.getClientRemoteConfiguration().getAllChatChannels().contains(channelName);
  }

  @Override
  public void destroy() {
    close();
  }

  public void close() {
    if (client != null) {
      client.shutdown();
    }
  }

  @Override
  public ReadOnlyObjectProperty<ConnectionState> connectionStateProperty() {
    return connectionState;
  }

  @Override
  public void reconnect() {
    log.debug("[reconnect]");
    Set<String> currentChannels = channels.keySet();
    client.reconnect();
    currentChannels.forEach(this::joinChannel);
  }

  @Override
  public void whois(String username) {
    client.sendRawLine("WHOIS " + username);
  }

  @Override
  public void incrementUnreadMessagesCount(int delta) {
    unreadMessagesCount.set(unreadMessagesCount.get() + delta);
    eventBus.post(UpdateApplicationBadgeEvent.ofDelta(delta));
  }

  @Override
  public ReadOnlyIntegerProperty unreadMessagesCount() {
    return unreadMessagesCount;
  }

  @Override
  public ObjectProperty<ChatBanNoticeMessage> getChatBanNoticeMessage() {
    return chatBanNoticeMessage;
  }

  private void onModeratorSet(String channelName, String username) {
    getOrCreateChatUser(username, channelName, true).setModerator(true);
  }

  private String mapKey(String username, String channelName) {
    return username + channelName;
  }

  private void onNewTadaReplayMessage(NewTadaReplayMessage newTadaReplayMessage) {
    String players = newTadaReplayMessage.getPlayers().stream()
        .reduce("", (a, b) -> a.isEmpty() ? b : a + "+" + b);

    String chatContent = i18n.get("tada.advertise.newReplay",
        clientProperties.getTada().getRootUrl(), newTadaReplayMessage.getTadaReplayId(),
        players, newTadaReplayMessage.getMapName());

    this.playerService.getCurrentPlayer()
        .map(player -> preferencesService.getClientRemoteConfiguration().getAllChatChannels().stream()
            .filter(channel -> this.chatChannelUsersByChannelAndName.containsKey(mapKey(player.getUsername(), channel)))
            .findFirst()
            .map(channel -> {
              ChatMessage msg = new ChatMessage(
                  channel, Instant.now(), i18n.get("chat.operator"), chatContent, 0.0, true);
              eventBus.post(new ChatMessageEvent(msg));
              return msg;
            })
        );
  }

  @Override
  public boolean isChatBanned() {
    return this.chatBanNoticeMessage.get() != null && this.chatBanNoticeMessage.get().getIsBanned();
  }

  @Override
  public boolean isChatBannedAllChannels() {
    if (!isChatBanned()) {
      return false;
    }
    String channels = this.chatBanNoticeMessage.get().getChannels();
    if (channels == null || channels.isEmpty()) {
      return true;
    }
    return false;
  }

  private void onChatBanNotification(ChatBanNoticeMessage msg) {
    this.chatBanNoticeMessage.set(msg);
    if (msg.getIsBanned() && "".equals(msg.getChannels())) {
      disconnect();
    }
  }
}
