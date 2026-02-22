package com.faforever.client.galacticwar;

import com.faforever.client.game.Faction;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;

public class GwLeaderboardRow {
  private final int rank;
  private final StringProperty playerName = new SimpleStringProperty("Loading...");
  private final int wins;
  private final long winScore;
  private final int losses;
  private final long lossScore;
  private final Node medal;

  private final Faction faction;

  public GwLeaderboardRow(
      int rank,
      StringProperty playerName,
      int wins,
      long winScore,
      int losses,
      long lossScore,
      Node medal,
      Faction faction
  ) {
    this.rank = rank;
    this.playerName.bind(playerName);
    this.wins = wins;
    this.winScore = winScore;
    this.losses = losses;
    this.lossScore = lossScore;
    this.medal = medal;
    this.faction = faction;
  }

  public int getRank() { return rank; }
  public int getWins() { return wins; }
  public long getWinScore() { return winScore; }
  public int getLosses() { return losses; }
  public long getLossScore() { return lossScore; }
  public Node getMedal() { return medal; }
  public Faction getFaction() { return faction; }

  public StringProperty playerNameProperty() {
    return playerName;
  }

  public String getPlayerName() {
    return playerName.get();
  }

  public void setPlayerName(String name) {
    this.playerName.set(name);
  }
}
