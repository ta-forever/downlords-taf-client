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
@Type("tournamentStanding")
public class TournamentStanding {
  @Id
  private String id;
  private int rank;
  private int wins;
  private int losses;
  private int opponentStrength;
  private int winStrength;

  @Relationship("player")
  private Player player;

  @Relationship("tournament")
  private Tournament tournament;
}
