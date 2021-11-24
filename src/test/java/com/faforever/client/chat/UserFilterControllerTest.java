package com.faforever.client.chat;

import com.faforever.client.game.GameBuilder;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.LeaderboardRatingMapBuilder;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerBuilder;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.faforever.client.remote.domain.PlayerStatus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class UserFilterControllerTest extends AbstractPlainJavaFxTest {

  @Mock
  private ChannelTabController channelTabController;
  @Mock
  private I18n i18n;
  @Mock
  private CountryFlagService flagService;


  private ChatChannelUser chatChannelUser;
  private UserFilterController instance;
  private Player player;

  @Before
  public void setUp() throws Exception {
    instance = new UserFilterController(i18n, flagService);
    instance.channelTabController = channelTabController;

    player = PlayerBuilder.create("junit").defaultValues().get();
    chatChannelUser = ChatChannelUserBuilder.create("junit")
        .defaultValues()
        .player(player)
        .get();

    loadFxml("theme/chat/user_filter.fxml", clazz -> instance);
  }

  @Test
  public void setChannelTabControllerTest() {
    instance.setChannelController(channelTabController);
    assertEquals(channelTabController, instance.channelTabController);
  }

  @Test
  public void testIsInClan() {
    String testClan = "testClan";
    player.setClan(testClan);
    instance.clanFilterField.setText(testClan);

    assertTrue(instance.isInClan(chatChannelUser));
  }

  @Test
  public void testIsGameStatusMatchPlaying() {
    player.setGame(GameBuilder.create().defaultValues().status(GameStatus.LIVE).get());
    instance.playerStatusFilter = PlayerStatus.PLAYING;

    assertTrue(instance.isGameStatusMatch(chatChannelUser));
  }

  @Test
  public void testIsGameStatusMatchLobby() {
    player.setGame(GameBuilder.create().defaultValues().status(GameStatus.STAGING).host(player.getUsername()).get());
    instance.playerStatusFilter = PlayerStatus.HOSTING;

    assertTrue(instance.isGameStatusMatch(chatChannelUser));

    player.setGame(GameBuilder.create().defaultValues().status(GameStatus.STAGING).get());
    instance.playerStatusFilter = PlayerStatus.JOINING;

    assertTrue(instance.isGameStatusMatch(chatChannelUser));
  }

  @Test
  public void testOnGameStatusPlaying() {
    when(i18n.get("game.gameStatus.playing")).thenReturn("playing");

    instance.onGameStatusPlaying();
    assertEquals(PlayerStatus.PLAYING, instance.playerStatusFilter);
    assertEquals(i18n.get("game.gameStatus.playing"), instance.gameStatusMenu.getText());
  }

  @Test
  public void testOnGameStatusLobby() {
    when(i18n.get("game.gameStatus.lobby")).thenReturn("lobby");

    instance.onGameStatusLobby();
    assertEquals(PlayerStatus.JOINING, instance.playerStatusFilter);
    assertEquals(i18n.get("game.gameStatus.lobby"), instance.gameStatusMenu.getText());
  }

  @Test
  public void testOnGameStatusNone() {
    when(i18n.get("game.gameStatus.none")).thenReturn("none");

    instance.onGameStatusNone();
    assertEquals(PlayerStatus.IDLE, instance.playerStatusFilter);
    assertEquals(i18n.get("game.gameStatus.none"), instance.gameStatusMenu.getText());
  }
}
