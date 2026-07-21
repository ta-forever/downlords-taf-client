package com.faforever.client.chat;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.game.Game;
import com.faforever.client.notification.NotificationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChatGameInviteServiceTest {

  @Mock
  private ChatService chatService;
  @Mock
  private NotificationService notificationService;

  private ChatGameInviteService instance;

  @Before
  public void setUp() {
    ClientProperties clientProperties = new ClientProperties();
    clientProperties.getWebsite().setBaseUrl("https://www.taforever.com");

    instance = new ChatGameInviteService(chatService, clientProperties, notificationService);
  }

  @Test
  public void testInviteMessageIsSentInEnglish() {
    Game game = new Game();
    game.setId(1234);
    game.setTitle("Mullet");
    game.setMapName("Comet Catcher");
    game.setNumPlayers(1);
    game.setMaxPlayers(4);

    when(chatService.sendMessageInBackground("Axle1975",
        "Join my game \"Mullet\" on Comet Catcher (1/4 players): https://www.taforever.com/play/join/1234"))
        .thenReturn(CompletableFuture.completedFuture("message"));

    instance.inviteToGame("Axle1975", game);

    verify(chatService).sendMessageInBackground("Axle1975",
        "Join my game \"Mullet\" on Comet Catcher (1/4 players): https://www.taforever.com/play/join/1234");
  }
}
