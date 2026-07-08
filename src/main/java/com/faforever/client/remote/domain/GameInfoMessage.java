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
  /** Host toggle for start-position preselection. When false the position
   *  picker is hidden on every client and the game uses TA's random starts. */
  private Boolean fixedPositionsEnabled;
  private List<String> reservedPlayers;
  /** Parallel to {@link #reservedPlayers}. Sent so the host's edit-list popup
   *  can seed itself with the current selection even for players who aren't
   *  in PlayerService (offline / never seen by this client). */
  private List<Integer> reservedPlayerIds;
  /** Host's manual +autoteam team pins, parallel lists: player id at index i is
   *  pinned to team {@link #pinnedTeams}[i] (0 = Team 1, 1 = Team 2). Broadcast
   *  so every client can show which players the host has pinned. */
  private List<Integer> pinnedPlayerIds;
  private List<Integer> pinnedTeams;
  /** Players' start-position role requests, parallel lists in request order:
   *  player id at index i requests role {@link #positionRequests}[i] (0..4, a
   *  pair of mirrored map start positions, one per team). Order matters — the
   *  host's client resolves same-role ties first-come-first-served. */
  private List<Integer> positionRequestPlayerIds;
  private List<Integer> positionRequests;
  /**
   * The server may either send a single game or a list of games in the same message... *cringe*.
   */
  private List<GameInfoMessage> games;

  public GameInfoMessage() {
    super(FafServerMessageType.GAME_INFO);
  }
}
