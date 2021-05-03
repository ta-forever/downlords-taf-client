package com.faforever.client.player.event;

import com.faforever.client.game.Game;
import com.faforever.client.player.Player;

public class PlayerJoinedGameEvent {
  private Player player;
  private Game game;

  public PlayerJoinedGameEvent(Player player, Game game) {
    this.player = player;
    this.game = game;
  }

  public Player getPlayer() {
    return player;
  }


  public Game getGame() {
    return game;
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof PlayerJoinedGameEvent)
        && ((PlayerJoinedGameEvent) obj).player.equals(player)
        && ((PlayerJoinedGameEvent) obj).game.equals(game);
  }
}
