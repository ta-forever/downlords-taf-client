package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Type("tournament")
public class Tournament {
  @Id
  private String id;
  private String name;
  private String description;
  private String state;
  private String format;
  private int playersPerSide;
  private int bestOf;
  private Integer minRating;
  private Integer maxRating;
  private int noshowTimeoutMinutes;
  private int checkInMinutes;
  private String formatOptions;
  private OffsetDateTime createdAt;
  private OffsetDateTime startedAt;
  private OffsetDateTime completedAt;
  // Auto-scheduled tournaments populate this with their *intended* start time;
  // startedAt is only set once the tournament actually transitions to underway.
  private OffsetDateTime scheduledStartAt;
  private String recurrenceCron;
  private Integer recurrenceIndex;
  private String mapVisibility;

  @Relationship("participants")
  private List<TournamentParticipant> participants;

  @Relationship("matches")
  private List<TournamentMatch> matches;

  @Relationship("placements")
  private List<TournamentPlacement> placements;

  @Relationship("standings")
  private List<TournamentStanding> standings;

  @Relationship("featuredMod")
  private FeaturedMod featuredMod;

  @Relationship("leaderboard")
  private Leaderboard leaderboard;

  @Relationship("mapPool")
  private MapPool mapPool;

  @Relationship("teams")
  private List<TournamentTeam> teams;

  @Relationship("createdBy")
  private com.faforever.client.api.dto.Player createdBy;

  @Relationship("winnerAvatar")
  private Avatar winnerAvatar;

  @Relationship("secondPlaceAvatar")
  private Avatar secondPlaceAvatar;

  @Relationship("thirdPlaceAvatar")
  private Avatar thirdPlaceAvatar;
}
