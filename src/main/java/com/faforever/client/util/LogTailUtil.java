package com.faforever.client.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Helpers for fitting verbose text logs into the small (~1 MB) game-log upload bundle without losing
 * the part that matters. The crash-relevant content of a rolling log is at its tail, so instead of
 * dropping a whole oversized file we keep only its last N bytes.
 */
@Slf4j
public final class LogTailUtil {

  private LogTailUtil() {
  }

  /**
   * Returns a copy of {@code files} in which every oversized text log has been replaced by a tailed
   * copy (see {@link #tailIfTooLarge}); all other entries pass through unchanged.
   */
  public static File[] tailLargeTextLogs(File[] files, Path tempDir, long maxBytes) {
    File[] result = new File[files.length];
    for (int i = 0; i < files.length; i++) {
      result[i] = tailIfTooLarge(files[i], tempDir, maxBytes);
    }
    return result;
  }

  /**
   * If {@code source} is a text log (.log/.txt) larger than {@code maxBytes}, writes a copy containing
   * only its last ~{@code maxBytes} bytes (trimmed to a line boundary, with a one-line truncation
   * header) into {@code tempDir} under the original file name — so the zip entry name is preserved —
   * and returns that copy. JVM crash logs (hs_err_*) are never tailed because their crucial content is
   * at the head. Best-effort: on any IO error the original file is returned unchanged.
   */
  public static File tailIfTooLarge(File source, Path tempDir, long maxBytes) {
    if (source == null || !source.isFile() || source.length() <= maxBytes) {
      return source;
    }
    String lower = source.getName().toLowerCase(Locale.ROOT);
    boolean textLog = lower.endsWith(".log") || lower.endsWith(".txt");
    boolean jvmCrashLog = lower.startsWith("hs_err"); // crash summary is at the HEAD; never tail it
    if (!textLog || jvmCrashLog) {
      return source;
    }
    File tailFile = tempDir.resolve(source.getName()).toFile();
    try (RandomAccessFile raf = new RandomAccessFile(source, "r");
         OutputStream out = new BufferedOutputStream(new FileOutputStream(tailFile))) {
      raf.seek(source.length() - maxBytes);
      // Advance past the (partial) first line so we don't start mid-line / mid-character.
      int b;
      while ((b = raf.read()) != -1 && b != '\n') {
        // discard partial line
      }
      long shown = source.length() - raf.getFilePointer();
      String header = String.format("[log truncated for upload: showing last %d of %d bytes]%n",
          shown, source.length());
      out.write(header.getBytes(StandardCharsets.UTF_8));
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = raf.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      out.flush();
      log.info("[tailIfTooLarge] tailed {} from {} to {} bytes for upload",
          source.getName(), source.length(), tailFile.length());
      return tailFile;
    } catch (IOException e) {
      log.warn("[tailIfTooLarge] could not tail {}; using whole file: {}", source, e.toString());
      return source;
    }
  }

  /** Recursively deletes {@code dir} (a temp directory of tailed copies); never throws. */
  public static void deleteRecursivelyQuietly(Path dir) {
    if (dir == null) {
      return;
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(p -> {
            try {
              Files.deleteIfExists(p);
            } catch (IOException ignored) {
              // best effort
            }
          });
    } catch (IOException ignored) {
      // best effort
    }
  }
}
