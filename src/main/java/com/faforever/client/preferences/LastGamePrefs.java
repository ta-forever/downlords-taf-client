package com.faforever.client.preferences;

import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.game.LiveReplayOption;
import com.faforever.client.preferences.gson.ExcludeFromGson;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;

public class LastGamePrefs {
  private final StringProperty lastGameType;
  private final StringProperty lastGameTitle;
  private final StringProperty lastMap;
  private final StringProperty lastGamePassword;
  private final ObjectProperty<Integer> lastGameMinRating;
  private final ObjectProperty<Integer> lastGameMaxRating;
  private final BooleanProperty lastGameEnforceRating;
  private final BooleanProperty lastGameOnlyFriends;
  private final ObjectProperty<LiveReplayOption> liveReplayOption;
  private final BooleanProperty lastGameRankedEnabled;
  private final ObjectProperty<Integer> maxPlayers;
  // Reserved-slots toggle: remembers the host's last toggle state across games
  // and client restarts, defaulting ON. Uses a fresh serialized key
  // ("reserveSlotsEnabled") so existing users — whose prefs hold the old
  // "lastReservedSlotsEnabled" key — pick up the new enabled-by-default value.
  @SerializedName("reserveSlotsEnabled")
  private final BooleanProperty lastReservedSlotsEnabled;
  // Reserved player list: kept only in memory for the current session so it is
  // remembered between hosted game instances but reverts to empty on restart.
  // Excluded from Gson so it is never written to disk. IDs (so a rename doesn't
  // break the selection) plus a parallel logins list (so the editor can show
  // the right name even when the player is offline). The two lists are kept the
  // same length; if a future-write breaks parity, the read-side tolerates it.
  @ExcludeFromGson
  private final ListProperty<Integer> lastReservedPlayerIds;
  @ExcludeFromGson
  private final ListProperty<String> lastReservedPlayerLogins;
  // Last-game roster snapshot, taken when our currentGame transitions to
  // LAUNCHING or ENDED. Feeds the "Add players from last game" button in
  // the reserved-slots editor. Stored as parallel lists so we can show
  // the logins in the button label even when the player is offline.
  private final ListProperty<Integer> lastGameRosterPlayerIds;
  private final ListProperty<String> lastGameRosterPlayerLogins;

  public LastGamePrefs() {
    lastGameType = new SimpleStringProperty(KnownFeaturedMod.DEFAULT.getTechnicalName());
    lastGameTitle = new SimpleStringProperty();
    lastMap = new SimpleStringProperty();
    lastGamePassword = new SimpleStringProperty();
    lastGameMinRating = new SimpleObjectProperty<>(null);
    lastGameMaxRating = new SimpleObjectProperty<>(null);
    lastGameOnlyFriends = new SimpleBooleanProperty();
    lastGameEnforceRating = new SimpleBooleanProperty(false);
    liveReplayOption = new SimpleObjectProperty<>(LiveReplayOption.FIVE_MINUTES);
    lastGameRankedEnabled = new SimpleBooleanProperty(true);
    maxPlayers = new SimpleObjectProperty<>(4);
    lastReservedSlotsEnabled = new SimpleBooleanProperty(true);
    lastReservedPlayerIds = new SimpleListProperty<>(FXCollections.observableArrayList());
    lastReservedPlayerLogins = new SimpleListProperty<>(FXCollections.observableArrayList());
    lastGameRosterPlayerIds = new SimpleListProperty<>(FXCollections.observableArrayList());
    lastGameRosterPlayerLogins = new SimpleListProperty<>(FXCollections.observableArrayList());
  }

  public String getLastGameType() {
    return lastGameType.get();
  }

  public void setLastGameType(String lastGameType) {
    this.lastGameType.set(lastGameType);
  }

  public StringProperty lastGameTypeProperty() {
    return lastGameType;
  }

  public String getLastGameTitle() {
    return lastGameTitle.get();
  }

  public void setLastGameTitle(String lastGameTitle) {
    this.lastGameTitle.set(lastGameTitle);
  }

  public StringProperty lastGameTitleProperty() {
    return lastGameTitle;
  }

  public String getLastMap() {
    return lastMap.get();
  }

  public void setLastMap(String lastMap) {
    this.lastMap.set(lastMap);
  }

  public StringProperty lastMapProperty() {
    return lastMap;
  }

  public String getLastGamePassword() {
    return lastGamePassword.get();
  }

  public void setLastGamePassword(String lastGamePassword) {
    this.lastGamePassword.set(lastGamePassword);
  }

