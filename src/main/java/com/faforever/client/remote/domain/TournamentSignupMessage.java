package com.faforever.client.remote.domain;

public class TournamentSignupMessage extends ClientMessage {
  private int tournamentId;

  public TournamentSignupMessage(int tournamentId) {
    super(ClientMessageType.TOURNAMENT_SIGNUP);
    setTarget(MessageTarget.TOURNAMENT);
    this.tournamentId = tournamentId;
  }

  public int getTournamentId() { return tournamentId; }
}
