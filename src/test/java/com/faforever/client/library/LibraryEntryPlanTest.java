package com.faforever.client.library;

import com.faforever.client.library.LibraryPrompts.EntryPlan;
import com.faforever.client.library.LibraryReadiness.Status;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class LibraryEntryPlanTest {

  private static final LibraryMod TALON =
      new LibraryMod("talon", "Talon", "1.03", 3, "coreprime", "totala", List.of(), Path.of("talon"));

  private static LibrarySnapshot snapshot(Status status, LibraryMod... mods) {
    return new LibrarySnapshot(new LibraryReadiness(status, "0.8.0", null, "0.8.1"), List.of(mods));
  }

  @Test
  public void readyWithModsListsThemWithAHeaderAndNoPlaceholder() {
    EntryPlan plan = LibraryPrompts.entryPlan(snapshot(Status.READY, TALON));

    assertThat(plan.header(), is(true));
    assertThat(plan.mods(), contains(TALON));
    assertThat(plan.placeholder(), is(false));
  }

  @Test
  public void readyWithoutModsOffersTheSelectablePlaceholder() {
    EntryPlan plan = LibraryPrompts.entryPlan(snapshot(Status.READY));

    assertThat(plan.header(), is(true));
    assertThat(plan.mods(), is(empty()));
    assertThat(plan.placeholder(), is(true));
  }

  @Test
  public void setupNotDoneOffersNothingSoTheNoticeIsTheOnlyPrompt() {
    EntryPlan plan = LibraryPrompts.entryPlan(snapshot(Status.SETUP_NOT_DONE));

    assertThat(plan.header(), is(false));
    assertThat(plan.placeholder(), is(false));
  }

  @Test
  public void noOaOrNotAllowedVersionOffersNoPlaceholder() {
    assertThat(LibraryPrompts.entryPlan(snapshot(Status.NO_OA)).placeholder(), is(false));
    assertThat(LibraryPrompts.entryPlan(snapshot(Status.OA_VERSION_NOT_ALLOWED)).placeholder(), is(false));
  }
}
