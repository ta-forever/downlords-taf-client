package com.faforever.client.library;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class LibraryMapPreviewsTest {

  private static final URI MAPTOOL = URI.create("file:///cache/maptool.png");
  private static final URI TAF = URI.create("http://taf/preview.png");
  private static final URI REGISTRY = URI.create("http://downloads.coreprime.net/v1/i/abc.png");

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private final List<String> calls = new ArrayList<>();
  private Optional<URI> tafPreview = Optional.empty();
  private Optional<URI> registryPreview = Optional.empty();
  private Path modLayer = Path.of("mod");
  private Path expansionLayer = Path.of("exp");
  private Path baseLayer = Path.of("base");
  private Path layerWithMap = null;
  private boolean maptoolFails = false;

  private LibraryMapPreviews previews() {
    return new LibraryMapPreviews(
        (layer, name) -> {
          calls.add("maptool:" + layer + ":" + name);
          if (maptoolFails) {
            throw new IOException("Map tool exited with error code 1");
          }
          return layer.equals(layerWithMap) ? Optional.of(MAPTOOL) : Optional.empty();
        },
        name -> {
          calls.add("taf:" + name);
          return tafPreview;
        },
        key -> {
          calls.add("registry:" + key);
          return registryPreview;
        });
  }

  @Before
  public void setUp() {
    tafPreview = Optional.of(TAF);
    registryPreview = Optional.of(REGISTRY);
  }

  @Test
  public void firstLayerWithTheMapWinsAndNothingElseIsTried() {
    layerWithMap = expansionLayer;

    Optional<URI> found = previews().find("Acid Pools", List.of(modLayer, expansionLayer, baseLayer));

    assertThat(found, is(Optional.of(MAPTOOL)));
    assertThat(calls, contains("maptool:mod:Acid Pools", "maptool:exp:Acid Pools"));
  }

  @Test
  public void aMapMaptoolCannotReadFallsBackToTheTafMapOfTheSameName() {
    Optional<URI> found = previews().find("Acid Pools", List.of(modLayer, baseLayer));

    assertThat(found, is(Optional.of(TAF)));
    assertThat(calls, contains("maptool:mod:Acid Pools", "maptool:base:Acid Pools", "taf:Acid Pools"));
  }

  @Test
  public void aMaptoolFailureCountsAsAMissAndDoesNotStopTheFallbacks() {
    maptoolFails = true;

    assertThat(previews().find("Acid Pools", List.of(modLayer)), is(Optional.of(TAF)));
  }

  @Test
  public void packMapsAreLookedUpByNameInLayersAndTafAndThenByKeyInTheRegistry() {
    tafPreview = Optional.empty();

    Optional<URI> found = previews().find("1 Continent@1-continent-king", List.of(modLayer));

    assertThat(found, is(Optional.of(REGISTRY)));
    assertThat(calls, contains("maptool:mod:1 Continent", "taf:1 Continent",
        "registry:1 Continent@1-continent-king"));
  }

  @Test
  public void theRegistryIsNotAskedAboutMapsThatAreNotInAPack() {
    tafPreview = Optional.empty();

    assertThat(previews().find("Acid Pools", List.of()), is(Optional.empty()));
    assertThat(calls, contains("taf:Acid Pools"));
  }

  @Test
  public void aMapWithNoSourceHasNoPreviewAndNoError() {
    tafPreview = Optional.empty();
    registryPreview = Optional.empty();

    assertThat(previews().find("1 Continent@1-continent-king", List.of(modLayer)), is(Optional.empty()));
  }

  @Test
  public void aSourceThatThrowsIsSkipped() {
    LibraryMapPreviews previews = new LibraryMapPreviews(
        (layer, name) -> Optional.empty(),
        name -> {
          throw new IllegalStateException("API down");
        },
        key -> Optional.of(REGISTRY));

    assertThat(previews.find("A@pack", List.of()), is(Optional.of(REGISTRY)));
  }

  @Test
  public void layersAreTheModThenItsExpansionsThenTheBaseGameAndOnlyThoseThatExist() throws IOException {
    Path user = temp.newFolder("user").toPath();
    Path mods = Files.createDirectories(user.resolve("Mods").resolve("rainbow"));
    Files.createDirectories(user.resolve("Games").resolve("totala"));
    Path cc = Files.createDirectories(user.resolve("Expansions").resolve("totala").resolve("core-contingency"));
    Path bt = Files.createDirectories(user.resolve("Expansions").resolve("totala").resolve("battle-tactics"));
    LibraryMod mod = new LibraryMod("rainbow", "Rainbow Edition", "1", null, "coreprime", "totala",
        List.of("core-contingency", "not-installed"), mods);

    assertThat(LibraryMapPreviews.layers(user, mod), contains(mods, cc, user.resolve("Games").resolve("totala")));
    assertThat(LibraryMapPreviews.layers(user, mod, List.of("core-contingency", "battle-tactics")),
        contains(mods, cc, bt, user.resolve("Games").resolve("totala")));
    assertThat(LibraryMapPreviews.layers(user.resolve("nowhere"), new LibraryMod("x", "X", "1", null, null, "totala",
        List.of(), user.resolve("nowhere").resolve("Mods").resolve("x"))), is(empty()));
  }

  @Test
  public void theMapNameIsTheKeyWithoutItsPack() {
    assertThat(LibraryMapPreviews.mapName("1 Continent@1-continent-king"), is("1 Continent"));
    assertThat(LibraryMapPreviews.mapName("Acid Pools"), is("Acid Pools"));
  }
}
