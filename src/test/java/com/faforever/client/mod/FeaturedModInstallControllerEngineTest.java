package com.faforever.client.mod;

import com.faforever.client.fa.oa.OaInstallationService;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.AskAlwaysOrNever;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.theme.UiService;
import com.faforever.client.update.ClientConfiguration;
import com.faforever.client.update.ClientConfiguration.OaMod;
import com.faforever.client.update.ClientConfiguration.OaSupport;
import com.faforever.client.update.ClientConfiguration.OpenAnnihilation;
import javafx.scene.Scene;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FeaturedModInstallControllerEngineTest extends AbstractPlainJavaFxTest {

  private FeaturedModInstallController instance;

  private void load(boolean supported) throws Exception {
    PreferencesService prefs = mock(PreferencesService.class);
    TotalAnnihilationPrefs taPrefs = new TotalAnnihilationPrefs("tacc", Path.of("games", "TAF-Core Contingency", "TotalA.exe"), "", AskAlwaysOrNever.NEVER);
    when(prefs.getTotalAnnihilation(anyString())).thenReturn(taPrefs);
    ClientConfiguration configuration = new ClientConfiguration();
    if (supported) {
      OaMod mod = new OaMod();
      mod.setModTechnical("tacc");
      mod.setSupport(OaSupport.OA_ONLY);
      OpenAnnihilation oa = new OpenAnnihilation();
      oa.setMods(List.of(mod));
      oa.setReleases(List.of());
      configuration.setOpenAnnihilation(oa);
    }
    when(prefs.getClientRemoteConfiguration()).thenReturn(configuration);
    UiService ui = mock(UiService.class);
    when(ui.createScene(any())).thenAnswer(i -> new Scene(i.getArgument(0)));
    I18n i18n = mock(I18n.class);
    when(i18n.get(anyString(), any())).thenAnswer(i -> i.getArgument(0));

    instance = new FeaturedModInstallController(prefs, ui, mock(ModService.class), mock(NotificationService.class), i18n,
        mock(PlatformService.class), new OaInstallationService(prefs));
    loadFxml("theme/featured_mod_install.fxml", clazz -> instance);
    FeaturedMod fm = new FeaturedMod();
    fm.setTechnicalName("tacc");
    fm.setDisplayName("Core Contingency");
    fm.setInstallPackage("{\"url\":\"http://x/y.zip\"}");
    runOnFxThreadAndWait(() -> instance.setFeaturedMod(fm));
  }

  @Test
  public void engineSectionIsHiddenWhenTheServerDoesNotOfferOa() throws Exception {
    load(false);
    assertThat(instance.engineBox.isVisible(), is(false));
    assertThat(instance.engineBox.isManaged(), is(false));
  }

  @Test
  public void engineSectionShowsAndSwitchingRevealsTheOaOptions() throws Exception {
    load(true);
    assertThat(instance.engineBox.isVisible(), is(true));
    assertThat(instance.cavedogRadioButton.isSelected(), is(true));
    assertThat(instance.oaOptionsBox.isVisible(), is(false));

    runOnFxThreadAndWait(() -> instance.oaRadioButton.setSelected(true));
    assertThat(instance.oaOptionsBox.isVisible(), is(true));
    assertThat(instance.oaManagedRadioButton.isSelected(), is(true));
    assertThat(instance.oaExecutableTextField.isDisabled(), is(true));

    runOnFxThreadAndWait(() -> instance.oaExistingRadioButton.setSelected(true));
    assertThat(instance.oaExecutableTextField.isDisabled(), is(false));
    assertThat(instance.oaStatusLabel.getText(), is(""));
  }
}
