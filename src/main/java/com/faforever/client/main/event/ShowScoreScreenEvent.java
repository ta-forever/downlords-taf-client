package com.faforever.client.main.event;

import lombok.Value;

/** Opens the post-game "Battle Report" score screen for a finished game (LADDER_POINTS_DESIGN
 * §13.4). The screen fills its reward bundle (Combat Score -> LP chain + medals) asynchronously. */
@Value
public class ShowScoreScreenEvent {
  int gameId;
}
