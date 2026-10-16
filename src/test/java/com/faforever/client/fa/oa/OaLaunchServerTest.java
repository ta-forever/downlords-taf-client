package com.faforever.client.fa.oa;

import com.faforever.client.fa.oa.OaLaunchServer.Mode;
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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
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
    assertThat(started.get(0), hasItems("--join", "127.0.0.1", "--player-name", "TheCoreCommander"));
    assertThat(started.get(0), not(hasItem("--fark")));
    assertThat(started.get(0), not(hasItem("secret-token")));
  }

  @Test
  public void hostStartsOaWithTheAutomationEndpointForLaunchDetection() throws Exception {
    startServer(Mode.GAME);

    send("/host 196786 {guid} TheArmCommander 127.0.0.1 https://api/launch_codes secret-token");

    assertThat(receive(), is("RUNNING"));
    assertThat(started.get(0), hasItems("--host", "--game-name", "TAF196786", "--fark"));
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
}
