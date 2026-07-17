package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** One buy/sell in the append-only trade log (read-only); backs price sparklines
 * (filter by outcomeId, order by createdAt) and trade history. */
@Getter
@Setter
@Type("wagerTrade")
public class WagerTrade {
  @Id
  private String id;
  private long outcomeId;
  private int userId;
  private double deltaShares;
  private int costLp;
  private int feeLp;
  private double priceAfter;
  private OffsetDateTime createdAt;

  @Relationship("market")
  private WagerMarket market;
}
