package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Host-only push of mutable per-game state that doesn't belong in the
 * public game_info broadcast. Currently carries the join_requests queue
 * (players who clicked "Request access" after being denied entry to a
 * reserved-slots game).
 *
 * The server emits this on every dirty cycle for any reserved-slots game
 * the receiver is hosting. Clients diff against the last-seen set to
 * decide whether to surface a new persistent notification.
 */
@Getter
@Setter
@ToString(of = {"gameUid", "joinRequests"})
public class HostGameStateMessage extends FafServerMessage {

  private Integer gameUid;
  private List<JoinRequestEntry> joinRequests;

  public HostGameStateMessage() {
    super(FafServerMessageType.HOST_GAME_STATE);
  }

  @Getter
  @Setter
  @ToString
  public static class JoinRequestEntry {
    private Integer playerId;
    private String playerLogin;
    private Double requestedAt;
  }
}
