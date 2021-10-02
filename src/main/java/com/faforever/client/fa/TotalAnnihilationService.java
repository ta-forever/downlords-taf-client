package com.faforever.client.fa;

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
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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
  private int launchServerPort; // the gpgnet4ta process listening on this port may have UAC and the only thing it can do is start TotalA.exe
  private int consolePort;      // the gpgnet4ta process listening on this port doesn't need root privileges to do any of its tasks (/quit, /map and all the non UAC stuff related to /launch)
  private Timer launchServerKeepAliveTimer;
  private KeepAliveTimerTask launchServerKeepAliveTimerTask;

  Path getNativeGpgnet4taDir() {
    String nativeDir = System.getProperty("nativeDir", "lib");
    return Paths.get(nativeDir).resolve("bin");
  }

  private List<String> getRegisterDplayCommand(String gameMod, Path gamePath, Path gameExe, String gameArgs, int gameId) {
    Path exePath = getNativeGpgnet4taDir().resolve("talauncher.exe");
    Path logFile = preferencesService.getNewLogFile("registerdplay", gameId);

    List<String> command = new ArrayList<>();
    if (org.bridj.Platform.isLinux()) {
      command.add("wine");
    }

    command.addAll(List.of(
        exePath.toAbsolutePath().toString(),
        "--registerdplay",
        "--gamemod", gameMod,
        "--gamepath", gamePath.toString(),
        "--gameexe", gameExe.toString(),
        "--logfile", logFile.toString()
    ));

    if (gameArgs != null && !gameArgs.isEmpty()) {
      command.add("--gameargs");
      command.add(gameArgs);
    }

    return command;
  }

  private List<String> getLaunchServerCommand(int port, boolean uac, int gameGUID) {
    Path exePath = getNativeGpgnet4taDir().resolve("talauncher.exe");
    Path logFile = preferencesService.getNewLogFile("talauncher", gameGUID);

    List<String> command = new ArrayList<>();
    if (org.bridj.Platform.isLinux()) {
      command.add("wine");
    }

    command.addAll(List.of(
        exePath.toAbsolutePath().toString(),
        "--bindport", String.valueOf(port),
        "--logfile", logFile.toString()
    ));

    if (uac) {
      command.add("--uac");
    }

    return command;
  }

  private List<String> getGpgNet4TaCommand(
      String bindAddress, int consolePort, String gameMod, Path gamePath, boolean autoLaunch, boolean lockOptions,
      int players, boolean proactiveResend, String gpgNetUrl, String demoCompilerUrl, @Nullable String ircUrl,
      Path logFile, int launchServerPort
  ) {
    Path exePath = getNativeGpgnet4taDir().resolve(org.bridj.Platform.isLinux() ? "gpgnet4ta" : "gpgnet4ta.exe");

    List<String> command = new ArrayList<>();
    command.addAll(List.of(
        exePath.toAbsolutePath().toString(),
        "--lobbybindaddress", bindAddress,
        "--consoleport", String.valueOf(consolePort),
        "--gamemod", gameMod,
        "--gamepath", gamePath.toString(),
        "--players", String.valueOf(players),
        "--gpgnet", gpgNetUrl,
        "--logfile", logFile.toString(),
        "--launchserverport", String.valueOf(launchServerPort),
        "--democompilerurl", demoCompilerUrl
    ));

    if (autoLaunch) {
      command.add("--autolaunch");
    }

    if (lockOptions) {
      command.add("--lockoptions");
    }

    if (proactiveResend) {
      command.add("--proactiveresend");
    }

    if (ircUrl != null && !ircUrl.isEmpty()) {
      command.add("--irc");
      command.add(ircUrl);
    }

    return command;
  }

  private List<String> getTotalACommand(Path totalA) {
    List<String> command = new ArrayList<>();
    if (org.bridj.Platform.isLinux()) {
      command.add("wine");
    }
    command.add(totalA.toString());
    return command;
  }

  private List<String> getReplayCommand(String modTechnical, String replayUrlOrPath, String playerName,
                                        Path logFile, int launchServerPort) {
    Path exePath = getNativeGpgnet4taDir().resolve("replayer.exe");

    List<String> command = new ArrayList<>();
    if (org.bridj.Platform.isLinux()) {
      command.add("wine");
    }

    command.addAll(List.of(
        exePath.toAbsolutePath().toString(),
        "--gamemod", modTechnical,
        "--demourl", replayUrlOrPath,
        "--logfile", logFile.toString(),
        "--playername", playerName,
        "--launchserverport", String.valueOf(launchServerPort)
    ));

    return command;
  }

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
      if (launchServerProcess != null && launchServerProcess.isAlive()) {
        try (Socket socket = new Socket("127.0.0.1", launchServerPort)) {
          socket.setSoTimeout(300);
          socket.getOutputStream().write("/keepalive".getBytes());
          socket.getOutputStream().flush();
          retries = 10;
          connectedOnce = true;
        }
        catch (IOException e) {
          logger.warn("timeout connecting to launch server on port {}", launchServerPort);
          if (connectedOnce) {
            retries--;
          }
        }
      }
      else {
        logger.warn("launch server not alive ...");
        retries--;
      }

      if (retries <= 0) {
        logger.warn("cannot keepalive launch server on port {} ... giving up", launchServerPort);
        launchServerKeepAliveTimer.cancel();
        launchServerProcess.destroyForcibly();
        launchServerProcess = null;
        launchServerKeepAliveTimer = null;
        launchServerKeepAliveTimerTask = null;
      }
    }

  }

  public void sendToConsole(String command) {
    logger.info("Sending command '{}' to gpgnet4ta console port: {}", command, this.consolePort);
    try (Socket socket = new Socket("127.0.0.1", this.consolePort)) {
      socket.getOutputStream().write(command.getBytes());
      socket.getOutputStream().flush();
    } catch (IOException e) {
      logger.warn("Unable to connect to gpgnet4ta console port");
    }
  }

  // The launch server listens on a tcp port for instructions to invoke TotalA.exe using DirectPlay API.
  // We use a launch server so that we don't have to keep asking over and over again for UAC if user has chosen to launch TA as admin.
  // Instead we start the launch server with UAC and we keep it alive for as long as Downlord's client is open.
  public Process startLaunchServer(String modTechnical, int gameGUID) throws IOException {
    if (launchServerProcess != null &&
        launchServerKeepAliveTimerTask != null && launchServerKeepAliveTimerTask.retries == 10 &&
        preferencesService.getPreferences().getRequireUacEnabled() == this.launchServerHasUac) {
      logger.info("[startLaunchServer] already started, is healthy and should have required UAC");
      return null;
    }

    this.launchServerPort = getFreeTcpPort();
    this.launchServerHasUac = preferencesService.getPreferences().getRequireUacEnabled();

    if (launchServerKeepAliveTimer != null) {
      launchServerKeepAliveTimer.cancel();
    }
    launchServerKeepAliveTimer = new Timer();
    launchServerKeepAliveTimerTask = new KeepAliveTimerTask();
    launchServerKeepAliveTimer.scheduleAtFixedRate(launchServerKeepAliveTimerTask, 0, 1000);

    logger.info("[startLaunchServer] starting on port {}", this.launchServerPort);
    List<String> startLaunchServerCommand = getLaunchServerCommand(this.launchServerPort, this.launchServerHasUac, gameGUID);
    this.launchServerProcess = launch(getNativeGpgnet4taDir(), startLaunchServerCommand);
    return this.launchServerProcess;
  }

  public Process startGameOffline(String modTechnical, List<String> args) throws IOException {
    startLaunchServer(modTechnical, 0);

    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    Path totalA = prefs.getInstalledExePath().getFileName();
    List<String> startGameOfflineCommand = getTotalACommand(totalA);
    startGameOfflineCommand.addAll(Arrays.asList(prefs.getCommandLineOptions().split(" ")));
    return launch(prefs.getInstalledPath(), startGameOfflineCommand);
  }

  public Process startGame(String modTechnical, int uid, @Nullable List<String> additionalArgs, int gpgPort,
                           Player currentPlayer, String demoCompilerUrl, @Nullable String ircUrl, boolean autoLaunch) throws IOException {
    this.freePort47624();
    this.consolePort = getFreeTcpPort();

    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    List<String> registerDplayCommand = getRegisterDplayCommand(prefs.getBaseGameName(), prefs.getInstalledPath(), prefs.getInstalledExePath().getFileName(), prefs.getCommandLineOptions(), uid);
    try {
      this.launch(getNativeGpgnet4taDir(), registerDplayCommand).waitFor();
    } catch (InterruptedException e) {
      logger.warn("Interrupted exception waiting for dplay registration: {}", e.getMessage());
    }

    String loopbackAddress = Inet4Address.getLoopbackAddress().getHostAddress();
    boolean proactiveResend = preferencesService.getPreferences().getProactiveResendEnabled();
    String gpgNetUrl = String.format("%s:%d", loopbackAddress, gpgPort);

    List<String> gpgnet4taCommand = getGpgNet4TaCommand(
        loopbackAddress, this.consolePort, prefs.getBaseGameName(), prefs.getInstalledPath(), autoLaunch,
        false, 10, proactiveResend, gpgNetUrl, demoCompilerUrl, ircUrl,
        preferencesService.getNewLogFile("game", uid), this.launchServerPort);

    return launch(getNativeGpgnet4taDir(), gpgnet4taCommand);
  }

  public Process startReplay(String modTechnical, String replayUrlOrPath, Integer replayId, String playerName) throws IOException {
    this.freePort47624();

    TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    List<String> registerDplayCommand = getRegisterDplayCommand(
        prefs.getBaseGameName(), prefs.getInstalledPath(), prefs.getInstalledExePath().getFileName(),
        prefs.getCommandLineOptions(), replayId);
    try {
      this.launch(getNativeGpgnet4taDir(), registerDplayCommand).waitFor();
    } catch (InterruptedException e) {
      logger.warn("Interrupted exception waiting for dplay registration: {}", e.getMessage());
    }

    List<String> replayCommand = getReplayCommand(modTechnical, replayUrlOrPath, playerName,
        preferencesService.getNewLogFile("replay", replayId), this.launchServerPort);

    return launch(getNativeGpgnet4taDir(), replayCommand);
  }

  private boolean isPortAvailable(String host, int port) {
    boolean result = true;
    try {
      (new Socket(host, port)).close();
      result = false;
    }
    catch(IOException e) {  }
    return result;
  }

  private void freePort47624() {
    if (!isPortAvailable("127.0.0.1", 47624)) {
      logger.info("Port 47624 appears to be in use");
    }
    try {
      if (org.bridj.Platform.isLinux()) {
        for (String executable : List.of("dplaysvr.exe", "gpgnet4ta")) {
          logger.info("Issuing killall {} to free up port 47624 ...", executable);
          ProcessBuilder processBuilder = new ProcessBuilder();
          processBuilder.command(List.of("killall", executable));
          processBuilder.start().waitFor();
        }
      } else if (org.bridj.Platform.isWindows()) {
        for (String executable : List.of("gpgnet4ta.exe")) {
          logger.info("Issuing taskkill {} to free up port 47624 ...", executable);
          ProcessBuilder processBuilder = new ProcessBuilder();
          processBuilder.command(List.of("taskkill", "/F", "/IM", executable));
          processBuilder.start().waitFor();
        }
      }
    } catch (InterruptedException e) {
      logger.warn("InterruptedException shutting process: {}", e.getMessage());
    } catch (IOException e) {
      logger.warn("IOException shutting down process: {}", e.getMessage());
    }
    if (!isPortAvailable("127.0.0.1", 47624)) {
      logger.warn("Port 47624 appears to be STILL in use!");
    }
  }

  @NotNull
  private Process launch(Path launchWorkingDirectory, List<String> launchCommand) throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.directory(launchWorkingDirectory.toFile());
    processBuilder.command(launchCommand);

    logger.info("{}", processBuilder.command());
    Process process = processBuilder.start();

    return process;
  }
}
