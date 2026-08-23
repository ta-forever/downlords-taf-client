package com.faforever.client.api.dto;

import lombok.Value;

import java.util.List;

@Value
public class ReplayMeta {
  Integer gameId;
  String unitsHash;
  Integer taVersionMajor;
  Integer taVersionMinor;
  Boolean cheatsEnabled;
  Integer permLosByte;
  String mapName;
  String taMapHash;
  /**
   * In-game player records, in the demo compiler's "locked in" order. The canonical replay file
   * name (see {@code CanonicalReplayName}) joins these names in exactly this order, so don't sort
   * them. Note these are the in-game aliases from the TA player-status packet, not TAF logins.
   */
  List<ReplayMetaPlayer> players;

  @Value
  public static class ReplayMetaPlayer {
    String name;
    Integer side;
    Integer number;
    /** DirectPlay id as fixed-width hex, as written by the demo compiler. */
    String dpid;
  }
}