  public StringProperty lastGamePasswordProperty() {
    return lastGamePassword;
  }

  public Integer getLastGameMinRating() {
    return lastGameMinRating.get();
  }

  public void setLastGameMinRating(Integer lastGameMinRating) {
    this.lastGameMinRating.set(lastGameMinRating);
  }

  public ObjectProperty<Integer> lastGameMinRatingProperty() {
    return lastGameMinRating;
  }

  public Integer getLastGameMaxRating() {
    return lastGameMaxRating.get();
  }

  public void setLastGameMaxRating(Integer lastGameMaxRating) {
    this.lastGameMaxRating.set(lastGameMaxRating);
  }

  public ObjectProperty<Integer> lastGameMaxRatingProperty() {
    return lastGameMaxRating;
  }

  public boolean isLastGameOnlyFriends() {
    return lastGameOnlyFriends.get();
  }

  public void setLastGameOnlyFriends(boolean lastGameOnlyFriends) {
    this.lastGameOnlyFriends.set(lastGameOnlyFriends);
  }

  public BooleanProperty lastGameOnlyFriendsProperty() {
    return lastGameOnlyFriends;
  }

  public boolean isLastGameEnforceRating() {
    return lastGameEnforceRating.get();
  }

  public void setLastGameEnforceRating(boolean lastGameEnforceRating) {
    this.lastGameEnforceRating.set(lastGameEnforceRating);
  }

  public BooleanProperty lastGameEnforceRatingProperty() {
    return lastGameEnforceRating;
  }

  public void setLastGameLiveReplayOption(LiveReplayOption liveReplayOption) {
    this.liveReplayOption.set(liveReplayOption);
  }

  public LiveReplayOption getLastGameLiveReplayOption() {
    return this.liveReplayOption.get();
  }

  public ObjectProperty<LiveReplayOption> lastGameLiveReplayOptionProperty() {
    return this.liveReplayOption;
  }

  public void setLastGameRankedEnabled(boolean option) {
    this.lastGameRankedEnabled.set(option);
  }

  public boolean getLastGameRankedEnabled() {
    return this.lastGameRankedEnabled.get();
  }

  public BooleanProperty lastGameRankedEnabledProperty() {
    return this.lastGameRankedEnabled;
  }

  public void setMaxPlayers(int maxPlayers) { this.maxPlayers.set(maxPlayers); }
  public int getMaxPlayers() { return this.maxPlayers.get(); }
  public ObjectProperty<Integer> maxPlayersProperty() { return this.maxPlayers; }

  public boolean isLastReservedSlotsEnabled() { return lastReservedSlotsEnabled.get(); }
  public void setLastReservedSlotsEnabled(boolean v) { lastReservedSlotsEnabled.set(v); }
  public BooleanProperty lastReservedSlotsEnabledProperty() { return lastReservedSlotsEnabled; }

  public javafx.collections.ObservableList<Integer> getLastReservedPlayerIds() { return lastReservedPlayerIds.get(); }
  public void setLastReservedPlayerIds(java.util.List<Integer> ids) {
    lastReservedPlayerIds.setAll(ids == null ? java.util.List.of() : ids);
  }
  public ListProperty<Integer> lastReservedPlayerIdsProperty() { return lastReservedPlayerIds; }

  public javafx.collections.ObservableList<String> getLastReservedPlayerLogins() { return lastReservedPlayerLogins.get(); }
  public void setLastReservedPlayerLogins(java.util.List<String> logins) {
    lastReservedPlayerLogins.setAll(logins == null ? java.util.List.of() : logins);
  }
  public ListProperty<String> lastReservedPlayerLoginsProperty() { return lastReservedPlayerLogins; }

  public javafx.collections.ObservableList<Integer> getLastGameRosterPlayerIds() { return lastGameRosterPlayerIds.get(); }
  public void setLastGameRosterPlayerIds(java.util.List<Integer> ids) {
    lastGameRosterPlayerIds.setAll(ids == null ? java.util.List.of() : ids);
  }
  public ListProperty<Integer> lastGameRosterPlayerIdsProperty() { return lastGameRosterPlayerIds; }

  public javafx.collections.ObservableList<String> getLastGameRosterPlayerLogins() { return lastGameRosterPlayerLogins.get(); }
  public void setLastGameRosterPlayerLogins(java.util.List<String> logins) {
    lastGameRosterPlayerLogins.setAll(logins == null ? java.util.List.of() : logins);
  }
  public ListProperty<String> lastGameRosterPlayerLoginsProperty() { return lastGameRosterPlayerLogins; }
}
