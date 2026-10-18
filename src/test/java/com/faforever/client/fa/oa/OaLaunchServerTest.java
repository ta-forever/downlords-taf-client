package com.faforever.client.fa.oa;

import com.faforever.client.fa.oa.OaLaunchServer.Mode;
import com.faforever.client.library.LibraryLaunchPlan;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;

/**
 * Pins the talauncher protocol gpgnet4ta's LaunchClient relies on, with a fake in place of Open Annihilation.
 */
public class OaLaunchServerTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private final List<List<String>> started = new CopyOnWriteArrayList<>();
  private FakeProcess process;
  private OaLaunchServer server;
  private Socket client;

  /** A process that lives until {@link #exit(int)}. */
  private static final class FakeProcess extends Process {
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile int exitCode;
    volatile boolean destroyed;

    void exit(int code) {
      exitCode = code;
      done.countDown();
    }

    @Override
    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream getErrorStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() throws InterruptedException {
      done.await();
      return exitCode;
    }

    @Override
    public int exitValue() {
      if (done.getCount() > 0) {
        throw new IllegalThreadStateException();
      }
      return exitCode;
    }

    @Override
    public boolean isAlive() {
      return done.getCount() > 0;
    }

    @Override
    public void destroy() {
      destroyed = true;
      exit(143);
    }
  }

  private void startServer(Mode mode) throws IOException {
    Path logs = temp.getRoot().toPath();
    server = new OaLaunchServer(Path.of("oa", "open-annihilation"), Path.of("games", "TAF-Core"), "", id -> logs.resolve("oa.log"),
        logs, mode, (command, workDir, log) -> {
      started.add(command);
      process = new FakeProcess();
      return process;
    });
    int port = server.start();
    client = new Socket(InetAddress.getByName("127.0.0.1"), port);
    client.setSoTimeout(5000);
  }

  @Before
  public void setUp() {
    // each test starts its own server (the mode differs)
  }

  @After
  public void tearDown() throws IOException {
    if (client != null) {
      client.close();
    }
    if (server != null) {
      server.close();
    }
  }

  private void send(String text) throws IOException {
    client.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
    client.getOutputStream().flush();
  }

  private String receive() throws IOException {
    byte[] buffer = new byte[256];
    int read = client.getInputStream().read(buffer);
    return new String(buffer, 0, read, StandardCharsets.UTF_8);
  }

  @Test
  public void joinStartsOaAndAnswersRunning() throws Exception {
    startServer(Mode.GAME);

    send("/join 196786 {guid} TheCoreCommander 127.0.0.1 https://api/launch_codes secret-token");

    assertThat(receive(), is("RUNNING"));
    assertThat(started.size(), is(1));
    List<String> command = started.get(0);
    assertThat(command, hasItems("--game-dir", "--base-game", "-c"));
    assertThat(command, not(hasItem("--fark")));
    String file = Files.readString(Path.of(command.get(command.indexOf("-c") + 1)));
    assertThat(file.contains("role: join"), is(true));
    assertThat(file.contains("address: \"127.0.0.1\""), is(true));
    assertThat(file.contains("application-guid: \"{guid}\""), is(true));
    assertThat(file.contains("name: \"TheCoreCommander\""), is(true));
    assertThat(file.contains("content:"), is(false));
    assertThat(file.contains("secret-token"), is(false));
    assertThat(started.get(0), not(hasItem("secret-token")));
  }

  @Test
  public void aFeaturedModHostIsStartedFromAnOalaunchFileWithTheMapRulesGuidAndStatusStream() throws Exception {
    startServer(Mode.GAME);
    server.setHostSettings(new OaHostSetup.Settings("[Bully] Twice Upon a Time", OaHostSetup.CommanderDeath.ENDS));
    server.setFixedPositions(true);

    send("/host 196786 {guid} TheArmCommander 127.0.0.1 https://api/launch_codes secret-token");

    assertThat(receive(), is("RUNNING"));
    List<String> command = started.get(0);
    assertThat(command, hasItems("--game-dir", "--base-game", "--skip-intro", "-c"));
    assertThat(command, not(hasItem("--fark")));
    String file = Files.readString(Path.of(command.get(command.indexOf("-c") + 1)));
    assertThat(file.contains("role: host"), is(true));
    assertThat(file.contains("name: \"TAF196786\""), is(true));
    assertThat(file.contains("application-guid: \"{guid}\""), is(true));
    assertThat(file.contains("map: \"[Bully] Twice Upon a Time\""), is(true));
    assertThat(file.contains("commander-death: ends"), is(true));
    assertThat(file.contains("start-positions: fixed"), is(true));
    assertThat(file.contains("status:"), is(true));
    assertThat(file.contains("content:"), is(false));
    assertThat(file.contains("secret-token"), is(false));
  }

  @Test
  public void aFeaturedModHostMapChosenWhileStagingIsWhatOaIsStartedWith() throws Exception {
    startServer(Mode.GAME);
    server.setHostSettings(new OaHostSetup.Settings("Old Map", OaHostSetup.CommanderDeath.ENDS));

    assertThat(server.setLibraryMapKey("New Map"), is(true));
    send("/host 7 {guid} Host 127.0.0.1 a b");

    assertThat(receive(), is("RUNNING"));
    List<String> command = started.get(0);
    assertThat(Files.readString(Path.of(command.get(command.indexOf("-c") + 1))).contains("map: \"New Map\""), is(true));
  }

  @Test
  public void aSecondRequestWhileOaRunsDoesNotStartAnotherOa() throws Exception {
    startServer(Mode.GAME);
    send("/join 1 {guid} Player 127.0.0.1 a b");
    assertThat(receive(), is("RUNNING"));

    send("/join 1 {guid} Player 127.0.0.1 a b");

    assertThat(receive(), is("RUNNING"));
    assertThat(started.size(), is(1));
  }

  @Test
  public void exitingCleanlyIsReportedAsIdle() throws Exception {
    startServer(Mode.GAME);
    send("/join 1 {guid} Player 127.0.0.1 a b");
    assertThat(receive(), is("RUNNING"));

    process.exit(0);

    assertThat(receive(), is("IDLE"));
  }

  @Test
  public void crashingIsReportedAsFail() throws Exception {
    startServer(Mode.GAME);
    send("/join 1 {guid} Player 127.0.0.1 a b");
    assertThat(receive(), is("RUNNING"));

    process.exit(1);

    assertThat(receive(), is("FAIL"));
  }

  @Test
  public void quitStopsOa() throws Exception {
    startServer(Mode.GAME);
    send("/join 1 {guid} Player 127.0.0.1 a b");
    assertThat(receive(), is("RUNNING"));

    send("/quit");

    assertThat(process.done.await(5, TimeUnit.SECONDS), is(true));
    assertThat(process.destroyed, is(true));
    // stopping it on request is not a crash
    assertThat(receive(), is("IDLE"));
  }

  @Test
  public void keepaliveIsSilentAndUnknownCommandsReportState() throws Exception {
    startServer(Mode.GAME);

    send("/status");
    assertThat(receive(), is("IDLE"));

    // the TAF client sends its keepalive on a connection of its own, and the protocol has no terminator
    try (Socket keepalive = new Socket(InetAddress.getByName("127.0.0.1"), server.getPort())) {
      keepalive.getOutputStream().write("/keepalive".getBytes(StandardCharsets.UTF_8));
      keepalive.getOutputStream().flush();
      keepalive.setSoTimeout(300);
      assertThrows(java.net.SocketTimeoutException.class, () -> keepalive.getInputStream().read());
    }
    send("/join 1 {guid} Player 127.0.0.1 a b");
    assertThat(receive(), is("RUNNING"));
  }

  @Test
  public void failversionIsRelayedAsFail() throws Exception {
    startServer(Mode.GAME);

    send("/failversion tdraw.dll bad");

    assertThat(receive(), is("FAIL"));
    assertThat(started.size(), is(0));
  }

  @Test
  public void aMalformedHostRequestFails() throws Exception {
    startServer(Mode.GAME);

    send("/host 1");

    assertThat(receive(), is("FAIL"));
    assertThat(started.size(), is(0));
  }

  @Test
  public void replayViewerModeJoinsTheReplayerForASearchJoin() throws Exception {
    startServer(Mode.REPLAY_VIEWER);

    send("/searchjoin 0 {guid} Watcher 127.0.0.1");

    assertThat(receive(), is("RUNNING"));
    assertThat(started.get(0), hasItems("--replay-viewer", "-n1:127.0.0.1"));
    assertThat(started.get(0), not(hasItem("--player-name")));
    assertThat(started.get(0), not(hasItem("--fark")));
  }

  private void startLibraryServer(LibraryLaunchPlan plan) throws IOException {
    startLibraryServer(plan, Mode.GAME);
  }

  private void startLibraryServer(LibraryLaunchPlan plan, Mode mode) throws IOException {
    Path logs = temp.getRoot().toPath();
    server = new OaLaunchServer(Path.of("oa", "open-annihilation"), null, "", id -> logs.resolve("oa.log"),
        logs, mode, plan, (command, workDir, log) -> {
      started.add(command);
      process = new FakeProcess();
      return process;
    });
    int port = server.start();
    client = new Socket(InetAddress.getByName("127.0.0.1"), port);
    client.setSoTimeout(5000);
  }

  private String launchFileOf(List<String> command) throws IOException {
    assertThat(command.get(1), is("-c"));
    return Files.readString(Path.of(command.get(2)));
  }

  @Test
  public void aLibraryHostIsStartedFromAnOalaunchFileNotFromGameDirSwitches() throws Exception {
    startLibraryServer(new LibraryLaunchPlan("talon", "totala", List.of("core-contingency"), "Red Planet@pack"));

    send("/host 196786 {4a7c1d0e-6c62-5d6e-9a49-0f1d2c3b4a59} TheArmCommander 127.0.0.1 https://api/launch_codes secret-token");

    assertThat(receive(), is("RUNNING"));
    List<String> command = started.get(0);
    assertThat(command.size(), is(3));
    assertThat(command, not(hasItem("--game-dir")));
    assertThat(command, not(hasItem("--fark")));
    assertThat(command, not(hasItem("secret-token")));
    String file = launchFileOf(command);
    assertThat(file.contains("role: host"), is(true));
    assertThat(file.contains("name: \"TAF196786\""), is(true));
    assertThat(file.contains("application-guid: \"{4a7c1d0e-6c62-5d6e-9a49-0f1d2c3b4a59}\""), is(true));
    assertThat(file.contains("map: \"Red Planet@pack\""), is(true));
    assertThat(file.contains("mod: \"talon\""), is(true));
    assertThat(file.contains("expansions: [\"core-contingency\"]"), is(true));
    assertThat(file.contains("name: \"TheArmCommander\""), is(true));
    assertThat(file.contains("secret-token"), is(false));
  }

  @Test
  public void aLiveReplayOfALibraryGameJoinsTheReplayerWithTheInstalledModAndItsContent() throws Exception {
    Path modFolder = Path.of("Mods", "talon-1.02");
    startLibraryServer(new LibraryLaunchPlan("talon", "totala", List.of("core-contingency"), null, modFolder),
        Mode.REPLAY_VIEWER);

    send("/searchjoin 0 {guid} Watcher 127.0.0.1");

    assertThat(receive(), is("RUNNING"));
    List<String> command = started.get(0);
    assertThat(command, hasItems("--replay-viewer", "-n1:127.0.0.1", "--mod-dir", modFolder.toString(),
        "--content", "totala:core-contingency"));
    // --game-dir is the Library's own base game folder, there only when this machine has one
    assertThat(command, not(hasItem("--player-name")));
  }

  @Test
  public void aLibraryJoinerJoinsTheLoopbackProxyWithTheSameContent() throws Exception {
    startLibraryServer(new LibraryLaunchPlan("talon", "totala", List.of("core-contingency"), null));

    send("/join 196786 {4a7c1d0e-6c62-5d6e-9a49-0f1d2c3b4a59} TheCoreCommander 127.0.0.1 a b");

    assertThat(receive(), is("RUNNING"));
    String file = launchFileOf(started.get(0));
    assertThat(file.contains("role: join"), is(true));
    assertThat(file.contains("address: \"127.0.0.1\""), is(true));
    assertThat(file.contains("mod: \"talon\""), is(true));
    assertThat(file.contains("map:"), is(false));
  }

  @Test
  public void aLibraryGameThatEndsWithOasLeftCodeIsNotACrash() throws Exception {
    startLibraryServer(new LibraryLaunchPlan("talon", "totala", List.of(), null));
    send("/join 1 {guid} Player 127.0.0.1 a b");
    assertThat(receive(), is("RUNNING"));

    process.exit(0);

    assertThat(receive(), is("IDLE"));
  }

  @Test
  public void aLibraryHostIsToldTheMatchLaunchedWhenTheStatusStreamSaysSo() throws Exception {
    startLibraryServer(new LibraryLaunchPlan("talon", "totala", List.of(), "Acid Pools"));
    send("/host 7 {guid} Host 127.0.0.1 a b");
    assertThat(receive(), is("RUNNING"));
    String file = launchFileOf(started.get(0));
    String statusPath = file.lines().filter(line -> line.startsWith("  file: ")).findFirst().orElseThrow()
        .substring("  file: ".length()).replace("\"", "").replace("\\\\", "\\");

    Files.writeString(Path.of(statusPath), "{\"event\":\"hello\"}\n"
        + "{\"event\":\"match\",\"seq\":7,\"change\":\"started\"}\n");

    client.setSoTimeout(8000);
    assertThat(receive(), is("LAUNCHED"));
  }

  @Test
  public void aLibraryHostMapChosenWhileStagingIsWhatOaIsStartedWith() throws Exception {
    startLibraryServer(new LibraryLaunchPlan("talon", "totala", List.of(), "Acid Pools"));

    assertThat(server.setLibraryMapKey("Red Planet@pack"), is(true));
    send("/host 7 {guid} Host 127.0.0.1 a b");
    assertThat(receive(), is("RUNNING"));

    assertThat(launchFileOf(started.get(0)).contains("map: \"Red Planet@pack\""), is(true));
  }

  @Test
  public void aLibraryHostMapCannotBeChangedOnceOaIsRunning() throws Exception {
    startLibraryServer(new LibraryLaunchPlan("talon", "totala", List.of(), "Acid Pools"));
    send("/host 7 {guid} Host 127.0.0.1 a b");
    assertThat(receive(), is("RUNNING"));

    assertThat(server.setLibraryMapKey("Red Planet@pack"), is(false));
    assertThat(launchFileOf(started.get(0)).contains("map: \"Acid Pools\""), is(true));
  }
}
