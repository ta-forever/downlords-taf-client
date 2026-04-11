package com.faforever.client.remote.domain;

public class TournamentTeamRemoveMemberMessage extends ClientMessage {
  private int teamId;
  private int targetPlayerId;

  public TournamentTeamRemoveMemberMessage(int teamId, int targetPlayerId) {
    super(ClientMessageType.TOURNAMENT_TEAM_REMOVE_MEMBER);
    setTarget(MessageTarget.TOURNAMENT);
    this.teamId = teamId;
    this.targetPlayerId = targetPlayerId;
  }

  public int getTeamId() { return teamId; }
  public int getTargetPlayerId() { return targetPlayerId; }
}
