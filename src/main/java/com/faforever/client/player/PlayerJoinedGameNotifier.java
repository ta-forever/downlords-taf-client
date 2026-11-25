package com.faforever.client.player;

import com.faforever.client.audio.AudioService;
import com.faforever.client.game.Game;
import com.faforever.client.game.JoinGameHelper;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.TransientNotification;
import com.faforever.client.player.event.PlayerJoinedGameEvent;
import com.faforever.client.preferences.NotificationsPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.util.IdenticonUtil;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Displays a notification if enabled in settings on:
 *   - any player joined current game
 *   - friend joined any game
 */
@Component
@RequiredArgsConstructor
public class PlayerJoinedGameNotifier implements InitializingBean {

  private final NotificationService notificationService;
  private final I18n i18n;
  private final EventBus eventBus;
  private final JoinGameHelper joinGameHelper;
  private final PreferencesService preferencesService;
  private final AudioService audioService;

  @Override
  public void afterPropertiesSet() {
    eventBus.register(this);
  }

  @Subscribe
  public void onPlayerJoinedGame(PlayerJoinedGameEvent event) {
    Player player = event.getPlayer();
    Game game = event.getGame();
    NotificationsPrefs notificationPrefs = preferencesService.getPreferences().getNotification();
    boolean friend = player.getSocialStatus() == SocialStatus.FRIEND;
    // "Player joins game" is about our own lobby; it must not re-enable "Friend joins game" for other lobbies.
    boolean joinedOurGame = event.isJoinedCurrentPlayersGame();

    if (friend && notificationPrefs.isFriendJoinsGameSoundEnabled()) {
      audioService.playFriendJoinsGameSound();
    }
    else if (joinedOurGame && notificationPrefs.isPlayerJoinsGameSoundEnabled()) {
      audioService.playPlayerJoinsGameSound();
    }

    if (friend && notificationPrefs.isFriendJoinsGameToastEnabled()
        || joinedOurGame && notificationPrefs.isPlayerJoinsGameToastEnabled()) {
      notificationService.addNotification(new TransientNotification(
          i18n.get("friend.joinedGameNotification.title", player.getUsername(), game.getTitle()),
          i18n.get("friend.joinedGameNotification.action"),
          IdenticonUtil.createIdenticon(player.getId()),
          event1 -> joinGameHelper.join(player.getGame())
      ));
    }
  }
}
