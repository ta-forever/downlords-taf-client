package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Type("tournamentMatchPlannedMap")
public class TournamentMatchPlannedMap {
  @Id
  private String id;
  private int gameNumber;

  @Relationship("mapVersion")
  private MapVersion mapVersion;

  @Relationship("match")
  private TournamentMatch match;
}
