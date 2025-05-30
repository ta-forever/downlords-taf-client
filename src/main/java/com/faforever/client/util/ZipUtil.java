package com.faforever.client.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtil {
  /**
   * Zip a list of file into one zip file.
   *
   * @param files
   *          files to zip
   * @param targetZipFile
   *          target zip file
   * @throws IOException
   *           IO error exception can be thrown when copying ...
   */
  public static void zipFile(final File[] files, final File targetZipFile, boolean ignoreIoException) throws IOException {
    try (
        FileOutputStream fos = new FileOutputStream(targetZipFile);
        ZipOutputStream zos = new ZipOutputStream(fos)
    ) {
      byte[] buffer = new byte[128];
      for (File currentFile : files) {
        if (currentFile == null || currentFile.isDirectory() || !currentFile.exists()) {
          continue;
        }

        try (
            FileInputStream fis = new FileInputStream(currentFile)
        ) {
          ZipEntry entry = new ZipEntry(currentFile.getName());
          zos.putNextEntry(entry);
          int read;
          while ((read = fis.read(buffer)) != -1) {
            zos.write(buffer, 0, read);
          }
          zos.closeEntry();
        } catch (IOException e) {
          if (!ignoreIoException) {
            throw e;
          }
        }
      }
    }
  }

}
