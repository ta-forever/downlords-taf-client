package com.faforever.client.remote.domain;

public class TournamentStartMessage extends ClientMessage {
  private int tournamentId;

  public TournamentStartMessage(int tournamentId) {
    super(ClientMessageType.TOURNAMENT_START);
    setTarget(MessageTarget.TOURNAMENT);
    this.tournamentId = tournamentId;
  }

  public int getTournamentId() { return tournamentId; }
}
