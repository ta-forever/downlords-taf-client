package com.faforever.client.main.event;

import com.faforever.client.game.Game;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HostGameEvent extends OpenCustomGamesEvent {
  final private String mapFolderName;
  private Game contextGame;

  public HostGameEvent setContextGame(Game game) {
    contextGame = game;
    return this;
  }
}
