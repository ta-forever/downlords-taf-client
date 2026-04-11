package com.faforever.client.tournament;

import com.faforever.client.remote.FafService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Lazy
@Service
@Slf4j
public class TournamentService {
  private final FafService fafService;

  public TournamentService(FafService fafService) {
    this.fafService = fafService;
  }

  public CompletableFuture<List<TournamentBean>> getAllTournaments() {
    return fafService.getAllTournaments();
  }

  /**
   * Fetch a single tournament with the full graph (participants, matches,
   * planned maps, placements, standings). Used by the detail pane when a row
   * is selected — the list query is intentionally light, so this is what
   * actually pulls the bracket data.
   */
  public CompletableFuture<TournamentBean> getTournamentById(String id) {
    return fafService.getTournamentById(id);
  }

  /**
   * Hall of Fame: per-player aggregated tournament achievements. Pass
   * {@code null} for the across-all-mods rollup, or a specific featured mod
   * id to scope to that mod only.
   */
  public CompletableFuture<List<HallOfFameEntryBean>> getHallOfFame(Integer featuredModId) {
    return fafService.getHallOfFame(featuredModId)
        .thenApply(dtos -> dtos.stream()
            .map(HallOfFameEntryBean::fromDto)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList()));
  }

  /**
   * List of game IDs the player has played in completed tournaments. Pass
   * {@code null} for the featured mod to span all mods. Used by the Hall of
   * Fame "View Replays" context menu.
   */
  public CompletableFuture<List<Integer>> getPlayerTournamentGameIds(int playerId, Integer featuredModId) {
    return fafService.getPlayerTournamentGameIds(playerId, featuredModId);
  }
}
