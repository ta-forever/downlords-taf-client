package com.faforever.client.replay;

import com.faforever.client.api.dto.ReplayMeta;
import com.faforever.client.api.dto.ReplayMeta.ReplayMetaPlayer;
import com.faforever.client.mod.FeaturedMod;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

/**
 * Pins {@link CanonicalReplayName}. Everything but the mod segment follows the server's TADA upload
 * naming ({@code server/tada_service.py::TadaService._upload}), so drift from the Python format is a
 * bug rather than a cosmetic difference. The mod segment is ours alone and is what makes the
 * downloaded name differ from the copy on tademos.xyz.
 */
public class CanonicalReplayNameTest {

  private static final OffsetDateTime MIDNIGHT_UTC =
      OffsetDateTime.of(2026, 8, 23, 0, 0, 0, 0, ZoneOffset.UTC);

  private static ReplayMetaPlayer player(String name, int side) {
    return new ReplayMetaPlayer(name, side, 1, "0000abcd");
  }

  private static Replay replay(OffsetDateTime startTime, ReplayMeta meta, String fileExtension) {
    return replay(startTime, meta, fileExtension, "Escalation", null);
  }

  private static Replay replay(OffsetDateTime startTime, ReplayMeta meta, String fileExtension,
                               String modDisplayName, String modTechnicalName) {
    Replay replay = new Replay();
    replay.setId(123456);
    replay.setStartTime(startTime);
    replay.setReplayMeta(meta);
    if (fileExtension != null || modDisplayName != null || modTechnicalName != null) {
      FeaturedMod featuredMod = new FeaturedMod();
      featuredMod.setFileExtension(fileExtension);
      featuredMod.setDisplayNameNotLocalised(modDisplayName);
      featuredMod.setTechnicalName(modTechnicalName);
      replay.setFeaturedMod(featuredMod);
    }
    return replay;
  }

  private static ReplayMeta meta(String mapName, List<ReplayMetaPlayer> players) {
    return new ReplayMeta(123456, "abc", 3, 1, false, 0, mapName, "deadbeef", players);
  }

  @Test
  public void buildsTheFullName() {
    Replay replay = replay(
        OffsetDateTime.of(2026, 8, 23, 19, 30, 0, 0, ZoneOffset.UTC),
        meta("Painted Desert", Arrays.asList(player("Alice", 0), player("Bob", 1))),
        "tad");

    assertThat(CanonicalReplayName.of(replay, "9.86"),
        is("2026-08-23 - Escalation 9.86 - Painted Desert - Alice, Bob.tad"));
    assertThat(CanonicalReplayName.stem(replay, "9.86"),
        is("2026-08-23 - Escalation 9.86 - Painted Desert - Alice, Bob"));
  }

  /**
   * An unresolved version leaves the segment out entirely rather than filling in a placeholder. For
   * TA a wrong or vague version is worse than none, since the unit set changes between builds.
   */
  @Test
  public void omitsAnUnknownVersionWithoutAPlaceholder() {
    Replay replay = replay(
        MIDNIGHT_UTC,
        meta("Painted Desert", Arrays.asList(player("Alice", 0), player("Bob", 1))),
        "tad");

    assertThat(CanonicalReplayName.of(replay, null),
        is("2026-08-23 - Escalation - Painted Desert - Alice, Bob.tad"));
    assertThat(CanonicalReplayName.of(replay, "   "),
        is("2026-08-23 - Escalation - Painted Desert - Alice, Bob.tad"));
  }

  /** A mod with no display name still has a technical name, which beats dropping the segment. */
  @Test
  public void fallsBackToTheModsTechnicalName() {
    Replay replay = replay(MIDNIGHT_UTC, meta("The Pass", List.of(player("Alice", 0))),
        "tad", null, "taesc");

    assertThat(CanonicalReplayName.of(replay, "9.86"),
        is("2026-08-23 - taesc 9.86 - The Pass - Alice.tad"));
  }

