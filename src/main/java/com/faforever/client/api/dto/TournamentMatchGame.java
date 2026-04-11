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
@Type("tournamentMatchGame")
public class TournamentMatchGame {
  @Id
  private String id;
  private int gameNumber;

  @Relationship("match")
  private TournamentMatch match;

  @Relationship("game")
  private Game game;

  @Relationship("winner")
  private Player winner;
}
