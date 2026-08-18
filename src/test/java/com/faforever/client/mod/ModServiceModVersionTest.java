package com.faforever.client.mod;

import com.faforever.client.fa.DemoFileInfo;
import com.faforever.client.remote.FafService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Optional;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link ModService#findModVersionDisplayName} only — the replay vault shows this string on
 * cards and in the detail dialog, and getting it wrong means telling the user a replay was played
 * on a build it wasn't.
 *
 * Scoped to its own class (rather than the broken ModServiceTest) so it can be compiled in
 * isolation; ModService's 16-arg constructor is filled by @InjectMocks, and only fafService is
 * actually reached by this code path.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ModServiceModVersionTest {

  private static final String UNITS_HASH = "5f2d0c1ea1b34c9f8e7d6a5b4c3d2e1f";

  @Mock
  private FafService fafService;

  @InjectMocks
  private ModService instance;

  private static DemoFileInfo demo(String modHash) {
    return new DemoFileInfo("game.tad", "Comet Catcher", "mapHash", modHash, 3, 1);
  }

  private static FeaturedMod modWithVersions(String... versionDisplayNames) {
    FeaturedMod featuredMod = new FeaturedMod();
    featuredMod.setTechnicalName("tacc");
    featuredMod.setVersions(java.util.Arrays.stream(versionDisplayNames).map(displayName -> {
      FeaturedModVersion version = new FeaturedModVersion();
      version.setDisplayName(displayName);
      return version;
    }).collect(java.util.stream.Collectors.toList()));
    return featuredMod;
  }

  @Test
  public void returnsTheDisplayNameOfTheMatchedVersion() {
    when(fafService.findFeaturedModByTaDemoModHash(UNITS_HASH))
        .thenReturn(completedFuture(modWithVersions("10.1")));

    assertThat(instance.findModVersionDisplayName(demo(UNITS_HASH)).join(), is(Optional.of("10.1")));
  }

  /**
   * findFeaturedModByTaDemoModHash narrows the versions list to the version whose taHash matched,
   * so element 0 IS the played build. If that contract ever changes this test is the tripwire.
   */
  @Test
  public void takesTheFirstVersionBecauseTheLookupNarrowsToTheMatch() {
    when(fafService.findFeaturedModByTaDemoModHash(UNITS_HASH))
        .thenReturn(completedFuture(modWithVersions("9.5", "10.1")));

    assertThat(instance.findModVersionDisplayName(demo(UNITS_HASH)).join(), is(Optional.of("9.5")));
  }

  @Test
  public void unknownHashResolvesToEmpty() {
    when(fafService.findFeaturedModByTaDemoModHash(anyString())).thenReturn(completedFuture(null));

    assertThat(instance.findModVersionDisplayName(demo(UNITS_HASH)).join(), is(Optional.empty()));
  }

  @Test
  public void modWithoutVersionsResolvesToEmpty() {
    when(fafService.findFeaturedModByTaDemoModHash(UNITS_HASH))
        .thenReturn(completedFuture(modWithVersions()));

    assertThat(instance.findModVersionDisplayName(demo(UNITS_HASH)).join(), is(Optional.empty()));
  }

  @Test
  public void blankVersionDisplayNameResolvesToEmpty() {
    when(fafService.findFeaturedModByTaDemoModHash(UNITS_HASH))
        .thenReturn(completedFuture(modWithVersions("  ")));

    assertThat(instance.findModVersionDisplayName(demo(UNITS_HASH)).join(), is(Optional.empty()));
  }

  @Test
  public void lookupFailureResolvesToEmptyRatherThanPropagating() {
    when(fafService.findFeaturedModByTaDemoModHash(UNITS_HASH))
        .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("API down")));

    assertThat(instance.findModVersionDisplayName(demo(UNITS_HASH)).join(), is(Optional.empty()));
  }

  /** Replays predating replayMeta carry no units hash — don't call the API at all for those. */
  @Test
  public void replayWithoutAUnitsHashIsShortCircuited() {
    assertThat(instance.findModVersionDisplayName(demo(null)).join(), is(Optional.empty()));
    assertThat(instance.findModVersionDisplayName(demo("   ")).join(), is(Optional.empty()));
    assertThat(instance.findModVersionDisplayName(null).join(), is(Optional.empty()));

    verifyNoInteractions(fafService);
  }

  @Test
  public void featuredModVersionListIsNotSharedAcrossLookups() {
    FeaturedMod featuredMod = modWithVersions("10.1");
    when(fafService.findFeaturedModByTaDemoModHash(UNITS_HASH)).thenReturn(completedFuture(featuredMod));

    instance.findModVersionDisplayName(demo(UNITS_HASH)).join();

    // We only read the list; nothing here may prune the caller's bean.
    assertThat(featuredMod.getVersions().size(), is(1));
    assertThat(List.of(featuredMod.getVersions().get(0).getDisplayName()), is(List.of("10.1")));
  }
}
