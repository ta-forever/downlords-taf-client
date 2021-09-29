package com.faforever.client.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LinkOrCopy {

  static public void linkOrCopyWithBackup(Path source, Path dest) throws IOException {
    final Path backupFile = dest.getParent().resolve(dest.getFileName() + ".bak");
    boolean backupMade = false;
    try {
      if (Files.exists(dest)) {
        if (Files.exists(backupFile)) {
          Files.delete(backupFile);
        }
        Files.move(dest, backupFile);
        backupMade = true;
      }

      linkOrCopy(source, dest);
    }

    catch (Exception e) {
      if (backupMade && Files.exists(backupFile)) {
        if (Files.exists(dest)) {
          Files.delete(dest);
        }
        Files.move(backupFile, dest);
        throw(e);
      }
    }
  }

  static public void linkOrCopy(Path source, Path dest) throws IOException {
    if (Files.exists(dest)) {
      Files.delete(dest);
    }
    try {
      Files.createSymbolicLink(dest, source);
    } catch (UnsupportedOperationException | IOException ex1) {
      try {
        Files.createLink(dest, source);
      } catch (UnsupportedOperationException | IOException ex2) {
        Files.copy(source, dest);
      }
    }
  }
}
