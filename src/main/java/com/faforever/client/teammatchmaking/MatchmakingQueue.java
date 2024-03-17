package com.faforever.client.teammatchmaking;

import com.faforever.client.api.dto.MatchmakerQueue;
import com.faforever.client.api.dto.MatchmakerQueueMapPool;
import com.faforever.client.leaderboard.Leaderboard;
import com.faforever.client.map.MapBean;
import com.faforever.client.mod.FeaturedMod;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;

public class MatchmakingQueue {
  private final IntegerProperty queueId;
  private final StringProperty queueName;
  private final ObjectProperty<Instant> queuePopTime;
  private final IntegerProperty teamSize;
  private final BooleanProperty enabled;
  private final IntegerProperty partiesInQueue;
  private final IntegerProperty playersInQueue;
  private final BooleanProperty joined;
  private final ObjectProperty<MatchingStatus> matchingStatus;
  private final ObjectProperty<Leaderboard> leaderboard;
  private final ObjectProperty<FeaturedMod> featuredMod;
  private final ListProperty<MapBean> mapPool;

  public MatchmakingQueue() {
    this.queueId = new SimpleIntegerProperty();
    this.queueName = new SimpleStringProperty();
    this.queuePopTime = new SimpleObjectProperty<>(Instant.now().plus(Duration.ofDays(1)));
    this.teamSize = new SimpleIntegerProperty(0);
    this.enabled = new SimpleBooleanProperty(true);
    this.partiesInQueue = new SimpleIntegerProperty(0);
    this.playersInQueue = new SimpleIntegerProperty(0);
    this.joined = new SimpleBooleanProperty(false);
    this.matchingStatus = new SimpleObjectProperty<>(null);
    this.leaderboard = new SimpleObjectProperty<>(null);
    this.featuredMod = new SimpleObjectProperty<>(null);
    this.mapPool = new SimpleListProperty<>(FXCollections.observableArrayList());
  }

  public static MatchmakingQueue makePsuedoQueue(String name, FeaturedMod mod, String ratingType) {
    MatchmakingQueue queue = new MatchmakingQueue();
    queue.setQueueName(name);
    queue.setFeaturedMod(mod);
    Leaderboard leaderboard = new Leaderboard();
    leaderboard.setNameKey(name);
    leaderboard.setTechnicalName(ratingType);
    queue.setLeaderboard(leaderboard);
    return queue;
  }

  public static MatchmakingQueue fromDto(MatchmakerQueue dto) {
    MatchmakingQueue queue = new MatchmakingQueue();
    queue.setQueueId(Integer.parseInt(dto.getId()));
    queue.setQueueName(dto.getTechnicalName());
    queue.setTeamSize(dto.getTeamSize());
    queue.setEnabled(dto.isEnabled());
    queue.setLeaderboard(Leaderboard.fromDto(dto.getLeaderboard()));
    queue.setFeaturedMod(FeaturedMod.fromFeaturedMod(dto.getFeaturedMod()));
    return queue;
  }

  public static MatchmakingQueue fromMatchmakerQueueMapPoolDto(MatchmakerQueueMapPool dto) {
    MatchmakingQueue queue = fromDto(dto.getMatchmakerQueue());
    queue.getMapPool().setAll(
        dto.getMapPool().getMapPoolAssignments().stream()
            .map(mpa -> MapBean.fromMapVersionDto(mpa.getMapVersion()))
            .toList()
    );
    return queue;
  }

  public void setTimedOutMatchingStatus(MatchingStatus status, Duration timeout, TaskScheduler taskScheduler) {
    setMatchingStatus(status);
    taskScheduler.schedule(() -> {
      if (getMatchingStatus() == status) {
        setMatchingStatus(null);
      }
    }, Instant.now().plus(timeout));
  }

  public enum MatchingStatus {
    MATCH_FOUND, GAME_LAUNCHING, MATCH_CANCELLED
  }

  public MatchingStatus getMatchingStatus() {
    return matchingStatus.get();
  }

  public ObjectProperty<MatchingStatus> matchingStatusProperty() {
    return matchingStatus;
  }

  public void setMatchingStatus(MatchingStatus matchingStatus) {
    this.matchingStatus.set(matchingStatus);
  }

  public int getQueueId() {
    return queueId.get();
  }

  public void setQueueId(int queueId) {
    this.queueId.set(queueId);
  }

  public IntegerProperty queueIdProperty() {
    return queueId;
  }

  public String getQueueName() {
    return queueName.get();
  }

  public void setQueueName(String queueName) {
    this.queueName.set(queueName);
  }

  public StringProperty queueNameProperty() {
    return queueName;
  }

  public Instant getQueuePopTime() {
    return queuePopTime.get();
  }

  public void setQueuePopTime(Instant queuePopTime) {
    this.queuePopTime.set(queuePopTime);
  }

  public ObjectProperty<Instant> queuePopTimeProperty() {
    return queuePopTime;
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

  public FeaturedMod getFeaturedMod() {
    return featuredMod.get();
  }

  public void setFeaturedMod(FeaturedMod featuredMod) {
    this.featuredMod.set(featuredMod);
  }

  public ObjectProperty<FeaturedMod> featuredModProperty() {
    return featuredMod;
  }

  public int getPartiesInQueue() {
    return partiesInQueue.get();
  }

  public void setPartiesInQueue(int partiesInQueue) {
    this.partiesInQueue.set(partiesInQueue);
  }

  public IntegerProperty partiesInQueueProperty() {
    return partiesInQueue;
  }

  public int getTeamSize() {
    return teamSize.get();
  }

  public void setTeamSize(int teamSize) {
    this.teamSize.set(teamSize);
  }

  public IntegerProperty teamSizeProperty() {
    return teamSize;
  }

  public boolean getEnabled() {
    return enabled.get();
  }

  public void setEnabled(boolean enabled) { this.enabled.set(enabled); }

  public BooleanProperty enabledProperty() {
    return enabled;
  }

  public int getPlayersInQueue() {
    return playersInQueue.get();
  }

  public void setPlayersInQueue(int playersInQueue) {
    this.playersInQueue.set(playersInQueue);
  }

  public IntegerProperty playersInQueueProperty() {
    return playersInQueue;
  }

  public boolean isJoined() {
    return joined.get();
  }

  public void setJoined(boolean joined) {
    this.joined.set(joined);
  }

  public BooleanProperty joinedProperty() {
    return joined;
  }

  public ObservableList<MapBean> getMapPool() { return mapPool; }
}
