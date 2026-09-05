package com.faforever.client.replay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class ReplayArchiveWriter {

  private static final long MAX_UNCOMPRESSED_BYTES = 100_000_000;

  private ReplayArchiveWriter() {
    // utility
  }

  static void write(InputStream source, Path destination, String replayFileName) throws IOException {
    Path absoluteDestination = destination.toAbsolutePath();
    Path temporaryFile = Files.createTempFile(absoluteDestination.getParent(), ".taf-replay-", ".zip");
    try {
      try (OutputStream output = Files.newOutputStream(temporaryFile)) {
        rewrite(source, output, replayFileName);
      }
      Files.move(temporaryFile, absoluteDestination, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporaryFile);
    }
  }

  static void rewrite(InputStream source, OutputStream destination, String replayFileName) throws IOException {
    boolean replayRenamed = false;
    long uncompressedBytes = 0;
    byte[] buffer = new byte[8192];

    try (ZipInputStream zipInput = new ZipInputStream(source);
         ZipOutputStream zipOutput = new ZipOutputStream(destination)) {
      ZipEntry inputEntry;
      while ((inputEntry = zipInput.getNextEntry()) != null) {
        String outputName = inputEntry.getName();
        if (!replayRenamed && !inputEntry.isDirectory() && isReplayFile(outputName)) {
          outputName = replayFileName;
          replayRenamed = true;
        }

        ZipEntry outputEntry = new ZipEntry(outputName);
        outputEntry.setComment(inputEntry.getComment());
        outputEntry.setTime(inputEntry.getTime());
        zipOutput.putNextEntry(outputEntry);

        int count;
        while ((count = zipInput.read(buffer)) != -1) {
          uncompressedBytes += count;
          if (uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
            throw new ZipException("Replay archive exceeds the safe uncompressed size");
          }
          zipOutput.write(buffer, 0, count);
        }
        zipOutput.closeEntry();
        zipInput.closeEntry();
      }
    }

    if (!replayRenamed) {
      throw new ZipException("Replay archive does not contain a .tad file");
    }
  }

  private static boolean isReplayFile(String entryName) {
    return entryName.toLowerCase(Locale.ROOT).endsWith(".tad");
  }
}
