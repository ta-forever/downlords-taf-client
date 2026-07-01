package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Type("ladderPoints")
public class LadderPoints {
  @Id
  private String id;
  private int score;
  private int games;
  private int wins;
  private int draws;
  private int losses;
  private int currentStreak;
  private int bestStreak;
  private String recentResults;

  @Relationship("player")
  private Player player;

  @Relationship("league")
  private League league;

  @Relationship("season")
  private LeagueSchedule season;
}
