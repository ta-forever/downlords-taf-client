package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

/** A medal awarded for a game. {@code medalCode} is a stable key; display name/icon are i18n. */
@Getter
@Setter
@Type("gameMedal")
public class GameMedal {
  @Id
  private String id;
  private int gameId;
  private String medalCode;
  private Integer season;
  private Float value;

  @Relationship("player")
  private Player player;

  @Relationship("leaderboard")
  private Leaderboard leaderboard;
}
