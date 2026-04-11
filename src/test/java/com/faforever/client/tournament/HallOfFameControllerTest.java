package com.faforever.client.tournament;

import com.faforever.client.i18n.I18n;
import com.faforever.client.mod.ModService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TournamentPrefs;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.theme.UiService;
import com.google.common.eventbus.EventBus;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * FXML smoke test for the Hall of Fame view. Loads {@code hall_of_fame.fxml}
 * through the same FXMLLoader path the runtime uses, with all controller deps
 * mocked to no-op CompletableFutures and a 1x1 dummy image. This catches
 * FXML-load-time and controller-{@code initialize}-time wiring failures
 * (missing event handlers, wrong icon paths, illegal property bindings, etc.)
 * without needing the user to launch the client and click the Tournaments tab.
 *
 * <p>Add new assertions to {@link #smokeTestLoadsWithoutError()} as the
 * controller's responsibilities grow.
 */
public class HallOfFameControllerTest extends AbstractPlainJavaFxTest {

  @Mock private TournamentService tournamentService;
  @Mock private ModService modService;
  @Mock private PlayerService playerService;
  @Mock private PreferencesService preferencesService;
  @Mock private UiService uiService;
  @Mock private EventBus eventBus;
  @Mock private I18n i18n;

  private HallOfFameController instance;

  @Before
  public void setUp() throws Exception {
    // Dummy 1x1 image — the controller's loadIcons() needs a non-null Image
    // back from getThemeImage so it can stuff it into ImageView constructors.
    Image dummy = new WritableImage(1, 1);
    when(uiService.getThemeImage(anyString())).thenReturn(dummy);

    // Preferences chain — controller reads the persisted hall-of-fame mod id.
    Preferences prefs = new Preferences(List.of());
    when(preferencesService.getPreferences()).thenReturn(prefs);

    // The two async fetches kicked off in initialize() / setupModFilterCombo.
    when(modService.getFeaturedMods()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(tournamentService.getHallOfFame(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

    // i18n mock returns the key as-is so column headers / tooltips have something.
    when(i18n.get(anyString())).thenAnswer(inv -> inv.getArgument(0));
    // playerService.getCurrentPlayer is intentionally NOT stubbed: the row
    // factory only consults it when populating actual rows, and the smoke
    // test runs with an empty table. Adding the stub would trip Mockito's
    // strict-stubs detector. If a future test populates rows it should add
    // the stub locally.

    instance = new HallOfFameController(tournamentService, modService, playerService,
        preferencesService, uiService, eventBus, i18n);

    // Same loader path TournamentsRootController will use at runtime.
    loadFxml("theme/tournaments/hall_of_fame.fxml", clazz -> instance);
  }

  @Test
  public void smokeTestLoadsWithoutError() {
    // If we got here, FXMLLoader.load() and HallOfFameController.initialize()
    // both completed without throwing — that's the smoke test. The runtime
    // failures we keep hitting (#onRefresh missing, getThemeImage path wrong,
    // SortedList bound comparator) all surface here.
    assertThat(instance.getRoot(), notNullValue());
    assertThat(instance.hallOfFameTable, notNullValue());
    assertThat(instance.modFilterCombo, notNullValue());
    assertThat(instance.searchField, notNullValue());
    assertThat(instance.emptyStateLabel, notNullValue());
  }
}
