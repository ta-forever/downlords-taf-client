package com.faforever.client.tournament;

import com.faforever.client.api.dto.PlayerTournamentSummary;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Plain Java bean wrapping a {@link PlayerTournamentSummary} DTO for use in
 * the Hall of Fame TableView. The DTO has lazy fields and JSON:API
 * relationship objects, which are awkward to bind directly to TableView
 * cells; this wrapper extracts the flat values once at construction time.
 *
 * <p>{@code featuredModId} is null on the across-all-mods rollup row.
 *
 * <p>{@link #rank} is assigned by the controller after the API response
 * is sorted by {@link #DEFAULT_COMPARATOR}, so it reflects the canonical
 * "golds → silvers → bronzes → participations → name" ordering and stays
 * stable when the user re-sorts the TableView by some other column.
 */
@Getter
public class HallOfFameEntryBean {

  private final int playerId;
  private final String playerLogin;
  private final Integer featuredModId;
  private final int firsts;
  private final int seconds;
  private final int thirds;
  private final int participations;
  private final OffsetDateTime lastTournamentAt;

  /**
   * Stable position in the canonical (default-comparator) ranking. 1-based.
   * Set by HallOfFameController after each fetch. Mutable so we can keep
   * the rest of the bean immutable while still allowing rank assignment
   * post-construction.
   */
  @Setter
  private int rank;

  public HallOfFameEntryBean(int playerId, String playerLogin, Integer featuredModId,
                             int firsts, int seconds, int thirds, int participations,
                             OffsetDateTime lastTournamentAt) {
    this.playerId = playerId;
    this.playerLogin = playerLogin;
    this.featuredModId = featuredModId;
    this.firsts = firsts;
    this.seconds = seconds;
    this.thirds = thirds;
    this.participations = participations;
    this.lastTournamentAt = lastTournamentAt;
  }

  public static HallOfFameEntryBean fromDto(PlayerTournamentSummary dto) {
    if (dto == null || dto.getPlayer() == null) {
      return null;
    }
    int playerId = 0;
    try {
      playerId = Integer.parseInt(dto.getPlayer().getId());
    } catch (NumberFormatException ignored) {}
    String login = dto.getPlayer().getLogin();
    Integer modId = null;
    if (dto.getFeaturedMod() != null) {
      try {
        modId = Integer.parseInt(dto.getFeaturedMod().getId());
      } catch (NumberFormatException ignored) {}
    }
    return new HallOfFameEntryBean(
        playerId,
        login,
        modId,
        dto.getFirsts(),
        dto.getSeconds(),
        dto.getThirds(),
        dto.getParticipations(),
        dto.getLastTournamentAt());
  }

  /**
   * Default Hall of Fame ordering: firsts desc → seconds desc → thirds desc
   * → participations desc → name asc. Used as the initial sort and as the
   * tiebreaker when the user clicks a column header.
   */
  public static final java.util.Comparator<HallOfFameEntryBean> DEFAULT_COMPARATOR =
      java.util.Comparator
          .comparingInt(HallOfFameEntryBean::getFirsts).reversed()
          .thenComparing(java.util.Comparator.comparingInt(HallOfFameEntryBean::getSeconds).reversed())
          .thenComparing(java.util.Comparator.comparingInt(HallOfFameEntryBean::getThirds).reversed())
          .thenComparing(java.util.Comparator.comparingInt(HallOfFameEntryBean::getParticipations).reversed())
          .thenComparing(HallOfFameEntryBean::getPlayerLogin,
              java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
}
