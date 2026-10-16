package com.faforever.client.game;

import com.faforever.client.library.LibraryGameInfo;
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
  private Integer maxPlayers;
  private LibraryGameInfo library; // Library mod games only, else null

  /** Everything but the Library fields, for games that are not Library mod games. */
  public NewGameInfo(String title, String password, FeaturedMod featuredMod, String featuredModVersionKey, String map,
                     Set<String> simMods, GameVisibility gameVisibility, Integer ratingMin, Integer ratingMax,
                     Boolean enforceRatingRange, Integer replayDelaySeconds, String ratingType,
                     String galacticWarPlanetName, Integer maxPlayers) {
    this(title, password, featuredMod, featuredModVersionKey, map, simMods, gameVisibility, ratingMin, ratingMax,
        enforceRatingRange, replayDelaySeconds, ratingType, galacticWarPlanetName, maxPlayers, null);
  }
}
