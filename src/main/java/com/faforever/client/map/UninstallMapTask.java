package com.faforever.client.map;

import com.faforever.client.io.FileUtils;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.CompletableTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UninstallMapTask extends CompletableTask<Void> {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final MapService mapService;

  private Path installationPath;
  private String hpiArchiveName;

  @Inject
  public UninstallMapTask(MapService mapService) {
    super(Priority.LOW);
    this.mapService = mapService;
  }

  public void setInstallationPath(Path installationPath) {
    this.installationPath = installationPath;
  }
  public void setHpiArchiveName(String hpiArchiveName) { this.hpiArchiveName = hpiArchiveName; }

  @Override
  protected Void call() throws Exception {
    Objects.requireNonNull(installationPath, "installationPath has not been set");
    Objects.requireNonNull(hpiArchiveName, "hpiArchiveName has not been set");

    Path archiveFullPath = installationPath.resolve(hpiArchiveName);
    logger.info("Uninstalling map archive '{}'", archiveFullPath);
    Files.deleteIfExists(archiveFullPath);
    return null;
  }
}
