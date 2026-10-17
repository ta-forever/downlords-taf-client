package com.faforever.client.fa.oa;

import com.faforever.client.library.LibraryCommands;
import com.faforever.client.library.LibraryLaunch;
import com.faforever.client.library.LibraryLaunchPlan;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntFunction;

/**
 * Stands in for {@code talauncher.exe} when the mod runs on Open Annihilation
 * (OPEN_ANNIHILATION_INTEGRATION.md §6.3). gpgnet4ta's {@code LaunchClient} connects here, sends
 * {@code /host} or {@code /join} and watches the replies:
 *
 * <pre>
 *   -> "/host|/join|/searchjoin &lt;gameId&gt; &lt;guid&gt; &lt;playerName&gt; &lt;addr&gt; &lt;hashEndpoint&gt; &lt;token&gt;"
 *   -> "/keepalive" | "/quit" | "/failversion ..."
 *   &lt;- "RUNNING" | "LAUNCHED" | "IDLE" | "FAIL"        (plain text, no terminator)
 * </pre>
 *
 * gpgnet4ta needs {@code RUNNING} within 30 s. Only then does it send the Battleroom state the server
 * requires before it accepts Launching/Live. {@code LAUNCHED} matters on the host only (it is what makes
 * gpgnet4ta report Launching). Anything but {@code RUNNING}/{@code LAUNCHED} reads as "the game exited".
 *
 * <p>The game-launched signal comes from OA's {@code --fark} endpoint until OA reports it itself
 * (upstream ask U2). It is requested for the host only.
 *
 * <p>Hash/launch-code arguments are deliberately never logged: the last one is the API token.
 */
@Slf4j
public class OaLaunchServer implements Closeable {

  /** Starts the OA process; replaceable so the protocol can be tested without a real OA. */
  @FunctionalInterface
  interface ProcessFactory {
    Process start(List<String> command, Path workingDirectory, Path logFile) throws IOException;
  }

  private static Process startProcess(List<String> command, Path workingDirectory, Path logFile) throws IOException {
    return new ProcessBuilder(command)
        .directory(workingDirectory.toFile())
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
        .start();
  }

  /** What the launched OA is for. */
  public enum Mode {
    /** A TAF game: OA hosts on /host and joins on /join. */
    GAME,
    /** Live watch: replayer.exe hosts the stream and OA joins it with --replay-viewer. */
    REPLAY_VIEWER
  }

  private static final long POLL_INTERVAL_MS = 2000;
  private static final int MAX_SETUP_ATTEMPTS = 3;

  private final Path oaExe;
  private final Path modDir;
  private final String extraArgs;
  private final IntFunction<Path> logFileForGame;
  private final Path farkDir;
  private final Mode mode;
  private final ProcessFactory processFactory;
  /** Set for a Library mod game: OA is started from a .oalaunch file naming the mod, and modDir is unused. */
  private volatile LibraryLaunchPlan libraryPlan;

  private final List<Socket> clients = new CopyOnWriteArrayList<>();
  private ServerSocket serverSocket;
  private volatile Process process;
  private volatile boolean closed;
  /** Set when gpgnet4ta asked us to stop OA, so that the resulting non-zero exit is not reported as a crash. */
  private volatile boolean stopRequested;
  /** What the host's battle room is set to once it opens; see {@link OaHostSetup}. */
  private volatile OaHostSetup.Settings hostSettings = OaHostSetup.Settings.NONE;

  /**
   * Chooses the map and rules the host's battle room is set to when it opens. Set before gpgnet4ta's
   * {@code /host} arrives; has no effect on a joiner.
   */
  public void setHostSettings(OaHostSetup.Settings settings) {
    this.hostSettings = settings == null ? OaHostSetup.Settings.NONE : settings;
  }

  /**
   * @param logFileForGame OA's stdout and stderr go here, keyed by game id
   * @param farkDir        where OA writes its automation endpoint file
   */
  public OaLaunchServer(Path oaExe, Path modDir, String extraArgs, IntFunction<Path> logFileForGame, Path farkDir,
                        Mode mode) {
    this(oaExe, modDir, extraArgs, logFileForGame, farkDir, mode, OaLaunchServer::startProcess);
  }

  /** A Library mod game: uses the player's own OA install and user folder, not a TAF mod directory. */
  public OaLaunchServer(Path oaExe, String extraArgs, IntFunction<Path> logFileForGame, Path launchDir,
                        LibraryLaunchPlan libraryPlan) {
    this(oaExe, extraArgs, logFileForGame, launchDir, libraryPlan, Mode.GAME);
  }

  /** A Library mod game ({@link Mode#GAME}) or a live replay of one ({@link Mode#REPLAY_VIEWER}). */
  public OaLaunchServer(Path oaExe, String extraArgs, IntFunction<Path> logFileForGame, Path launchDir,
                        LibraryLaunchPlan libraryPlan, Mode mode) {
    this(oaExe, null, extraArgs, logFileForGame, launchDir, mode, libraryPlan, OaLaunchServer::startProcess);
  }

