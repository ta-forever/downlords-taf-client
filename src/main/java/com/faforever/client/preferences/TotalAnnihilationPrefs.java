package com.faforever.client.preferences;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.ObjectProperty;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TotalAnnihilationPrefs {



  private final StringProperty modName;
  private final ObjectProperty<Path> installedPath;
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

  public TotalAnnihilationPrefs(String modName, Path installedPath, String commandLineOptions) {
    this.modName = new SimpleStringProperty(modName);
    this.installedPath = new SimpleObjectProperty<>(installedPath);
    this.commandLineOptions = new SimpleStringProperty(commandLineOptions);
  }

  private TotalAnnihilationPrefs() {
    this.modName = new SimpleStringProperty();
    this.installedPath = new SimpleObjectProperty<>();
    this.commandLineOptions = new SimpleStringProperty();
  }

  public StringProperty getModNameProperty() { return modName; }
  public String getModName() {
    return modName.get();
  }
  public void setModName(String modName) { this.modName.set(modName); }

  public ObjectProperty<Path> getInstalledPathProperty() { return installedPath; }
  public Path getInstalledPath() { return this.installedPath.get(); }
  public void setInstalledPath(Path installedPath) { this.installedPath.set(installedPath); }

  public StringProperty getCommandLineOptionsProperty() { return commandLineOptions; }
  public String getCommandLineOptions() { return this.commandLineOptions.get(); }
  public void setCommandLineOptions(String commandLineOptions) { this.commandLineOptions.set(commandLineOptions); }

  public Path getExecutable() {
    if (installedPath.get() != null) {
      return this.installedPath.get().resolve(TOTAL_ANNIHILATION_EXE);
    } else {
      return null;
    }
  }

}