  /** No featured mod at all (a local replay file) drops the segment and defaults the extension. */
  @Test
  public void omitsTheModSegmentWithoutAFeaturedMod() {
    Replay replay = replay(MIDNIGHT_UTC, meta("The Pass", List.of(player("Alice", 0))),
        null, null, null);

    assertThat(CanonicalReplayName.of(replay, "9.86"), is("2026-08-23 - The Pass - Alice.tad"));
  }

  /** A mod name is no safer than a player name: "TA:CC" carries a character Windows rejects. */
  @Test
  public void sanitisesTheModName() {
    Replay replay = replay(MIDNIGHT_UTC, meta("The Pass", List.of(player("Alice", 0))),
        "tad", "TA:CC", null);

    assertThat(CanonicalReplayName.of(replay, "10.1"),
        is("2026-08-23 - TA_CC 10.1 - The Pass - Alice.tad"));
  }

  /**
   * The server reads a naive UTC datetime out of MariaDB and takes its date. A start time late in
   * the UTC day, delivered to a client with a positive offset, must still stamp the UTC day - the
   * naive local date here would be the 24th.
   */
  @Test
  public void datestampIsTheUtcDateNotTheLocalOne() {
    Replay replay = replay(
        OffsetDateTime.of(2026, 8, 24, 7, 30, 0, 0, ZoneOffset.ofHours(10)),
        meta("Anteer Straight", List.of(player("Alice", 0))),
        "tad");

    assertThat(CanonicalReplayName.of(replay, "9.86"),
        is("2026-08-23 - Escalation 9.86 - Anteer Straight - Alice.tad"));
  }

  /** Observers (side >= 2) are in the file name; only TADA's game matching filters them out. */
  @Test
  public void includesObservers() {
    Replay replay = replay(
        MIDNIGHT_UTC,
        meta("Comet Catcher", Arrays.asList(player("Alice", 0), player("Watcher", 2))),
        "tad");

    assertThat(CanonicalReplayName.of(replay, "9.86"),
        is("2026-08-23 - Escalation 9.86 - Comet Catcher - Alice, Watcher.tad"));
  }

  /** Player order is the demo compiler's lock-in order, never sorted. */
  @Test
  public void preservesPlayerOrder() {
    Replay replay = replay(
        MIDNIGHT_UTC,
        meta("Gods Of War", Arrays.asList(player("Zeb", 0), player("Alice", 1))),
        "tad");

    assertThat(CanonicalReplayName.of(replay, "9.86"),
        is("2026-08-23 - Escalation 9.86 - Gods Of War - Zeb, Alice.tad"));
  }

  /** The extension follows the featured mod, as {@code game_featuredMods.file_extension} does. */
  @Test
  public void usesTheFeaturedModsFileExtension() {
    Replay replay = replay(MIDNIGHT_UTC, meta("The Pass", List.of(player("Alice", 0))), "tad3");

    assertThat(CanonicalReplayName.of(replay, "9.86"),
        is("2026-08-23 - Escalation 9.86 - The Pass - Alice.tad3"));
  }

  @Test
  public void usesProtaReplayExtensionEvenWhenModIsOmittedFromName() {
    Replay replay = replay(MIDNIGHT_UTC, meta("The Pass", List.of(player("Alice", 0))),
        "pro", "ProTA", "tavmod");
    ReplayDownloadNameOptions options = new ReplayDownloadNameOptions(true, false, true, true);

    assertThat(CanonicalReplayName.of(replay, "4.6", options),
        is("2026-08-23 - The Pass - Alice.pro"));
  }

  @Test
  public void includesOnlySelectedNameParts() {
    Replay replay = replay(MIDNIGHT_UTC,
        meta("Painted Desert", Arrays.asList(player("Alice", 0), player("Bob", 1))), "tad");
    ReplayDownloadNameOptions options = new ReplayDownloadNameOptions(false, true, true, false);

    assertThat(CanonicalReplayName.of(replay, "9.86", options),
        is("Escalation 9.86 - Painted Desert.tad"));
  }

