package com.faforever.client.ladder;

/** Posted on the EventBus after a player's chosen medal-as-avatar is changed (set or cleared), so
 * surfaces showing it (the chat user list) can refresh immediately rather than on the next rebind. */
public class FeaturedMedalChangedEvent {
  private final int playerId;

  public FeaturedMedalChangedEvent(int playerId) {
    this.playerId = playerId;
  }

  public int getPlayerId() {
    return playerId;
  }
}
