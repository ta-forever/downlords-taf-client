package com.faforever.client.game;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Crash-resilient record of "a game was launched but its logs have not been uploaded yet".
 *
 * <p>A marker file is written when a game process starts ({@link #mark}) and deleted once the
 * normal game-termination handler has run and submitted the logs ({@link #clear}). If the client
 * dies (hard JVM crash, kill, power loss) between those two points, the marker survives. On the
 * next login the client scans for orphaned markers ({@link #listAndPrune}) and uploads the logs it
 * could not upload last time — before a subsequent game launch overwrites the fixed-name logs
 * (ice-adapter.log, tdrawlog.txt, ErrorLog.txt).</p>
 *
 * <p>State is a tiny {@link Properties} file per game id, so a half-written marker (interrupted
 * mid-crash) simply fails to parse and is discarded rather than corrupting anything.</p>
 */
@Slf4j
public final class GameLogUploadMarker {

  private static final String MARKER_SUFFIX = ".marker";

  private GameLogUploadMarker() {
  }

  /** One pending upload recovered from disk. {@code modTechnical} is null when it was not recorded. */
  public record Pending(int gameId, String modTechnical, long startedAtEpochMs) {
  }

  public static void mark(Path markerDir, int gameId, String modTechnical) {
    try {
      Files.createDirectories(markerDir);
      Properties props = new Properties();
      props.setProperty("gameId", Integer.toString(gameId));
      props.setProperty("modTechnical", modTechnical == null ? "" : modTechnical);
      props.setProperty("startedAtEpochMs", Long.toString(System.currentTimeMillis()));
      try (OutputStream out = Files.newOutputStream(markerFile(markerDir, gameId))) {
        props.store(out, "TAF pending game-log upload; presence of this file means the client "
            + "exited before these logs were uploaded");
      }
    } catch (IOException e) {
      log.warn("[GameLogUploadMarker] could not write marker for game {}: {}", gameId, e.toString());
    }
  }

  public static void clear(Path markerDir, int gameId) {
    try {
      Files.deleteIfExists(markerFile(markerDir, gameId));
    } catch (IOException e) {
      log.warn("[GameLogUploadMarker] could not clear marker for game {}: {}", gameId, e.toString());
    }
  }

  /**
   * Returns every pending upload still on disk, deleting markers that are unparseable or older than
   * {@code maxAgeMs} (so we never re-upload ancient logs that have long since rolled away).
   */
  public static List<Pending> listAndPrune(Path markerDir, long maxAgeMs) {
    List<Pending> result = new ArrayList<>();
    if (!Files.isDirectory(markerDir)) {
      return result;
    }
    long now = System.currentTimeMillis();
    try (Stream<Path> stream = Files.list(markerDir)) {
      List<Path> markers = stream
          .filter(p -> p.getFileName().toString().endsWith(MARKER_SUFFIX))
          .toList();
      for (Path marker : markers) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(marker)) {
          props.load(in);
        } catch (IOException e) {
          log.warn("[GameLogUploadMarker] could not read {}; discarding: {}", marker, e.toString());
          deleteQuietly(marker);
          continue;
        }
        int gameId;
        try {
          gameId = Integer.parseInt(props.getProperty("gameId", "").trim());
        } catch (NumberFormatException e) {
          deleteQuietly(marker);
          continue;
        }
        long startedAt = parseLong(props.getProperty("startedAtEpochMs"));
        if (maxAgeMs > 0 && startedAt > 0 && (now - startedAt) > maxAgeMs) {
          log.info("[GameLogUploadMarker] discarding stale marker for game {} ({} ms old)", gameId, now - startedAt);
          deleteQuietly(marker);
          continue;
        }
        String mod = props.getProperty("modTechnical", "");
        result.add(new Pending(gameId, mod.isEmpty() ? null : mod, startedAt));
      }
    } catch (IOException e) {
      log.warn("[GameLogUploadMarker] could not list marker dir {}: {}", markerDir, e.toString());
    }
    return result;
  }

  private static Path markerFile(Path markerDir, int gameId) {
    return markerDir.resolve(gameId + MARKER_SUFFIX);
  }

  private static long parseLong(String s) {
    try {
      return s == null ? 0L : Long.parseLong(s.trim());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private static void deleteQuietly(Path p) {
    try {
      Files.deleteIfExists(p);
    } catch (IOException ignored) {
      // best effort
    }
  }
}
