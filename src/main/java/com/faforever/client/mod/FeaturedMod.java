package com.faforever.client.mod;

import com.faforever.client.i18n.I18n;
import com.google.common.base.Strings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;

import java.util.List;
import java.util.stream.Collectors;

public class FeaturedMod {

  public static final FeaturedMod UNKNOWN = new FeaturedMod();
  private final StringProperty id;
  private final StringProperty technicalName;
  private final StringProperty displayName;
  private final StringProperty displayNameNotLocalised;
  private final StringProperty website;
  private final StringProperty description;
  private final StringProperty gitUrl;
  private final StringProperty fileExtension;
  private final StringProperty gp3Filename;
  private final StringProperty gitBranch;
  private final StringProperty installPackage;
  private final BooleanProperty visible;
  private final ListProperty<FeaturedModVersion> versions;

  public FeaturedMod() {
    id = new SimpleStringProperty();
    technicalName = new SimpleStringProperty();
    displayNameNotLocalised = new SimpleStringProperty();
    displayName = new SimpleStringProperty();
    website = new SimpleStringProperty();
    description = new SimpleStringProperty();
    visible = new SimpleBooleanProperty();
    gitUrl = new SimpleStringProperty();
    fileExtension = new SimpleStringProperty();
    gp3Filename = new SimpleStringProperty();
    gitBranch = new SimpleStringProperty();
    installPackage = new SimpleStringProperty();
    versions = new SimpleListProperty<>(FXCollections.observableArrayList());
  }

  public static FeaturedMod fromDto(com.faforever.client.api.dto.FeaturedMod featuredMod, I18n i18n) {
    FeaturedMod bean = new FeaturedMod();
    bean.setId(featuredMod.getId());
    bean.technicalName.set(featuredMod.getTechnicalName());
    String i18nKey = "featuredMod.displayName." + featuredMod.getTechnicalName();
    bean.displayName.set(i18n.getWithDefault(featuredMod.getDisplayName(), i18nKey));
    bean.displayNameNotLocalised.set(featuredMod.getDisplayName());
    bean.website.set(featuredMod.getWebsite());
    bean.description.setValue(featuredMod.getDescription());
    bean.visible.setValue(featuredMod.isVisible());
    bean.gitUrl.set(Strings.emptyToNull(featuredMod.getGitUrl()));
    bean.fileExtension.set(Strings.emptyToNull(featuredMod.getFileExtension()));
    bean.gp3Filename.set(Strings.emptyToNull(featuredMod.getGp3Filename()));
    bean.gitBranch.set(Strings.emptyToNull(featuredMod.getGitBranch()));
    bean.installPackage.set(Strings.emptyToNull(featuredMod.getInstallPackage()));
    if (featuredMod.getVersions() != null && !featuredMod.getVersions().isEmpty()) {
      bean.versions.setAll(featuredMod.getVersions().stream()
          .filter(fmv -> fmv.getDisplayName() != null)
          .map(com.faforever.client.mod.FeaturedModVersion::fromDto)
          .collect(Collectors.toList()));
    }
    return bean;
  }

  public String getTechnicalName() {
    return technicalName.get();
  }
  public void setTechnicalName(String technicalName) {
    this.technicalName.set(technicalName);
  }
  public StringProperty technicalNameProperty() {
    return technicalName;
  }

  public String getDisplayName() { return displayName.get(); }
  public void setDisplayName(String displayName) { this.displayName.set(displayName); }
  public StringProperty displayNameProperty() { return displayName; }

  public String getDisplayNameNotLocalised() { return displayNameNotLocalised.get(); }
  public void setDisplayNameNotLocalised(String displayNameNotLocalised) { this.displayNameNotLocalised.set(displayNameNotLocalised); }
  public StringProperty displayNameNotLocalisedProperty() { return displayNameNotLocalised; }

  public String getWebsite() {
    return website.get();
  }
  public void setWebsite(String website) {
    this.website.set(website);
  }
  public StringProperty websiteProperty() {
    return website;
  }

  public String getDescription() {
    return description.get();
  }
  public void setDescription(String description) { this.description.set(description); }
  public StringProperty descriptionProperty() {
    return description;
  }

  public boolean isVisible() {
    return visible.get();
  }
  public void setVisible(boolean visible) {
    this.visible.set(visible);
  }
  public BooleanProperty visibleProperty() {
    return visible;
  }

  public String getGitUrl() {
    return gitUrl.get();
  }
  public void setGitUrl(String gitUrl) {
    this.gitUrl.set(gitUrl);
  }
  public StringProperty gitUrlProperty() {
    return gitUrl;
  }

  public String getFileExtension() {
    return fileExtension.get();
  }
  public void setFileExtension(String fileExtension) {
    this.fileExtension.set(fileExtension);
  }
  public StringProperty gitFileExtensionProperty() {
    return fileExtension;
  }

  public String getGp3Filename() {
    return gp3Filename.get();
  }
  public void setGp3Filename(String gp3Filename) {
    this.gp3Filename.set(gp3Filename);
  }
  public StringProperty gitGp3FilenameProperty() {
    return gp3Filename;
  }

  public String getGitBranch() { return gitBranch.get(); }
  public void setGitBranch(String gitBranch) { this.gitBranch.set(gitBranch); }
  public StringProperty gitBranchProperty() { return gitBranch; }

  public String getInstallPackage() { return installPackage.get(); }
  public void setInstallPackage(String installPackage) { this.installPackage.set(installPackage); }
  public StringProperty installPackageProperty() { return installPackage; }

  public String getId() {
    return id.get();
  }
  public void setId(String id) {
    this.id.set(id);
  }
  public StringProperty idProperty() {
    return id;
  }

  public ListProperty<FeaturedModVersion> getVersionsProperty() { return versions; }
  public List<FeaturedModVersion> getVersions() { return versions.get(); }
  public void setVersions(List<FeaturedModVersion> v) { versions.setAll(v); }
}
