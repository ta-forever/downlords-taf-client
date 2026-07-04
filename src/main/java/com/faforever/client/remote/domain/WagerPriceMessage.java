package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A price tick pushed to a game's subscribers after any trade (WAGER_DESIGN.md §14). Carries
 * the full post-trade price vector for one market plus the last trade (the sparkline point).
 */
@Getter
@Setter
public class WagerPriceMessage extends FafServerMessage {

  private long marketId;
  private List<PriceInfo> outcomes;
  private LastTrade lastTrade;

  public WagerPriceMessage() {
    super(FafServerMessageType.WAGER_PRICE);
  }

  @Getter
  @Setter
  public static class PriceInfo {
    private String outcomeKey;
    private double price;
  }

  @Getter
  @Setter
  public static class LastTrade {
    private String outcomeKey;
    private double deltaShares;
    private double priceAfter;
  }
}
