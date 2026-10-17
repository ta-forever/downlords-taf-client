package com.faforever.client.library;

import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewOverlayType;
import com.faforever.client.preferences.PreferencesService;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the REAL maptool over a map that OA installed as a loose folder, through {@link LibraryMapPreviewService},
 * and checks the images appear where the client reads TAF map images from ({@code <cache>/maps/...}). Skipped
 * unless maptool and OA's {@code Maps/not-a-pic-nic} are on this machine.
 */
public class LibraryLooseMapLiveTest {

  private static final String MAP = "(Not a) Pic-Nic";
  private static final Path OA_USER_FOLDER =
      Path.of(System.getProperty("user.home"), "Documents", "Open Annihilation");
  private static final Path[] NATIVE_DIRS = {
      Path.of("build", "resources", "native"),
      Path.of("C:/Users/Alex/IdeaProjects/downlords-faf-client/build/resources/native")};

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void realMaptoolMakesThePreviewAndOverlaysWhereTheClientReadsThem() throws Exception {
    Path nativeDir = null;
    for (Path candidate : NATIVE_DIRS) {
      if (Files.isRegularFile(candidate.resolve("bin").resolve("maptool.exe"))) {
        nativeDir = candidate.toAbsolutePath();
        break;
      }
    }
    Assume.assumeTrue("maptool.exe not built", nativeDir != null);
    Assume.assumeTrue("OA map not installed",
        Files.isRegularFile(OA_USER_FOLDER.resolve("Maps/not-a-pic-nic/maps/" + MAP + ".tnt")));
    System.setProperty("nativeDir", nativeDir.toString());

    Path cache = temp.newFolder("cache").toPath();
    Path tafFeatures = Path.of("C:/ProgramData/TAForever/cache/maps/TA_Features_2013.ccx");
    Files.createDirectories(cache.resolve("maps"));
    if (Files.isRegularFile(tafFeatures)) {
      Files.copy(tafFeatures, cache.resolve("maps").resolve("TA_Features_2013.ccx"));
    }
    LibraryService libraryService = mock(LibraryService.class);
    when(libraryService.installedMods()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(libraryService.userFolder()).thenReturn(OA_USER_FOLDER);
    PreferencesService preferencesService = mock(PreferencesService.class);
    when(preferencesService.getCacheDirectory()).thenReturn(cache);
    LibraryMapPreviewService service = new LibraryMapPreviewService(libraryService, mock(MapService.class),
        preferencesService, Executors.newSingleThreadExecutor());

    try {
      javafx.application.Platform.startup(() -> {
      });
    } catch (IllegalStateException alreadyRunning) {
      // toolkit already up
    }

    String key = MAP + "@not-a-pic-nic";
    service.loadPreview("mynn", null, key).get(120, TimeUnit.SECONDS);
    service.loadOverlay("mynn", key, PreviewOverlayType.POSITIONS, 10).get(120, TimeUnit.SECONDS);
    service.loadOverlay("mynn", key, PreviewOverlayType.MEXES, 10).get(120, TimeUnit.SECONDS);

    Path maps = cache.resolve("maps");
    assertThat(Files.size(maps.resolve("mini").resolve(MAP + ".png")) > 0, is(true));
    assertThat(Files.size(maps.resolve("positions-overlay_10").resolve(MAP + ".png")) > 0, is(true));
    assertThat(Files.size(maps.resolve("mexes-overlay_10").resolve(MAP + ".png")) > 0, is(true));
    assertThat(Files.exists(maps.resolve("library/staged/not-a-pic-nic/not-a-pic-nic.ufo")), is(true));
  }
}
