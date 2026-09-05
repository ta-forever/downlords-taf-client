package com.faforever.client.replay;

import com.faforever.client.api.dto.ReplayMeta;
import com.faforever.client.api.dto.ReplayMeta.ReplayMetaPlayer;
import com.faforever.client.mod.FeaturedMod;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds the replay file name used when the user downloads a replay, following the name the server
 * gives the copy it uploads to TADA, plus the mod the game was played on.
 * <p>
 * The reference implementation is {@code TadaService._upload} in faf-server
 * ({@code server/tada_service.py}):
 * <pre>
 *   {datestamp} - {mapName} - {name1, name2, ...}.{file_extension}
 * </pre>
 * with a {@code {datestamp} - TAF-{id}.tad} fallback for games recorded before {@code replay_meta}
 * existed. The three inputs are assembled server-side in {@code GameService.get_replay_info}:
 * {@code datestamp} is {@code game_stats.startTime}, {@code mapName}/{@code players} come from the
 * demo compiler's JSON blob, and {@code file_extension} comes from {@code game_featuredMods}.
 * <p>
 * By default we insert one segment the server does not, so the name says which game the replay is
 * even of:
 * <pre>
 *   {datestamp} - {mod}[ {version}] - {mapName} - {name1, name2, ...}.{file_extension}
 * </pre>
 * The user may omit any of the four segments. If every segment is omitted, the stable replay id is
 * used instead. The mod name is the <em>untranslated</em> display name: the localised one would give
 * the same replay a different file name in every language. The version is dropped entirely when it
 * is not known rather than filled with a placeholder - see
 * {@code ModService#findModVersionDisplayName}, which cannot resolve it for a replay predating
 * {@code replay_meta} or whose units hash matches no build we know about. <b>Because of this
 * segment the name no longer matches the copy on tademos.xyz.</b>
 * <p>
 * Two things differ from a naive transcription of the server's format and both matter:
 * <ul>
 *   <li>The date is the <em>UTC</em> date of the start time. The server reads a naive UTC datetime
 *       straight out of MariaDB; formatting the client's {@link OffsetDateTime} in local time would
 *       put players east or west of UTC on the wrong day.</li>
 *   <li>The player list includes observers ({@code side >= 2}). Only TADA's own game <em>matching</em>
 *       filters those out; the file name does not.</li>
 * </ul>
 * The server does not sanitise or length-limit, because TADA accepts any name. A local file save
 * cannot be so relaxed, so {@link #sanitize} and a budget on the joined player list are applied on
 * top — see {@link #MAX_LENGTH}.
 */
public final class CanonicalReplayName {

  /**
   * Budget for the whole name, extension included. Windows caps a path component at 255 UTF-16
   * units; leave room for the ".zip"/".tad" the caller appends and for a browser-style " (1)"
   * de-duplication suffix.
   */
  static final int MAX_LENGTH = 240;

  private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ISO_LOCAL_DATE;

  /** Characters no Windows file name may contain, plus the separators the other platforms reject. */
  private static final String ILLEGAL_CHARS = "\\/:*?\"<>|";

  private CanonicalReplayName() {
    // utility
  }

  /**
   * The canonical name including its extension, e.g.
   * {@code "2026-08-23 - Escalation 9.86 - Painted Desert - Alice, Bob.tad"}.
   *
   * @param modVersion the display name of the featured-mod build the game ran on, from
   *                   {@code ModService#findModVersionDisplayName}, or null when it could not be
   *                   resolved - in which case the name simply carries no version.
   */
  public static String of(Replay replay, @Nullable String modVersion) {
    return of(replay, modVersion, ReplayDownloadNameOptions.all());
  }

  public static String of(Replay replay, @Nullable String modVersion,
                          ReplayDownloadNameOptions options) {
    return stem(replay, modVersion, options) + "." + extension(replay);
  }

  /**
   * The canonical name without the replay extension, for callers naming a container of the demo
   * rather than the demo itself. The vault download is a zip holding the mod-specific replay file,
   * so the two share this stem while retaining their own extensions.
   */
  public static String stem(Replay replay, @Nullable String modVersion) {
    return stem(replay, modVersion, ReplayDownloadNameOptions.all());
  }

  public static String stem(Replay replay, @Nullable String modVersion,
                            ReplayDownloadNameOptions options) {
    Objects.requireNonNull(options);
    ReplayMeta meta = replay.getReplayMeta();
    List<String> segments = new ArrayList<>();

    if (options.includeDate()) {
      segments.add(datestamp(replay.getStartTime()));
    }
    if (options.includeMod()) {
      String mod = modSegment(replay, modVersion);
      if (!mod.isEmpty()) {
        segments.add(mod);
      }
    }

    // Pre-replay_meta games (and any game whose demo never compiled) have no map or player list on
    // record. The server falls back to the replay id alone; match it rather than inventing a name
    // out of the TAF-side team lists, which carry logins, not in-game aliases. The mod is still
    // known from the game record, so it stays.
    if (meta == null || meta.getMapName() == null || meta.getPlayers() == null || meta.getPlayers().isEmpty()) {
      segments.add("TAF-" + replay.getId());
      return String.join(" - ", segments);
    }

    if (options.includeMap()) {
      segments.add(sanitize(meta.getMapName()));
    }
    if (options.includePlayers()) {
      String prefix = segments.isEmpty() ? "" : String.join(" - ", segments) + " - ";
      int budget = MAX_LENGTH - prefix.length() - extension(replay).length() - 1;
      String players = playerList(meta.getPlayers(), budget);
      if (!players.isEmpty()) {
        segments.add(players);
      }
    }

    if (segments.isEmpty()) {
      return "TAF-" + replay.getId();
    }
    return String.join(" - ", segments);
  }

  /**
   * {@code "Escalation 9.86"}, or {@code "Escalation"} with no known version, or {@code ""}
   * when the replay carries no featured mod at all (local replay files). Note the mod name itself
   * needs sanitising like any other: {@code TA:CC} has a colon in it.
   */
  private static String modSegment(Replay replay, @Nullable String modVersion) {
    Optional<FeaturedMod> featuredMod = Optional.ofNullable(replay.getFeaturedMod());
    String name = featuredMod
        .map(FeaturedMod::getDisplayNameNotLocalised)
        .filter(displayName -> !displayName.isBlank())
        // A mod the API has not given a display name still has a technical name ("taesc"), which
        // beats dropping the segment.
        .or(() -> featuredMod.map(FeaturedMod::getTechnicalName))
        .filter(displayName -> !displayName.isBlank())
        .map(CanonicalReplayName::sanitize)
        .filter(displayName -> !displayName.isEmpty())
        .orElse(null);

    if (name == null) {
      return "";
    }

    String version = modVersion == null ? "" : sanitize(modVersion).trim();
    return version.isEmpty() ? name : name + " " + version;
  }

  private static String extension(Replay replay) {
    return Optional.ofNullable(replay.getFeaturedMod())
        .map(FeaturedMod::getFileExtension)
        .filter(fileExtension -> !fileExtension.isBlank())
        .map(CanonicalReplayName::sanitize)
        .orElse("tad");
  }

  private static String datestamp(@Nullable OffsetDateTime startTime) {
    if (startTime == null) {
      return "0000-00-00";
    }
    return DATE_STAMP.format(startTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate());
  }

  /**
   * {@code "Alice, Bob, Carol"}, truncated to {@code budget} characters on a whole-name boundary.
   * The server emits the full list; a ten-player game on a long map name would otherwise overrun
   * the file name limit. Truncation is signalled so a shortened name is never mistaken for the
   * complete line-up.
   */
  private static String playerList(List<ReplayMetaPlayer> players, int budget) {
    List<String> names = players.stream()
        .map(ReplayMetaPlayer::getName)
        .filter(Objects::nonNull)
        .map(CanonicalReplayName::sanitize)
        .filter(name -> !name.isEmpty())
        .collect(Collectors.toList());

    if (names.isEmpty()) {
      return "";
    }

    StringBuilder joined = new StringBuilder(names.get(0));
    int included = 1;
    for (int i = 1; i < names.size(); i++) {
      String candidate = ", " + names.get(i);
      // Keep room for the "+N more" that the remaining names would need.
      int remaining = names.size() - i - 1;
      int reserve = remaining > 0 ? overflowMarker(remaining).length() : 0;
      if (joined.length() + candidate.length() + reserve > budget) {
        break;
      }
      joined.append(candidate);
      included++;
    }

    int dropped = names.size() - included;
    if (dropped > 0) {
      joined.append(overflowMarker(dropped));
    }
    return joined.toString();
  }

  private static String overflowMarker(int dropped) {
    return ", +" + dropped + " more";
  }

  /**
   * Strips what a file name may not carry. Player names come off the wire from TA and are not
   * constrained to anything, so this has to cope with arbitrary bytes, not just the polite cases.
   */
  static String sanitize(String raw) {
    StringBuilder cleaned = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c < 0x20 || c == 0x7F || ILLEGAL_CHARS.indexOf(c) >= 0) {
        cleaned.append('_');
      } else {
        cleaned.append(c);
      }
    }
    // Windows silently drops trailing dots and spaces, which would leave the saved file under a
    // name the caller never asked for.
    int end = cleaned.length();
    while (end > 0 && (cleaned.charAt(end - 1) == '.' || cleaned.charAt(end - 1) == ' ')) {
      end--;
    }
    return cleaned.substring(0, end);
  }
}
