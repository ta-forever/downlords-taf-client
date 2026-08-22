package com.faforever.client.chat;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.i18n.I18n;
import com.faforever.client.net.ConnectionState;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.user.UserService;
import com.google.common.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javafx.collections.MapChangeListener;
import org.kitteh.irc.client.library.defaults.DefaultClient;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Channels are joined only from {@link KittehChatService#onSocialMessage}, which the lobby sends
 * at login. An IRC-only reconnect gets no fresh SocialMessage, so the client came back online in
 * no channels — 2026-08-22 showed 25 IRC connects against 10 #coreprime joins.
 */
public class KittehRejoinOnReconnectTest {

  private KittehChatService instance;
  private DefaultClient client;
  private Method onDisconnected;
  private Method rejoinChannels;

  @BeforeEach
  public void setUp() throws Exception {
    client = mock(DefaultClient.class);
    when(client.getNick()).thenReturn("Walker");

    UserService userService = mock(UserService.class);
    when(userService.getUsername()).thenReturn("Walker");

    PreferencesService preferencesService = mock(PreferencesService.class);
    PlayerService playerService = mock(PlayerService.class);
    when(playerService.getPlayerForUsername(anyString())).thenReturn(java.util.Optional.empty());

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
    instance.connectionState.set(ConnectionState.CONNECTED);

    onDisconnected = KittehChatService.class.getDeclaredMethod("onDisconnected");
    onDisconnected.setAccessible(true);
    rejoinChannels = KittehChatService.class.getDeclaredMethod("rejoinChannels");
    rejoinChannels.setAccessible(true);
  }

  @Test
  public void channelsWeWereInAreRestoredOnReconnect() throws Exception {
    instance.getOrCreateChannel("#coreprime");
    instance.getOrCreateChannel("#aeolus");

    onDisconnected.invoke(instance);
    rejoinChannels.invoke(instance);

    verify(client).addChannel("#coreprime");
    verify(client).addChannel("#aeolus");
  }

  @Test
  public void aFreshConnectJoinsNothingItself() throws Exception {
    // Nothing was recorded, so the normal SocialMessage path must remain in charge.
    rejoinChannels.invoke(instance);

    verify(client, never()).addChannel(anyString());
  }

  @Test
  public void theRestoreHappensOnlyOnce() throws Exception {
    instance.getOrCreateChannel("#coreprime");
    onDisconnected.invoke(instance);

    rejoinChannels.invoke(instance);
    rejoinChannels.invoke(instance);

    verify(client).addChannel("#coreprime");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void aChannelTheUserLeftBeforeTheDropIsNotDraggedBack() throws Exception {
    instance.getOrCreateChannel("#coreprime");
    instance.getOrCreateChannel("#aeolus");

    // Model the user having parted #aeolus: the PART echo drops it from the channel map.
    java.lang.reflect.Field channelsField = KittehChatService.class.getDeclaredField("channels");
    channelsField.setAccessible(true);
    ((java.util.Map<String, ChatChannel>) channelsField.get(instance)).remove("#aeolus");

    onDisconnected.invoke(instance);
    rejoinChannels.invoke(instance);

    verify(client).addChannel("#coreprime");
    verify(client, never()).addChannel("#aeolus");
  }

  @Test
  public void aDropDoesNotFireAChannelRemoval() throws Exception {
    // A removal closes the tab, and closing a tab discards its message history and parts the
    // channel for real. Reported as "hiccup, all chat history gone".
    instance.getOrCreateChannel("#coreprime");
    boolean[] removed = {false};
    instance.addChannelsListener((MapChangeListener<String, ChatChannel>) change -> {
      if (change.wasRemoved()) {
        removed[0] = true;
      }
    });

    onDisconnected.invoke(instance);

    assertFalse(removed[0], "the tab must survive a reconnect");
  }

  @Test
  public void theSameChannelObjectIsStillThereAfterRejoining() throws Exception {
    ChatChannel before = instance.getOrCreateChannel("#coreprime");

    onDisconnected.invoke(instance);
    rejoinChannels.invoke(instance);

    assertSame(before, instance.getOrCreateChannel("#coreprime"),
        "a new instance means a new empty tab");
  }

  @Test
  public void theUserListIsStillEmptiedSoNamesCanRebuildIt() throws Exception {
    ChatChannel channel = instance.getOrCreateChannel("#coreprime");
    channel.addUser(new ChatChannelUser("Walker", false));

    onDisconnected.invoke(instance);

    assertTrue(channel.getUsers().isEmpty());
  }
}
