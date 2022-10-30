package com.faforever.client.game;

import com.faforever.client.mod.FeaturedMod;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewGameInfo {
  private String title;
  private String password;
  private FeaturedMod featuredMod;
  private String featuredModVersionKey; // git branch name when using GitLfsFeaturedModUpdater
  private String map;
  private Set<String> simMods;
  private GameVisibility gameVisibility;
  private Integer ratingMin;
  private Integer ratingMax;
  private Boolean enforceRatingRange;
  private Integer replayDelaySeconds; // or -ve to disable
  private String ratingType;
  private String galacticWarPlanetName; // or null
}
