package com.faforever.client.game;

import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.preferences.event.MissingGamePathEvent;
import com.faforever.client.ui.preferences.event.GameDirectoryChosenEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import javafx.scene.control.TextInputDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

@Component
public class GamePathHandler implements InitializingBean {
  private static final String DIRECTPLAY_TOTAL_ANNIHILATION_REGKEY = "SOFTWARE\\WOW6432Node\\Microsoft\\DirectPlay\\Applications\\Total Annihilation";

  private static final Collection<Path> USUAL_GAME_PATHS = Arrays.asList(
      Platform.isWindows() && Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, DIRECTPLAY_TOTAL_ANNIHILATION_REGKEY) ?
          Paths.get(Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, DIRECTPLAY_TOTAL_ANNIHILATION_REGKEY, "Path")) : null,
      Platform.isWindows() && Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, DIRECTPLAY_TOTAL_ANNIHILATION_REGKEY) ?
          Paths.get(Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, DIRECTPLAY_TOTAL_ANNIHILATION_REGKEY, "CurrentDirectory")) : null,
      Paths.get(System.getProperty("user.home"), "GOG Games", "Total Annihilation"), // @todo verify default GOG install path
      Paths.get(System.getProperty("user.home"), ".steam", "steam", "steamapps", "common", "Total Annihilation"), // @todo verify default steam install path
      Paths.get("C:\\CAVEDOG\\TOTALA"),
      Paths.get("D:\\CAVEDOG\\TOTALA")
      );
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final NotificationService notificationService;
  private final I18n i18n;
  private final EventBus eventBus;
  private final PreferencesService preferencesService;

  public GamePathHandler(NotificationService notificationService, I18n i18n, EventBus eventBus, PreferencesService preferencesService) {
    this.notificationService = notificationService;
    this.i18n = i18n;
    this.eventBus = eventBus;
    this.preferencesService = preferencesService;

  }

  @Override
  public void afterPropertiesSet() {
    eventBus.register(this);
  }

  /**
   * Checks whether the chosen game path contains a ForgedAlliance.exe (either directly if the user selected the "bin"
   * directory, or in the "bin" sub folder). If the path is valid, it is stored in the preferences.
   */
  @Subscribe
  public void onGameDirectoryChosenEvent(GameDirectoryChosenEvent event) {
    final String modTechnical = event.getModTechnicalName();
    final Path gamePath = event.getPath();
    final String commandLineOptions = event.getCommandLineOptions();

    Optional<CompletableFuture<Path>> future = event.getFuture();

    if (gamePath == null) {
      notificationService.addNotification(new ImmediateNotification(i18n.get("gameSelect.select.invalidPath"), i18n.get("gamePath.select.noneChosen"), Severity.WARN));
      future.ifPresent(pathCompletableFuture -> pathCompletableFuture.completeExceptionally(new CancellationException("User cancelled")));
      return;
    }

    String gamePathValidWithError;
    try {
      gamePathValidWithError = preferencesService.isGamePathValidWithError(gamePath);
    } catch (Exception e) {
      notificationService.addImmediateErrorNotification(e, "gamePath.select.error");
      future.ifPresent(pathCompletableFuture -> pathCompletableFuture.completeExceptionally(e));
      return;
    }
    if (gamePathValidWithError != null) {
      notificationService.addNotification(new ImmediateNotification(i18n.get("gameSelect.select.invalidPath"), i18n.get(gamePathValidWithError), Severity.WARN));
      future.ifPresent(pathCompletableFuture -> pathCompletableFuture.completeExceptionally(new IllegalArgumentException("Invalid path")));
      return;
    }

    logger.info("Found game path at {}", gamePath);
    final TotalAnnihilationPrefs prefs = preferencesService.getTotalAnnihilation(modTechnical);
    prefs.setInstalledPath(gamePath);
    prefs.setCommandLineOptions(commandLineOptions);

    preferencesService.storeInBackground();
    future.ifPresent(pathCompletableFuture -> pathCompletableFuture.complete(gamePath));
  }


  private void detectGamePath(String modTechnical) {
    for (Path path : USUAL_GAME_PATHS) {
      if (path != null && preferencesService.isGamePathValid(path)) {
        onGameDirectoryChosenEvent(new GameDirectoryChosenEvent(path, "", Optional.empty(), modTechnical));
        return;
      }
    }

    logger.info("Game path could not be detected");
    eventBus.post(new MissingGamePathEvent(modTechnical));
  }

  public void detectAndUpdateGamePath(String modTechnical) {
    Path taExePath = preferencesService.getTotalAnnihilation(modTechnical).getExecutable();
    if (taExePath == null || Files.notExists(taExePath)) {
      logger.info("Game path is not specified or non-existent, trying to detect");
      detectGamePath(modTechnical);
    }
  }
}
