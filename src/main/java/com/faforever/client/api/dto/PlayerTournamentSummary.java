package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Hall of Fame row — aggregated tournament achievements for one (player,
 * featuredMod) pair. Backed by the {@code player_tournament_summary} SQL
 * view in faf-api. The synthetic id is {@code "{modId}-{playerId}"} for
 * per-mod rows and {@code "all-{playerId}"} for the across-all-mods rollup
 * (which has {@link #featuredMod} == null).
 */
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Type("playerTournamentSummary")
public class PlayerTournamentSummary {
  @Id
  private String id;
  private int firsts;
  private int seconds;
  private int thirds;
  private int participations;
  private OffsetDateTime lastTournamentAt;

  @Relationship("player")
  private Player player;

  /** Null on the across-all-mods rollup row. */
  @Relationship("featuredMod")
  private FeaturedMod featuredMod;
}