  OaLaunchServer(Path oaExe, Path modDir, String extraArgs, IntFunction<Path> logFileForGame, Path farkDir,
                 Mode mode, ProcessFactory processFactory) {
    this(oaExe, modDir, extraArgs, logFileForGame, farkDir, mode, null, processFactory);
  }

  OaLaunchServer(Path oaExe, Path modDir, String extraArgs, IntFunction<Path> logFileForGame, Path farkDir,
                 Mode mode, LibraryLaunchPlan libraryPlan, ProcessFactory processFactory) {
    this.libraryPlan = libraryPlan;
    this.mode = mode;
    this.processFactory = processFactory;
    this.oaExe = oaExe;
    this.modDir = modDir;
    this.extraArgs = extraArgs;
    this.logFileForGame = logFileForGame;
    this.farkDir = farkDir;
  }

  /** @return the loopback port gpgnet4ta must be given as {@code --launchserverport} */
  public int start() throws IOException {
    serverSocket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
    Thread acceptor = new Thread(this::acceptLoop, "oa-launch-server");
    acceptor.setDaemon(true);
    acceptor.start();
    log.info("[OaLaunchServer] listening on 127.0.0.1:{}", serverSocket.getLocalPort());
    return serverSocket.getLocalPort();
  }

  public int getPort() {
    return serverSocket.getLocalPort();
  }

  /**
   * Changes the map a Library host's OA is started with (the {@code map:} of its .oalaunch file). Only possible
   * before OA is started: once its battle room is open the client has no channel to change the map (--fark is not
   * enabled for Library games, and the status stream is output only).
   *
   * @param mapKey the map key as {@code --list-content} gives it ({@code name@pack} for pack maps)
   * @return whether the new map will be used; false when OA is already running or this is not a Library game
   */
  public synchronized boolean setLibraryMapKey(String mapKey) {
    LibraryLaunchPlan plan = libraryPlan;
    if (plan == null || isOaRunning()) {
      return false;
    }
    libraryPlan = new LibraryLaunchPlan(plan.mod(), plan.baseGame(), plan.expansions(), mapKey, plan.modFolder());
    return true;
  }

  public boolean isOaRunning() {
    Process p = process;
    return p != null && p.isAlive();
  }

  private void acceptLoop() {
    while (!closed) {
      try {
        Socket client = serverSocket.accept();
        clients.add(client);
        Thread handler = new Thread(() -> handle(client), "oa-launch-client");
        handler.setDaemon(true);
        handler.start();
      } catch (IOException e) {
        if (!closed) {
          log.warn("[OaLaunchServer] accept failed", e);
        }
        return;
      }
    }
  }

  private void handle(Socket client) {
    byte[] buffer = new byte[4096];
    try (client) {
      while (!closed) {
        int read = client.getInputStream().read(buffer);
        if (read < 0) {
          return;
        }
        String message = new String(buffer, 0, read, StandardCharsets.UTF_8).trim();
        if (!message.isEmpty()) {
          onCommand(client, message.split("\\s+"));
        }
      }
    } catch (IOException e) {
      if (!closed) {
        log.debug("[OaLaunchServer] client connection ended: {}", e.getMessage());
      }
    } finally {
      clients.remove(client);
    }
  }

  private void onCommand(Socket client, String[] args) throws IOException {
    String command = args[0];
    switch (command) {
      case "/keepalive" -> {
      }
      case "/host", "/join", "/searchjoin" -> {
        // args: command gameId guid playerName address [hashEndpoint token]; the tail is never logged
        if (args.length < 4) {
          log.warn("[OaLaunchServer] malformed {} ({} arguments)", command, args.length);
          reply(client, "FAIL");
          return;
        }
        log.info("[OaLaunchServer] {} gameId={} player={}", command, args[1], args[3]);
        boolean host = command.equals("/host");
        if (!isOaRunning()) {
          try {
            startOa(host, parseGameId(args[1]), args[2], args[3]);
          } catch (IOException e) {
            log.warn("[OaLaunchServer] could not start Open Annihilation", e);
            reply(client, "FAIL");
            return;
          }
        }
        reply(client, "RUNNING");
      }
      case "/quit" -> stopOa();
      case "/failversion" -> reply(client, "FAIL");
      default -> reply(client, isOaRunning() ? "RUNNING" : "IDLE");
    }
  }

