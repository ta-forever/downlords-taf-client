package com.faforever.client.io;

import com.faforever.client.task.ResourceLocks;
import com.faforever.commons.io.ByteCopier;
import com.faforever.commons.io.ByteCountListener;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Lazy
@Slf4j
public class DownloadService {

  public void downloadFile(URL url, Path targetFile, ByteCountListener progressListener) throws IOException {
    if (!Files.exists(targetFile.getParent())) {
      Files.createDirectories(targetFile.getParent());
    }
    Path tempFile = Files.createTempFile(targetFile.getParent(), "download", null);

    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

    ResourceLocks.acquireDownloadLock();
    try (InputStream inputStream = url.openStream();
         OutputStream outputStream = Files.newOutputStream(tempFile)) {

      ByteCopier.from(inputStream)
          .to(outputStream)
          .totalBytes(urlConnection.getContentLength())
          .listener(progressListener)
          .copy();

      Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      ResourceLocks.freeDownloadLock();
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException e) {
        log.warn("Could not delete temporary file: " + tempFile.toAbsolutePath(), e);
      }
    }
  }

  // @param charsetName eg StandardCharsets.UTF_8.name()
  public <T> T downloadJson(URL url, Class<T> classT) throws IOException {
    ResourceLocks.acquireDownloadLock();
    try (InputStream inputStream = url.openStream();
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int length;
      while ((length = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, length);
      }
      String json = outputStream.toString(StandardCharsets.UTF_8.name());
      Gson gson = new Gson();
      return gson.fromJson(json, classT);

    } finally {
      ResourceLocks.freeDownloadLock();
    }
  }
}
