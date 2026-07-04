package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

/** One tradeable outcome of a {@link WagerMarket} (read-only). {@code lastPrice} is the
 * last implied probability in [0,1]; multiply by the share payout for a 0..100 display. */
@Getter
@Setter
@Type("wagerOutcome")
public class WagerOutcome {
  @Id
  private String id;
  private String outcomeKey;
  private String label;
  private double quantity;
  private Double lastPrice;
  private Boolean isWinner;

  @Relationship("market")
  private WagerMarket market;
}
