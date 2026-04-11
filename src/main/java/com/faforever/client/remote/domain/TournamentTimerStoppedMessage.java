package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Broadcast when a game goes live and the noshow timer is cancelled.
 * Clients should stop the visual forfeit countdown for the specified match.
 */
@Getter
@Setter
public class TournamentTimerStoppedMessage extends FafServerMessage {

  private Integer tournamentId;
  private Integer matchId;

  public TournamentTimerStoppedMessage() {
    super(FafServerMessageType.TOURNAMENT_TIMER_STOPPED);
  }
}
