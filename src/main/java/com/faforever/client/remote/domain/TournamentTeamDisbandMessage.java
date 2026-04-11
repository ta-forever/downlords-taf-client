package com.faforever.client.remote.domain;

public class TournamentTeamDisbandMessage extends ClientMessage {
  private int teamId;

  public TournamentTeamDisbandMessage(int teamId) {
    super(ClientMessageType.TOURNAMENT_TEAM_DISBAND);
    setTarget(MessageTarget.TOURNAMENT);
    this.teamId = teamId;
  }

  public int getTeamId() { return teamId; }
}
