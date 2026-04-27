package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Type("player")
public class Player {
  @Id
  private String id;
  private String login;
  private String userAgent;
  // Cumulative no-show reputation. Server is the only writer; clients
  // read these to display "X / Y" next to player names on signup lists.
  private int tournamentSignupCount;
  private int tournamentNoCheckInCount;
  private int tournamentMatchForfeitCount;

  @Deprecated
  @Relationship("globalRating")
  private GlobalRating globalRating;

  @Deprecated
  @Relationship("ladder1v1Rating")
  private Ladder1v1Rating ladder1v1Rating;

  @Relationship("names")
  private List<NameRecord> names;
}
