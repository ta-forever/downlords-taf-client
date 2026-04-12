package com.faforever.client.tournament;

import com.faforever.client.i18n.I18n;
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
 * mocked to no-op CompletableFutures and a 1x1 dummy image.
 */
public class HallOfFameControllerTest extends AbstractPlainJavaFxTest {

  @Mock private TournamentService tournamentService;
  @Mock private PlayerService playerService;
  @Mock private PreferencesService preferencesService;
  @Mock private UiService uiService;
  @Mock private EventBus eventBus;
  @Mock private I18n i18n;

  private HallOfFameController instance;

  @Before
  public void setUp() throws Exception {
    Image dummy = new WritableImage(1, 1);
    when(uiService.getThemeImage(anyString())).thenReturn(dummy);

    Preferences prefs = new Preferences(List.of());
    when(preferencesService.getPreferences()).thenReturn(prefs);

    when(tournamentService.getHallOfFame(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

    when(i18n.get(anyString())).thenAnswer(inv -> inv.getArgument(0));

    instance = new HallOfFameController(tournamentService, playerService,
        preferencesService, uiService, eventBus, i18n);

    loadFxml("theme/tournaments/hall_of_fame.fxml", clazz -> instance);
  }

  @Test
  public void smokeTestLoadsWithoutError() {
    assertThat(instance.getRoot(), notNullValue());
    assertThat(instance.hallOfFameTable, notNullValue());
    assertThat(instance.searchField, notNullValue());
    assertThat(instance.emptyStateLabel, notNullValue());
  }
}
