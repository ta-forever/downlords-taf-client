package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-player per-game combat metrics (Combat Score #1 + medal inputs). {@code regime} tells
 * which fields are populated: generative = all; costfree = no destroyedValue/dvRatio; binary
 * has no row at all.
 */
@Getter
@Setter
@Type("gamePlayerMetrics")
public class GamePlayerMetrics {
  @Id
  private String id;
  private int gameId;
  private Byte team;
  private String regime;
  private Integer destroyedValue;
  private Integer lostValue;
  private Float dvRatio;
  private Integer econValue;
  private Integer taKills;
  private Integer taLosses;
  private Long dmgDealt;
  private Long lastActTick;
  private Long gameMaxTick;

  @Relationship("player")
  private Player player;

  @Relationship("leaderboard")
  private Leaderboard leaderboard;
}
