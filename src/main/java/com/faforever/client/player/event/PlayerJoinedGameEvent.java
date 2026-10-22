package com.faforever.client.player.event;

import com.faforever.client.game.Game;
import com.faforever.client.player.Player;

public class PlayerJoinedGameEvent {
  private Player player;
  private Game game;
  private boolean joinedCurrentPlayersGame;

  /**
   * @param joinedCurrentPlayersGame whether the current player is in {@code game}, i.e. someone joined our
   * lobby, as opposed to a friend joining some other lobby
   */
  public PlayerJoinedGameEvent(Player player, Game game, boolean joinedCurrentPlayersGame) {
    this.player = player;
    this.game = game;
    this.joinedCurrentPlayersGame = joinedCurrentPlayersGame;
  }

  public Player getPlayer() {
    return player;
  }


  public Game getGame() {
    return game;
  }

  public boolean isJoinedCurrentPlayersGame() {
    return joinedCurrentPlayersGame;
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof PlayerJoinedGameEvent)
        && ((PlayerJoinedGameEvent) obj).player.equals(player)
        && ((PlayerJoinedGameEvent) obj).game.equals(game)
        && ((PlayerJoinedGameEvent) obj).joinedCurrentPlayersGame == joinedCurrentPlayersGame;
  }
}
