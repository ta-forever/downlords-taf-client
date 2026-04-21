package com.faforever.client.remote.domain;

public class TournamentCancelMessage extends ClientMessage {
  private int tournamentId;

  public TournamentCancelMessage(int tournamentId) {
    super(ClientMessageType.TOURNAMENT_CANCEL);
    setTarget(MessageTarget.TOURNAMENT);
    this.tournamentId = tournamentId;
  }

  public int getTournamentId() { return tournamentId; }
}
