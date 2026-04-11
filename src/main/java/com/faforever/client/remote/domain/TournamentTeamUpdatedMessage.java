package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Broadcast when a team's roster, seed, or invite state changes.
 * The client should reload teams for the named tournament.
 */
@Getter
@Setter
public class TournamentTeamUpdatedMessage extends FafServerMessage {

  private Integer tournamentId;
  private Integer teamId;

  public TournamentTeamUpdatedMessage() {
    super(FafServerMessageType.TOURNAMENT_TEAM_UPDATED);
  }
}
