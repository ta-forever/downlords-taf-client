package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

/** Per-game Ladder Points award + breakdown (backs the replay card + Combat Score → LP chain). */
@Getter
@Setter
@Type("ladderPointsJournal")
public class LadderPointsJournal {
  @Id
  private String id;
  private int gameId;
  private int scoreBefore;
  private int lpAwarded;
  private int combatBase;
  private int winBonus;
  private int upsetBonus;
  private int scoreAfter;
  private Integer destroyedValue;
  private Float dvRatio;
  private int season;
  private java.time.OffsetDateTime createTime;

  @Relationship("player")
  private Player player;

  @Relationship("league")
  private League league;
}
