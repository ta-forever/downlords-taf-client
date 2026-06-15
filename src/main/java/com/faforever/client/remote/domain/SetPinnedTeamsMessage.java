package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Host -&gt; server: the host's manual +autoteam team pins. {@link #playerIds} and
 * {@link #teams} are parallel lists (player id at index i is pinned to team
 * {@code teams[i]}, 0 = Team 1, 1 = Team 2). The server stores these and
 * rebroadcasts them in GAME_INFO so every client can show which players the host
 * has pinned. An empty list clears the pins.
 */
@Getter
@Setter
public class SetPinnedTeamsMessage extends ClientMessage {

  private List<Integer> playerIds;
  private List<Integer> teams;

  public SetPinnedTeamsMessage(List<Integer> playerIds, List<Integer> teams) {
    super(ClientMessageType.SET_PINNED_TEAMS);
    this.playerIds = playerIds;
    this.teams = teams;
  }
}
