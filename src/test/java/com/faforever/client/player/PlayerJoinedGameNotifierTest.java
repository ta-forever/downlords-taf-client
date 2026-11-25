package com.faforever.client.player;

import com.faforever.client.audio.AudioService;
import com.faforever.client.game.Game;
import com.faforever.client.game.JoinGameHelper;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.TransientNotification;
import com.faforever.client.player.event.PlayerJoinedGameEvent;
import com.faforever.client.preferences.NotificationsPrefs;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static com.natpryce.hamcrest.reflection.HasAnnotationMatcher.hasAnnotation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #46: "Friend joins game" and "Player joins game" (someone joined our lobby) are separate settings; turning
 * off the friend one must silence friends joining other lobbies even while the player one is on (the default).
 */
public class PlayerJoinedGameNotifierTest extends AbstractPlainJavaFxTest {

  @Mock
  private NotificationService notificationService;
  @Mock
  private EventBus eventBus;
  @Mock
  private I18n i18n;
  @Mock
  private JoinGameHelper joinGameHelper;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private AudioService audioService;

  private NotificationsPrefs notificationPrefs;
  private PlayerJoinedGameNotifier instance;
  private Game game;

  @Before
  public void setUp() {
    Preferences preferences = new Preferences(java.util.List.of());
    notificationPrefs = preferences.getNotification();
    lenient().when(preferencesService.getPreferences()).thenReturn(preferences);

    game = new Game();
    game.setTitle("My Game");

    instance = new PlayerJoinedGameNotifier(notificationService, i18n, eventBus, joinGameHelper, preferencesService, audioService);
    instance.afterPropertiesSet();
  }

  private Player player(SocialStatus socialStatus) {
    Player player = new Player("junit");
    player.setId(1);
    player.setSocialStatus(socialStatus);
    return player;
  }

  private void fire(SocialStatus socialStatus, boolean joinedCurrentPlayersGame) {
    instance.onPlayerJoinedGame(new PlayerJoinedGameEvent(player(socialStatus), game, joinedCurrentPlayersGame));
  }

  @Test
  public void registersOnEventBus() {
    verify(eventBus).register(instance);
    assertThat(org.springframework.util.ReflectionUtils.findMethod(PlayerJoinedGameNotifier.class, "onPlayerJoinedGame", PlayerJoinedGameEvent.class),
        hasAnnotation(Subscribe.class));
  }

  @Test
  public void friendJoiningAnotherGameShowsToastWhenFriendToastEnabled() {
    when(i18n.get("friend.joinedGameNotification.title", "junit", "My Game")).thenReturn("junit joined My Game");
    when(i18n.get("friend.joinedGameNotification.action")).thenReturn("Click to join");

    fire(SocialStatus.FRIEND, false);

    ArgumentCaptor<TransientNotification> captor = ArgumentCaptor.forClass(TransientNotification.class);
    verify(notificationService).addNotification(captor.capture());
    assertThat(captor.getValue().getTitle(), is("junit joined My Game"));
    assertThat(captor.getValue().getText(), is("Click to join"));
    assertThat(captor.getValue().getImage(), notNullValue());
  }

  /** The bug: with only "Friend joins game" turned off, the default-on "Player joins game" toast still fired. */
  @Test
  public void friendJoiningAnotherGameIsSilentWhenFriendNotificationsDisabled() {
    notificationPrefs.setFriendJoinsGameToastEnabled(false);
    notificationPrefs.setFriendJoinsGameSoundEnabled(false);
    notificationPrefs.setPlayerJoinsGameToastEnabled(true);
    notificationPrefs.setPlayerJoinsGameSoundEnabled(true);

    fire(SocialStatus.FRIEND, false);

    verify(notificationService, never()).addNotification(any(TransientNotification.class));
    verify(audioService, never()).playFriendJoinsGameSound();
    verify(audioService, never()).playPlayerJoinsGameSound();
  }

  @Test
  public void playerJoiningOurGameShowsToastWhenPlayerToastEnabled() {
    notificationPrefs.setFriendJoinsGameToastEnabled(false);

    fire(SocialStatus.OTHER, true);

    verify(notificationService).addNotification(any(TransientNotification.class));
    verify(audioService).playPlayerJoinsGameSound();
  }

  @Test
  public void playerJoiningOurGameIsSilentWhenPlayerNotificationsDisabled() {
    notificationPrefs.setPlayerJoinsGameToastEnabled(false);
    notificationPrefs.setPlayerJoinsGameSoundEnabled(false);

    fire(SocialStatus.OTHER, true);

    verify(notificationService, never()).addNotification(any(TransientNotification.class));
    verify(audioService, never()).playPlayerJoinsGameSound();
  }

  /** A friend joining our own lobby counts as "a player joined our game" too. */
  @Test
  public void friendJoiningOurGameStillNotifiesViaPlayerSettingWhenFriendSettingDisabled() {
    notificationPrefs.setFriendJoinsGameToastEnabled(false);
    notificationPrefs.setFriendJoinsGameSoundEnabled(false);

    fire(SocialStatus.FRIEND, true);

    verify(notificationService).addNotification(any(TransientNotification.class));
    verify(audioService).playPlayerJoinsGameSound();
    verify(audioService, never()).playFriendJoinsGameSound();
  }

  @Test
  public void friendSoundTakesPriorityOverPlayerSound() {
    fire(SocialStatus.FRIEND, true);

    verify(audioService).playFriendJoinsGameSound();
    verify(audioService, never()).playPlayerJoinsGameSound();
  }

  @Test
  public void bothSettingsDisabledMeansNoNotification() {
    notificationPrefs.setFriendJoinsGameToastEnabled(false);
    notificationPrefs.setFriendJoinsGameSoundEnabled(false);
    notificationPrefs.setPlayerJoinsGameToastEnabled(false);
    notificationPrefs.setPlayerJoinsGameSoundEnabled(false);

    fire(SocialStatus.FRIEND, true);

    verify(notificationService, never()).addNotification(any(TransientNotification.class));
    verify(audioService, never()).playFriendJoinsGameSound();
    verify(audioService, never()).playPlayerJoinsGameSound();
  }
}
