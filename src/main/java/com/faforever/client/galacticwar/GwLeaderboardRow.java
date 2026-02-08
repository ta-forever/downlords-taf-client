package com.faforever.client.galacticwar;

import javafx.scene.Node;

public class GwLeaderboardRow {
  private final int rank;
  private final String playerName;
  private final int wins;
  private final long winScore;
  private final int losses;
  private final long lossScore;
  private final Node medal;

  public GwLeaderboardRow(
      int rank,
      String playerName,
      int wins,
      long winScore,
      int losses,
      long lossScore,
      Node medal
  ) {
    this.rank = rank;
    this.playerName = playerName;
    this.wins = wins;
    this.winScore = winScore;
    this.losses = losses;
    this.lossScore = lossScore;
    this.medal = medal;
  }

  public int getRank() { return rank; }
  public String getPlayerName() { return playerName; }
  public int getWins() { return wins; }
  public long getWinScore() { return winScore; }
  public int getLosses() { return losses; }
  public long getLossScore() { return lossScore; }
  public Node getMedal() { return medal; }
}
