package com.faforever.client.fa.relay.event;

import com.faforever.client.game.Game;
import javafx.beans.value.ChangeListener;
import lombok.Value;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Fired to request that GameService should auto-join user with the given Game (or one any similar one created by same host) either immediately or as soon one become joinable
 */
@Value
public class AutoJoinRequestEvent {
  private Game prototype; // null to clear current auto-join request
}
