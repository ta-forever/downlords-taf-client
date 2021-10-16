package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Type("leaderboardRating")
public class LeaderboardEntry {
  @Id
  private String id;
  private Double mean;
  private Double deviation;
  private Integer totalGames;
  private Integer wonGames;
  private Integer lostGames;
  private Integer drawnGames;
  private Double rating;
  private OffsetDateTime createTime;
  private OffsetDateTime updateTime;
  private Integer streak;
  private String recentScores;
  private String recentMod;

  @Relationship("player")
  private Player player;

  @Relationship("leaderboard")
  private Leaderboard leaderboard;
}
