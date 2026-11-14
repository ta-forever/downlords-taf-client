package com.faforever.client.preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
  private final ObjectProperty<AskAlwaysOrNever> autoUpdateEnable;
  private final ObjectProperty<GameEngine> engine;
  private final ObjectProperty<EngineUpdates> oaInstallation;
  private final ObjectProperty<Path> oaExecutablePath;
  private final StringProperty oaExtraArgs;

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

  public TotalAnnihilationPrefs(String baseGameName, Path installedExePath, String commandLineOptions,
                                AskAlwaysOrNever autoUpdateEnable) {
    this.baseGameName = new SimpleStringProperty(baseGameName);
    this.installedExePath = new SimpleObjectProperty<>(installedExePath);
    this.commandLineOptions = new SimpleStringProperty(commandLineOptions);
    this.autoUpdateEnable = new SimpleObjectProperty<>(autoUpdateEnable);
    this.engine = new SimpleObjectProperty<>(GameEngine.CAVEDOG);
    this.oaInstallation = new SimpleObjectProperty<>(EngineUpdates.TAF);
    this.oaExecutablePath = new SimpleObjectProperty<>();
    this.oaExtraArgs = new SimpleStringProperty("");
  }

  private TotalAnnihilationPrefs() {
    this.baseGameName = new SimpleStringProperty();
    this.installedExePath = new SimpleObjectProperty<>();
    this.commandLineOptions = new SimpleStringProperty();
    this.autoUpdateEnable = new SimpleObjectProperty<>(AskAlwaysOrNever.NEVER);
    this.engine = new SimpleObjectProperty<>(GameEngine.CAVEDOG);
    this.oaInstallation = new SimpleObjectProperty<>(EngineUpdates.TAF);
    this.oaExecutablePath = new SimpleObjectProperty<>();
    this.oaExtraArgs = new SimpleStringProperty("");
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

  public ObjectProperty<AskAlwaysOrNever> getAutoUpdateEnableProperty() { return autoUpdateEnable; }
  public AskAlwaysOrNever getAutoUpdateEnable() { return this.autoUpdateEnable.get(); }
  public void setAutoUpdateEnable(AskAlwaysOrNever autoUpdateEnable) { this.autoUpdateEnable.set(autoUpdateEnable); }

  public ObjectProperty<GameEngine> getEngineProperty() { return engine; }
  /** Never null: preferences.json written before the engine choice existed has no value. */
  public GameEngine getEngine() { return this.engine.get() == null ? GameEngine.CAVEDOG : this.engine.get(); }
  public void setEngine(GameEngine engine) { this.engine.set(engine); }

  public ObjectProperty<EngineUpdates> getOaInstallationProperty() { return oaInstallation; }
  public EngineUpdates getOaInstallation() {
    // the property itself is null when preferences.json holds an unreadable or null value
    return this.oaInstallation == null || this.oaInstallation.get() == null ? EngineUpdates.TAF : this.oaInstallation.get();
  }
  public void setOaInstallation(EngineUpdates oaInstallation) { this.oaInstallation.set(oaInstallation); }

  public ObjectProperty<Path> getOaExecutablePathProperty() { return oaExecutablePath; }
  public Path getOaExecutablePath() { return this.oaExecutablePath.get(); }
  public void setOaExecutablePath(Path oaExecutablePath) { this.oaExecutablePath.set(oaExecutablePath); }

  public StringProperty getOaExtraArgsProperty() { return oaExtraArgs; }
  public String getOaExtraArgs() { return this.oaExtraArgs.get() == null ? "" : this.oaExtraArgs.get(); }
  public void setOaExtraArgs(String oaExtraArgs) { this.oaExtraArgs.set(oaExtraArgs); }

  public boolean isOpenAnnihilation() {
    return getEngine() == GameEngine.OPEN_ANNIHILATION || com.faforever.client.library.LibraryGameInfo.UMBRELLA_FEATURED_MOD.equals(getBaseGameName());
  }

  /**
   * The mod install directory, for both engines. For Cavedog it is the folder holding TotalA.exe.
   * An Open Annihilation install needs no TotalA.exe, so the exe path only has to point at where
   * it would be ({@code <dir>/TotalA.exe}).
   */
  public Path getInstalledPath() {
    if (this.installedExePath.get() != null) {
      return this.installedExePath.get().getParent();
    }
    else {
      return null;
    }
  }

}
