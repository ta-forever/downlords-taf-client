package com.faforever.client.preferences;

import com.faforever.client.game.Faction;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.ObservableList;

import static javafx.collections.FXCollections.observableArrayList;

public class MatchmakerPrefs {

  private final ListProperty<Faction> factions;

  public MatchmakerPrefs() {
    this.factions = new SimpleListProperty<>(observableArrayList(Faction.CORE, Faction.GOK, Faction.ARM));
  }

  public ObservableList<Faction> getFactions() {
    return factions.get();
  }

  public ListProperty<Faction> factionsProperty() {
    return factions;
  }

}
