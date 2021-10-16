package com.faforever.client.replay;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.i18n.I18n;
import com.faforever.client.io.DownloadService;
import com.faforever.client.task.CompletableTask;
import com.faforever.commons.io.Unzipper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.inject.Inject;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ReplayDownloadTask extends CompletableTask<Path> {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final I18n i18n;
  private final ClientProperties clientProperties;
  private final DownloadService downloadService;

  private String replayId;
  private URL replayUrl;
  private Path downloadPath;

  @Inject
  public ReplayDownloadTask(I18n i18n, ClientProperties clientProperties,
                            DownloadService downloadService) {
    super(Priority.HIGH);

    this.i18n = i18n;
    this.clientProperties = clientProperties;
    this.downloadService = downloadService;
  }

  @Override
  protected Path call() throws Exception {
    updateTitle(i18n.get("mapReplayTask.title", replayId));
    Assert.state(downloadPath != null, "you must set the replay download path!");
    if (replayUrl == null) {
      replayUrl = new URL(Replay.getReplayUrl(Integer.parseInt(replayId), clientProperties.getVault().getReplayDownloadUrlFormat()));
    }

    logger.info("Downloading replay {} from {}", replayId, replayUrl);
    HttpURLConnection urlConnection = (HttpURLConnection) replayUrl.openConnection();
    urlConnection.setInstanceFollowRedirects(true);
    // I don't know man .... only required when source is a zip file
    while (urlConnection.getHeaderFields().containsKey("Location")) {
      replayUrl = new URL(urlConnection.getHeaderFields().get("Location").get(0));
      urlConnection = (HttpURLConnection) replayUrl.openConnection();
      urlConnection.setInstanceFollowRedirects(true);
    }

    try (InputStream inputStream = urlConnection.getInputStream()) {
      if (urlConnection.getContentType().equals("application/zip")) {
        Unzipper.from(inputStream)
            .zipBombByteCountThreshold(100_000_000)
            .to(downloadPath.getParent())
            .totalBytes(urlConnection.getContentLength())
            .listener(this::updateProgress) // @todo this only notifies progress on each file within the zip??c
            .unzip();
      }
      else {
        downloadService.downloadFile(replayUrl, downloadPath, this::updateProgress);
      }
    }

    return downloadPath;
  }

  public ReplayDownloadTask setReplayId(String replayId) {
    this.replayId = replayId;
    return this;
  }

  public ReplayDownloadTask setReplayUrl(URL url) {
    this.replayUrl = url;
    return this;
  }

  public ReplayDownloadTask setDownloadPath(Path downloadPath) {
    this.downloadPath = downloadPath;
    return this;
  }

}
