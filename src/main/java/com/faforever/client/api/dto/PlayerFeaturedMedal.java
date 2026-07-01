package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

/**
 * The one medal a player chose to display next to their name (CL-7) — the LP analogue of the
 * selected avatar. Read by anyone; created/updated only by the owner (server enforces via
 * IsEntityOwner). {@code medalCode} is a stable code, or null to display none.
 */
@Getter
@Setter
@Type("playerFeaturedMedal")
public class PlayerFeaturedMedal {
  @Id
  private String id;
  private String medalCode;

  @Relationship("player")
  private Player player;
}
