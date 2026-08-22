package com.faforever.client.chat;

import lombok.extern.slf4j.Slf4j;
import net.engio.mbassy.listener.Handler;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.defaults.listener.AbstractDefaultListenerBase;
import org.kitteh.irc.client.library.event.client.ClientReceiveNumericEvent;
import org.kitteh.irc.client.library.event.client.NickRejectedEvent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.kitteh.irc.client.library.feature.filter.NumericFilter;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Replaces Kitteh's {@code DefaultNickRejectedListener}, which renames us off our own nick.
 *
 * <p>Kitteh answers any 433 with {@code sendNickChange(getRequestedNick() + '`')}, regardless of
 * the nick we currently hold. A late or racing rejection therefore takes us off the nick moments
 * after we reclaim it — measured at 126ms on 2026-08-21 (won at 21:50:19.514, renamed away at
 * .640). It cannot be intercepted from a handler, because Kitteh fires the event and sends the
 * fallback from a different thread than the one our handlers run on.
 *
 * <p>Owning the listener makes the decision synchronous: during registration we still need a
 * fallback or we never get online, but once registered and holding the nick we want, a rejection
 * is stale and we ignore it.
 */
@Slf4j
public class TafNickRejectedListener extends AbstractDefaultListenerBase {

  private final Supplier<String> desiredNick;
  private final BooleanSupplier registered;

  public TafNickRejectedListener(Client.@NonNull WithManagement client,
                                 Supplier<String> desiredNick,
                                 BooleanSupplier registered) {
    super(client);
    this.desiredNick = desiredNick;
    this.registered = registered;
  }

  @NumericFilter(431) // No nick given
  @NumericFilter(432) // Erroneous nickname
  @NumericFilter(433) // Nick in use
  @Handler(priority = Integer.MAX_VALUE - 1)
  public void nickInUse(ClientReceiveNumericEvent event) {
    String current = this.getClient().getNick();
    String wanted = desiredNick.get();

    if (registered.getAsBoolean() && wanted != null && wanted.equalsIgnoreCase(current)) {
      log.info("[TafNickRejectedListener] already on '{}', ignoring a stale rejection", current);
      return;
    }

    String attempted = this.getClient().getRequestedNick();
    NickRejectedEvent rejected =
        new NickRejectedEvent(this.getClient(), event.getSource(), attempted, attempted + '`');
    this.fire(rejected);
    log.debug("[TafNickRejectedListener] '{}' rejected, falling back to '{}'",
        attempted, rejected.getNewNick());
    this.getClient().sendNickChange(rejected.getNewNick());
  }
}
