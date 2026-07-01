package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Type("leagueSchedule")
public class LeagueSchedule {
  @Id
  private String id;
  private OffsetDateTime timeframeFrom;
  private OffsetDateTime timeframeTo;
  private String description;

  @Relationship("league")
  private League league;
}
