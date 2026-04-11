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
@Type("tournamentTeamMember")
public class TournamentTeamMember {
  @Id
  private String id;

  @Relationship("team")
  private TournamentTeam team;

  @Relationship("player")
  private Player player;
}
