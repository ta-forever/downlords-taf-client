package com.faforever.client.game;

import com.faforever.client.leaderboard.Leaderboard;
import com.faforever.client.teammatchmaking.MatchmakingQueue;
import org.junit.Test;

import static com.faforever.client.leaderboard.LeaderboardService.DEFAULT_RATING_TYPE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression: the Update button of a staging Library game did nothing. The Library has no map pools, but the
 * ranked checkbox stays ticked (just disabled), so Update dereferenced the empty pool selection; the NPE was
 * thrown inside a CompletableFuture callback and swallowed, so neither the client nor the server saw anything.
 */
public class StagingUpdateRatingTypeTest {

  private static MatchmakingQueue pool(String technicalName) {
    MatchmakingQueue queue = mock(MatchmakingQueue.class);
    Leaderboard leaderboard = mock(Leaderboard.class);
    when(leaderboard.getTechnicalName()).thenReturn(technicalName);
    when(queue.getLeaderboard()).thenReturn(leaderboard);
    return queue;
  }

  @Test
  public void libraryGameWithTickedRankedBoxAndNoPoolIsUnrated() {
    assertEquals(DEFAULT_RATING_TYPE, CreateGameController.stagingRatingType(true, true, null));
  }

  @Test
  public void libraryGameIgnoresAPoolEvenIfOneIsSelected() {
    assertEquals(DEFAULT_RATING_TYPE, CreateGameController.stagingRatingType(true, true, pool("ladder1v1")));
  }

  @Test
  public void rankedTaGameUsesTheSelectedPool() {
    assertEquals("ladder1v1", CreateGameController.stagingRatingType(false, true, pool("ladder1v1")));
  }

  @Test
  public void unrankedTaGameIsGlobal() {
    assertEquals(DEFAULT_RATING_TYPE, CreateGameController.stagingRatingType(false, false, pool("ladder1v1")));
  }

  @Test
  public void rankedTaGameWithNoPoolSelectedFallsBackToGlobalInsteadOfFailing() {
    assertEquals(DEFAULT_RATING_TYPE, CreateGameController.stagingRatingType(false, true, null));
  }
}
