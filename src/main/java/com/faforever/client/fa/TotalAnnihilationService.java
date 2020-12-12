package com.faforever.client.fa;

import com.faforever.client.game.Faction;
import com.faforever.client.player.Player;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Knows how to starts/stop Total Annihilation with proper parameters. Downloading maps, mods and updates as well as
 * notifying the server about whether the preferences is running or not is <strong>not</strong> this service's
 * responsibility.
 */
@Lazy
@Service
@RequiredArgsConstructor
public class TotalAnnihilationService {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PreferencesService preferencesService;


  public Process startGameOffline(String modTechnical, List<String> args) throws IOException {

    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    Path launcherExecutable = getLauncherExectuable();
    List<String> launchCommand = defaultLaunchCommand()
        .gpgnet4taExecutable(launcherExecutable)
        .requireUac(preferencesService.getPreferences().getRequireUacEnabled())
        .logFile(preferencesService.getNewGameLogFile(0))
        .baseModName(prefs.getBaseGameName())
        .gameInstalledPath(prefs.getInstalledPath())
        .gameExecutable(prefs.getInstalledExePath().getFileName().toString())
        .gameCommandLineOptions(prefs.getCommandLineOptions())
        .build();
    return launch(launcherExecutable.getParent(), launchCommand);
  }


  public Process startGame(String modTechnical, int uid, @Nullable Faction faction, @Nullable List<String> additionalArgs,
                           RatingMode ratingMode, int gpgPort, int localReplayPort, boolean rehost, Player currentPlayer, String ircUrl) throws IOException {

    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    Path launcherExecutable = getLauncherExectuable();
    List<String> launchCommand = defaultLaunchCommand()
        .gpgnet4taExecutable(launcherExecutable)
        .requireUac(preferencesService.getPreferences().getRequireUacEnabled())
        .logFile(preferencesService.getNewGameLogFile(uid))
        .baseModName(prefs.getBaseGameName())
        .gameInstalledPath(prefs.getInstalledPath())
        .gameExecutable(prefs.getInstalledExePath().getFileName().toString())
        .gameCommandLineOptions(prefs.getCommandLineOptions())
        .uid(uid)
        .country(currentPlayer.getCountry())
        .deviation(100.0f)
        .mean(1500.0f)
        .username(currentPlayer.getUsername())
        .additionalArgs(additionalArgs)
        .logFile(preferencesService.getNewGameLogFile(uid))
        .localGpgPort(gpgPort)
        .ircUrl(ircUrl)
        .build();

    return launch(launcherExecutable.getParent(), launchCommand);
  }


  public Process startReplay(String modTechnical, Path path, @Nullable Integer replayId) throws IOException {

    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    Path launcherExecutable = getLauncherExectuable();
    List<String> launchCommand = defaultLaunchCommand()
        .gpgnet4taExecutable(launcherExecutable)
        .requireUac(preferencesService.getPreferences().getRequireUacEnabled())
        .logFile(preferencesService.getNewGameLogFile(replayId))
        .baseModName(prefs.getBaseGameName())
        .gameInstalledPath(prefs.getInstalledPath())
        .gameExecutable(prefs.getInstalledExePath().getFileName().toString())
        .gameCommandLineOptions(prefs.getCommandLineOptions())
        .build();
    return launch(launcherExecutable.getParent(), launchCommand);
  }


  public Process startReplay(String modTechnical, URI replayUri, Integer replayId, Player currentPlayer) throws IOException {

    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    Path launcherExecutable = getLauncherExectuable();
    List<String> launchCommand = defaultLaunchCommand().baseModName(modTechnical)
        .gpgnet4taExecutable(launcherExecutable)
        .requireUac(preferencesService.getPreferences().getRequireUacEnabled())
        .logFile(preferencesService.getFafLogDirectory().resolve("replay.log"))
        .baseModName(prefs.getBaseGameName())
        .gameInstalledPath(prefs.getInstalledPath())
        .gameExecutable(prefs.getInstalledExePath().getFileName().toString())
        .gameCommandLineOptions(prefs.getCommandLineOptions())
        .build();
    return launch(launcherExecutable.getParent(), launchCommand);
  }

  private Path getLauncherExectuable() {
    if (false) {
      return Paths.get("D:\\games\\pause.bat");
    }
    else {
      String nativeDir = System.getProperty("nativeDir", "lib");
      Path jdplay = Paths.get(nativeDir).resolve("gpgnet4ta").resolve("gpgnet4ta.exe");
      return jdplay;
    }
  }

  private LaunchCommandBuilder defaultLaunchCommand() {
    return LaunchCommandBuilder.create();
  }

  @NotNull
  //private Process launch(Path executablePath) throws IOException {
  private Process launch(Path launchWorkingDirectory, List<String> launchCommand) throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.inheritIO();
    processBuilder.directory(launchWorkingDirectory.toFile());
    processBuilder.command(launchCommand);

    logger.info("Starting Total Annihilation Launcher with command: {}", String.join(" ", processBuilder.command()));
    Process process = processBuilder.start();

    return process;
  }
}
