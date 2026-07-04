package com.faforever.client.ladder;

import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.TransientNotification;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.NotificationsPrefs;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LadderSocialServiceTest {

  private static final int ME = 1;
  private static final int FRIEND = 2;

  @Mock private PlayerService playerService;
  @Mock private NotificationService notificationService;
  @Mock private PreferencesService preferencesService;
  @Mock private I18n i18n;

  private final NotificationsPrefs notificationsPrefs = new NotificationsPrefs();
  private LadderSocialService service;

  @Before
  public void setUp() {
    service = new LadderSocialService(playerService, notificationService, preferencesService, i18n);

    Player me = mock(Player.class);
    when(me.getId()).thenReturn(ME);
    Player friend = mock(Player.class);
    lenient().when(friend.getUsername()).thenReturn("Frostbite");

    when(playerService.getCurrentPlayer()).thenReturn(Optional.of(me));
    lenient().when(playerService.isFriend(FRIEND)).thenReturn(true);
    lenient().when(playerService.getPlayerById(FRIEND)).thenReturn(Optional.of(friend));

    Preferences preferences = mock(Preferences.class);
    when(preferences.getNotification()).thenReturn(notificationsPrefs);
    when(preferencesService.getPreferences()).thenReturn(preferences);
    // i18n is left unstubbed: the tests assert on toast count, not copy, and a mock returns null
    // strings harmlessly. (Avoids brittle varargs matcher stubs.)
  }

  private SeasonStanding row(int playerId) {
    return new SeasonStanding(playerId, "p" + playerId, "ladder1v1_taesc", 1, 1, 0, 0, 0,
        0, 0, 0, 0, 0, "", 0);
  }

  @Test
  public void firstRefreshSeedsWithoutToasting() {
    // Friend ahead of me.
    service.detectPasses("ladder1v1_taesc", "Esc 1v1", List.of(row(FRIEND), row(ME)));
    verify(notificationService, never()).addNotification(any(TransientNotification.class));
  }

  @Test
  public void passingAFriendFiresExactlyOneToast() {
    service.detectPasses("ladder1v1_taesc", "Esc 1v1", List.of(row(FRIEND), row(ME))); // seed: I'm behind
    service.detectPasses("ladder1v1_taesc", "Esc 1v1", List.of(row(ME), row(FRIEND))); // now ahead
    verify(notificationService, times(1)).addNotification(any(TransientNotification.class));

    // A static ladder must not re-toast.
    service.detectPasses("ladder1v1_taesc", "Esc 1v1", List.of(row(ME), row(FRIEND)));
    verify(notificationService, times(1)).addNotification(any(TransientNotification.class));
  }

  @Test
  public void mutedPreferenceSuppressesToast() {
    notificationsPrefs.setLadderPassToastEnabled(false);
    service.detectPasses("ladder1v1_taesc", "Esc 1v1", List.of(row(FRIEND), row(ME)));
    service.detectPasses("ladder1v1_taesc", "Esc 1v1", List.of(row(ME), row(FRIEND)));
    verify(notificationService, never()).addNotification(any(TransientNotification.class));
  }

  @Test
  public void fallingBehindNeverToasts() {
    // Positive-only: being passed by a friend must never notify.
    service.detectPasses("ladder1v1_taesc", "Esc 1v1", List.of(row(ME), row(FRIEND))); // seed: I'm ahead
    service.detectPasses("ladder1v1_taesc", "Esc 1v1", List.of(row(FRIEND), row(ME))); // friend passed me
    verify(notificationService, never()).addNotification(any(TransientNotification.class));
  }
}
