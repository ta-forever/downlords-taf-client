package com.faforever.client.preferences;

import com.faforever.client.vault.search.SearchController.SortConfig;
import com.faforever.client.vault.search.SearchController.SortOrder;
import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.jetbrains.annotations.Nullable;

public class VaultPrefs {
  private final ObjectProperty<SortConfig> onlineReplaySortConfig;
  private final ObjectProperty<SortConfig> mapSortConfig;
  private final ObjectProperty<SortConfig> modVaultConfig;
  private final MapProperty<String, String> savedReplayQueries;
  private final MapProperty<String, String> savedMapQueries;
  private final MapProperty<String, String> savedModQueries;
  private final BooleanProperty showWinnersInReplays;
  private final BooleanProperty replayDownloadIncludeDate;
  private final BooleanProperty replayDownloadIncludeMod;
  private final BooleanProperty replayDownloadIncludeVersion;
  private final BooleanProperty replayDownloadIncludeMap;
  private final BooleanProperty replayDownloadIncludePlayers;


  public VaultPrefs() {
    onlineReplaySortConfig = new SimpleObjectProperty<>(new SortConfig("startTime", SortOrder.DESC));
    mapSortConfig = new SimpleObjectProperty<>(new SortConfig("statistics.plays", SortOrder.DESC));
    modVaultConfig = new SimpleObjectProperty<>(new SortConfig("latestVersion.createTime", SortOrder.DESC));
    savedReplayQueries = new SimpleMapProperty<>(FXCollections.observableHashMap());
    savedMapQueries = new SimpleMapProperty<>(FXCollections.observableHashMap());
    savedModQueries = new SimpleMapProperty<>(FXCollections.observableHashMap());
    showWinnersInReplays = new SimpleBooleanProperty(true);
    replayDownloadIncludeDate = new SimpleBooleanProperty(true);
    replayDownloadIncludeMod = new SimpleBooleanProperty(true);
    replayDownloadIncludeVersion = new SimpleBooleanProperty(true);
    replayDownloadIncludeMap = new SimpleBooleanProperty(true);
    replayDownloadIncludePlayers = new SimpleBooleanProperty(true);
  }

  public SortConfig getOnlineReplaySortConfig() {
    return onlineReplaySortConfig.get();
  }

  public void setOnlineReplaySortConfig(SortConfig onlineReplaySortConfig) {
    this.onlineReplaySortConfig.set(onlineReplaySortConfig);
  }

  public ObjectProperty<SortConfig> onlineReplaySortConfigProperty() {
    return onlineReplaySortConfig;
  }

  public SortConfig getMapSortConfig() {
    return mapSortConfig.get();
  }

  public void setMapSortConfig(SortConfig mapSortConfig) {
    this.mapSortConfig.set(mapSortConfig);
  }

  public boolean isShowWinnersInReplays() {
    return showWinnersInReplays.get();
  }

  public void setShowWinnersInReplays(boolean showWinnersInReplays) {
    this.showWinnersInReplays.set(showWinnersInReplays);
  }

  public BooleanProperty showWinnersInReplaysProperty() {
    return showWinnersInReplays;
  }

  public BooleanProperty replayDownloadIncludeDateProperty() {
    return replayDownloadIncludeDate;
  }

  public BooleanProperty replayDownloadIncludeModProperty() {
    return replayDownloadIncludeMod;
  }

  public BooleanProperty replayDownloadIncludeVersionProperty() {
    return replayDownloadIncludeVersion;
  }

  public BooleanProperty replayDownloadIncludeMapProperty() {
    return replayDownloadIncludeMap;
  }

  public BooleanProperty replayDownloadIncludePlayersProperty() {
    return replayDownloadIncludePlayers;
  }

  public ObjectProperty<SortConfig> mapSortConfigProperty() {
    return mapSortConfig;
  }

  public SortConfig getModVaultConfig() {
    return modVaultConfig.get();
  }

  public void setModVaultConfig(SortConfig modVaultConfig) {
    this.modVaultConfig.set(modVaultConfig);
  }

  public ObjectProperty<SortConfig> modVaultConfigProperty() {
    return modVaultConfig;
  }

  @Nullable
  public ObservableMap<String, String> getSavedReplayQueries() {
    return savedReplayQueries.get();
  }

  public void setSavedReplayQueries(ObservableMap<String, String> savedReplayQueries) {
    this.savedReplayQueries.set(savedReplayQueries);
  }

  public MapProperty savedReplayQueriesProperty() {
    return savedReplayQueries;
  }

  @Nullable
  public ObservableMap<String, String> getSavedMapQueries() {
    return savedMapQueries.get();
  }

  public void setSavedMapQueries(ObservableMap<String, String> savedMapQueries) {
    this.savedMapQueries.set(savedMapQueries);
  }

  public MapProperty savedMapQueriesProperty() {
    return savedMapQueries;
  }

  @Nullable
  public ObservableMap<String, String> getSavedModQueries() {
    return savedModQueries.get();
  }

  public void setSavedModQueries(ObservableMap<String, String> savedModQueries) {
    this.savedModQueries.set(savedModQueries);
  }

  public MapProperty savedModQueriesProperty() {
    return savedModQueries;
  }
}
