package com.faforever.client.preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

public class NotificationsPrefs {

  private final BooleanProperty soundsEnabled;
  private final BooleanProperty transientNotificationsEnabled;
  private final BooleanProperty mentionSoundEnabled;
  private final BooleanProperty infoSoundEnabled;
  private final BooleanProperty warnSoundEnabled;
  private final BooleanProperty errorSoundEnabled;
  private final BooleanProperty friendOnlineToastEnabled;
  private final BooleanProperty friendOfflineToastEnabled;
  private final BooleanProperty friendOnlineSoundEnabled;
  private final BooleanProperty friendOfflineSoundEnabled;
  private final BooleanProperty friendJoinsGameSoundEnabled;
  private final BooleanProperty playerJoinsGameSoundEnabled;
  private final BooleanProperty friendPlaysGameSoundEnabled;
  private final BooleanProperty friendPlaysGameToastEnabled;
  private final BooleanProperty privateMessageSoundEnabled;
  private final BooleanProperty privateMessageToastEnabled;
  private final BooleanProperty friendJoinsGameToastEnabled;
  private final BooleanProperty playerJoinsGameToastEnabled;
  private final BooleanProperty notifyOnAtMentionOnlyEnabled;
  private final BooleanProperty afterGameReviewEnabled;
  // "You passed a friend on the season ladder" toast (LADDER_POINTS_DESIGN §15.2). Positive-only,
  // individually mutable; default on.
  private final BooleanProperty ladderPassToastEnabled;
  // Auto-show the post-game Battle Report (score screen). Default on; the report's "Don't show
  // again" button flips this off until the user re-enables it here.
  private final BooleanProperty battleReportEnabled;
  // Tournament server notifications — three categories matching the kind=
  // dispatch in FafServerAccessorImpl.onNotice. Default to true so existing
  // users keep getting them; opt-out only.
  private final BooleanProperty tournamentMatchReadyEnabled;
  private final BooleanProperty tournamentResultsEnabled;
  private final BooleanProperty tournamentAnnouncementsEnabled;
  private final ObjectProperty<ToastPosition> toastPosition;
  private final IntegerProperty toastScreen;
  private final IntegerProperty toastDisplayTime;

  public NotificationsPrefs() {
    soundsEnabled = new SimpleBooleanProperty(true);
    mentionSoundEnabled = new SimpleBooleanProperty(true);
    infoSoundEnabled = new SimpleBooleanProperty(true);
    warnSoundEnabled = new SimpleBooleanProperty(true);
    errorSoundEnabled = new SimpleBooleanProperty(true);
    transientNotificationsEnabled = new SimpleBooleanProperty(true);
    toastPosition = new SimpleObjectProperty<>(ToastPosition.BOTTOM_RIGHT);
    friendOnlineToastEnabled = new SimpleBooleanProperty(true);
    friendOfflineToastEnabled = new SimpleBooleanProperty(true);
    friendOnlineSoundEnabled = new SimpleBooleanProperty(true);
    friendOfflineSoundEnabled = new SimpleBooleanProperty(true);
    friendJoinsGameSoundEnabled = new SimpleBooleanProperty(true);
    playerJoinsGameSoundEnabled = new SimpleBooleanProperty(true);
    friendPlaysGameSoundEnabled = new SimpleBooleanProperty(true);
    friendPlaysGameToastEnabled = new SimpleBooleanProperty(true);
    friendJoinsGameToastEnabled = new SimpleBooleanProperty(true);
    playerJoinsGameToastEnabled = new SimpleBooleanProperty(true);
    privateMessageSoundEnabled = new SimpleBooleanProperty(true);
    privateMessageToastEnabled = new SimpleBooleanProperty(true);
    notifyOnAtMentionOnlyEnabled = new SimpleBooleanProperty(false);
    toastScreen = new SimpleIntegerProperty(0);
    toastDisplayTime = new SimpleIntegerProperty(5000);
    afterGameReviewEnabled = new SimpleBooleanProperty(true);
    ladderPassToastEnabled = new SimpleBooleanProperty(true);
    battleReportEnabled = new SimpleBooleanProperty(true);
    tournamentMatchReadyEnabled = new SimpleBooleanProperty(true);
    tournamentResultsEnabled = new SimpleBooleanProperty(true);
    tournamentAnnouncementsEnabled = new SimpleBooleanProperty(true);
  }

  public boolean isTournamentMatchReadyEnabled() { return tournamentMatchReadyEnabled.get(); }
  public void setTournamentMatchReadyEnabled(boolean v) { this.tournamentMatchReadyEnabled.set(v); }
  public BooleanProperty tournamentMatchReadyEnabledProperty() { return tournamentMatchReadyEnabled; }

  public boolean isTournamentResultsEnabled() { return tournamentResultsEnabled.get(); }
  public void setTournamentResultsEnabled(boolean v) { this.tournamentResultsEnabled.set(v); }
  public BooleanProperty tournamentResultsEnabledProperty() { return tournamentResultsEnabled; }

