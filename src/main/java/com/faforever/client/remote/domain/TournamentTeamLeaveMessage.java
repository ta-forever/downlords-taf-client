package com.faforever.client.remote.domain;

public class TournamentTeamLeaveMessage extends ClientMessage {
  private int teamId;

  public TournamentTeamLeaveMessage(int teamId) {
    super(ClientMessageType.TOURNAMENT_TEAM_LEAVE);
    setTarget(MessageTarget.TOURNAMENT);
    this.teamId = teamId;
  }

  public int getTeamId() { return teamId; }
}
