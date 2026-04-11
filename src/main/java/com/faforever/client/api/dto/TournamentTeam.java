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
@Type("tournamentTeam")
public class TournamentTeam {
  @Id
  private String id;
  private String name;
  private Integer seed;

  @Relationship("captain")
  private Player captain;

  @Relationship("members")
  private java.util.List<TournamentTeamMember> members;
}
