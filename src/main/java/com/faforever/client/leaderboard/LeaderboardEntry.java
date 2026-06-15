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

import java.util.Objects;

public class LeaderboardEntry {

  private final IntegerProperty userId;
  private final StringProperty username;
  private final DoubleProperty rating;
  private final IntegerProperty totalGames;
  private final IntegerProperty wonGames;
  private final IntegerProperty drawnGames;
  private final IntegerProperty lostGames;
  private final FloatProperty winRate;
  private final StringProperty recentResults;
  private final StringProperty allResults;
  private final StringProperty recentMod;
  private final IntegerProperty streak;
  private final IntegerProperty bestStreak;
  private final ObjectProperty<Leaderboard> leaderboard;

  public LeaderboardEntry() {
    userId = new SimpleIntegerProperty();
    username = new SimpleStringProperty();
    rating = new SimpleDoubleProperty();
    totalGames = new SimpleIntegerProperty();
    wonGames = new SimpleIntegerProperty();
    drawnGames = new SimpleIntegerProperty();
    lostGames = new SimpleIntegerProperty();
    winRate = new SimpleFloatProperty();
    recentResults = new SimpleStringProperty();
    allResults = new SimpleStringProperty();
    recentMod = new SimpleStringProperty();
    streak = new SimpleIntegerProperty();
    bestStreak = new SimpleIntegerProperty();
    leaderboard = new SimpleObjectProperty<>();
  }

  public static LeaderboardEntry fromDto(com.faforever.client.api.dto.LeaderboardEntry entry) {
    LeaderboardEntry leaderboardEntry = new LeaderboardEntry();
    leaderboardEntry.setLeaderboard(Leaderboard.fromDto(entry.getLeaderboard()));
    leaderboardEntry.setUserId(Integer.parseInt(entry.getPlayer().getId()));
    leaderboardEntry.setUsername(entry.getPlayer().getLogin());
    leaderboardEntry.setRating(entry.getRating());
    leaderboardEntry.setStreak(entry.getStreak());
    leaderboardEntry.setBestStreak(entry.getBestStreak());
    leaderboardEntry.setRecentMod(entry.getRecentMod());

    int totalGames = entry.getWonGames() + entry.getDrawnGames() + entry.getLostGames();
    leaderboardEntry.setTotalGames(totalGames);
    leaderboardEntry.setWinRate(entry.getWonGames() / (float) totalGames);
    leaderboardEntry.setWonGames(entry.getWonGames());
    leaderboardEntry.setDrawnGames(entry.getDrawnGames());
    leaderboardEntry.setLostGames(entry.getLostGames());
    leaderboardEntry.setAllResults(String.format("%d-%d-%d",
        entry.getWonGames(), entry.getDrawnGames(), entry.getLostGames()));

    long recentWinCount = entry.getRecentScores().chars().filter(c -> c == '2').count();
    long recentDrawCount = entry.getRecentScores().chars().filter(c -> c == '1').count();
    long recentLossCount = entry.getRecentScores().chars().filter(c -> c == '0').count();
    leaderboardEntry.setRecentResults(String.format("%d-%d-%d",
        recentWinCount, recentDrawCount, recentLossCount));

    return leaderboardEntry;
  }

  public int getUserId() {
    return userId.get();
  }

  public void setUserId(int id) {
    this.userId.set(id);
  }

  public IntegerProperty getUserIdProperty() { return userId; }

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

  public int getTotalGames() {
    return totalGames.get();
  }

  public void setTotalGames(int count) {
    this.totalGames.set(count);
  }

  public IntegerProperty totalGamesProperty() { return totalGames; }

  public int getWonGames() {
    return wonGames.get();
  }

  public void setWonGames(int count) {
    this.wonGames.set(count);
  }

  public IntegerProperty wonGamesProperty() {
    return wonGames;
  }

  public int getDrawnGames() {
    return drawnGames.get();
  }

  public void setDrawnGames(int count) { this.drawnGames.set(count); }

  public IntegerProperty drawnGamesProperty() {
    return drawnGames;
  }

  public int getLostGames() {
    return lostGames.get();
  }

  public void setLostGames(int count) {
    this.lostGames.set(count);
  }

  public IntegerProperty lostGamesProperty() {
    return lostGames;
  }

  public float getWinRate() { return winRate.get(); }

  public void setWinRate(float winRate) {
    this.winRate.set(winRate);
  }

  public FloatProperty winRateProperty() {
    return winRate;
  }

  public String getRecentResults() { return recentResults.get(); }

  public void setRecentResults(String recentResults) { this.recentResults.set(recentResults); }

  public StringProperty recentResultsProperty() {
    return recentResults;
  }

  public String getAllResults() { return allResults.get(); }

  public void setAllResults(String results) { this.allResults.set(results); }

  public StringProperty allResultsProperty() {
    return allResults;
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

  public int getBestStreak() { return bestStreak.get(); }

  public void setBestStreak(int streak) {
    this.bestStreak.set(streak);
  }

  public IntegerProperty bestStreakProperty() {
    return bestStreak;
  }

  private String leaderboardTechnicalName() {
    Leaderboard lb = leaderboard.get();
    return lb != null ? lb.getTechnicalName() : null;
  }

  @Override
  public int hashCode() {
    // Value-based: two entries for the same player in different formats (leaderboards) or with
    // different ratings/stats must NOT be equal, otherwise JavaFX's Cell.isItemChanged() skips
    // refreshing the row when the player's rank happens to be unchanged across formats, leaving
    // stale ratings/stats on screen.
    return Objects.hash(getUserId(), leaderboardTechnicalName(), getRating(), getTotalGames(),
        getWonGames(), getDrawnGames(), getLostGames(), getStreak(), getBestStreak(),
        getRecentResults(), getAllResults());
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

    return getUserId() == that.getUserId()
        && Objects.equals(leaderboardTechnicalName(), that.leaderboardTechnicalName())
        && getRating() == that.getRating()
        && getTotalGames() == that.getTotalGames()
        && getWonGames() == that.getWonGames()
        && getDrawnGames() == that.getDrawnGames()
        && getLostGames() == that.getLostGames()
        && getStreak() == that.getStreak()
        && getBestStreak() == that.getBestStreak()
        && Objects.equals(getRecentResults(), that.getRecentResults())
        && Objects.equals(getAllResults(), that.getAllResults());
  }

  @Override
  public String toString() {
    return "LeaderboardEntry{" +
        "username=" + username.get() +
        ",leaderboard=" + leaderboard.get().getTechnicalName() +
        '}';
  }
}
