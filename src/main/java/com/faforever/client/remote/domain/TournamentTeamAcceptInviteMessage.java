package com.faforever.client.remote.domain;

public class TournamentTeamAcceptInviteMessage extends ClientMessage {
  private int inviteId;

  public TournamentTeamAcceptInviteMessage(int inviteId) {
    super(ClientMessageType.TOURNAMENT_TEAM_ACCEPT_INVITE);
    setTarget(MessageTarget.TOURNAMENT);
    this.inviteId = inviteId;
  }

  public int getInviteId() { return inviteId; }
}
