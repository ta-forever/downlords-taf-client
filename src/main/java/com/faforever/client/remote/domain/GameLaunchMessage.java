package com.faforever.client.remote.domain;

import com.faforever.client.fa.relay.LobbyMode;
import com.faforever.client.game.Faction;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GameLaunchMessage extends FafServerMessage {

  /**
   * Stores game launch arguments, like "/ratingcolor d8d8d8d8", "/numgames 236".
   */
  private List<String> args;
  private int uid;
  private String mod;
  private String mapname;
  private String mapCrc;
  private String mapArchive;
  @NonNull
  private String name;
  private Integer expectedPlayers;
  private Integer team;
  private Integer mapPosition;
  private Faction faction;
  private LobbyMode initMode;
  private String ratingType;
  /** IRC channel the server assigns to this game's chat, mirroring
   *  {@link GameInfoMessage#getChatChannel()}. Delivered in the host/join launch
   *  response so the client can join the correct (server-decided) channel without
   *  inferring it from the possibly-rewritten title. Null on older servers. */
  private String chatChannel;

  public GameLaunchMessage() {
    super(FafServerMessageType.GAME_LAUNCH);
  }
}
