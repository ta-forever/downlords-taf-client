package com.faforever.client.remote.domain;

public class TournamentCheckInMessage extends ClientMessage {
  private int tournamentId;

  public TournamentCheckInMessage(int tournamentId) {
    super(ClientMessageType.TOURNAMENT_CHECK_IN);
    setTarget(MessageTarget.TOURNAMENT);
    this.tournamentId = tournamentId;
  }

  public int getTournamentId() { return tournamentId; }
}
