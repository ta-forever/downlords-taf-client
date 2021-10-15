package com.faforever.client.leaderboard;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class LeaderboardEntry {

  private final StringProperty username;
  private final DoubleProperty rating;
  private final IntegerProperty gamesPlayed;
  private final FloatProperty winRate;
  private final FloatProperty recentWinRate;
  private final StringProperty recentMod;
  private final IntegerProperty streak;
  private final ObjectProperty<Leaderboard> leaderboard;

  public LeaderboardEntry() {
    username = new SimpleStringProperty();
    rating = new SimpleDoubleProperty();
    gamesPlayed = new SimpleIntegerProperty();
    winRate = new SimpleFloatProperty();
    recentWinRate = new SimpleFloatProperty();
    recentMod = new SimpleStringProperty();
    streak = new SimpleIntegerProperty();
    leaderboard = new SimpleObjectProperty<>();
  }

  public static LeaderboardEntry fromDto(com.faforever.client.api.dto.LeaderboardEntry entry) {
    LeaderboardEntry leaderboardEntry = new LeaderboardEntry();
    leaderboardEntry.setLeaderboard(Leaderboard.fromDto(entry.getLeaderboard()));
    leaderboardEntry.setUsername(entry.getPlayer().getLogin());
    leaderboardEntry.setRating(entry.getRating());
    leaderboardEntry.setWinRate(entry.getWonGames() / (float) entry.getTotalGames());
    leaderboardEntry.setGamesPlayed(entry.getTotalGames());
    leaderboardEntry.setStreak(entry.getStreak());
    leaderboardEntry.setRecentMod(entry.getRecentMod());

    long recentWinCount = entry.getRecentScores().chars().filter(c -> c == '2').count();
    long recentPlayCount = entry.getRecentScores().length();
    float recentWinRate = recentPlayCount > 0 ? (float)recentWinCount / (float)recentPlayCount : 0.0f;
    leaderboardEntry.setRecentWinRate(recentWinRate);

    return leaderboardEntry;
  }

  public String getUsername() {
    return username.get();
  }

  public void setUsername(String username) {
    this.username.set(username);
  }

  public StringProperty usernameProperty() {
    return username;
  }

  public Leaderboard getLeaderboard() {
    return leaderboard.get();
  }

  public void setLeaderboard(Leaderboard leaderboard) {
    this.leaderboard.set(leaderboard);
  }

  public ObjectProperty<Leaderboard> leaderboardProperty() {
    return leaderboard;
  }

  public double getRating() {
    return rating.get();
  }

  public void setRating(double rating) {
    this.rating.set(rating);
  }

  public DoubleProperty ratingProperty() {
    return rating;
  }

  public int getGamesPlayed() {
    return gamesPlayed.get();
  }

  public void setGamesPlayed(int gamesPlayed) {
    this.gamesPlayed.set(gamesPlayed);
  }

  public IntegerProperty gamesPlayedProperty() {
    return gamesPlayed;
  }

  public float getWinRate() { return winRate.get(); }

  public void setWinRate(float winRate) {
    this.winRate.set(winRate);
  }

  public FloatProperty winRateProperty() {
    return winRate;
  }

  public float getRecentWinRate() { return recentWinRate.get(); }

  public void setRecentWinRate(float recentWinRate) { this.recentWinRate.set(recentWinRate); }

  public FloatProperty recentWinRateProperty() {
    return recentWinRate;
  }

  public String getRecentMod() { return recentMod.get(); }

  public void setRecentMod(String recentMod) { this.recentMod.set(recentMod); }

  public StringProperty recentModProperty() {
    return recentMod;
  }

  public int getStreak() { return streak.get(); }

  public void setStreak(int streak) {
    this.streak.set(streak);
  }

  public IntegerProperty streakProperty() {
    return streak;
  }

  @Override
  public int hashCode() {
    return username.get() != null ? username.get().hashCode() : 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LeaderboardEntry that = (LeaderboardEntry) o;

    return !(username.get() != null ? !username.get().equalsIgnoreCase(that.username.get()) : that.username.get() != null);

  }

  @Override
  public String toString() {
    return "LeaderboardEntry{" +
        "username=" + username.get() +
        ",leaderboard=" + leaderboard.get().getTechnicalName() +
        '}';
  }
}
