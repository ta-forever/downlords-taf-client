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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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

  private Process launchServerProcess;
  private boolean launchServerHasUac;
  private int launchServerPort;
  private Timer launchServerKeepAliveTimer;
  private KeepAliveTimerTask launchServerKeepAliveTimerTask;

  private int getFreeTcpPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    }
  }

  public class KeepAliveTimerTask extends TimerTask {
    // server doesn't start until user accepts UAC ... we don't know how long that could be
    // so we won't give up trying unless we've previously had a successful connection to server
    private boolean connectedOnce;
    private int retries;

    public KeepAliveTimerTask() {
      connectedOnce = false;
      retries = 10;
    }

    @Override
    public void run() {
      try (Socket socket = new Socket("127.0.0.1", launchServerPort)) {
        socket.getOutputStream().write("/keepalive".getBytes());
        socket.getOutputStream().flush();
        retries = 10;
        connectedOnce = true;

      } catch (IOException e) {
        if (connectedOnce) {
          retries--;
        }
      }

      if (retries <= 0) {
        logger.warn("cannot keepalive launch server on port {}", launchServerPort);
        launchServerKeepAliveTimer.cancel();
        launchServerProcess = null;
        launchServerKeepAliveTimer = null;
        launchServerKeepAliveTimerTask = null;
      }
    }

  }

  // The launch server listens on a tcp port for instructions to invoke TotalA.exe using DirectPlay API.
  // We use a launch server so that we don't have to keep asking over and over again for UAC if user has chosen to launch TA as admin.
  // Instead we start the launch server with UAC and we keep it alive for as long as Downlord's client is open.
  public Process startLaunchServer(String modTechnical) throws IOException {
    if (launchServerProcess != null &&
        launchServerKeepAliveTimerTask != null && launchServerKeepAliveTimerTask.retries == 10 &&
        preferencesService.getPreferences().getRequireUacEnabled() == this.launchServerHasUac) {
      logger.info("[startLaunchServer] already started, is healthy and should have required UAC");
      return launchServerProcess;
    }

    this.launchServerPort = getFreeTcpPort();
    this.launchServerHasUac = preferencesService.getPreferences().getRequireUacEnabled();
    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);

    logger.info("[startLaunchServer] starting on port {}", this.launchServerPort);
    Path launcherExecutable = getLauncherExectuable();
    LaunchCommandBuilder launchCommandBuilder = defaultLaunchCommand()
        .gpgnet4taExecutable(launcherExecutable)
        .requireUac(launchServerHasUac)
        .launchServerPort(this.launchServerPort)
        .startLaunchServer(true)
        .logFile(preferencesService.getFafLogDirectory().resolve("launchserver.log"));

    if (this.launchServerHasUac) {
      // On the assumption we're about to launch a game, set the dplay entries so we don't bother user with UAC twice
      launchCommandBuilder
          .baseModName(prefs.getBaseGameName())
          .gameInstalledPath(prefs.getInstalledPath())
          .gameExecutable(prefs.getInstalledExePath().getFileName().toString())
          .gameCommandLineOptions(prefs.getCommandLineOptions());
    }

    List<String> launchCommand = launchCommandBuilder.build();
    this.launchServerProcess = launch(launcherExecutable.getParent(), launchCommand);

    if (launchServerKeepAliveTimer != null) {
      launchServerKeepAliveTimer.cancel();
    }
    launchServerKeepAliveTimer = new Timer();
    launchServerKeepAliveTimerTask = new KeepAliveTimerTask();
    launchServerKeepAliveTimer.scheduleAtFixedRate(launchServerKeepAliveTimerTask, 0, 1000);

    return this.launchServerProcess;
  }

  public Process startGameOffline(String modTechnical, List<String> args) throws IOException {
    startLaunchServer(modTechnical);

    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    Path launcherExecutable = getLauncherExectuable();
    List<String> launchCommand = defaultLaunchCommand()
        .gpgnet4taExecutable(launcherExecutable)
        //.requireUac(preferencesService.getPreferences().getRequireUacEnabled())
        .logFile(preferencesService.getNewGameLogFile(0))
        .baseModName(prefs.getBaseGameName())
        .gameInstalledPath(prefs.getInstalledPath())
        .gameExecutable(prefs.getInstalledExePath().getFileName().toString())
        .gameCommandLineOptions(prefs.getCommandLineOptions())
        .launchServerPort(this.launchServerPort)
        .build();
    return launch(launcherExecutable.getParent(), launchCommand);
  }


  public Process startGame(String modTechnical, int uid, @Nullable List<String> additionalArgs, int gpgPort,
                           Player currentPlayer, String ircUrl, boolean autoLaunch, String commandInputFile) throws IOException {

    startLaunchServer(modTechnical);

    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    Path launcherExecutable = getLauncherExectuable();
    List<String> launchCommand = defaultLaunchCommand()
        .gpgnet4taExecutable(launcherExecutable)
        //.requireUac(preferencesService.getPreferences().getRequireUacEnabled())
        .logFile(preferencesService.getNewGameLogFile(uid))
        .proactiveResendEnabled(preferencesService.getPreferences().getProactiveResendEnabled())
        .autoLaunch(autoLaunch)
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
        .commandInputFile(commandInputFile)
        .launchServerPort(this.launchServerPort)
        .build();

    return launch(launcherExecutable.getParent(), launchCommand);
  }


  public Process startReplay(String modTechnical, Path path, @Nullable Integer replayId) throws IOException {
    startLaunchServer(modTechnical);
    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    Path launcherExecutable = getLauncherExectuable();
    List<String> launchCommand = defaultLaunchCommand()
        .gpgnet4taExecutable(launcherExecutable)
        //.requireUac(preferencesService.getPreferences().getRequireUacEnabled())
        .logFile(preferencesService.getNewGameLogFile(replayId))
        .baseModName(prefs.getBaseGameName())
        .gameInstalledPath(prefs.getInstalledPath())
        .gameExecutable(prefs.getInstalledExePath().getFileName().toString())
        .gameCommandLineOptions(prefs.getCommandLineOptions())
        .launchServerPort(this.launchServerPort)
        .build();
    return launch(launcherExecutable.getParent(), launchCommand);
  }


  public Process startReplay(String modTechnical, URI replayUri, Integer replayId, Player currentPlayer) throws IOException {
    startLaunchServer(modTechnical);
    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    Path launcherExecutable = getLauncherExectuable();
    List<String> launchCommand = defaultLaunchCommand().baseModName(modTechnical)
        .gpgnet4taExecutable(launcherExecutable)
        //.requireUac(preferencesService.getPreferences().getRequireUacEnabled())
        .logFile(preferencesService.getFafLogDirectory().resolve("replay.log"))
        .baseModName(prefs.getBaseGameName())
        .gameInstalledPath(prefs.getInstalledPath())
        .gameExecutable(prefs.getInstalledExePath().getFileName().toString())
        .gameCommandLineOptions(prefs.getCommandLineOptions())
        .launchServerPort(this.launchServerPort)
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
    //processBuilder.inheritIO();
    processBuilder.directory(launchWorkingDirectory.toFile());
    processBuilder.command(launchCommand);

    logger.info("Starting Total Annihilation Launcher with command: {}", String.join(" ", processBuilder.command()));
    Process process = processBuilder.start();

    return process;
  }
}
