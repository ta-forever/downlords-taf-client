package com.faforever.client.remote.domain;

public class TournamentWithdrawMessage extends ClientMessage {
  private int tournamentId;

  public TournamentWithdrawMessage(int tournamentId) {
    super(ClientMessageType.TOURNAMENT_WITHDRAW);
    setTarget(MessageTarget.TOURNAMENT);
    this.tournamentId = tournamentId;
  }

  public int getTournamentId() { return tournamentId; }
}
