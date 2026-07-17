package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A market settled or voided (WAGER_DESIGN.md §14). Pushed to the game's spectators and to
 * any paid-out player. {@code payoutLp} is <em>this</em> player's payout (0 if they didn't
 * win / weren't in the market); {@code status} is SETTLED or VOIDED. Lets the client
 * auto-refresh the portfolio and cue a win without a manual refresh.
 */
@Getter
@Setter
public class WagerSettledMessage extends FafServerMessage {

  private int gameId;
  private long marketId;
  private String marketType;
  private String status;
  private List<String> winningOutcomeKeys;
  private int payoutLp;

  public WagerSettledMessage() {
    super(FafServerMessageType.WAGER_SETTLED);
  }
}
