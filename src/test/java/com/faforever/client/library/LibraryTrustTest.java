package com.faforever.client.library;

import com.faforever.client.update.ClientConfiguration;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class LibraryTrustTest {

  private static final String SIM_TALON = "a".repeat(64);
  private static final String CATALOGUE = """
      {"catalogue":1,"registry":"coreprime","packages":[
        {"kind":"expansion","id":"core-contingency","version":"1"},
        {"kind":"oamod","id":"talon","version":"1.03","revision":2,"sim_hash":"%s"},
        {"kind":"oamod","id":"escalation","version":"10.2.0","revision":4,"sim_hash":"%s"},
        {"kind":"oamod","id":"rhyoss","version":"1.0"}]}""".formatted(SIM_TALON, "b".repeat(64));

  private static final LibraryPolicy POLICY = new LibraryPolicy(
      List.of(new LibraryPolicy.Registry("coreprime", "http://example.invalid/catalogue.json")),
      Set.of("escalation", "mayhem"));

  private final List<String> catalogueRequests = new ArrayList<>();
  private final Function<String, Optional<RegistryCatalogue>> catalogues = registry -> {
    catalogueRequests.add(registry);
    return registry.equals("coreprime") ? Optional.of(RegistryCatalogue.parse(CATALOGUE)) : Optional.empty();
  };

  private static LibraryMod mod(String id, String version, String registry) {
    return new LibraryMod(id, id, version, 1, registry, "totala", List.of(), Path.of("Mods", id));
  }

  private List<String> kept(List<LibraryMod> mods, Function<LibraryMod, Optional<String>> simHash) {
    return LibraryTrust.filter(mods, POLICY, catalogues, simHash).stream().map(LibraryMod::id).toList();
  }

  private List<String> kept(LibraryMod... mods) {
    return kept(List.of(mods), m -> Optional.empty());
  }

  @Test
  public void aModOaInstalledFromATrustedRegistryIsKeptWithoutFetchingTheCatalogue() {
    assertThat(kept(mod("talon", "1.03", "coreprime")), contains("talon"));
    assertThat(catalogueRequests, is(empty()));
  }

  @Test
  public void aModOaInstalledFromAnUntrustedRegistryIsDropped() {
    assertThat(kept(mod("talon", "1.03", "someone-else")), is(empty()));
  }

  @Test
  public void hiddenIdsAreDroppedEvenFromATrustedRegistry() {
    assertThat(kept(mod("escalation", "10.2.0", "coreprime"), mod("MAYHEM", "11.4", "coreprime"),
        mod("talon", "1.03", "coreprime")), contains("talon"));
  }

  @Test
  public void aLocalModNotInTheCatalogueIsDropped() {
    assertThat(kept(mod("my-handmade", "1", null), mod("talon", "9.99", null)), is(empty()));
  }

  @Test
  public void aModWithoutAnOriginRecordMatchesTheCatalogueByIdAndVersion() {
    assertThat(kept(mod("talon", "1.03", null), mod("rhyoss", "1.0", null)), contains("talon", "rhyoss"));
  }

  @Test
  public void aModWithoutAnOriginRecordMatchesTheCatalogueBySimHash() {
    List<String> ids = kept(List.of(mod("talon", "1.04-local-build", null)),
        m -> Optional.of(SIM_TALON));

    assertThat(ids, contains("talon"));
  }

  @Test
  public void aSimHashOfAnotherModDoesNotMatch() {
    assertThat(kept(List.of(mod("talon", "1.04", null)), m -> Optional.of("b".repeat(64))), is(empty()));
  }

  @Test
  public void anUnavailableCatalogueFailsClosed() {
    LibraryPolicy other = new LibraryPolicy(List.of(new LibraryPolicy.Registry("offline", "x")), Set.of());
    assertThat(LibraryTrust.filter(List.of(mod("talon", "1.03", null)), other, catalogues, m -> Optional.empty()),
        is(empty()));
  }

  @Test
  public void withNoTrustedRegistriesNothingIsOffered() {
    LibraryPolicy none = new LibraryPolicy(List.of(), Set.of());
    assertThat(LibraryTrust.filter(List.of(mod("talon", "1.03", "coreprime")), none, catalogues,
        m -> Optional.empty()), is(empty()));
  }

  @Test
  public void thePolicyComesFromTheServerConfiguration() {
    ClientConfiguration configuration = new com.google.gson.Gson().fromJson("""
        {"openAnnihilation":{"trustedRegistries":[{"id":"coreprime","catalogueUrl":"http://c/v1/catalogue.json"}],
         "hiddenLibraryMods":["escalation","mayhem"]}}""", ClientConfiguration.class);

    LibraryPolicy policy = LibraryPolicy.from(configuration);

    assertThat(policy.trustedRegistries().get(0).id(), is("coreprime"));
    assertThat(policy.trustedRegistries().get(0).catalogueUrl(), is("http://c/v1/catalogue.json"));
    assertThat(policy.isHidden("Escalation"), is(true));
    assertThat(policy.isHidden("talon"), is(false));
  }

  @Test
  public void aMissingConfigurationTrustsNothing() {
    assertThat(LibraryPolicy.from(null).trustedRegistries(), is(empty()));
    assertThat(LibraryPolicy.from(new ClientConfiguration()).trustedRegistries(), is(empty()));
  }
}
