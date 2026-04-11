package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Notifies inviter and invitee when an invite leaves pending state
 * (accepted / declined / cancelled / expired).
 */
@Getter
@Setter
public class TournamentTeamInviteResolvedMessage extends FafServerMessage {

  private Integer inviteId;
  private Integer tournamentId;
  private Integer teamId;
  private Integer inviteeId;
  private String state;

  public TournamentTeamInviteResolvedMessage() {
    super(FafServerMessageType.TOURNAMENT_TEAM_INVITE_RESOLVED);
  }
}