  @Test
  public void fallsBackToReplayIdWhenEveryNamePartIsDisabled() {
    Replay replay = replay(MIDNIGHT_UTC,
        meta("Painted Desert", Arrays.asList(player("Alice", 0), player("Bob", 1))), "tad");
    ReplayDownloadNameOptions options = new ReplayDownloadNameOptions(false, false, false, false);

    assertThat(CanonicalReplayName.of(replay, "9.86", options), is("TAF-123456.tad"));
  }

  /**
   * Games predating replay_meta fall back to the replay id, as the server does - but the mod is
   * known from the game record rather than the demo, so it survives the fallback.
   */
  @Test
  public void fallsBackToTheReplayIdWithoutMeta() {
    Replay replay = replay(MIDNIGHT_UTC, null, "tad");

    assertThat(CanonicalReplayName.of(replay, null), is("2026-08-23 - Escalation - TAF-123456.tad"));
  }

  /** A meta blob whose demo never compiled far enough to record players is the same case. */
  @Test
  public void fallsBackWhenThePlayerListIsEmpty() {
    Replay replay = replay(MIDNIGHT_UTC, meta("Painted Desert", List.of()), "tad");

    assertThat(CanonicalReplayName.of(replay, null), is("2026-08-23 - Escalation - TAF-123456.tad"));
  }

  /**
   * Player names come off the wire from TA and are not constrained. The server does not sanitise
   * because TADA takes any name; a local file save has to.
   */
  @Test
  public void stripsCharactersAFileNameCannotCarry() {
    Replay replay = replay(
        MIDNIGHT_UTC,
        meta("Map/With:Colon", Arrays.asList(player("a\\b", 0), player("c?d\"e", 1))),
        "tad");

    String name = CanonicalReplayName.of(replay, "9.86");
    for (char illegal : "\\/:*?\"<>|".toCharArray()) {
      assertThat(name, not(containsString(String.valueOf(illegal))));
    }
    assertThat(name, is("2026-08-23 - Escalation 9.86 - Map_With_Colon - a_b, c_d_e.tad"));
  }

  /** Windows silently drops trailing dots and spaces, renaming the file behind the user's back. */
  @Test
  public void dropsTrailingDotsAndSpaces() {
    Replay replay = replay(MIDNIGHT_UTC, meta("Painted Desert", List.of(player("Alice", 0))), "tad.");

    assertThat(CanonicalReplayName.of(replay, "9.86"),
        is("2026-08-23 - Escalation 9.86 - Painted Desert - Alice.tad"));
  }

  /**
   * Ten long names on a long map name overrun the 255-character path component limit, which the
   * server never has to care about. Truncation happens on a whole-name boundary and says so - and
   * it eats player names only, never the mod segment, which is why that segment goes up front.
   */
  @Test
  public void truncatesAnOverlongPlayerList() {
    List<ReplayMetaPlayer> players = IntStream.range(0, 10)
        .mapToObj(i -> player("PlayerWithARatherLongName" + i, 0))
        .collect(Collectors.toList());
    Replay replay = replay(
        MIDNIGHT_UTC,
        meta("A Map Name That Is Itself Quite Long Indeed For Testing", players),
        "tad");

    String name = CanonicalReplayName.of(replay, "9.86");
    assertThat(name.length(), lessThanOrEqualTo(CanonicalReplayName.MAX_LENGTH));
    assertThat(name, containsString("more."));
    assertThat(name, containsString("PlayerWithARatherLongName0"));
    assertThat(name, containsString("2026-08-23 - Escalation 9.86 - A Map Name"));
  }

  /** A short line-up must not pick up an overflow marker. */
  @Test
  public void doesNotTruncateWhenItFits() {
    Replay replay = replay(
        MIDNIGHT_UTC,
        meta("Painted Desert", Arrays.asList(player("Alice", 0), player("Bob", 1))),
        "tad");

    assertThat(CanonicalReplayName.of(replay, "9.86"), not(containsString("more")));
  }
}
