package com.faforever.client.mod;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class FeaturedModVersion {

  public static final FeaturedModVersion UNKNOWN = new FeaturedModVersion();
  private final StringProperty taHash;
  private final StringProperty taEngineVersion;
  private final StringProperty gitBranch;
  private final StringProperty displayName;

  public FeaturedModVersion() {
    taHash = new SimpleStringProperty();
    taEngineVersion = new SimpleStringProperty();
    gitBranch = new SimpleStringProperty();
    displayName = new SimpleStringProperty();
  }

  public static FeaturedModVersion fromDto(com.faforever.client.api.dto.FeaturedModVersion fmv) {
    FeaturedModVersion bean = new FeaturedModVersion();
    bean.setTaHash(fmv.getTaHash());
    bean.setTaEngineVersion(fmv.getVersion());
    bean.setGitBranch(fmv.getGitBranch());
    bean.setDisplayName(fmv.getDisplayName());
    return bean;
  }

  public String getTaHash() {
    return taHash.get();
  }
  public void setTaHash(String taHash) {
    this.taHash.set(taHash);
  }
  public StringProperty taHashProperty() {
    return taHash;
  }

  public String getTaEngineVersion() {
    return taEngineVersion.get();
  }
  public void setTaEngineVersion(String taEngineVersion) {
    this.taEngineVersion.set(taEngineVersion);
  }
  public StringProperty taEngineVersionProperty() {
    return taEngineVersion;
  }

  public String getGitBranch() {
    return gitBranch.get();
  }
  public void setGitBranch(String gitBranch) {
    this.gitBranch.set(gitBranch);
  }
  public StringProperty gitBranchProperty() {
    return gitBranch;
  }

  public String getDisplayName() {
    return displayName.get();
  }
  public void setDisplayName(String displayName) {
    this.displayName.set(displayName);
  }
  public StringProperty displayNameProperty() {
    return displayName;
  }
}