  public boolean isTournamentAnnouncementsEnabled() { return tournamentAnnouncementsEnabled.get(); }
  public void setTournamentAnnouncementsEnabled(boolean v) { this.tournamentAnnouncementsEnabled.set(v); }
  public BooleanProperty tournamentAnnouncementsEnabledProperty() { return tournamentAnnouncementsEnabled; }

  public boolean isSoundsEnabled() {
    return soundsEnabled.get();
  }

  public void setSoundsEnabled(boolean soundsEnabled) {
    this.soundsEnabled.set(soundsEnabled);
  }

  public BooleanProperty soundsEnabledProperty() {
    return soundsEnabled;
  }

  public boolean isTransientNotificationsEnabled() {
    return transientNotificationsEnabled.get();
  }

  public void setTransientNotificationsEnabled(boolean transientNotificationsEnabled) {
    this.transientNotificationsEnabled.set(transientNotificationsEnabled);
  }

  public BooleanProperty transientNotificationsEnabledProperty() {
    return transientNotificationsEnabled;
  }

  public boolean isMentionSoundEnabled() {
    return mentionSoundEnabled.get();
  }

  public void setMentionSoundEnabled(boolean mentionSoundEnabled) {
    this.mentionSoundEnabled.set(mentionSoundEnabled);
  }

  public BooleanProperty mentionSoundEnabledProperty() {
    return mentionSoundEnabled;
  }

  public boolean isInfoSoundEnabled() {
    return infoSoundEnabled.get();
  }

  public void setInfoSoundEnabled(boolean infoSoundEnabled) {
    this.infoSoundEnabled.set(infoSoundEnabled);
  }

  public BooleanProperty infoSoundEnabledProperty() {
    return infoSoundEnabled;
  }

  public boolean isWarnSoundEnabled() {
    return warnSoundEnabled.get();
  }

  public void setWarnSoundEnabled(boolean warnSoundEnabled) {
    this.warnSoundEnabled.set(warnSoundEnabled);
  }

  public BooleanProperty warnSoundEnabledProperty() {
    return warnSoundEnabled;
  }

  public boolean isErrorSoundEnabled() {
    return errorSoundEnabled.get();
  }

  public void setErrorSoundEnabled(boolean errorSoundEnabled) {
    this.errorSoundEnabled.set(errorSoundEnabled);
  }

  public BooleanProperty errorSoundEnabledProperty() {
    return errorSoundEnabled;
  }

  public boolean isFriendOnlineToastEnabled() {
    return friendOnlineToastEnabled.get();
  }

  public void setFriendOnlineToastEnabled(boolean friendOnlineToastEnabled) {
    this.friendOnlineToastEnabled.set(friendOnlineToastEnabled);
  }

  public BooleanProperty friendOnlineToastEnabledProperty() {
    return friendOnlineToastEnabled;
  }

  public boolean isFriendOfflineToastEnabled() {
    return friendOfflineToastEnabled.get();
  }

  public void setFriendOfflineToastEnabled(boolean friendOfflineToastEnabled) {
    this.friendOfflineToastEnabled.set(friendOfflineToastEnabled);
  }

  public BooleanProperty friendOfflineToastEnabledProperty() {
    return friendOfflineToastEnabled;
  }

  public boolean isFriendOnlineSoundEnabled() {
    return friendOnlineSoundEnabled.get();
  }

  public void setFriendOnlineSoundEnabled(boolean friendOnlineSoundEnabled) {
    this.friendOnlineSoundEnabled.set(friendOnlineSoundEnabled);
  }

  public BooleanProperty friendOnlineSoundEnabledProperty() {
    return friendOnlineSoundEnabled;
  }

  public boolean isFriendOfflineSoundEnabled() {
    return friendOfflineSoundEnabled.get();
  }

  public void setFriendOfflineSoundEnabled(boolean friendOfflineSoundEnabled) {
    this.friendOfflineSoundEnabled.set(friendOfflineSoundEnabled);
  }

  public BooleanProperty friendOfflineSoundEnabledProperty() {
    return friendOfflineSoundEnabled;
  }

  public boolean isFriendJoinsGameSoundEnabled() {
    return friendJoinsGameSoundEnabled.get();
  }

  public void setFriendJoinsGameSoundEnabled(boolean friendJoinsGameSoundEnabled) {
    this.friendJoinsGameSoundEnabled.set(friendJoinsGameSoundEnabled);
  }

  public BooleanProperty friendJoinsGameSoundEnabledProperty() {
    return friendJoinsGameSoundEnabled;
  }

  public boolean isPlayerJoinsGameSoundEnabled() {
    return playerJoinsGameSoundEnabled.get();
  }

  public void setPlayerJoinsGameSoundEnabled(boolean playerJoinsGameSoundEnabled) {
    this.playerJoinsGameSoundEnabled.set(playerJoinsGameSoundEnabled);
  }

  public BooleanProperty playerJoinsGameSoundEnabledProperty() {
    return playerJoinsGameSoundEnabled;
  }

