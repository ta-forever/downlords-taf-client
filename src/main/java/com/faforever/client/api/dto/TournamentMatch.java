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
@Type("tournamentMatch")
public class TournamentMatch {
  @Id
  private String id;
  private int round;
  private int position;
  private String role;
  private boolean preview;
  private int player1Wins;
  private int player2Wins;
  private String state;
  private String openedAt;
  private String timesOutAt;

  @Relationship("player1")
  private Player player1;

  @Relationship("player2")
  private Player player2;

  @Relationship("winner")
  private Player winner;

  @Relationship("team1")
  private TournamentTeam team1;

  @Relationship("team2")
  private TournamentTeam team2;

  @Relationship("winnerTeam")
  private TournamentTeam winnerTeam;

  @Relationship("tournament")
  private Tournament tournament;

  @Relationship("plannedMaps")
  private java.util.List<TournamentMatchPlannedMap> plannedMaps;

  @Relationship("games")
  private java.util.List<TournamentMatchGame> games;
}
