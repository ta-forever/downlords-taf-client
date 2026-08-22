package com.faforever.client.chat;

import lombok.extern.slf4j.Slf4j;
import net.engio.mbassy.listener.Handler;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.defaults.listener.AbstractDefaultListenerBase;
import org.kitteh.irc.client.library.element.ServerMessage;
import org.kitteh.irc.client.library.element.User;
import org.kitteh.irc.client.library.event.client.ClientReceiveCommandEvent;
import org.kitteh.irc.client.library.event.user.UserNickChangeEvent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.kitteh.irc.client.library.feature.filter.CommandFilter;

import java.util.Optional;

/**
 * Replaces Kitteh's {@code DefaultNickListener}, which cannot read a rename on this network.
 *
 * <p>taforever's ircd announces renames with a bare-nick prefix — {@code :Nick` NICK :Nick},
 * no {@code !user@host}. Kitteh's version opens with
 * {@code if (!(event.getActor() instanceof User)) { trackException(...); return; }}, so it
 * throws {@code NICK message sent for non-user} and returns without ever calling
 * {@code setCurrentNick}. The consequence is that {@link Client#getNick()} reports the nick we
 * *asked for* rather than the one we hold, permanently, for the life of the connection — and no
 * {@link UserNickChangeEvent} is fired for anyone, so every client's user list keeps the old
 * name. Both were observed in production on 2026-08-22 (identical code in Kitteh 9.0.0).
 *
 * <p>This version takes the old nick from the prefix whatever its shape, updates the client's
 * own nick when the rename is ours, and still fires {@link UserNickChangeEvent} for tracked
 * users so the rest of the library behaves normally.
 */
@Slf4j
public class TafNickListener extends AbstractDefaultListenerBase {

  public TafNickListener(Client.@NonNull WithManagement client) {
    super(client);
  }

  @CommandFilter("NICK")
  @Handler(priority = Integer.MAX_VALUE - 1)
  public void nick(ClientReceiveCommandEvent event) {
    if (event.getParameters().size() < 1) {
      this.trackException(event, "NICK message too short");
      return;
    }
    applyNickChange(event.getActor().getName(), event.getParameters().get(0), event.getSource());
  }

  /**
   * Package-private so it can be driven directly in tests: {@code getActor()} is final in
   * Kitteh's {@code ActorEventBase}, so the event itself cannot be stubbed.
   *
   * @param actorName the prefix as sent, bare nick or {@code nick!user@host}
   */
  void applyNickChange(String actorName, String newNick, ServerMessage source) {
    String oldNick = actorName;
    int bang = oldNick.indexOf('!');
    if (bang > 0) {
      oldNick = oldNick.substring(0, bang);
    }
    boolean isSelf = oldNick.equalsIgnoreCase(this.getClient().getNick());

    Optional<User> tracked = this.getTracker().getTrackedUser(oldNick);
    if (tracked.isPresent()) {
      User oldUser = tracked.get();
      this.getTracker().trackUserNickChange(oldNick, newNick);
      this.getTracker().getTrackedUser(newNick).ifPresent(newUser ->
          this.fire(new UserNickChangeEvent(this.getClient(), source, oldUser, newUser)));
    }

    if (isSelf) {
      log.info("[TafNickListener] our nick changed {} -> {}", oldNick, newNick);
      this.getClient().setCurrentNick(newNick);
    }
  }
}
