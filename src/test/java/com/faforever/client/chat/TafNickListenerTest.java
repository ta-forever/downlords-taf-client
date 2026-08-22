package com.faforever.client.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.element.ServerMessage;
import org.kitteh.irc.client.library.element.User;
import org.kitteh.irc.client.library.feature.ActorTracker;
import org.kitteh.irc.client.library.feature.EventManager;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bug being fixed: Kitteh's DefaultNickListener requires the NICK prefix to parse as a
 * {@code User}, and taforever's ircd sends a bare nick. It therefore never called
 * {@code setCurrentNick}, so {@code client.getNick()} reported the nick we asked for rather than
 * the one we hold — for the life of the connection.
 */
public class TafNickListenerTest {

  private static final String NICK = "TheCoreCommander";

  private Client.WithManagement client;
  private ActorTracker tracker;
  private TafNickListener instance;

  @BeforeEach
  public void setUp() {
    client = mock(Client.WithManagement.class);
    tracker = mock(ActorTracker.class);
    // Build every mock before stubbing: creating one inside a when(...) argument leaves
    // Mockito mid-stubbing and breaks every later when() in the class.
    EventManager eventManager = mock(EventManager.class);
    when(client.getActorTracker()).thenReturn(tracker);
    when(client.getEventManager()).thenReturn(eventManager);
    when(tracker.getTrackedUser(anyString())).thenReturn(Optional.empty());
    instance = new TafNickListener(client);
  }

  private void rename(String prefixName, String newNick) {
    instance.applyNickChange(prefixName, newNick, mock(ServerMessage.class));
  }

  @Test
  public void bareNickPrefixUpdatesOurOwnNick() {
    when(client.getNick()).thenReturn(NICK + "`");

    rename(NICK + "`", NICK);

    verify(client).setCurrentNick(NICK);
  }

  @Test
  public void fullPrefixAlsoWorks() {
    when(client.getNick()).thenReturn(NICK + "`");

    rename(NICK + "`!1@host", NICK);

    verify(client).setCurrentNick(NICK);
  }

  @Test
  public void somebodyElsesRenameLeavesOurNickAlone() {
    when(client.getNick()).thenReturn(NICK);

    rename("SomebodyElse", "SomebodyElseRenamed");

    verify(client, never()).setCurrentNick(anyString());
  }

  @Test
  public void aTrackedUserIsRenamedInTheTracker() {
    when(client.getNick()).thenReturn(NICK);
    User old = mock(User.class);
    User renamed = mock(User.class);
    // UserNickChangeEvent's base class asserts the actors belong to this client, and its
    // Change record rejects a null old value.
    when(old.getClient()).thenReturn(client);
    when(renamed.getClient()).thenReturn(client);
    when(old.getNick()).thenReturn("Someone");
    when(renamed.getNick()).thenReturn("SomeoneNew");
    when(tracker.getTrackedUser("Someone")).thenReturn(Optional.of(old));
    when(tracker.getTrackedUser("SomeoneNew")).thenReturn(Optional.of(renamed));

    rename("Someone", "SomeoneNew");

    verify(tracker).trackUserNickChange("Someone", "SomeoneNew");
  }
}
