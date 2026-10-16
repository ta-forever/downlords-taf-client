package com.faforever.client.library;

import org.junit.Test;

import java.net.URI;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RegistryMapPreviewsTest {

  private static final String JSON = "{\"catalogue\":1,\"registry\":\"coreprime\",\"packages\":["
      + "{\"kind\":\"oamod\",\"id\":\"talon\",\"name\":\"Talon\"},"
      + "{\"kind\":\"oamap\",\"id\":\"1-continent-king\",\"maps\":[{\"map\":\"1 Continent@1-continent-king\","
      + "\"name\":\"1 Continent (King)\",\"preview\":\"/v1/i/eeef.png\"},"
      + "{\"map\":\"No Preview@1-continent-king\",\"name\":\"x\"}]}]}";

  private final RegistryMapPreviews catalogue = RegistryMapPreviews.parse("http://downloads.coreprime.net", JSON);

  @Test
  public void aPackMapHasItsPreviewUnderTheRegistryHost() {
    assertThat(catalogue.previewOf("1 Continent@1-continent-king"),
        is(Optional.of(URI.create("http://downloads.coreprime.net/v1/i/eeef.png"))));
  }

  @Test
  public void unknownMapsAndMapsWithoutAPreviewHaveNone() {
    assertThat(catalogue.previewOf("Other@pack"), is(Optional.empty()));
    assertThat(catalogue.previewOf("No Preview@1-continent-king"), is(Optional.empty()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void aCatalogueThatIsNotJsonIsRejected() {
    RegistryMapPreviews.parse("http://x", "<html>");
  }
}
