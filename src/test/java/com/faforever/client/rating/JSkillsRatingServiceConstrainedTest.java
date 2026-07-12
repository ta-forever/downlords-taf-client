package com.faforever.client.rating;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.update.ClientConfiguration;
import javafx.util.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

/**
 * Tests the constrained team balancer's soft-pin behaviour: it must always
 * produce a valid interleaved order (team 0 equal to, or one larger than,
 * team 1) by relaxing over-pinned constraints.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class JSkillsRatingServiceConstrainedTest {

  @Mock
  private ClientProperties clientProperties;
  @Mock
  private ClientProperties.TrueSkill trueSkill;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private PlayerService playerService;
  @Mock
  private FafService fafService;

  private JSkillsRatingService instance;
  private final Map<Integer, Pair<String, LeaderboardRating>> ratings = new HashMap<>();

  @Before
  public void setUp() {
    when(clientProperties.getTrueSkill()).thenReturn(trueSkill);
    ClientConfiguration config = new ClientConfiguration();
    ClientConfiguration.AutoBalance autoBalance = new ClientConfiguration.AutoBalance();
    autoBalance.setMetric("kl");
    config.setAutoBalance(autoBalance);
    when(preferencesService.getClientRemoteConfiguration()).thenReturn(config);

    instance = new JSkillsRatingService(clientProperties, preferencesService, playerService, fafService);
  }

  private Player player(int id, float mean) {
    Player p = new Player(new com.faforever.client.remote.domain.Player());
    p.setId(id);
    p.setUsername("p" + id);
    ratings.put(id, new Pair<>("global", LeaderboardRating.create(mean, 50f)));
    return p;
  }

  private static int evenCount(List<Player> order) {
    return (order.size() + 1) / 2;
  }

  private static int indexOfId(List<Player> order, int id) {
    for (int i = 0; i < order.size(); i++) {
      if (order.get(i).getId() == id) {
        return i;
      }
    }
    return -1;
  }

  @Test
  public void overPinnedTeamIsRelaxedToValidInterleave() {
    List<Player> all = new ArrayList<>(List.of(
        player(1, 1000), player(2, 1500), player(3, 1400),
        player(4, 900), player(5, 1100), player(6, 1200)));
    Map<Integer, Integer> pins = new HashMap<>();
    pins.put(1, 0);   // host
    pins.put(2, 0);
    pins.put(3, 0);
    pins.put(4, 0);   // 4 of 6 pinned to team 0 — impossible to interleave

    List<Player> order = instance.balancePlayersConstrained(all, ratings, pins, 1);

    assertThat(order.size(), is(6));
    // Valid interleave: team 0 (even slots) equals team 1 (odd slots) for 6.
    int even = evenCount(order);
    assertThat(even, is(3));
    assertThat(order.size() - even, is(3));
    // Host stays at start spot 0 (Team 1).
    assertThat(order.get(0).getId(), is(1));
  }

  @Test
  public void overPinnedPoolGoesToUnderTeamAndNonHostFreed() {
    // Team 0 over-pinned (p1 host, p2, p3, p4). Team 1 has p5. p6 unassigned.
    // Expected: p6 (pool) is pinned to the under-team (Team 1) and kept there;
    // p5 (already on the under-team) stays; the over-team's non-host players
    // (p2,p3,p4) are freed and balanced; the host stays on Team 1.
    List<Player> all = new ArrayList<>(List.of(
        player(1, 1000), player(2, 1500), player(3, 1400),
        player(4, 900), player(5, 1100), player(6, 1200)));
    Map<Integer, Integer> pins = new HashMap<>();
    pins.put(1, 0);
    pins.put(2, 0);
    pins.put(3, 0);
    pins.put(4, 0);
    pins.put(5, 1);

    List<Player> order = instance.balancePlayersConstrained(all, ratings, pins, 1);

    assertThat(order.size(), is(6));
    assertThat(evenCount(order), is(3));
    assertThat(order.get(0).getId(), is(1));        // host stays at slot 0
    assertThat(indexOfId(order, 5) % 2, is(1));      // p5 stays on the under-team
    assertThat(indexOfId(order, 6) % 2, is(1));      // p6 (pool) pinned to under-team
  }

  @Test
  public void feasiblePinsAreRespected() {
    List<Player> all = new ArrayList<>(List.of(
        player(1, 1000), player(2, 1500), player(3, 1100), player(4, 1200)));
    Map<Integer, Integer> pins = new HashMap<>();
    pins.put(1, 0);   // host on team 0
    pins.put(2, 1);   // p2 pinned to team 1

    List<Player> order = instance.balancePlayersConstrained(all, ratings, pins, 1);

    assertThat(order.size(), is(4));
    assertThat(order.get(0).getId(), is(1));            // host at slot 0
    assertThat(indexOfId(order, 1) % 2, is(0));         // host on team 0
    assertThat(indexOfId(order, 2) % 2, is(1));         // p2 honoured on team 1
  }

  // --- getBalancedTeams(game, pins, oppositeTeamPairs): preselected pairs force
  // their two claimants onto opposite teams; balance is optimised around that. ---

  private com.faforever.client.game.Game gameOf(List<Player> all, String host) {
    com.faforever.client.game.Game game = org.mockito.Mockito.mock(com.faforever.client.game.Game.class);
    Map<String, List<String>> teams = new HashMap<>();
    teams.put("1", all.stream().map(Player::getUsername).toList());
    when(game.getTeams()).thenReturn(javafx.collections.FXCollections.observableMap(teams));
    when(game.getHost()).thenReturn(host);
    when(game.getFeaturedMod()).thenReturn("faf");
    when(game.getId()).thenReturn(1);
    for (Player p : all) {
      when(playerService.getPlayerForUsername(p.getUsername())).thenReturn(java.util.Optional.of(p));
    }
    // Leaderboard lookup feeds getDistilledPlayerRatings, which we stub on the spy;
    // just keep the queue lookup from NPEing.
    when(fafService.getMatchmakerQueueMapPools())
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(List.of()));
    return game;
  }

  @Test
  public void sharedPairPutsBothClaimantsOnOppositeTeams() {
    // p2 and p3 both claim the same pair -> they must end up on opposite teams.
    List<Player> all = new ArrayList<>(List.of(
        player(1, 1000), player(2, 1500), player(3, 1400), player(4, 900)));
    com.faforever.client.game.Game game = gameOf(all, "p1");
    JSkillsRatingService spy = org.mockito.Mockito.spy(instance);
    org.mockito.Mockito.doReturn(new ArrayList<>(all)).when(spy).getBalancedTeams(game);
    org.mockito.Mockito.doReturn(ratings).when(spy)
        .getDistilledPlayerRatings(org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anySet(),
            org.mockito.ArgumentMatchers.anyString());

    List<Player> order = spy.getBalancedTeams(game, Map.of(), List.of(new int[]{2, 3}));

    assertThat(order.size(), is(4));
    assertThat("p2 and p3 must be on opposite teams",
        indexOfId(order, 2) % 2 != indexOfId(order, 3) % 2, is(true));
  }
}
