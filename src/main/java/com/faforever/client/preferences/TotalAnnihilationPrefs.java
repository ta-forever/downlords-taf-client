package com.faforever.client.preferences;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.ObjectProperty;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TotalAnnihilationPrefs {

  private final StringProperty baseGameName;
  private final ObjectProperty<Path> installedExePath;
  private final StringProperty commandLineOptions;

  public static final String TOTAL_ANNIHILATION_EXE = "TotalA.exe";
  public static final Path CAVEDOG_TA_PATH;

  static {
    if (org.bridj.Platform.isWindows()) {
      CAVEDOG_TA_PATH = Paths.get("C:", "CAVEDOG", "TOTALA");
    } else {
      String userHome = System.getProperty("user.home");
      CAVEDOG_TA_PATH = Paths.get(userHome, ".wine", "drive_c", "CAVEDOG", "TOTALA");
    }
  }

  public TotalAnnihilationPrefs(String baseGameName, Path installedExePath, String commandLineOptions) {
    this.baseGameName = new SimpleStringProperty(baseGameName);
    this.installedExePath = new SimpleObjectProperty<>(installedExePath);
    this.commandLineOptions = new SimpleStringProperty(commandLineOptions);
  }

  private TotalAnnihilationPrefs() {
    this.baseGameName = new SimpleStringProperty();
    this.installedExePath = new SimpleObjectProperty<>();
    this.commandLineOptions = new SimpleStringProperty();
  }

  public StringProperty getModNameProperty() { return baseGameName; }
  public String getBaseGameName() {
    return baseGameName.get();
  }
  public void setBaseGameName(String baseGameName) { this.baseGameName.set(baseGameName); }

  public ObjectProperty<Path> getInstalledExePathProperty() { return installedExePath; }
  public Path getInstalledExePath() { return this.installedExePath.get(); }
  public void setInstalledExePath(Path installedExePath) { this.installedExePath.set(installedExePath); }

  public StringProperty getCommandLineOptionsProperty() { return commandLineOptions; }
  public String getCommandLineOptions() { return this.commandLineOptions.get(); }
  public void setCommandLineOptions(String commandLineOptions) { this.commandLineOptions.set(commandLineOptions); }

  public Path getInstalledPath() {
    if (this.installedExePath.get() != null) {
      return this.installedExePath.get().getParent();
    }
    else {
      return null;
    }
  }

}
