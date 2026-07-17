package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

/** A trader's net holding in one outcome (read-only; backed by the wager_position_view).
 * Live P&amp;L ≈ shares * lastPrice * payoutUnit − costLp. */
@Getter
@Setter
@Type("wagerPosition")
public class WagerPosition {
  @Id
  private String id;
  private int userId;
  private long marketId;
  private double shares;
  private int costLp;
  private Double lastPrice;
  private Boolean isWinner;
  private String marketStatus;

  @Relationship("player")
  private Player player;

  @Relationship("outcome")
  private WagerOutcome outcome;
}
