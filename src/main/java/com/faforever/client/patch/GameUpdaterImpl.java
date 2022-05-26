package com.faforever.client.patch;

import com.faforever.client.map.MapService;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.remote.FafService;
import com.faforever.client.task.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.springframework.context.ApplicationContext;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public class GameUpdaterImpl implements GameUpdater {

  private final List<FeaturedModUpdater> featuredModUpdaters = new ArrayList<>();
  private final ApplicationContext applicationContext;
  private final TaskService taskService;
  private final FafService fafService;
  private final MapService mapService;
  private final NotificationService notificationService;
  private final PreferencesService preferencesService;

  @Override
  public GameUpdater addFeaturedModUpdater(FeaturedModUpdater featuredModUpdater) {
    featuredModUpdaters.add(featuredModUpdater);
    return this;
  }

  @Override
  public CompletableFuture<String> update(FeaturedMod featuredMod, String version) {
    return updateFeaturedMod(featuredMod, version)
        .exceptionally(throwable -> {
          notificationService.addImmediateErrorNotification(
              throwable, "error.game.cannotUpdate", featuredMod.getDisplayName(), version);
          return null;
        });
  }

  @Override
  public CompletableFuture<List<FeaturedMod>> getFeaturedMods() {
    return fafService.getFeaturedMods();
  }

  @Override
  public CompletableFuture<Void> proactiveUpdateCurrentVersions() {
    return fafService.getFeaturedMods()
        .thenCompose(fms -> {
          CompletableFuture<String> updateFutures = CompletableFuture.completedFuture(null);
          for (FeaturedMod fm: fms) {
            TotalAnnihilationPrefs taPrefs = preferencesService.getTotalAnnihilation(fm.getTechnicalName());
            if (fm.isVisible() &&
                taPrefs.getInstalledExePath() != null &&
                !taPrefs.getInstalledExePath().toString().isEmpty() &&
                Files.exists(taPrefs.getInstalledExePath())) {
              log.info("Proactively updating {}", fm.getDisplayName());
              updateFutures = updateFutures.thenCompose((version) -> updateFeaturedMod(fm, null));
            }
          }
          return updateFutures.thenApply((aVoid) -> null);
        });
  }

  private CompletableFuture<String> updateFeaturedMod(FeaturedMod featuredMod, String version) {
    for (FeaturedModUpdater featuredModUpdater : featuredModUpdaters) {
      if (featuredModUpdater.canUpdate(featuredMod)) {
        mapService.addInstalledMapsUpdateLock();
        return featuredModUpdater.updateMod(featuredMod, version)
            .thenApply(modVersionKey -> {
              mapService.releaseInstalledMapsUpdateLock();
              if (modVersionKey != null) {
                // null indicates no action was taken
                mapService.loadInstalledMaps(featuredMod.getTechnicalName());
              }
              return modVersionKey;
            });
      }
    }
    throw new UnsupportedOperationException("No updater available for featured mod: " + featuredMod
        + " with version:" + version);
  }

  private CompletableFuture<Void> updateGameBinaries(ComparableVersion version) {
    GameBinariesUpdateTask binariesUpdateTask = applicationContext.getBean(GameBinariesUpdateTaskImpl.class);
    binariesUpdateTask.setVersion(version);
    return taskService.submitTask(binariesUpdateTask).getFuture();
  }
}
