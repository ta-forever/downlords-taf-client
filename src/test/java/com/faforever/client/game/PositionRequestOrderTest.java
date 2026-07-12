package com.faforever.client.game;

import com.faforever.client.player.Player;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Tests {@link GameService#applyPositionRequests}: within-team reordering of
 * the interleaved start order so position role requests are honoured. Index i
 * of the order = map position i+1, team = i%2; the player at index 2r / 2r+1
 * gets role r (a pair of mirrored map start positions).
 */
public class PositionRequestOrderTest {

  private static Player player(int id) {
    Player p = new Player(new com.faforever.client.remote.domain.Player());
    p.setId(id);
    p.setUsername("p" + id);
    return p;
  }

  private static List<Integer> ids(List<Player> order) {
    return order.stream().map(Player::getId).toList();
  }

  /** Interleaved 4-player order: team A = ids 1,3 (indices 0,2), team B = ids 2,4. */
  private static List<Player> fourPlayers() {
    return List.of(player(1), player(2), player(3), player(4));
  }

  @Test
  public void noRequestsReturnsOrderUnchanged() {
    List<Player> order = fourPlayers();
    assertThat(GameService.applyPositionRequests(Map.of(), order), sameInstance(order));
    assertThat(GameService.applyPositionRequests(null, order), sameInstance(order));
  }

  @Test
  public void requestMovesPlayerToRequestedRoleWithinTeam() {
    // Player 1 (team A, currently role 0) wants role 1 (map positions 3/4).
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(1, 1);
    List<Integer> result = ids(GameService.applyPositionRequests(requests, fourPlayers()));
    // Team A order becomes [3, 1]; team B untouched -> interleave 3,2,1,4.
    assertThat(result, contains(3, 2, 1, 4));
  }

  @Test
  public void sameRoleOnOppositeTeamsBothSatisfied() {
    // Players 1 (team A) and 2 (team B) both want role 1 — different teams,
    // both get a slot of the same mirrored pair.
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(1, 1);
    requests.put(2, 1);
    List<Integer> result = ids(GameService.applyPositionRequests(requests, fourPlayers()));
    // Team A: [3, 1]; team B: [4, 2] -> 3,4,1,2. Roles: 1 and 2 share pair 1.
    assertThat(result, contains(3, 4, 1, 2));
  }

  @Test
  public void sameRoleSameTeamFirstRequestWins() {
    // Players 1 and 3 are teammates; both want role 0. Player 3 asked first
    // (iteration order of the request map is server request order).
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(3, 0);
    requests.put(1, 0);
    List<Integer> result = ids(GameService.applyPositionRequests(requests, fourPlayers()));
    // Team A: role 0 -> 3, player 1 fills leftover role 1 -> [3, 1].
    assertThat(result, contains(3, 2, 1, 4));
  }

  @Test
  public void roleBeyondTeamSizeIsIgnored() {
    // 4 players -> 2 roles per team; role 3 is unsatisfiable.
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(1, 3);
    List<Integer> result = ids(GameService.applyPositionRequests(requests, fourPlayers()));
    assertThat(result, contains(1, 2, 3, 4));
  }

  @Test
  public void requestFromPlayerNotInOrderIsIgnored() {
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(99, 0);
    List<Integer> result = ids(GameService.applyPositionRequests(requests, fourPlayers()));
    assertThat(result, contains(1, 2, 3, 4));
  }

  @Test
  public void unevenTeamsKeepInterleaveShape() {
    // 5 players: team A = 1,3,5 (indices 0,2,4), team B = 2,4.
    List<Player> order = List.of(player(1), player(2), player(3), player(4), player(5));
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(1, 2);  // team A role 2 (map position 5)
    requests.put(4, 0);  // team B role 0 (map position 2)
    List<Integer> result = ids(GameService.applyPositionRequests(requests, order));
    // Team A: [3, 5, 1]; team B: [4, 2] -> 3,4,5,2,1.
    assertThat(result, contains(3, 4, 5, 2, 1));
    assertThat(result.size(), is(5));
  }

  @Test
  public void twoPlayerGameSidePick() {
    // 1v1: picking role 0 vs any other role — with one role per team, only
    // role 0 exists; both players get it trivially.
    List<Player> order = List.of(player(1), player(2));
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(1, 0);
    List<Integer> result = ids(GameService.applyPositionRequests(requests, order));
    assertThat(result, contains(1, 2));
  }

  // --- oppositeTeamPairsFromRequests: derive the "must be on opposite teams"
  // constraints handed to the balancer (a pair claimed by two players). ---

  private static List<List<Integer>> edges(List<int[]> pairs) {
    return pairs.stream().map(e -> List.of(e[0], e[1])).toList();
  }

  @Test
  public void noSharedPairYieldsNoEdges() {
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(1, 0);
    requests.put(2, 1);  // different pairs -> no opposite-team constraint
    assertThat(GameService.oppositeTeamPairsFromRequests(requests, 8).isEmpty(), is(true));
  }

  @Test
  public void twoPlayersOnSamePairBecomeAnOppositeTeamEdge() {
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(5, 2);
    requests.put(7, 2);  // both want pair 2 -> forced onto opposite teams
    assertThat(edges(GameService.oppositeTeamPairsFromRequests(requests, 8)),
        contains(List.of(5, 7)));
  }

  @Test
  public void thirdRequesterOfAFullPairIsDropped() {
    // The server rejects a third claim, but defend against races: only the first
    // two requesters of a pair form the edge.
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(1, 0);
    requests.put(2, 0);
    requests.put(3, 0);
    assertThat(edges(GameService.oppositeTeamPairsFromRequests(requests, 8)),
        contains(List.of(1, 2)));
  }

  @Test
  public void roleBeyondAvailablePairsIsIgnored() {
    // 6 players -> 3 pairs (roles 0..2). Role 3 has no mirrored positions.
    Map<Integer, Integer> requests = new LinkedHashMap<>();
    requests.put(1, 3);
    requests.put(2, 3);
    assertThat(GameService.oppositeTeamPairsFromRequests(requests, 6).isEmpty(), is(true));
  }
}
