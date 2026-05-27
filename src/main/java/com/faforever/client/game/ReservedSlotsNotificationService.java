package com.faforever.client.game;

import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.PersistentNotification;
import com.faforever.client.notification.Severity;
import com.faforever.client.notification.TransientNotification;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.HostGameStateMessage;
import com.faforever.client.remote.domain.HostGameStateMessage.JoinRequestEntry;
import com.faforever.client.remote.domain.NoticeMessage;
import com.faforever.client.remote.domain.ReservedSlotsProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Surfaces the reserved-slots cross-component flows:
 *
 *   1. Host-side: incoming join requests via {@code host_game_state} push.
 *      The full request list is mirrored onto the target Game's
 *      {@link Game#joinRequestsProperty} so the game-detail card can render
 *      the inline "knocking on door" rows with tick/cross actions. A short
 *      transient toast is fired on each newly-arrived request as an
 *      attention-grab in case the host isn't currently looking at the
 *      game-detail view. Diff-tracking on {@code (gameUid, playerId)} pairs
 *      ensures we don't re-toast on every dirty cycle.
 *
 *   2. Joiner-side denial: {@code notice} with {@code style=game_join_fail}
 *      and {@code reason_code=reserved_slots} -> modal with "Request access".
 *
 *   3. Joiner-side invite: {@code notice} with {@code style=game_join_invite}
 *      -> persistent notification with "Join now" / "Dismiss".
 *
 *   4. Leaver-side auto-reservation: {@code notice} with
 *      {@code style=reserved_slot_auto_reserved} -> hold/cancel prompt.
 *
 * Lives in its own Spring service rather than being inlined into
 * FafServerAccessorImpl so it can inject GameService / JoinGameHelper without
 * creating a circular dependency through the accessor.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReservedSlotsNotificationService {

  private final FafService fafService;
  private final NotificationService notificationService;
  private final JoinGameHelper joinGameHelper;
  private final GameService gameService;
  private final I18n i18n;

  /** Last-seen set of pending join requests per game, used for diffing the
   *  next push so we only toast once per newly-arrived request. */
  private final Map<Integer, Set<Integer>> lastSeenRequestsByGame = new HashMap<>();
  /** Game ids for which THIS user has already submitted a request-access.
   *  Matches the server's one-shot-per-game rule
   *  ({@code Game.requested_player_ids}); we track it locally too so the
   *  denial modal stops offering the "Request access" button once it's been
   *  used. In-memory only; lost on app restart (server still enforces). */
  private final Set<Integer> requestedGameIds = ConcurrentHashMap.newKeySet();

  @PostConstruct
  public void initialize() {
    fafService.addOnMessageListener(HostGameStateMessage.class, this::onHostGameState);
    fafService.addOnMessageListener(NoticeMessage.class, this::onNotice);
  }

  private void onHostGameState(HostGameStateMessage message) {
    Integer gameUid = message.getGameUid();
    List<JoinRequestEntry> requests = message.getJoinRequests();
    if (gameUid == null) {
      return;
    }

    // 1. Mirror onto the Game so the game-detail view can render inline rows.
    Game game = gameService.getByUid(gameUid);
    if (game != null) {
      List<Game.JoinRequest> mapped = new ArrayList<>();
      if (requests != null) {
        for (JoinRequestEntry entry : requests) {
          if (entry.getPlayerId() == null) continue;
          String login = entry.getPlayerLogin() != null
              ? entry.getPlayerLogin()
              : ("#" + entry.getPlayerId());
          mapped.add(new Game.JoinRequest(entry.getPlayerId(), login));
        }
      }
      JavaFxUtil.runLater(() -> game.getJoinRequests().setAll(mapped));
    }

    // 2. Diff & toast only NEW requests.
    Set<Integer> nowSet = new HashSet<>();
    if (requests != null) {
      for (JoinRequestEntry entry : requests) {
        if (entry.getPlayerId() != null) {
          nowSet.add(entry.getPlayerId());
        }
      }
    }
    Set<Integer> prevSet = lastSeenRequestsByGame.getOrDefault(gameUid, Set.of());
    Set<Integer> newlyAdded = new HashSet<>(nowSet);
    newlyAdded.removeAll(prevSet);

    if (nowSet.isEmpty()) {
      lastSeenRequestsByGame.remove(gameUid);
    } else {
      lastSeenRequestsByGame.put(gameUid, nowSet);
    }

    if (newlyAdded.isEmpty() || requests == null) {
      return;
    }
    for (JoinRequestEntry entry : requests) {
      if (entry.getPlayerId() == null || !newlyAdded.contains(entry.getPlayerId())) {
        continue;
      }
      String requesterName = entry.getPlayerLogin() != null
          ? entry.getPlayerLogin()
          : ("#" + entry.getPlayerId());
      notificationService.addNotification(new TransientNotification(
          i18n.get("reservedSlots.requestAccess.toastTitle"),
          i18n.get("reservedSlots.requestAccess.toast", requesterName)));
    }
  }

  private void onNotice(NoticeMessage message) {
    String style = message.getStyle();
    String reasonCode = message.getReasonCode();
    Integer gameUid = message.getGameUid();

    // Joiner-side: denied entry to a reserved-slots game. Modal so the user
    // sees and acts on it immediately — they were actively trying to join
    // and need to either request access or acknowledge. FafServerAccessorImpl
    // suppresses its generic English modal when reason_code is set, so this
    // is the only notification shown to a modern client (legacy clients,
    // which don't recognize reason_code, still get the plain English modal).
    if (ReservedSlotsProtocol.NOTICE_STYLE_GAME_JOIN_FAIL.equals(style)
        && ReservedSlotsProtocol.NOTICE_REASON_CODE_RESERVED_SLOTS.equals(reasonCode)
        && gameUid != null) {
      String body = message.getText() != null
          ? message.getText()
          : i18n.get("reservedSlots.denied.fallback");
      int gameId = gameUid;
      List<Action> actions;
      if (requestedGameIds.contains(gameId)) {
        // Already asked the host for this game — server one-shots, so we
        // only offer dismiss here. Body is augmented so the user knows
        // their first request is still pending / was answered.
        actions = List.of(new Action(i18n.get("reservedSlots.denied.acknowledge"), ev -> { }));
        body = body + "\n\n" + i18n.get("reservedSlots.denied.alreadyRequested");
      } else {
        actions = List.of(
            new Action(i18n.get("reservedSlots.denied.requestAccess"), ev -> {
              // Pre-mark before firing so an early re-show doesn't re-offer.
              requestedGameIds.add(gameId);
              fafService.requestGameAccess(gameId);
            }),
            new Action(i18n.get("reservedSlots.denied.acknowledge"), ev -> { })
        );
      }
      ImmediateNotification notification = new ImmediateNotification(
          i18n.get("reservedSlots.denied.title"),
          body,
          Severity.WARN,
          actions);
      notificationService.addNotification(notification);
      return;
    }

    // Leaver-side: server auto-reserved our slot for 30 seconds when we
    // disconnected from a reserved-slots BR. Pop the upgrade/cancel prompt.
    if (ReservedSlotsProtocol.NOTICE_STYLE_RESERVED_SLOT_AUTO_RESERVED.equals(style) && gameUid != null) {
      gameService.offerReserveOnBattleroomExit(gameUid);
      return;
    }

    // Joiner-side: host has approved their request and invited them to join.
    if (ReservedSlotsProtocol.NOTICE_STYLE_GAME_JOIN_INVITE.equals(style) && gameUid != null) {
      int gameId = gameUid;
      PersistentNotification notification = new PersistentNotification(
          message.getText() != null
              ? message.getText()
              : i18n.get("reservedSlots.invitedToJoin"),
          Severity.INFO,
          List.of(
              new Action(i18n.get("reservedSlots.invitedToJoin.joinNow"),
                  ev -> joinInvitedGame(gameId)),
              new Action(i18n.get("dismiss"), ev -> { })
          ));
      notificationService.addNotification(notification);
    }
  }

  private void joinInvitedGame(int gameId) {
    Game game = gameService.getByUid(gameId);
    if (game == null) {
      log.warn("[joinInvitedGame] game {} not in local cache; ignoring invite", gameId);
      return;
    }
    joinGameHelper.join(game);
  }
}
