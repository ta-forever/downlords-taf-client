package com.faforever.client.chat;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.i18n.I18n;
import com.faforever.client.net.ConnectionState;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.update.ClientConfiguration;
import com.faforever.client.user.UserService;
import com.google.common.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kitteh.irc.client.library.defaults.DefaultClient;
import org.kitteh.irc.client.library.element.User;
import org.kitteh.irc.client.library.event.user.UserQuitEvent;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A dying ghost carries our own name, and services rename us onto that name as they kill it.
 * Seen in prod 2026-08-21 10:55:10: RECOVER worked, we became "TheCoreCommander" — and one
 * second later the ghost's QUIT made the client part #coreprime and drop itself from the list.
 */
public class KittehGhostQuitTest {

  private static final String CHANNEL = "#coreprime";
  private static final String NICK = "TheCoreCommander";

  private KittehChatService instance;
  private DefaultClient client;
  private Method onChatUserQuit;

  @BeforeEach
  public void setUp() throws Exception {
    client = mock(DefaultClient.class);
    UserService userService = mock(UserService.class);
    when(userService.getUsername()).thenReturn(NICK);
    when(userService.getPassword()).thenReturn("password");

    PlayerService playerService = mock(PlayerService.class);
    when(playerService.getPlayerForUsername(anyString())).thenReturn(Optional.empty());

    // onChatUserLeftChannel consults the remote channel list before posting UserOfflineEvent.
    ClientConfiguration remoteConfig = mock(ClientConfiguration.class);
    when(remoteConfig.getAllChatChannels()).thenReturn(List.of(CHANNEL));
    PreferencesService preferencesService = mock(PreferencesService.class);
    when(preferencesService.getClientRemoteConfiguration()).thenReturn(remoteConfig);

    instance = new KittehChatService(
        mock(ChatUserService.class),
        preferencesService,
        userService,
        mock(FafService.class),
        mock(EventBus.class),
        new ClientProperties(),
        playerService,
        mock(NotificationService.class),
        mock(I18n.class));
    instance.client = client;

    onChatUserQuit = KittehChatService.class
        .getDeclaredMethod("onChatUserQuit", UserQuitEvent.class);
    onChatUserQuit.setAccessible(true);
  }

  private void fireQuit(String nick) throws Exception {
    User user = mock(User.class);
    when(user.getNick()).thenReturn(nick);
    UserQuitEvent event = mock(UserQuitEvent.class);
    when(event.getUser()).thenReturn(user);
    onChatUserQuit.invoke(instance, event);
  }

  @Test
  public void aGhostQuittingUnderOurCurrentNickDoesNotDropUsFromTheChannel() throws Exception {
    // Services have already renamed us onto the nick the dying ghost holds.
    when(client.getNick()).thenReturn(NICK);
    ChatChannel channel = instance.getOrCreateChannel(CHANNEL);
    channel.addUser(new ChatChannelUser(NICK, false));

    fireQuit(NICK);

    assertTrue(instance.getUserChannels(NICK).contains(CHANNEL), "we must stay in the channel");
    assertNotNull(channel.getUser(NICK), "we must stay in the user list");
  }

  @Test
  public void aGhostQuittingUnderOurOwnNameIsLeftInPlace() throws Exception {
    // Our name is never deleted on a quit, even while we are still on the suffix. The ghost's
    // quit and our rename onto its name land on different threads ~1ms apart (22:03:45.774 vs
    // .775), so deleting it removed *us* and parted the channel. Keeping it is safe: the
    // rename adopts the entry, and a genuinely stale one is dropped by the next NAMES.
    when(client.getNick()).thenReturn(NICK + "`");
    ChatChannel channel = instance.getOrCreateChannel(CHANNEL);
    channel.addUser(new ChatChannelUser(NICK, false));
    channel.addUser(new ChatChannelUser(NICK + "`", false));

    fireQuit(NICK);

    assertNotNull(channel.getUser(NICK), "our name must survive the ghost's quit");
    assertNotNull(channel.getUser(NICK + "`"), "and so must the nick we currently hold");
  }

  @Test
  public void aGhostQuittingBeforeWeAreRenamedStillLeavesUsInTheChannel() throws Exception {
    // The 11:05:57 ordering: the ghost dies first, our rename lands a second later. Our nick
    // is still the suffix here, so the "is that us?" check cannot save us -- only refusing to
    // treat a QUIT as our own departure does.
    when(client.getNick()).thenReturn(NICK + "`");
    ChatChannel channel = instance.getOrCreateChannel(CHANNEL);
    channel.addUser(new ChatChannelUser(NICK, false));
    channel.addUser(new ChatChannelUser(NICK + "`", false));

    fireQuit(NICK);

    assertTrue(instance.getUserChannels(NICK + "`").contains(CHANNEL),
        "the channel must not be dropped -- dropping it parts it for real");
  }

  @Test
  public void anOrdinaryUserQuittingIsStillRemoved() throws Exception {
    when(client.getNick()).thenReturn(NICK);
    ChatChannel channel = instance.getOrCreateChannel(CHANNEL);
    channel.addUser(new ChatChannelUser("SomebodyElse", false));

    fireQuit("SomebodyElse");

    assertNull(channel.getUser("SomebodyElse"));
  }

  @Test
  public void leavingAChannelWhileDisconnectedSendsNoPart() {
    // onDisconnected() clears the channel map, closing the tabs, which lands in leaveChannel.
    // Kitteh would hold that PART and send it on the next connection -- observed on the wire
    // at 21:24:25.408, answered with 442, after the client had already rejoined.
    instance.connectionState.set(ConnectionState.DISCONNECTED);

    instance.leaveChannel(CHANNEL);

    verify(client, never()).removeChannel(CHANNEL);
  }

  @Test
  public void leavingAChannelWhileConnectedStillParts() {
    instance.connectionState.set(ConnectionState.CONNECTED);

    instance.leaveChannel(CHANNEL);

    verify(client).removeChannel(CHANNEL);
  }
}
