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
@Type("tournamentParticipant")
public class TournamentParticipant {
  @Id
  private String id;
  private Integer rating;

  @Relationship("player")
  private Player player;

  @Relationship("tournament")
  private Tournament tournament;
}
