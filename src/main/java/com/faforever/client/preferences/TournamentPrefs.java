package com.faforever.client.preferences;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Persistent settings for the Tournaments page. Currently just remembers the
 * Hall of Fame mod filter selection so it survives a client restart.
 *
 * <p>{@code hallOfFameModId == null} means "All mods" (the across-all-mods
 * rollup row in the player_tournament_summary view).
 */
public class TournamentPrefs {
  private final ObjectProperty<Integer> hallOfFameModId = new SimpleObjectProperty<>();

  public Integer getHallOfFameModId() {
    return hallOfFameModId.get();
  }

  public void setHallOfFameModId(Integer hallOfFameModId) {
    this.hallOfFameModId.set(hallOfFameModId);
  }

  public ObjectProperty<Integer> hallOfFameModIdProperty() {
    return hallOfFameModId;
  }
}
