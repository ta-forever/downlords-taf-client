package com.faforever.client.mod;

import com.faforever.client.fx.PlatformService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.CompletableTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import static com.faforever.client.task.CompletableTask.Priority.HIGH;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InstallModTask extends CompletableTask<Void> {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PlatformService platformService;
  private final PreferencesService preferencesService;
  private final I18n i18n;

  private URL url;

  @Inject
  public InstallModTask( PlatformService platformService, PreferencesService preferencesService, I18n i18n) {
    super(HIGH);
    this.platformService = platformService;
    this.preferencesService = preferencesService;
    this.i18n = i18n;
  }

  @Override
  protected Void call() throws Exception {
    platformService.showDocument(url.toString());
    return null;
  }

  public void setUrl(URL url) {
    this.url = url;
  }
}
