package com.faforever.client.remote.domain;

import com.faforever.client.game.GameVisibility;
import com.faforever.client.library.LibraryGameInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * Data sent from the client to the FAF server to tell it about a preferences to be hosted.
 */
@Getter
@Setter
public class HostGameMessage extends ClientMessage {

  private String mapname;
  private String title;
  private String mod;
  private boolean[] options;
  private GameAccess access;
  private String modVersion;
  private String password;
  private GameVisibility visibility;
  private Integer ratingMin;
  private Integer ratingMax;
  private Boolean enforceRatingRange;
  private Integer replayDelaySeconds;
  private String ratingType;
  private String galacticWarPlanetName;
  private Integer maxPlayers;
  /** "cavedog" or "oa"; null from clients that predate engine reporting. */
  private String engine;
  private String engineVersion;
  /** Library mod game fields (featured mod "oalibrary" only); null for every other game. */
  private String libraryModId;
  private String libraryModName;
  private String libraryRegistry;
  private String libraryModVersion;
  private Integer libraryModRevision;
  private String librarySimHash;
  private String libraryBaseGame;
  private java.util.List<String> libraryExpansions;

  public HostGameMessage(GameAccess gameAccess, String mapName, String title, boolean[] options, String mod,
                         String password, String modVersion, GameVisibility gameVisibility, Integer ratingMin,
                         Integer ratingMax, Boolean enforceRatingRange, Integer replayDelaySeconds, String ratingType,
                         String galacticWarPlanetName, Integer maxPlayers, String engine, String engineVersion) {
    super(ClientMessageType.HOST_GAME);
    access = gameAccess;
    this.mapname = mapName;
    this.title = title;
    this.options = options;
    this.mod = mod;
    this.password = password;
    this.modVersion = modVersion;
    this.visibility = gameVisibility;
    this.ratingMin = ratingMin;
    this.ratingMax = ratingMax;
    this.enforceRatingRange = enforceRatingRange;
    this.replayDelaySeconds = replayDelaySeconds;
    this.ratingType = ratingType;
    this.galacticWarPlanetName = galacticWarPlanetName;
    this.maxPlayers = maxPlayers;
    this.engine = engine;
    this.engineVersion = engineVersion;
  }

  public void setLibrary(LibraryGameInfo library) {
    if (library == null) {
      return;
    }
    libraryModId = library.modId();
    libraryModName = library.name();
    libraryRegistry = library.registry();
    libraryModVersion = library.version();
    libraryModRevision = library.revision();
    librarySimHash = library.simHash();
    libraryBaseGame = library.baseGame();
    libraryExpansions = library.expansions();
  }
}