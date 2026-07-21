package com.faforever.client.remote.domain;

import com.faforever.client.remote.gson.LenientBooleanTypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Snapshot of a game's wager markets, sent on subscribe (WAGER_DESIGN.md §14). Prices are
 * implied probabilities in [0,1] (multiply by the share payout for a 0..100 display).
 */
@Getter
@Setter
public class WagerMarketsMessage extends FafServerMessage {

  private int gameId;
  private List<MarketInfo> markets;

  public WagerMarketsMessage() {
    super(FafServerMessageType.WAGER_MARKETS);
  }

  @Getter
  @Setter
  public static class MarketInfo {
    private long marketId;
    private String marketType;
    private String medalCode;
    private String status;
    /** LMSR liquidity parameter (shares). With the live price, gives exact trade costs. */
    private double b;
    /** Trade fee in basis points, frozen at open — lets the LP preview include the fee. */
    private int feeBps;
    private List<OutcomeInfo> outcomes;
  }

  @Getter
  @Setter
  public static class OutcomeInfo {
    private long outcomeId;
    private String outcomeKey;
    private String label;
    private double price;
    private double qShares;
    /** Server sends this as a MySQL {@code TINYINT} (0/1), hence the lenient adapter. */
    @JsonAdapter(LenientBooleanTypeAdapter.class)
    private Boolean isWinner;
  }
}
