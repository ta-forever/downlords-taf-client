package com.faforever.client.preferences;

import com.faforever.client.preferences.gson.PathTypeAdapter;
import com.faforever.client.preferences.gson.PropertyTypeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.beans.property.Property;
import org.junit.Test;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EngineUpdatesMigrationTest {

  private final Gson gson = new GsonBuilder()
      .registerTypeHierarchyAdapter(Property.class, PropertyTypeAdapter.INSTANCE)
      .registerTypeHierarchyAdapter(Path.class, PathTypeAdapter.INSTANCE)
      .create();

  private TotalAnnihilationPrefs load(String oaInstallation) {
    return gson.fromJson("{\"baseGameName\":\"tacc\",\"oaInstallation\":" + oaInstallation + "}",
        TotalAnnihilationPrefs.class);
  }

  @Test
  public void savedTafManagedBecomesTaf() {
    assertThat(load("\"TAF_MANAGED\"").getOaInstallation(), is(EngineUpdates.TAF));
  }

  @Test
  public void savedExistingBecomesOwnInstall() {
    assertThat(load("\"EXISTING\"").getOaInstallation(), is(EngineUpdates.OWN_INSTALL));
  }

  @Test
  public void newValuesRoundTrip() {
    TotalAnnihilationPrefs prefs = load("\"OPEN_ANNIHILATION\"");
    assertThat(prefs.getOaInstallation(), is(EngineUpdates.OPEN_ANNIHILATION));
    assertThat(gson.toJson(prefs).contains("\"OPEN_ANNIHILATION\""), is(true));
  }

  @Test
  public void theDefaultIsTaf() {
    assertThat(load("null").getOaInstallation(), is(EngineUpdates.TAF));
    assertThat(new TotalAnnihilationPrefs("tacc", null, "", null).getOaInstallation(), is(EngineUpdates.TAF));
  }
}
