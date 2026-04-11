package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Targeted notification: a team has invited the receiving player to join.
 * The receiving client should surface this in the tournament view as a
 * pending-invite item the player can accept or decline.
 */
@Getter
@Setter
public class TournamentTeamInviteReceivedMessage extends FafServerMessage {

  private Integer inviteId;
  private Integer tournamentId;
  private Integer teamId;
  private String teamName;
  private Integer inviterId;
  private String inviterName;
  private String expiresAt;

  public TournamentTeamInviteReceivedMessage() {
    super(FafServerMessageType.TOURNAMENT_TEAM_INVITE_RECEIVED);
  }
}
