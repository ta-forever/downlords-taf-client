package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(of = "gameId")
@Type("gwGameStats")
public class GwGameStats {
  @Id
  private String gameId;
  private String galaxy;
  private int iteration;
  private int planetId;

  @Relationship("game")
  private Game game;
}
