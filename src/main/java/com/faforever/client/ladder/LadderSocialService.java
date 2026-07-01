package com.faforever.client.ladder;

import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.TransientNotification;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Social comparison v1 (LADDER_POINTS_DESIGN.md §15.2) — fires a one-time "you passed {friend}"
 * toast when a season-ladder refresh shows the current player has overtaken a friend since the last
 * refresh of that board.
 *
 * <p>Guardrails (§15 "Guardrails"): <b>positive-only</b> (only "you passed", never "you were
 * passed"), driven entirely off data already loaded for the leaderboard (no extra fetches), and
 * <b>individually mutable</b> via {@code notifications.ladderPassToastEnabled}. The diff is
 * inherently rate-limited: a pass fires once and the new ranks become the baseline, so a static
 * ladder never re-toasts.
 */
@Lazy
@Service
@Slf4j
@RequiredArgsConstructor
public class LadderSocialService {

  private final PlayerService playerService;
  private final NotificationService notificationService;
  private final PreferencesService preferencesService;
  private final I18n i18n;

  /** Per-board last-seen ranks of {self + friends}, keyed by leaderboard technical name. */
  private final Map<String, Map<Integer, Integer>> lastSeenRanksByBoard = new ConcurrentHashMap<>();

  /**
   * Diff a freshly loaded season ladder against the last seen state for this board and toast any
   * friends the current player has overtaken. The first call per board only seeds the baseline
   * (no toasts), so opening the tab never spams historical passes.
   *
   * @param technicalName    the board's leaderboard technical name (the diff key)
   * @param boardDisplayName the localized board name, shown in the toast
   * @param ladder           the full season ladder, ranked by score desc (rank == index + 1)
   */
  public void detectPasses(String technicalName, String boardDisplayName, java.util.List<SeasonStanding> ladder) {
    Optional<Player> currentPlayer = playerService.getCurrentPlayer();
    if (currentPlayer.isEmpty() || ladder == null || ladder.isEmpty()) {
      return;
    }
    int myId = currentPlayer.get().getId();

    // Current ranks of me + my friends, plus my own standing for the "now ..." copy.
    Map<Integer, Integer> currentRanks = new HashMap<>();
    SeasonStanding myStanding = null;
    for (int i = 0; i < ladder.size(); i++) {
      SeasonStanding standing = ladder.get(i);
      int pid = standing.getPlayerId();
      if (pid == myId) {
        myStanding = standing;
        currentRanks.put(pid, i + 1);
      } else if (playerService.isFriend(pid)) {
        currentRanks.put(pid, i + 1);
      }
    }

    Map<Integer, Integer> previousRanks = lastSeenRanksByBoard.put(technicalName, currentRanks);
    Integer myRankNow = currentRanks.get(myId);

    // Seed-only on first sight, when I'm absent, or when toasts are muted.
    if (previousRanks == null || myRankNow == null
        || !preferencesService.getPreferences().getNotification().isLadderPassToastEnabled()) {
      return;
    }
    Integer myRankBefore = previousRanks.get(myId);
    if (myRankBefore == null) {
      return;
    }

    String myStandingText = formatStanding(myStanding, myRankNow);
    for (Map.Entry<Integer, Integer> entry : currentRanks.entrySet()) {
      int friendId = entry.getKey();
      if (friendId == myId) {
        continue;
      }
      Integer friendRankNow = entry.getValue();
      Integer friendRankBefore = previousRanks.get(friendId);
      if (friendRankBefore == null) {
        continue;  // friend wasn't ranked last time — no defensible "passed" event
      }
      boolean wasBehindFriend = myRankBefore > friendRankBefore;
      boolean nowAheadOfFriend = myRankNow < friendRankNow;
      if (wasBehindFriend && nowAheadOfFriend) {
        playerService.getPlayerById(friendId)
            .map(Player::getUsername)
            .ifPresent(friendName -> notificationService.addNotification(new TransientNotification(
                i18n.get("social.passed.title"),
                i18n.get("social.passed", friendName, boardDisplayName, myStandingText))));
      }
    }
  }

  /** "#14" — the player's new within-board rank after the pass. */
  private String formatStanding(SeasonStanding standing, int rank) {
    return i18n.get("social.standing.rankOnly", rank);
  }

  /** Drop all baselines (e.g. on logout) so a new session re-seeds cleanly. */
  public void clear() {
    lastSeenRanksByBoard.clear();
  }
}
