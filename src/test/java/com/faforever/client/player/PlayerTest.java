package com.faforever.client.player;

import com.faforever.client.game.Game;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.remote.domain.PlayerStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertSame;


@RunWith(MockitoJUnitRunner.class)
public class PlayerTest {
  private Player instance;

  @Before
  public void setUp() throws Exception {
    instance = new Player("junit");
  }

  @Test
  public void testStateChangesOnGameBeingSet() {
    List<List<Object>> callParameters = Arrays.asList(
        Arrays.asList(GameStatus.STAGING, PlayerStatus.HOSTING, "junit"),
        Arrays.asList(GameStatus.STAGING, PlayerStatus.JOINING, "whatever"),
        Arrays.asList(GameStatus.LIVE, PlayerStatus.PLAYING, "junit"),
        Arrays.asList(GameStatus.ENDED, PlayerStatus.IDLE, "junit"),
        Arrays.asList(null, PlayerStatus.IDLE, "junit")
    );

    callParameters.forEach(objects -> checkStatusSetCorrectly(
        (GameStatus) objects.get(0), (PlayerStatus) objects.get(1), (String) objects.get(2))
    );

  }

  private void checkStatusSetCorrectly(GameStatus gameStatus, PlayerStatus playerStatus, String playerName) {
    Game game = null;
    if (gameStatus != null) {
      game = new Game();
      game.setStatus(gameStatus);
      game.setHost(playerName);
    }
    instance.setGame(game);
    assertSame(instance.getStatus(), playerStatus);
  }

  @Test
  public void testPlayerStateChangedOnGameStatusChanged() {
    Game game = new Game();
    game.setStatus(GameStatus.LIVE);
    instance.setGame(game);
    assertSame(instance.getStatus(), PlayerStatus.PLAYING);
    game.setStatus(GameStatus.ENDED);
    instance.setGame(null);
    assertSame(instance.getStatus(), PlayerStatus.IDLE);
  }
}