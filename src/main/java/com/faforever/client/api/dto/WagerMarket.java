package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/** A live-game LMSR wagering market (read-only; wager_service/WAGER_DESIGN.md §3, §9). */
@Getter
@Setter
@Type("wagerMarket")
public class WagerMarket {
  @Id
  private String id;
  private int gameId;
  private String marketType;
  private String medalCode;
  private boolean exclusive;
  private String ratingType;
  private double liquidity;
  private int feeBps;
  private int subsidyLp;
  private String status;
  private String voidReason;
  private OffsetDateTime openedAt;
  private OffsetDateTime closesAt;
  private OffsetDateTime settledAt;

  @Relationship("outcomes")
  private List<WagerOutcome> outcomes;

  @Relationship("league")
  private League league;

  @Relationship("season")
  private LeagueSchedule season;
}
