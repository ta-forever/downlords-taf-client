package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Type("tournamentParticipant")
public class TournamentParticipant {
  @Id
  private String id;
  private Integer rating;
  /** Stamped when the player checks in for a check-in-window tournament.
   *  NULL means not (yet) checked in. */
  private OffsetDateTime checkedInAt;

  @Relationship("player")
  private Player player;

  @Relationship("tournament")
  private Tournament tournament;
}
