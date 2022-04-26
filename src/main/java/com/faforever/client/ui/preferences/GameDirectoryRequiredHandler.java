package com.faforever.client.ui.preferences;

import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.mod.FeaturedModInstallController;
import com.faforever.client.mod.ModService;
import com.faforever.client.remote.FafService;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.StageHolder;
import com.faforever.client.ui.preferences.event.GameDirectoryChooseEvent;
import com.faforever.client.ui.preferences.event.GameDirectoryChosenEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Optional;


@Component
@RequiredArgsConstructor
public class GameDirectoryRequiredHandler implements InitializingBean {

  private final EventBus eventBus;
  private final ModService modService;
  private final UiService uiService;
  private  FeaturedModInstallController featuredModInstallController;

  @Override
  public void afterPropertiesSet() {
    eventBus.register(this);
  }

  @Subscribe
  public void onChooseGameDirectory(GameDirectoryChooseEvent event) {
    JavaFxUtil.runLater(() -> {
      modService.getFeaturedMod(event.getModTechnicalName())
          .thenAccept(fm -> JavaFxUtil.runLater(() -> {
            if (featuredModInstallController == null) {
              featuredModInstallController = uiService.loadFxml("theme/featured_mod_install.fxml");
            }
            featuredModInstallController.setFeaturedMod(fm);
            featuredModInstallController.setInstalledPathFuture(event.getFuture());
            featuredModInstallController.show(StageHolder.getStage());
          }));
    });
  }

}
