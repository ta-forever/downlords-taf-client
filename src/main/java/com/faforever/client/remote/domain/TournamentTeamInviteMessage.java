package com.faforever.client.remote.domain;

public class TournamentTeamInviteMessage extends ClientMessage {
  private int teamId;
  private int inviteeId;

  public TournamentTeamInviteMessage(int teamId, int inviteeId) {
    super(ClientMessageType.TOURNAMENT_TEAM_INVITE);
    setTarget(MessageTarget.TOURNAMENT);
    this.teamId = teamId;
    this.inviteeId = inviteeId;
  }

  public int getTeamId() { return teamId; }
  public int getInviteeId() { return inviteeId; }
}
