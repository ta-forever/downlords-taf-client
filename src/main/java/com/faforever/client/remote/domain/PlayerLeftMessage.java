package com.faforever.client.remote.domain;

import java.util.List;

public class PlayerLeftMessage extends FafServerMessage {

  private List<Player> players;

  public PlayerLeftMessage() {
    super(FafServerMessageType.PLAYER_LEFT);
  }

  public List<Player> getPlayers() { return players; }
  public void setPlayers(List<Player> players) { this.players = players; }
}
