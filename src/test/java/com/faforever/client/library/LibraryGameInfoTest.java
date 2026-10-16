package com.faforever.client.library;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class LibraryGameInfoTest {

  private static LibraryMod talon(String version) {
    return new LibraryMod("talon", "Talon", version, 2, "coreprime", "totala", List.of("core-contingency"),
        Path.of("Mods", "talon"));
  }

  @Test
  public void aHostedGameCarriesTheModsIdentityAndTheSimHash() {
    LibraryGameInfo info = LibraryGameInfo.of(talon("1.03"), "ab".repeat(32));

    assertThat(info.modId(), is("talon"));
    assertThat(info.name(), is("Talon"));
    assertThat(info.registry(), is("coreprime"));
    assertThat(info.version(), is("1.03"));
    assertThat(info.revision(), is(2));
    assertThat(info.simHash(), is("ab".repeat(32)));
    assertThat(info.baseGame(), is("totala"));
    assertThat(info.expansions(), is(List.of("core-contingency")));
  }

  @Test
  public void aMissingSimHashIsKeptAsNullRatherThanFailingTheHost() {
    assertThat(LibraryGameInfo.of(talon("1.03"), null).simHash(), is(nullValue()));
  }

  @Test
  public void theGameListShowsTheModsNameAndVersionNotTheUmbrella() {
    assertThat(LibraryGameInfo.of(talon("1.03"), null).displayLabel(), is("Talon 1.03"));
    assertThat(LibraryGameInfo.of(talon(""), null).displayLabel(), is("Talon"));
  }

  @Test
  public void aVaultGameRebuildsItsLibraryFieldsFromTheStoredColumns() {
    LibraryGameInfo info = LibraryGameInfo.fromStored("talon", "Talon", "coreprime", "1.02", 3, "cd", "totala",
        "[\"core-contingency\",\"battle-tactics\"]").orElseThrow();

    assertThat(info.modId(), is("talon"));
    assertThat(info.version(), is("1.02"));
    assertThat(info.revision(), is(3));
    assertThat(info.baseGame(), is("totala"));
    assertThat(info.expansions(), is(List.of("core-contingency", "battle-tactics")));
  }

  @Test
  public void aGameWithoutAStoredModIdOrWithUnreadableExpansionsStillBehaves() {
    assertThat(LibraryGameInfo.fromStored(null, null, null, null, null, null, null, null).isPresent(), is(false));
    assertThat(LibraryGameInfo.fromStored("talon", null, null, null, null, null, null, "not json").orElseThrow().expansions(),
        is(List.of()));
    assertThat(LibraryGameInfo.fromStored("talon", null, null, null, null, null, null, null).orElseThrow().name(), is("talon"));
  }

  @Test
  public void browserWatchIsNotOfferedForLibraryGamesOnly() {
    assertThat(LibraryGameInfo.offersBrowserWatch("oalibrary"), is(false));
    assertThat(LibraryGameInfo.offersBrowserWatch("taesc"), is(true));
    assertThat(LibraryGameInfo.offersBrowserWatch(null), is(true));
  }
}
