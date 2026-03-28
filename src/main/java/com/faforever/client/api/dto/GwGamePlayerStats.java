package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(of = "gamePlayerStatsId")
@Type("gwGamePlayerStats")
public class GwGamePlayerStats {
  @Id
  private String gamePlayerStatsId;
  private String gwFaction;
  private int gwRank;

  @Relationship("gamePlayerStats")
  private GamePlayerStats gamePlayerStats;
}
