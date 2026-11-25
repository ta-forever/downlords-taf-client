package com.faforever.client.library;

import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewOverlayType;
import com.faforever.client.preferences.PreferencesService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LibraryMapPreviewServiceTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private LibraryMapPreviewService service() throws Exception {
    LibraryService libraryService = mock(LibraryService.class);
    when(libraryService.installedMods()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(libraryService.userFolder()).thenReturn(temp.newFolder().toPath());
    PreferencesService preferencesService = mock(PreferencesService.class);
    when(preferencesService.getCacheDirectory()).thenReturn(temp.newFolder().toPath());
    return new LibraryMapPreviewService(libraryService, mock(MapService.class), preferencesService,
        Executors.newSingleThreadExecutor());
  }

  @Test
  public void noOverlayIsEmptyWithoutLookup() throws Exception {
    assertThat(service().loadOverlay("mynn", "Lava Alley", PreviewOverlayType.NONE, 10)
        .get(5, TimeUnit.SECONDS), is(Optional.empty()));
  }

  @Test
  public void askingAgainForTheSameOverlayDoesNotStartANewLookup() throws Exception {
    LibraryMapPreviewService service = service();
    CompletableFuture<?> first = service.loadOverlay("mynn", "Lava Alley", PreviewOverlayType.MEXES, 10);
    assertThat(service.loadOverlay("mynn", "Lava Alley", PreviewOverlayType.MEXES, 10), sameInstance(first));
    assertThat(first.get(5, TimeUnit.SECONDS), is(Optional.empty()));
  }
}
