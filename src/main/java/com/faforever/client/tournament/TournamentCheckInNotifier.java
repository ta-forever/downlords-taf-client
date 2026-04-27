package com.faforever.client.tournament;

import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.NavigationItem;
import com.faforever.client.main.event.RefreshTournamentsEvent;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.TournamentCheckInRequiredMessage;
import com.google.common.eventbus.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

/**
 * Eager-loaded listener for the {@code tournament_check_in_required} server
 * message.
 *
 * <p>The natural home for this would be {@link TournamentsController}, but
 * that controller is lazy-instantiated by Spring (only when the user first
 * navigates to the Tournaments tab) — so until that happens, no
 * addOnMessageListener is registered and the server's check-in nag arrives
 * to a client with nothing listening, silently dropped. The user sees
 * nothing despite the message being on the wire.
 *
 * <p>This component is a singleton bean (default Spring scope), constructed
 * at application startup. {@code @PostConstruct} registers the listener on
 * {@link FafService} before any user interaction. Login replays delivered
 * during the first lobby connection are caught.
 *
 * <p>De-dupe is per (login session, tournament id): repeat messages for the
 * same tournament don't re-pop the dialog. The session-scoped Set is fine
 * because the message is only re-fired by the server on login replay or
 * a fresh PENDING -> CHECK_IN transition; both are infrequent.
 */
@Component
@Slf4j
public class TournamentCheckInNotifier {

  private final FafService fafService;
  private final NotificationService notificationService;
  private final I18n i18n;
  private final EventBus eventBus;
  private final Set<Integer> notifiedTournamentIds = new HashSet<>();

  public TournamentCheckInNotifier(FafService fafService,
                                    NotificationService notificationService,
                                    I18n i18n,
                                    EventBus eventBus) {
    this.fafService = fafService;
    this.notificationService = notificationService;
    this.i18n = i18n;
    this.eventBus = eventBus;
  }

  @PostConstruct
  void init() {
    fafService.addOnMessageListener(TournamentCheckInRequiredMessage.class,
        msg -> JavaFxUtil.runLater(() -> handle(msg)));
  }

  private void handle(TournamentCheckInRequiredMessage msg) {
    Integer tid = msg.getTournamentId();
    log.debug("Received tournament_check_in_required for tournament {}", tid);
    if (tid == null || !notifiedTournamentIds.add(tid)) return;
    int tidInt = tid;
    java.util.List<Action> actions = new java.util.ArrayList<>();
    actions.add(new Action(i18n.get("tournament.checkIn"),
        ev -> fafService.tournamentCheckIn(tidInt)));
    actions.add(new Action(i18n.get("tournament.checkIn.open"),
        ev -> {
          eventBus.post(new NavigateEvent(NavigationItem.TOURNAMENTS));
          // Tell the (now-loading-or-loaded) Tournaments tab to refresh and
          // select this tournament. RefreshTournamentsEvent with selectAfter=true
          // does both in one round-trip.
          eventBus.post(new RefreshTournamentsEvent(tidInt, true));
        }));
    notificationService.addNotification(new ImmediateNotification(
        i18n.get("tournament.checkIn.title"),
        i18n.get("tournament.checkIn.text"),
        Severity.INFO, actions));
  }
}
