package com.faforever.client.game;

import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.remote.domain.GameType;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CustomGamesListingTest {

  private static Game openGame(String featuredMod, String mapArchive) {
    Game game = new Game();
    game.setGameType(GameType.CUSTOM);
    game.setStatus(GameStatus.STAGING);
    game.setReplayDelaySeconds(300);
    game.setNumPlayers(1);
    game.setFeaturedMod(featuredMod);
    game.setMapArchiveName(mapArchive);
    return game;
  }

  @Test
  public void libraryGameWithoutMapArchiveIsListed() {
    assertTrue(CustomGamesController.CUSTOM_GAMES_PREDICATE.test(openGame("oalibrary", null)));
  }

  @Test
  public void ordinaryGameWithoutMapArchiveIsStillHidden() {
    assertFalse(CustomGamesController.CUSTOM_GAMES_PREDICATE.test(openGame("tacc", null)));
  }

  @Test
  public void ordinaryGameWithMapArchiveIsListed() {
    assertTrue(CustomGamesController.CUSTOM_GAMES_PREDICATE.test(openGame("tacc", "totala2.hpi")));
  }
}
