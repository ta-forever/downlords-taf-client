package com.faforever.client.patch;

import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.task.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.springframework.context.ApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.faforever.client.game.KnownFeaturedMod.TACC;
import static com.faforever.client.game.KnownFeaturedMod.LADDER_1V1;

@Slf4j
@RequiredArgsConstructor
public class GameUpdaterImpl implements GameUpdater {

  private static final List<String> NAMES_OF_FEATURED_BASE_MODS = Stream.of(TACC, LADDER_1V1)
      .map(KnownFeaturedMod::getTechnicalName)
      .collect(Collectors.toList());

  private final List<FeaturedModUpdater> featuredModUpdaters = new ArrayList<>();
  private final ModService modService;
  private final ApplicationContext applicationContext;
  private final TaskService taskService;
  private final FafService fafService;
  private final PreferencesService preferencesService;
  private final NotificationService notificationService;

  @Override
  public GameUpdater addFeaturedModUpdater(FeaturedModUpdater featuredModUpdater) {
    featuredModUpdaters.add(featuredModUpdater);
    return this;
  }

  @Override
  public CompletableFuture<Void> update(FeaturedMod featuredMod, Integer version, Map<String, Integer> featuredModVersions, Set<String> simModUids) {
    // The following ugly code is sponsored by the featured-mod-mess. FAF and Coop are both featured mods - but others,
    // (except fafbeta and fafdevelop) implicitly depend on FAF. So if a non-base mod is being played, make sure FAF is
    // installed.
    List<PatchResult> patchResults = new ArrayList<>();

    CompletableFuture<Void> future = updateFeaturedMod(featuredMod, version)
        .thenAccept(patchResults::add)
        .thenCompose(s -> downloadMissingSimMods(simModUids));

    if (!NAMES_OF_FEATURED_BASE_MODS.contains(featuredMod.getTechnicalName())) {
      future = future.thenCompose(aVoid -> modService.getFeaturedMod(KnownFeaturedMod.DEFAULT.getTechnicalName()))
          .thenCompose(baseMod -> updateFeaturedMod(baseMod, version))
          .thenAccept(patchResults::add);
    }

    verifyUniformModFormat(patchResults);

    return future
        .thenCompose(s -> updateGameBinaries(patchResults.get(patchResults.size() - 1).getVersion()))
        .exceptionally(throwable -> {
          notificationService.addImmediateErrorNotification(throwable, "error.game.notTerminatedCorrectly");
          return null;
        });
  }

  /**
   * Older versions of featured mods didn't provide a mod info file but provided an init file instead. New featured mod
   * versions provide an mod info file from which an init file is generated. Mixing these two kind of mods doesn't work,
   * which is what this method ensures.
   */
  private void verifyUniformModFormat(List<PatchResult> patchResults) {
    long modsWithLegacyInitFile = patchResults.stream().filter(patchResult -> patchResult.getLegacyInitFile() != null).count();
    if (modsWithLegacyInitFile != 0 && modsWithLegacyInitFile != patchResults.size()) {
      throw new IllegalStateException("Legacy and non-legacy mods can't be mixed.");
    }
  }

  @Override
  public CompletableFuture<List<FeaturedMod>> getFeaturedMods() {
    return fafService.getFeaturedMods();
  }

  private CompletableFuture<Void> downloadMissingSimMods(Set<String> simModUids) {
    if (simModUids == null || simModUids.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    List<CompletableFuture<Void>> simModFutures = simModUids.stream()
        .filter(uid -> !modService.isModInstalled(uid))
        .map(modService::downloadAndInstallMod)
        .collect(Collectors.toList());
    return CompletableFuture.allOf(simModFutures.toArray(new CompletableFuture[simModFutures.size()]));
  }

  private CompletableFuture<PatchResult> updateFeaturedMod(FeaturedMod featuredMod, Integer version) {
    for (FeaturedModUpdater featuredModUpdater : featuredModUpdaters) {
      if (featuredModUpdater.canUpdate(featuredMod)) {
        return featuredModUpdater.updateMod(featuredMod, version);
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
