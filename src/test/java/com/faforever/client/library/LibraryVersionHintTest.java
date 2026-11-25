package com.faforever.client.library;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class LibraryVersionHintTest {

  @Test
  public void aVersionDifferenceNamesBothVersions() {
    LibraryVersionHint hint = LibraryVersionHint.compare("1.03", 2, "1.02", 2).orElseThrow();

    assertThat(hint.recordedVersion(), is("1.03"));
    assertThat(hint.localVersion(), is("1.02"));
  }

  @Test
  public void aRevisionOnlyDifferenceNamesTheRevisions() {
    LibraryVersionHint hint = LibraryVersionHint.compare("1.03", 2, "1.03", 3).orElseThrow();

    assertThat(hint.recordedVersion(), is("1.03 (revision 2)"));
    assertThat(hint.localVersion(), is("1.03 (revision 3)"));
  }

  @Test
  public void anExactMatchHasNoHint() {
    assertThat(LibraryVersionHint.compare("1.03", 2, "1.03", 2).isPresent(), is(false));
    assertThat(LibraryVersionHint.compare(" 1.03", 2, "1.03 ", 2).isPresent(), is(false));
  }

  @Test
  public void nothingRecordedMeansNothingToCompare() {
    assertThat(LibraryVersionHint.compare(null, null, "1.02", 2).isPresent(), is(false));
    assertThat(LibraryVersionHint.compare("", 2, "1.02", 5).isPresent(), is(false));
    assertThat(LibraryVersionHint.compare("1.03", null, "1.03", 9).isPresent(), is(false));
    assertThat(LibraryVersionHint.compare("1.03", 2, "1.03", null).isPresent(), is(false));
  }
}
