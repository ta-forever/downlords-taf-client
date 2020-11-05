package com.faforever.client.map;

import com.faforever.client.api.FafApiAccessor;
import com.faforever.client.i18n.I18n;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.ResourceLocks;
import com.faforever.client.util.Validator;
import com.faforever.commons.io.ByteCountListener;
import com.faforever.commons.io.Zipper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

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

  private Path mapPath;
  private Boolean isRanked;

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
      return null;
  }

  public void setMapPath(Path mapPath) {
    this.mapPath = mapPath;
  }

  public void setRanked(boolean ranked) {
    isRanked = ranked;
  }
}
