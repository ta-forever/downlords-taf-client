package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Server tells the player they need to check in for a tournament. Pushed
 * once per signed-up player when the tournament transitions PENDING -&gt;
 * CHECK_IN, and re-pushed for the same (player, tournament) pair on the
 * player's next login if they still owe a check-in. Idempotent on the
 * client: subsequent receipts for an already-checked-in tournament are
 * harmless because the in-page state shows the player's
 * {@code checkedInAt} and any stale notification's "Check in" action
 * is a no-op server-side.
 */
@Getter
@Setter
public class TournamentCheckInRequiredMessage extends FafServerMessage {

  private Integer tournamentId;

  public TournamentCheckInRequiredMessage() {
    super(FafServerMessageType.TOURNAMENT_CHECK_IN_REQUIRED);
  }
}
