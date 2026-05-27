package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString(of = {"uid", "title", "state"})
public class GameInfoMessage extends FafServerMessage {

  private String host;
  private Boolean passwordProtected;
  // TODO use enum
  private String visibility;
  private GameStatus state;
  private Integer numPlayers;
  private Map<String, List<String>> teams;
  private Map<Integer, List<List<Integer>>> pings;  // key=playerid(measuring), value=list of [playerid(measured), ping]
  private String featuredMod;
  private String featuredModVersion;
  private Integer uid;
  private Integer maxPlayers;
  private String title;
  private Map<String, String> simMods;
  private String mapName;
  private String mapFilePath;
  private Double launchedAt;
  private String ratingType;
  private Integer ratingMin;
  private Integer ratingMax;
  private Boolean enforceRatingRange;
  private Integer replayDelaySeconds;
  private GameType gameType;
  private String galacticWarPlanetName;
  private Boolean reservedSlotsEnabled;
  private List<String> reservedPlayers;
  /** Parallel to {@link #reservedPlayers}. Sent so the host's edit-list popup
   *  can seed itself with the current selection even for players who aren't
   *  in PlayerService (offline / never seen by this client). */
  private List<Integer> reservedPlayerIds;
  /**
   * The server may either send a single game or a list of games in the same message... *cringe*.
   */
  private List<GameInfoMessage> games;

  public GameInfoMessage() {
    super(FafServerMessageType.GAME_INFO);
  }
}
