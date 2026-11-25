package com.faforever.client.chat;

import com.faforever.client.chat.event.ChatUserJoinedChannelEvent;
import com.faforever.client.chat.event.ChatUserLeftChannelEvent;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.update.ClientConfiguration;
import com.faforever.client.user.UserService;
import com.google.common.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.defaults.DefaultClient;
import org.kitteh.irc.client.library.element.Channel;
import org.kitteh.irc.client.library.element.ServerMessage;
import org.kitteh.irc.client.library.element.User;
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent;
import org.kitteh.irc.client.library.event.channel.ChannelNamesUpdatedEvent;
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent;
import org.kitteh.irc.client.library.event.user.UserQuitEvent;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #110: the IRC layer posts join/leave events for the "→ Bob joined the channel" chat lines, only for real
 * JOIN / PART / QUIT by other users, never for the NAMES list we get on joining, and never for ourselves.
 */
public class KittehJoinLeaveEventTest {

  private static final String CHANNEL = "#aeolus";
  private static final String NICK = "Me";

  private KittehChatService instance;
  private EventBus eventBus;

  @BeforeEach
  public void setUp() {
    UserService userService = mock(UserService.class);
    when(userService.getUsername()).thenReturn(NICK);
    when(userService.getPassword()).thenReturn("password");

    PlayerService playerService = mock(PlayerService.class);
    when(playerService.getPlayerForUsername(anyString())).thenReturn(Optional.empty());

    ClientConfiguration remoteConfig = mock(ClientConfiguration.class);
    when(remoteConfig.getAllChatChannels()).thenReturn(List.of(CHANNEL));
    PreferencesService preferencesService = mock(PreferencesService.class);
    when(preferencesService.getClientRemoteConfiguration()).thenReturn(remoteConfig);

    eventBus = mock(EventBus.class);
    instance = new KittehChatService(
        mock(ChatUserService.class),
        preferencesService,
        userService,
        mock(FafService.class),
        eventBus,
        new ClientProperties(),
        playerService,
        mock(NotificationService.class),
        mock(I18n.class));
    DefaultClient client = mock(DefaultClient.class);
    when(client.getNick()).thenReturn(NICK);
    instance.client = client;
  }

  private void invoke(String handler, Class<?> eventType, Object event) throws Exception {
    Method method = KittehChatService.class.getDeclaredMethod(handler, eventType);
    method.setAccessible(true);
    method.invoke(instance, event);
  }

  private final Client ircClient = mock(Client.class);

  private User user(String nick) {
    User user = mock(User.class);
    when(user.getNick()).thenReturn(nick);
    when(user.getClient()).thenReturn(ircClient);
    return user;
  }

  private Channel channel(List<User> users) {
    Channel channel = mock(Channel.class);
    when(channel.getName()).thenReturn(CHANNEL);
    when(channel.getClient()).thenReturn(ircClient);
    when(channel.getUsers()).thenReturn(users);
    return channel;
  }

  // Kitteh's event getters are final, so the events are real objects around mocked users and channels.
  private void join(String nick) throws Exception {
    User user = user(nick);
    invoke("onJoinEvent", ChannelJoinEvent.class,
        new ChannelJoinEvent(ircClient, mock(ServerMessage.class), channel(List.of(user)), user));
  }

  private void part(String nick) throws Exception {
    invoke("onPartEvent", ChannelPartEvent.class,
        new ChannelPartEvent(ircClient, mock(ServerMessage.class), channel(List.of()), user(nick), ""));
  }

  private void quit(String nick) throws Exception {
    UserQuitEvent event = mock(UserQuitEvent.class);
    User user = user(nick);
    when(event.getUser()).thenReturn(user);
    invoke("onChatUserQuit", UserQuitEvent.class, event);
  }

  @Test
  public void anotherUserJoiningPostsJoinedEvent() throws Exception {
    join("Bob");

    verify(eventBus).post(new ChatUserJoinedChannelEvent(CHANNEL, "Bob"));
  }

  @Test
  public void weJoiningPostsNoJoinedEvent() throws Exception {
    join(NICK);

    verify(eventBus, never()).post(any(ChatUserJoinedChannelEvent.class));
  }

  @Test
  public void namesListOnJoiningPostsNoJoinedEvents() throws Exception {
    Channel channel = channel(List.of(user("Alice"), user("Bob"), user(NICK)));

    invoke("onChatUserList", ChannelNamesUpdatedEvent.class,
        new ChannelNamesUpdatedEvent(ircClient, List.of(mock(ServerMessage.class)), channel));

    verify(eventBus, never()).post(any(ChatUserJoinedChannelEvent.class));
  }

  @Test
  public void anotherUserPartingPostsLeftEvent() throws Exception {
    instance.getOrCreateChannel(CHANNEL).addUser(new ChatChannelUser("Bob", false));

    part("Bob");

    verify(eventBus).post(new ChatUserLeftChannelEvent(CHANNEL, "Bob"));
  }

  @Test
  public void anotherUserQuittingPostsLeftEvent() throws Exception {
    instance.getOrCreateChannel(CHANNEL).addUser(new ChatChannelUser("Bob", false));

    quit("Bob");

    verify(eventBus).post(new ChatUserLeftChannelEvent(CHANNEL, "Bob"));
  }

  @Test
  public void quitOfSomeoneNotInTheChannelPostsNothing() throws Exception {
    instance.getOrCreateChannel(CHANNEL);

    quit("Stranger");

    verify(eventBus, never()).post(any(ChatUserLeftChannelEvent.class));
  }

  @Test
  public void wePartingPostsNoLeftEvent() throws Exception {
    instance.getOrCreateChannel(CHANNEL).addUser(new ChatChannelUser(NICK, false));

    part(NICK);

    verify(eventBus, never()).post(any(ChatUserLeftChannelEvent.class));
  }
}
