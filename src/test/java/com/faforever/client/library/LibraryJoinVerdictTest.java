package com.faforever.client.library;

import com.faforever.client.library.LibraryJoinVerdict.Outcome;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class LibraryJoinVerdictTest {

  private static final LibraryReadiness READY = new LibraryReadiness(LibraryReadiness.Status.READY, null, null, null);

  private static LibraryGameInfo hosted(String version, Integer revision) {
    return new LibraryGameInfo("talon", "Talon", "coreprime", version, revision, null, "totala", List.of());
  }

  private static LibraryMod installed(String id, String version, Integer revision) {
    return new LibraryMod(id, id, version, revision, "coreprime", "totala", List.of(), Path.of("Mods", id));
  }

  private static LibraryReadiness notReady(LibraryReadiness.Status status) {
    return new LibraryReadiness(status, "0.8.0", null, null);
  }

  @Test
  public void aMatchingInstalledModIsJoinable() {
    LibraryJoinVerdict verdict = LibraryJoinVerdict.decide(hosted("1.03", 2), READY, List.of(installed("talon", "1.03", 2)));

    assertThat(verdict.outcome(), is(Outcome.JOINABLE));
    assertThat(verdict.blocksJoin(), is(false));
    assertThat(verdict.hasVersionHint(), is(false));
  }

  @Test
  public void aDifferentVersionIsJoinableWithAHintNamingBothVersions() {
    LibraryJoinVerdict verdict = LibraryJoinVerdict.decide(hosted("1.03", 2), READY, List.of(installed("talon", "1.02", 2)));

    assertThat(verdict.outcome(), is(Outcome.JOINABLE_WITH_VERSION_HINT));
    assertThat(verdict.blocksJoin(), is(false));
    assertThat(verdict.hasVersionHint(), is(true));
    assertThat(verdict.modName(), is("Talon"));
    assertThat(verdict.hostVersion(), is("1.03"));
    assertThat(verdict.localVersion(), is("1.02"));
  }

  @Test
  public void aRevisionOnlyDifferenceIsAlsoAHintAndNamesTheRevisions() {
    LibraryJoinVerdict verdict = LibraryJoinVerdict.decide(hosted("1.03", 2), READY, List.of(installed("talon", "1.03", 3)));

    assertThat(verdict.outcome(), is(Outcome.JOINABLE_WITH_VERSION_HINT));
    assertThat(verdict.hostVersion(), is("1.03 (revision 2)"));
    assertThat(verdict.localVersion(), is("1.03 (revision 3)"));
  }

  @Test
  public void aHostWithoutRecordedVersionOrRevisionNeverTriggersAHint() {
    assertThat(LibraryJoinVerdict.decide(hosted(null, null), READY, List.of(installed("talon", "1.02", 2))).outcome(),
        is(Outcome.JOINABLE));
    assertThat(LibraryJoinVerdict.decide(hosted("1.03", null), READY, List.of(installed("talon", "1.03", 9))).outcome(),
        is(Outcome.JOINABLE));
  }

  @Test
  public void withSeveralVersionsInstalledTheOneMatchingTheHostWins() {
    LibraryJoinVerdict verdict = LibraryJoinVerdict.decide(hosted("1.03", 2), READY,
        List.of(installed("talon", "1.02", 2), installed("talon", "1.03", 2)));

    assertThat(verdict.outcome(), is(Outcome.JOINABLE));
  }

  @Test
  public void aModThatIsNotInstalledIsNeeded() {
    LibraryJoinVerdict verdict = LibraryJoinVerdict.decide(hosted("1.03", 2), READY, List.of(installed("rhyoss", "1.0", 1)));

    assertThat(verdict.outcome(), is(Outcome.NEEDS_MOD));
    assertThat(verdict.blocksJoin(), is(true));
    assertThat(verdict.modName(), is("Talon"));
  }

  @Test
  public void firstRunSetupNotDoneComesBeforeAnythingAboutTheMod() {
    LibraryJoinVerdict verdict = LibraryJoinVerdict.decide(hosted("1.03", 2),
        notReady(LibraryReadiness.Status.SETUP_NOT_DONE), List.of());

    assertThat(verdict.outcome(), is(Outcome.NEEDS_SETUP));
    assertThat(verdict.blocksJoin(), is(true));
  }

  @Test
  public void noOaAndADisallowedVersionBothNeedOa() {
    assertThat(LibraryJoinVerdict.decide(hosted("1.03", 2), notReady(LibraryReadiness.Status.NO_OA), List.of()).outcome(),
        is(Outcome.NEEDS_OA));
    LibraryJoinVerdict old = LibraryJoinVerdict.decide(hosted("1.03", 2),
        new LibraryReadiness(LibraryReadiness.Status.OA_VERSION_NOT_ALLOWED, "0.8.0", null, "0.7.3"), List.of());
    assertThat(old.outcome(), is(Outcome.NEEDS_OA));
    assertThat(old.requiredOaVersion(), is("0.8.0"));
  }

  @Test
  public void aBlockingVerdictCarriesNoLocalVersion() {
    assertThat(LibraryJoinVerdict.decide(hosted("1.03", 2), READY, List.of()).localVersion(), is(nullValue()));
  }

  @Test
  public void theLocalModForAReplayIsTheInstalledVersionThatMatchesElseTheNewest() {
    LibraryMod old = new LibraryMod("talon", "Talon", "1.02", 2, "coreprime", "totala", List.of(), Path.of("Mods", "talon-1.02"));
    LibraryMod current = new LibraryMod("talon", "Talon", "1.03", 2, "coreprime", "totala", List.of(), Path.of("Mods", "talon"));

    assertThat(LibraryJoinVerdict.localModFor(hosted("1.02", 2), List.of(current, old)).orElseThrow(), is(old));
    assertThat(LibraryJoinVerdict.localModFor(hosted("1.01", 2), List.of(old, current)).orElseThrow(), is(current));
    assertThat(LibraryJoinVerdict.localModFor(hosted("1.02", 2), List.of(installed("rhyoss", "1.0", 1))).isPresent(), is(false));
  }
}