  public boolean isFriendPlaysGameSoundEnabled() {
    return friendPlaysGameSoundEnabled.get();
  }

  public void setFriendPlaysGameSoundEnabled(boolean friendPlaysGameSoundEnabled) {
    this.friendPlaysGameSoundEnabled.set(friendPlaysGameSoundEnabled);
  }

  public BooleanProperty friendPlaysGameSoundEnabledProperty() {
    return friendPlaysGameSoundEnabled;
  }

  public boolean isFriendPlaysGameToastEnabled() {
    return friendPlaysGameToastEnabled.get();
  }

  public void setFriendPlaysGameToastEnabled(boolean friendPlaysGameToastEnabled) {
    this.friendPlaysGameToastEnabled.set(friendPlaysGameToastEnabled);
  }

  public BooleanProperty friendPlaysGameToastEnabledProperty() {
    return friendPlaysGameToastEnabled;
  }

  public boolean isPrivateMessageSoundEnabled() {
    return privateMessageSoundEnabled.get();
  }

  public void setPrivateMessageSoundEnabled(boolean privateMessageSoundEnabled) {
    this.privateMessageSoundEnabled.set(privateMessageSoundEnabled);
  }

  public BooleanProperty privateMessageSoundEnabledProperty() {
    return privateMessageSoundEnabled;
  }

  public boolean isPrivateMessageToastEnabled() {
    return privateMessageToastEnabled.get();
  }

  public void setPrivateMessageToastEnabled(boolean privateMessageToastEnabled) {
    this.privateMessageToastEnabled.set(privateMessageToastEnabled);
  }

  public BooleanProperty privateMessageToastEnabledProperty() {
    return privateMessageToastEnabled;
  }

  public boolean isFriendJoinsGameToastEnabled() {
    return friendJoinsGameToastEnabled.get();
  }

  public void setFriendJoinsGameToastEnabled(boolean friendJoinsGameToastEnabled) {
    this.friendJoinsGameToastEnabled.set(friendJoinsGameToastEnabled);
  }

  public BooleanProperty friendJoinsGameToastEnabledProperty() {
    return friendJoinsGameToastEnabled;
  }

  public boolean isPlayerJoinsGameToastEnabled() {
    return playerJoinsGameToastEnabled.get();
  }

  public void setPlayerJoinsGameToastEnabled(boolean playerJoinsGameToastEnabled) {
    this.playerJoinsGameToastEnabled.set(playerJoinsGameToastEnabled);
  }

  public BooleanProperty playerJoinsGameToastEnabledProperty() {
    return playerJoinsGameToastEnabled;
  }

  public ToastPosition getToastPosition() {
    return toastPosition.get();
  }

  public void setToastPosition(ToastPosition toastPosition) {
    this.toastPosition.set(toastPosition);
  }

  public ObjectProperty<ToastPosition> toastPositionProperty() {
    return toastPosition;
  }

  public int getToastScreen() {
    return toastScreen.get();
  }

  public void setToastScreen(int toastScreen) {
    this.toastScreen.set(toastScreen);
  }

  public IntegerProperty toastScreenProperty() {
    return toastScreen;
  }

  public int getToastDisplayTime() {
    return toastDisplayTime.get();
  }

  public void setToastDisplayTime(int toastDisplayTime) {
    this.toastDisplayTime.set(toastDisplayTime);
  }

  public IntegerProperty toastDisplayTimeProperty() {
    return toastDisplayTime;
  }

  public boolean getNotifyOnAtMentionOnlyEnabled() {
    return notifyOnAtMentionOnlyEnabled.get();
  }

  public void setNotifyOnAtMentionOnlyEnabled(boolean notifyOnAtMentionOnlyEnabled) {
    this.notifyOnAtMentionOnlyEnabled.set(notifyOnAtMentionOnlyEnabled);
  }

  public BooleanProperty notifyOnAtMentionOnlyEnabledProperty() {
    return notifyOnAtMentionOnlyEnabled;
  }

  public boolean isAfterGameReviewEnabled() {
    return afterGameReviewEnabled.get();
  }

  public void setAfterGameReviewEnabled(boolean afterGameReviewEnabled) {
    this.afterGameReviewEnabled.set(afterGameReviewEnabled);
  }

  public BooleanProperty afterGameReviewEnabledProperty() {
    return afterGameReviewEnabled;
  }

  public boolean isLadderPassToastEnabled() {
    return ladderPassToastEnabled.get();
  }

  public void setLadderPassToastEnabled(boolean ladderPassToastEnabled) {
    this.ladderPassToastEnabled.set(ladderPassToastEnabled);
  }

  public BooleanProperty ladderPassToastEnabledProperty() {
    return ladderPassToastEnabled;
  }

  public boolean isBattleReportEnabled() {
    return battleReportEnabled.get();
  }

  public void setBattleReportEnabled(boolean battleReportEnabled) {
    this.battleReportEnabled.set(battleReportEnabled);
  }

  public BooleanProperty battleReportEnabledProperty() {
    return battleReportEnabled;
  }
}
