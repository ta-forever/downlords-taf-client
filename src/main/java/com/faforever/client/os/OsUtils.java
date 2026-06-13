package com.faforever.client.os;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.github.nocatch.NoCatch.noCatch;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public final class OsUtils {

  /**
   * Default time budget for a child process started by {@link #execAndGetOutput}. Generous enough not to trip on a
   * slow-but-healthy {@code faf-uid} (its WMI gathering on Windows can legitimately take several seconds), yet short
   * enough to surface a clear error and let the client retry instead of hanging the login forever.
   */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

  /** Grace period to let the stdout/stderr reader threads reach end-of-stream after the process has exited. */
  private static final long STREAM_DRAIN_TIMEOUT_MILLIS = 5_000;

  private OsUtils() {
    throw new AssertionError("Not instantiable");
  }

  public static String execAndGetOutput(String... cmd) throws IOException {
    return execAndGetOutput(DEFAULT_TIMEOUT, cmd);
  }

  /**
   * Runs {@code cmd}, waits up to {@code timeout} for it to finish, and returns its trimmed standard output.
   * <p>
   * Unlike a naive {@code exec().getInputStream()} read, this never blocks indefinitely: if the process outlives the
   * timeout it is forcibly terminated and an {@link IOException} is thrown. stdout and stderr are drained on separate
   * threads so a child that fills the stderr pipe buffer cannot deadlock. A non-zero exit code is also reported as an
   * {@link IOException}, with the captured stderr included for diagnosis.
   */
  public static String execAndGetOutput(Duration timeout, String... cmd) throws IOException {
    String commandLine = String.join(" ", cmd);
    Process process = new ProcessBuilder(cmd).start();

    StringBuilder stdout = new StringBuilder();
    StringBuilder stderr = new StringBuilder();
    Thread stdoutReader = drainAsync(process.getInputStream(), stdout);
    Thread stderrReader = drainAsync(process.getErrorStream(), stderr);

    try {
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        throw new IOException(String.format("Command '%s' did not complete within %d seconds and was terminated",
            commandLine, timeout.toSeconds()));
      }
      // The process has exited, so both pipes are at end-of-stream; let the readers finish appending what's left.
      stdoutReader.join(STREAM_DRAIN_TIMEOUT_MILLIS);
      stderrReader.join(STREAM_DRAIN_TIMEOUT_MILLIS);
    } catch (InterruptedException e) {
      // Typically the login was cancelled while the child was still running. Kill it and signal the interruption with a
      // distinct exception type so callers can tell a user-cancel apart from a genuine execution failure.
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      InterruptedIOException interrupted = new InterruptedIOException(
          String.format("Interrupted while waiting for command '%s'", commandLine));
      interrupted.initCause(e);
      throw interrupted;
    }

    int exitValue = process.exitValue();
    if (exitValue != 0) {
      throw new IOException(String.format("Command '%s' exited with code %d. Error output: %s",
          commandLine, exitValue, stderr.toString().trim()));
    }

    return stdout.toString().trim();
  }

  /** Reads {@code stream} to end-of-stream into {@code sink} on a daemon thread, preserving raw content (incl. newlines). */
  private static Thread drainAsync(InputStream stream, StringBuilder sink) {
    Thread thread = new Thread(() -> {
      try (Reader reader = new InputStreamReader(stream, UTF_8)) {
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
          sink.append(buffer, 0, read);
        }
      } catch (IOException e) {
        // The stream is closed when the process exits or is destroyed; nothing actionable to do here.
        log.trace("Error reading process output stream", e);
      }
    });
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  public static void gobbleLines(InputStream stream, Consumer<String> lineConsumer) {
    Thread thread = new Thread(() -> noCatch(() -> {
      try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream))) {
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          lineConsumer.accept(line);
        }
      }
    }));
    thread.setDaemon(true);
    thread.start();
  }
}
