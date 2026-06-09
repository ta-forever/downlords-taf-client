package com.faforever.client.game;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class GameLogUploadMarkerTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Path markerDir() {
    return temp.getRoot().toPath().resolve("pendingLogUploads");
  }

  @Test
  public void markCreatesRecoverablePending() {
    GameLogUploadMarker.mark(markerDir(), 175603, "taesc");

    List<GameLogUploadMarker.Pending> pending = GameLogUploadMarker.listAndPrune(markerDir(), 0);

    assertThat(pending, hasSize(1));
    assertThat(pending.get(0).gameId(), is(175603));
    assertThat(pending.get(0).modTechnical(), is("taesc"));
  }

  @Test
  public void clearRemovesPending() {
    GameLogUploadMarker.mark(markerDir(), 1, "taesc");
    GameLogUploadMarker.clear(markerDir(), 1);

    assertThat(GameLogUploadMarker.listAndPrune(markerDir(), 0), is(empty()));
  }

  @Test
  public void clearIsScopedToOneGame() {
    GameLogUploadMarker.mark(markerDir(), 1, "taesc");
    GameLogUploadMarker.mark(markerDir(), 2, "tavmod");

    GameLogUploadMarker.clear(markerDir(), 1);

    List<GameLogUploadMarker.Pending> pending = GameLogUploadMarker.listAndPrune(markerDir(), 0);
    assertThat(pending, hasSize(1));
    assertThat(pending.get(0).gameId(), is(2));
    assertThat(pending.get(0).modTechnical(), is("tavmod"));
  }

  @Test
  public void missingModIsRecoveredAsNull() {
    GameLogUploadMarker.mark(markerDir(), 7, null);

    List<GameLogUploadMarker.Pending> pending = GameLogUploadMarker.listAndPrune(markerDir(), 0);
    assertThat(pending, hasSize(1));
    assertThat(pending.get(0).modTechnical(), is(nullValue()));
  }

  @Test
  public void listOnMissingDirIsEmpty() {
    assertThat(GameLogUploadMarker.listAndPrune(markerDir(), 0), is(empty()));
  }

  @Test
  public void staleMarkerIsPrunedAndNotReturned() throws IOException {
    GameLogUploadMarker.mark(markerDir(), 42, "taesc");
    // Backdate the recorded start time well beyond the max age.
    Path marker = markerDir().resolve("42.marker");
    String content = Files.readString(marker)
        .replaceAll("startedAtEpochMs=.*", "startedAtEpochMs=1");
    Files.writeString(marker, content);

    List<GameLogUploadMarker.Pending> pending =
        GameLogUploadMarker.listAndPrune(markerDir(), Duration.ofDays(7).toMillis());

    assertThat(pending, is(empty()));
    assertThat(Files.exists(marker), is(false));
  }

  @Test
  public void unparseableMarkerIsDiscarded() throws IOException {
    Files.createDirectories(markerDir());
    Path junk = markerDir().resolve("999.marker");
    Files.writeString(junk, "gameId=not-a-number\n");

    assertThat(GameLogUploadMarker.listAndPrune(markerDir(), 0), is(empty()));
    assertThat(Files.exists(junk), is(false));
  }

  @Test
  public void multiplePendingAreAllReturned() {
    GameLogUploadMarker.mark(markerDir(), 100, "taesc");
    GameLogUploadMarker.mark(markerDir(), 200, "tavmod");

    List<Integer> ids = GameLogUploadMarker.listAndPrune(markerDir(), 0).stream()
        .map(GameLogUploadMarker.Pending::gameId)
        .sorted()
        .toList();

    assertThat(ids, contains(100, 200));
  }
}