  private static int parseGameId(String text) {
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private synchronized void startOa(boolean hostRequested, int gameId, String dplayGuid, String playerName)
      throws IOException {
    boolean host = hostRequested;
    if (isOaRunning()) {
      return;
    }
    Path farkFile = farkDir.resolve("oa_fark_" + gameId + ".json");
    Path statusFile = null;
    List<String> command;
    if (libraryPlan != null && mode == Mode.GAME) {
      statusFile = farkDir.resolve("oa_status_" + gameId + ".jsonl");
      Files.deleteIfExists(statusFile);
      Path launchFile = farkDir.resolve("oalibrary_" + gameId + ".oalaunch");
      LibraryLaunch launch = host
          ? LibraryLaunch.host(libraryPlan.mod(), libraryPlan.baseGame(), libraryPlan.expansions(), playerName,
          OaCommands.gameName(gameId), dplayGuid, libraryPlan.mapKey(), statusFile.toAbsolutePath())
          : LibraryLaunch.join(libraryPlan.mod(), libraryPlan.baseGame(), libraryPlan.expansions(), playerName,
          OaCommands.LOOPBACK, dplayGuid, statusFile.toAbsolutePath());
      Files.writeString(launchFile, launch.toOaLaunch(), StandardCharsets.UTF_8);
      command = LibraryCommands.launch(oaExe, launchFile.toAbsolutePath(), extraArgs);
    } else if (mode == Mode.REPLAY_VIEWER) {
      host = false;
      command = libraryPlan != null && libraryPlan.modFolder() != null
          ? LibraryCommands.replayViewer(oaExe, libraryPlan.modFolder(), libraryPlan.baseGame(),
          libraryPlan.expansions(), extraArgs)
          : OaCommands.replayViewer(oaExe, modDir, extraArgs);
    } else if (host) {
      command = OaCommands.host(oaExe, modDir, playerName, gameId, extraArgs);
    } else {
      command = OaCommands.join(oaExe, modDir, playerName, extraArgs);
    }
    if (host && statusFile == null) {
      command = new java.util.ArrayList<>(command);
      command.addAll(List.of("--fark", "--fark-file", farkFile.toString()));
    }
    log.info("[OaLaunchServer] starting {}", command);
    Process started = processFactory.start(command, oaExe.toAbsolutePath().getParent(), logFileForGame.apply(gameId));
    process = started;

    boolean watchForLaunch = host;
    Path watchedStatusFile = statusFile;
    Thread watcher = new Thread(() -> watch(started, watchForLaunch, farkFile, watchedStatusFile), "oa-launch-watch");
    watcher.setDaemon(true);
    watcher.start();
  }

  /** Pushes LAUNCHED once the host's match is running (host only), and IDLE or FAIL when OA exits. */
  private void watch(Process started, boolean host, Path farkFile, Path statusFile) {
    boolean launched = !host;
    int setupAttempts = host && !hostSettings.isEmpty() ? MAX_SETUP_ATTEMPTS : 0;
    while (started.isAlive() && !closed) {
      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      if (!launched && statusFile != null) {
        launched = matchStarted(statusFile);
        if (launched) {
          log.info("[OaLaunchServer] match started");
          notifyClients("LAUNCHED");
        }
      } else if (!launched && java.nio.file.Files.isRegularFile(farkFile)) {
        // the endpoint serves one client at a time: when it is busy this poll fails and the next one retries
        try (OaFarkClient fark = new OaFarkClient(farkFile)) {
          String screen = fark.screen();
          if ("match".equals(screen)) {
            launched = true;
            log.info("[OaLaunchServer] match started");
            notifyClients("LAUNCHED");
          } else if ("mp_battleroom".equals(screen) && setupAttempts > 0) {
            setupAttempts--;
            List<String> problems = new OaHostSetup(fark).apply(hostSettings);
            if (problems.isEmpty()) {
              setupAttempts = 0;
              log.info("[OaLaunchServer] battle room set up: {}", hostSettings);
            } else {
              log.warn("[OaLaunchServer] battle room set-up incomplete ({} attempts left): {}", setupAttempts, problems);
            }
          }
        } catch (IOException | RuntimeException e) {
          log.debug("[OaLaunchServer] fark poll failed: {}", e.getMessage());
        }
      }
    }
    if (closed) {
      return;
    }
    int exitCode = exitCode(started);
    log.info("[OaLaunchServer] Open Annihilation exited with {}", exitCode);
    notifyClients(exitCode == 0 || stopRequested ? "IDLE" : "FAIL");
  }

  /** Whether OA's status stream (JSON lines) says the match is running. */
  private static boolean matchStarted(Path statusFile) {
    try {
      return Files.isRegularFile(statusFile)
          && Files.readAllLines(statusFile, StandardCharsets.UTF_8).stream().anyMatch(LibraryCommands::isMatchStarted);
    } catch (IOException e) {
      log.debug("[OaLaunchServer] status stream not readable yet: {}", e.getMessage());
      return false;
    }
  }

  private static int exitCode(Process p) {
    try {
      return p.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return -1;
    }
  }

  private void stopOa() {
    Process p = process;
    if (p != null && p.isAlive()) {
      log.info("[OaLaunchServer] /quit: stopping Open Annihilation");
      stopRequested = true;
      p.destroy();
    }
  }

  private void notifyClients(String message) {
    for (Socket client : clients) {
      try {
        reply(client, message);
      } catch (IOException e) {
        clients.remove(client);
      }
    }
  }

  private static void reply(Socket client, String message) throws IOException {
    synchronized (client) {
      client.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
      client.getOutputStream().flush();
    }
  }

  @Override
  public void close() {
    closed = true;
    stopOa();
    for (Socket client : clients) {
      try {
        client.close();
      } catch (IOException ignored) {
        // closing anyway
      }
    }
    try {
      if (serverSocket != null) {
        serverSocket.close();
      }
    } catch (IOException ignored) {
      // closing anyway
    }
  }
}
