package com.faforever.client.game;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class StagingConsoleCommandsTest {

  @Test
  public void taGameGetsMapRatingAndSlotCommands() {
    List<String> commands = GameService.stagingConsoleCommands(false, "details", "ladder1v1", LiveReplayOption.ZERO_DELAY, 6);
    assertEquals(List.of("/map details", "/rating_type ladder1v1", "/enable_game_file_version_verify",
        "/replay_delay_seconds 0", "/max_players 6"), commands);
  }

  @Test
  public void libraryGameGetsNoTaLobbyCommands() {
    List<String> commands = GameService.stagingConsoleCommands(true, "details", "ladder1v1", LiveReplayOption.ZERO_DELAY, 6);
    assertEquals(List.of("/replay_delay_seconds 0"), commands);
  }
}
