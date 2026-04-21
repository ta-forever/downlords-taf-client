package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Broadcast whenever the tournament service (re)schedules a noshow
 * timer for a match, most commonly after an inconclusive game end
 * (draw / UNKNOWN / ambiguous team outcome) where the 10-minute
 * toilet-break grace period shifts the deadline beyond the original
 * opened_at + noshow_timeout. The payload carries the authoritative
 * deadline so clients can update their countdown without a full
 * tournament refetch.
 */
@Getter
@Setter
public class TournamentTimerRestartedMessage extends FafServerMessage {

  private Integer tournamentId;
  private Integer matchId;
  /** Absolute deadline ("yyyy-MM-dd HH:mm:ss", server-naive UTC). */
  private String timesOutAt;

  public TournamentTimerRestartedMessage() {
    super(FafServerMessageType.TOURNAMENT_TIMER_RESTARTED);
  }
}
