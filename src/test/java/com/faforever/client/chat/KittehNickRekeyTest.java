package com.faforever.client.chat;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.user.UserService;
import com.google.common.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kitteh.irc.client.library.element.User;
import org.kitteh.irc.client.library.event.user.UserNickChangeEvent;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Re-keying a renamed user in the chat list. Reachable only since {@link TafNickListener}
 * replaced Kitteh's nick listener — upstream threw on this ircd's bare-prefix NICK, so the
 * event never fired and both names stayed in the list (prod, 2026-08-22 22:41).
 */
public class KittehNickRekeyTest {

  private static final String CHANNEL = "#coreprime";
  private static final String NICK = "TheCoreCommander";

  private KittehChatService instance;
  private Method onUserNickChange;

  @BeforeEach
  public void setUp() throws Exception {
    UserService userService = mock(UserService.class);
    when(userService.getUsername()).thenReturn(NICK);
    PlayerService playerService = mock(PlayerService.class);
    when(playerService.getPlayerForUsername(anyString())).thenReturn(Optional.empty());

    instance = new KittehChatService(
        mock(ChatUserService.class),
        mock(PreferencesService.class),
        userService,
        mock(FafService.class),
        mock(EventBus.class),
        new ClientProperties(),
        playerService,
        mock(NotificationService.class),
        mock(I18n.class));

    onUserNickChange = KittehChatService.class
        .getDeclaredMethod("onUserNickChange", UserNickChangeEvent.class);
    onUserNickChange.setAccessible(true);
  }

  private void fireRename(String oldNick, String newNick) throws Exception {
    User oldUser = mock(User.class);
    User newUser = mock(User.class);
    when(oldUser.getNick()).thenReturn(oldNick);
    when(newUser.getNick()).thenReturn(newNick);
    UserNickChangeEvent event = mock(UserNickChangeEvent.class);
    when(event.getOldUser()).thenReturn(oldUser);
    when(event.getNewUser()).thenReturn(newUser);
    onUserNickChange.invoke(instance, event);
  }

  @Test
  public void theSuffixedNameIsReplacedByTheRealOne() throws Exception {
    ChatChannel channel = instance.getOrCreateChannel(CHANNEL);
    channel.addUser(new ChatChannelUser(NICK + "`", false));

    fireRename(NICK + "`", NICK);

    assertNull(channel.getUser(NICK + "`"), "the suffixed name must go");
    assertNotNull(channel.getUser(NICK), "the real name must be present");
    assertEquals(1, channel.getUsers().size(), "and not both at once");
  }

  @Test
  public void moderatorStatusSurvives() throws Exception {
    ChatChannel channel = instance.getOrCreateChannel(CHANNEL);
    channel.addUser(new ChatChannelUser("OldOp", true));

    fireRename("OldOp", "NewOp");

    assertEquals(true, channel.getUser("NewOp").isModerator());
  }

  @Test
  public void someoneNotInTheChannelIsIgnored() throws Exception {
    ChatChannel channel = instance.getOrCreateChannel(CHANNEL);
    channel.addUser(new ChatChannelUser("Present", false));

    fireRename("Stranger", "StrangerNew");

    assertNotNull(channel.getUser("Present"));
    assertNull(channel.getUser("StrangerNew"));
    assertEquals(1, channel.getUsers().size());
  }
}
