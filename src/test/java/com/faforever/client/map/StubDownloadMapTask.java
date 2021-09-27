package com.faforever.client.map;

import com.faforever.client.i18n.I18n;
import com.faforever.client.io.DownloadService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.PreferencesService;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.faforever.client.util.LinkOrCopy.linkOrCopy;

public class StubDownloadMapTask extends DownloadMapTask {

  private final Path mapsCacheDirectory;
  public MapBean mapToDownload;

  public StubDownloadMapTask(
      PreferencesService preferencesService, NotificationService notificationService,
      DownloadService downloadService, I18n i18n, Path mapsCacheDirectory) {
    super(preferencesService, notificationService, downloadService, i18n);
    this.mapsCacheDirectory = mapsCacheDirectory;
  }

  public void setMapToDownload(MapBean map) {
    this.mapToDownload = map;
  }

  @Override
  protected Void call() throws Exception {
    imitateMapDownload();
    return null;
  }

  private void imitateMapDownload() throws Exception {
    String hpiArchiveName = mapToDownload.getHpiArchiveName();
    linkOrCopy(
          Paths.get(getClass().getResource("/maps/" + hpiArchiveName).toURI()),
          mapsCacheDirectory.resolve(hpiArchiveName)
      );
  }
}
