package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Type("league")
public class League {
  @Id
  private String id;
  private String name;
  private String description;

  @Relationship("leaderboard")
  private Leaderboard leaderboard;
}
