package com.faforever.client.map;

import com.faforever.client.fx.PlatformService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.io.DownloadService;
import com.faforever.client.notification.DismissAction;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.CompletableTask;
import com.faforever.commons.io.Unzipper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DownloadMapTask extends CompletableTask<Void> {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PlatformService platformService;
  private final PreferencesService preferencesService;
  private final NotificationService notificationService;
  private final DownloadService downloadService;
  private final I18n i18n;

  private URL mapUrl;
  private String hpiArchiveName;
  private Path installationPath;

  @Inject
  public DownloadMapTask(PlatformService platformService, PreferencesService preferencesService,
                         NotificationService notificationService, DownloadService downloadService, I18n i18n) {
    super(Priority.HIGH);
    this.platformService = platformService;
    this.preferencesService = preferencesService;
    this.notificationService = notificationService;
    this.downloadService = downloadService;
    this.i18n = i18n;
  }

  private Checksum getCrc(Path target) throws IOException {
    byte[] data = Files.readAllBytes(target);
    Checksum checksum = new CRC32();
    checksum.update(data);
    return checksum;
  }

  @Override
  protected Void call() throws Exception {

    updateTitle(i18n.get("mapDownloadTask.title", hpiArchiveName));

    Objects.requireNonNull(mapUrl, "mapUrl has not been set");
    Objects.requireNonNull(hpiArchiveName, "hpiArchiveName has not been set");
    Objects.requireNonNull(installationPath, "installationPath has not been set");

    URLConnection urlConnection = mapUrl.openConnection();
    int bytesToRead = urlConnection.getContentLength();
    String content = urlConnection.getContentType();

    Path cacheDirectory = preferencesService.getCacheDirectory().resolve("maps");
    long cacheFreeSpace = cacheDirectory.toFile().getFreeSpace();

    Path target = installationPath.resolve(hpiArchiveName);
    if (target.toFile().exists()) {
      logger.info("{} already exists, skipping install", target);
      return null;
    }

    Path downloadDirectory = cacheDirectory;
    if (cacheFreeSpace < 10*bytesToRead) {
      downloadDirectory = installationPath;
    }

    target = downloadDirectory.resolve(hpiArchiveName);
    if (content != null && content.equals("application/zip") ||  // @todo pass down expected file size or crc so we can cache zip files too
        !target.toFile().exists() ||
        bytesToRead>1000 && Files.size(target)!=bytesToRead ||
        bytesToRead==0) {
      logger.info("Downloading archive {} {} bytes from {} to {}", hpiArchiveName, bytesToRead, mapUrl, downloadDirectory);
      try (InputStream inputStream = urlConnection.getInputStream()) {
        if (content.equals("application/zip")) {
          Unzipper.from(inputStream)
              .zipBombByteCountThreshold(100_000_000)
              .to(downloadDirectory)
              .totalBytes(bytesToRead)
              .listener(this::updateProgress) // @todo this only notifies progress on each file within the zip??c
              .unzip();
        }
        else {
          downloadService.downloadFile(mapUrl, downloadDirectory.resolve(hpiArchiveName), this::updateProgress);
        }
      }
      catch (FileNotFoundException e) {
        notificationService.addNotification(new ImmediateNotification(
            i18n.get("mapDownloadTask.title", hpiArchiveName),
            i18n.get("mapDownloadTask.notFound", hpiArchiveName),
            Severity.WARN, Collections.singletonList(new DismissAction(i18n))));
        return null;
      }
    }

    if (target.toFile().exists() && bytesToRead > 1000 && Files.size(target) != bytesToRead) {
      logger.warn("Download is not expected size! expected {}, received {}. Aborting", bytesToRead, Files.size(target));
      Files.delete(target);
      return null;
    }

    if (!downloadDirectory.equals(installationPath)) {
      logger.info("Installing archive {} from cache {} to {}", hpiArchiveName, downloadDirectory, installationPath);
      Files.copy(downloadDirectory.resolve(hpiArchiveName), installationPath.resolve(hpiArchiveName), StandardCopyOption.REPLACE_EXISTING);
    }

    if (!installationPath.resolve(hpiArchiveName).toFile().exists()) {
      notificationService.addNotification(new ImmediateNotification(
          i18n.get("mapDownloadTask.title", hpiArchiveName), "Install failed",
          Severity.ERROR, Collections.singletonList(new DismissAction(i18n))));
      return null;
    }

    return null;
  }

  public void setMapUrl(URL mapUrl) {
    this.mapUrl = mapUrl;
  }

  public void setHpiArchiveName(String hpiArchiveName) {
    this.hpiArchiveName = hpiArchiveName;
  }

  public void setInstallationPath(Path installationPath) {
    this.installationPath = installationPath;
  }

}
