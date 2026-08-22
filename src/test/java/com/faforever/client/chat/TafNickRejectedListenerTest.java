package com.faforever.client.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.event.client.ClientReceiveNumericEvent;
import org.kitteh.irc.client.library.element.ServerMessage;
import org.kitteh.irc.client.library.feature.EventManager;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bug being fixed: Kitteh answers any 433 by sending {@code requestedNick + '`'}, even when
 * we already hold the nick we wanted — so a stale rejection renamed us straight back off it,
 * measured at 126ms after a successful reclaim. Owning the listener makes that decision ours,
 * synchronously, instead of racing it from another thread.
 */
public class TafNickRejectedListenerTest {

  private static final String NICK = "TheCoreCommander";

  private Client.WithManagement client;
  private boolean registered;
  private TafNickRejectedListener instance;

  @BeforeEach
  public void setUp() {
    client = mock(Client.WithManagement.class);
    EventManager eventManager = mock(EventManager.class);
    when(client.getEventManager()).thenReturn(eventManager);
    when(client.getRequestedNick()).thenReturn(NICK);
    registered = true;
    instance = new TafNickRejectedListener(client, () -> NICK, () -> registered);
  }

  private ClientReceiveNumericEvent rejection() {
    ClientReceiveNumericEvent event = mock(ClientReceiveNumericEvent.class);
    ServerMessage source = mock(ServerMessage.class);
    when(event.getSource()).thenReturn(source);
    return event;
  }

  @Test
  public void aStaleRejectionDoesNotRenameUsOffOurNick() {
    when(client.getNick()).thenReturn(NICK);

    instance.nickInUse(rejection());

    verify(client, never()).sendNickChange(anyString());
  }

  @Test
  public void whileOnASuffixWeStillFallBack() {
    when(client.getNick()).thenReturn(NICK + "`");

    instance.nickInUse(rejection());

    verify(client).sendNickChange(NICK + "`");
  }

  @Test
  public void duringRegistrationWeAlwaysFallBackOrWeNeverGetOnline() {
    // Not yet registered: even though getNick() reads as the nick we asked for, a rejection
    // here is real and we must take a suffix.
    registered = false;
    when(client.getNick()).thenReturn(NICK);

    instance.nickInUse(rejection());

    verify(client).sendNickChange(NICK + "`");
  }
}
