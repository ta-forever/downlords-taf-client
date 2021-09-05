package com.faforever.client.replay;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.i18n.I18n;
import com.faforever.client.io.DownloadService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.CompletableTask;
import com.faforever.commons.io.ByteCopier;
import com.faforever.commons.io.Unzipper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ReplayDownloadTask extends CompletableTask<Path> {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final I18n i18n;
  private final ClientProperties clientProperties;
  private final PreferencesService preferencesService;
  private final DownloadService downloadService;

  private int replayId;

  @Inject
  public ReplayDownloadTask(I18n i18n, ClientProperties clientProperties, PreferencesService preferencesService,
                            DownloadService downloadService) {
    super(Priority.HIGH);

    this.i18n = i18n;
    this.clientProperties = clientProperties;
    this.preferencesService = preferencesService;
    this.downloadService = downloadService;
  }

  @Override
  protected Path call() throws Exception {
    updateTitle(i18n.get("mapReplayTask.title", replayId));

    URL replayUrl = new URL(Replay.getReplayUrl(replayId, clientProperties.getVault().getReplayDownloadUrlFormat()));
    Path tadPath = preferencesService.getCacheDirectory().resolve("replays").resolve(String.format("%d.tad", replayId));

    logger.info("Downloading replay {} from {}", replayId, replayUrl);
    HttpURLConnection urlConnection = (HttpURLConnection) replayUrl.openConnection();
    urlConnection.setInstanceFollowRedirects(true);

    try (InputStream inputStream = urlConnection.getInputStream()) {
      if (urlConnection.getContentType().equals("application/zip")) {
        Unzipper.from(inputStream)
            .zipBombByteCountThreshold(100_000_000)
            .to(tadPath.getParent())
            .totalBytes(urlConnection.getContentLength())
            .listener(this::updateProgress) // @todo this only notifies progress on each file within the zip??c
            .unzip();
      }
      else {
        downloadService.downloadFile(replayUrl, tadPath, this::updateProgress);
      }
    }

    return tadPath;
  }


  public void setReplayId(int replayId) {
    this.replayId = replayId;
  }
}
