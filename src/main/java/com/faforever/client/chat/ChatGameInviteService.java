package com.faforever.client.chat;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameInviteUrl;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.util.ConcurrentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatGameInviteService {

  private static final Duration INVITE_COOLDOWN = Duration.ofSeconds(30);

  private final ChatService chatService;
  private final ClientProperties clientProperties;
  private final NotificationService notificationService;
  private final Map<String, Instant> lastInviteTimes = new ConcurrentHashMap<>();

  public boolean canInvite(String username, Game game) {
    return !isOnCooldown(username, game);
  }

  public CompletableFuture<String> inviteToGame(String username, Game game) {
    if (isOnCooldown(username, game)) {
      return CompletableFuture.completedFuture(null);
    }

    String cooldownKey = cooldownKey(username, game.getId());
    lastInviteTimes.put(cooldownKey, Instant.now());

    String inviteUrl = GameInviteUrl.build(clientProperties.getWebsite().getBaseUrl(), game.getId());
    String message = "Join my game \""
        + valueOrFallback(game.getTitle(), "Untitled game")
        + "\" on "
        + valueOrFallback(game.getMapName(), "Unknown map")
        + " ("
        + game.getNumPlayers()
        + "/"
        + game.getMaxPlayers()
        + " players): "
        + inviteUrl;

    return chatService.sendMessageInBackground(username, message)
        .exceptionally(throwable -> {
          lastInviteTimes.remove(cooldownKey);
          throwable = ConcurrentUtil.unwrapIfCompletionException(throwable);
          log.warn("Game invite could not be sent to {}", username, throwable);
          notificationService.addImmediateErrorNotification(throwable, "chat.sendFailed");
          return null;
        });
  }

  private boolean isOnCooldown(String username, Game game) {
    if (username == null || game == null) {
      return true;
    }

    Instant lastInvite = lastInviteTimes.get(cooldownKey(username, game.getId()));
    return lastInvite != null && Instant.now().isBefore(lastInvite.plus(INVITE_COOLDOWN));
  }

  private String cooldownKey(String username, int gameId) {
    return gameId + ":" + username.toLowerCase(Locale.US);
  }

  private String valueOrFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
