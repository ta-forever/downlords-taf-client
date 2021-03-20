package com.faforever.client.map;

import com.faforever.client.api.FafApiAccessor;
import com.faforever.client.i18n.I18n;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.ResourceLocks;
import com.faforever.client.util.Validator;
import com.faforever.commons.io.ByteCountListener;
import com.faforever.commons.io.Zipper;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.faforever.commons.io.Bytes.formatSize;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.newOutputStream;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MapUploadTask extends CompletableTask<Void> implements InitializingBean {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PreferencesService preferencesService;
  private final FafApiAccessor fafApiAccessor;
  private final I18n i18n;

  private String archiveFileName;
  private Path stagingDirectory;
  private Boolean isRanked;
  private List<Map<String,String>> mapDetails;

  @Inject
  public MapUploadTask(PreferencesService preferencesService, FafApiAccessor fafApiAccessor, I18n i18n) {
    super(Priority.HIGH);
    this.preferencesService = preferencesService;
    this.fafApiAccessor = fafApiAccessor;
    this.i18n = i18n;
  }

  @Override
  public void afterPropertiesSet() {
    updateTitle(i18n.get("mapVault.upload.uploading"));
  }

  @Override
  protected Void call() throws Exception {
    Validator.notNull(stagingDirectory, "stagingDirectory must not be null");
    Validator.notNull(archiveFileName, "archiveFileName must not be null");
    Validator.notNull(isRanked, "isRanked must not be null");
    Validator.notNull(mapDetails, "mapDetails must not be null");

    if (!Files.exists(stagingDirectory.resolve("mini"))) {
      throw new FileNotFoundException("no previews found in staging directory");
    }
    if (!Files.exists(stagingDirectory.resolve(archiveFileName))) {
      throw new FileNotFoundException("expected map archive not found in staging directory");
    }
    if (!stagingDirectory.getFileName().toString().equals(archiveFileName)) {
      throw new FileNotFoundException("staging directory must be given same name as map archive");
    }

    ResourceLocks.acquireUploadLock();
    Path tmpFile = createTempFile(stagingDirectory.getParent(), "mapupload", ".tar");

    try {
      logger.debug("Zipping map {} to {}", stagingDirectory, tmpFile);
      updateTitle(i18n.get("mapVault.upload.compressing"));

      Locale locale = i18n.getUserSpecificLocale();
      ByteCountListener byteListener = (written, total) -> {
        updateMessage(i18n.get("bytesProgress", formatSize(written, locale), formatSize(total, locale)));
        updateProgress(written, total);
      };

      try (OutputStream outputStream = newOutputStream(tmpFile)) {
        //Zipper.of(stagingDirectory)
        Zipper.of(stagingDirectory, ArchiveStreamFactory.TAR)
            .to(outputStream)
            .listener(byteListener)
            .zip();
      }

      logger.debug("Uploading map {} as {}", stagingDirectory, tmpFile);
      updateTitle(i18n.get("mapVault.upload.uploading"));

      fafApiAccessor.uploadMap(tmpFile, isRanked, mapDetails, byteListener);
      return null;
    } finally {
      Files.delete(tmpFile);
      ResourceLocks.freeUploadLock();
    }
  }

  public void setArchiveFileName(String archiveFileName) {
    this.archiveFileName = archiveFileName;
  }

  public void setStagingDirectory(Path stagingDirectory) {
    this.stagingDirectory = stagingDirectory;
  }

  public void setRanked(boolean ranked) {
    this.isRanked = ranked;
  }

  public void setMapDetails(List<Map<String,String>> mapDetails) {
    this.mapDetails = mapDetails;
  }
}
