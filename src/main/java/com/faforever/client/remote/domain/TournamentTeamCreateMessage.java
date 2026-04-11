package com.faforever.client.remote.domain;

public class TournamentTeamCreateMessage extends ClientMessage {
  private int tournamentId;
  private String name;

  public TournamentTeamCreateMessage(int tournamentId, String name) {
    super(ClientMessageType.TOURNAMENT_TEAM_CREATE);
    setTarget(MessageTarget.TOURNAMENT);
    this.tournamentId = tournamentId;
    this.name = name;
  }

  public int getTournamentId() { return tournamentId; }
  public String getName() { return name; }
}
