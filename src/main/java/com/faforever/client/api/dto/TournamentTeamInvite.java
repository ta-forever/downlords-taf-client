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
@Type("tournamentTeamInvite")
public class TournamentTeamInvite {
  @Id
  private String id;
  private String state;

  @Relationship("tournament")
  private Tournament tournament;

  @Relationship("team")
  private TournamentTeam team;

  @Relationship("inviter")
  private Player inviter;

  @Relationship("invitee")
  private Player invitee;
}
