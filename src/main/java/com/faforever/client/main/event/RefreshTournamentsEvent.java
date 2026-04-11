package com.faforever.client.main.event;

import lombok.Getter;

/**
 * Posted on the EventBus when the tournament service signals (via a NoticeMessage
 * with non-null tournament_id) that tournament data has changed and the player
 * client should reload the affected tournament. The TournamentsController
 * subscribes and reloads (with debouncing).
 *
 * tournamentId is informational — currently the controller does a full
 * loadTournaments() which preserves the user's selection. Future fine-grained
 * refresh could fetch only the affected tournament by id.
 */
@Getter
public class RefreshTournamentsEvent {
  private final int tournamentId;
  private final boolean selectAfterRefresh;

  public RefreshTournamentsEvent(int tournamentId) {
    this(tournamentId, false);
  }

  public RefreshTournamentsEvent(int tournamentId, boolean selectAfterRefresh) {
    this.tournamentId = tournamentId;
    this.selectAfterRefresh = selectAfterRefresh;
  }
}
