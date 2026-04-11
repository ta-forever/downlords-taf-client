package com.faforever.client.remote.domain;

public class TournamentTeamDeclineInviteMessage extends ClientMessage {
  private int inviteId;

  public TournamentTeamDeclineInviteMessage(int inviteId) {
    super(ClientMessageType.TOURNAMENT_TEAM_DECLINE_INVITE);
    setTarget(MessageTarget.TOURNAMENT);
    this.inviteId = inviteId;
  }

  public int getInviteId() { return inviteId; }
}
