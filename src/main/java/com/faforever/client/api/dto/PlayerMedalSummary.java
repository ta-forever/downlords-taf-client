package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

/** Per-player medal counts grouped by season/board/medal (backs the medal case). */
@Getter
@Setter
@Type("playerMedalSummary")
public class PlayerMedalSummary {
  @Id
  private String id;
  private Integer season;
  private Integer leaderboardId;
  private String medalCode;
  private long cnt;

  @Relationship("player")
  private Player player;
}
