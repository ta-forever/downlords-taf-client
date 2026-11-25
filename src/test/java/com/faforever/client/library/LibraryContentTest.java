package com.faforever.client.library;

import org.junit.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class LibraryContentTest {

  private static final List<String> ALL_INSTALLED = List.of("battle-tactics", "campaign", "cavedog-tilesets",
      "cavedog-units", "core-contingency", "music");

  @Test
  public void aModWithoutRequiredExpansionsPlaysWithEveryInstalledRuleExpansionButMusic() {
    assertThat(LibraryContent.gameExpansions("totala", List.of(), ALL_INSTALLED),
        contains("campaign", "core-contingency", "battle-tactics", "cavedog-units", "cavedog-tilesets"));
  }

  @Test
  public void requiredExpansionsAreKeptInOasOrderEvenWhenNotListedAsInstalled() {
    assertThat(LibraryContent.gameExpansions("totala", List.of("core-contingency"), List.of("battle-tactics")),
        contains("core-contingency", "battle-tactics"));
    assertThat(LibraryContent.gameExpansions("totala", List.of("some-new-one"), List.of("campaign")),
        contains("campaign", "some-new-one"));
  }

  @Test
  public void aJoinerKeepsOnlyTheHostsExpansionsItHasInTheHostsOrder() {
    assertThat(LibraryContent.expansionsThisPlayerHas(
            List.of("campaign", "core-contingency", "battle-tactics", "cavedog-tilesets"),
            List.of("battle-tactics", "campaign", "core-contingency", "music")),
        contains("campaign", "core-contingency", "battle-tactics"));
  }

  @Test
  public void theDemoTakesNoExpansionBeyondWhatTheModRequires() {
    assertThat(LibraryContent.gameExpansions("totala-demo", List.of(), ALL_INSTALLED), is(empty()));
  }
}
