package com.faforever.client.tournament;

import com.faforever.client.player.PlayerService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.TournamentTeamInviteReceivedMessage;
import com.faforever.client.remote.domain.TournamentTeamInviteResolvedMessage;
import com.faforever.client.remote.domain.TournamentTeamUpdatedMessage;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the cache of (teams, my pending invites) for the tournament
 * currently being viewed by the user, and exposes that as JavaFX
 * observable lists for the brackets-tab team panel to bind to.
 *
 * Reads (team list, pending invites) go through the REST API. Writes
 * (create, invite, accept, leave, etc.) go through the lobby server
 * since they need an authenticated player_id.
 *
 * Server broadcasts (TournamentTeamUpdatedMessage) trigger an API
 * re-fetch so the cache stays current. Targeted invite messages
 * (received/resolved) mutate the cache directly for instant UI
 * feedback, then the next API fetch reconciles.
 */
@Lazy
@Service
@Slf4j
public class TournamentTeamService {

  private final FafService fafService;
  private final PlayerService playerService;

  /** Currently displayed tournament id, or 0 when nothing is selected. */
  private int activeTournamentId;

  /**
   * Teams for the active tournament. Each entry is a map with keys:
   * id, name, captain_id, captain_name, seed, members[{player_id, player_name}].
   * Built from the API TournamentTeam DTOs.
   */
  @Getter
  private final ObservableList<Map<String, Object>> teams =
      FXCollections.observableArrayList();

  @Getter
  private final ObservableList<TournamentTeamInviteReceivedMessage> pendingInvites =
      FXCollections.observableArrayList();

  @Getter
  private final ObjectProperty<Integer> myTeamId = new SimpleObjectProperty<>(null);

  public TournamentTeamService(FafService fafService, PlayerService playerService) {
    this.fafService = fafService;
    this.playerService = playerService;
  }

  @PostConstruct
  void postConstruct() {
    fafService.addOnMessageListener(TournamentTeamUpdatedMessage.class, this::onTeamUpdated);
    fafService.addOnMessageListener(TournamentTeamInviteReceivedMessage.class, this::onInviteReceived);
    fafService.addOnMessageListener(TournamentTeamInviteResolvedMessage.class, this::onInviteResolved);
  }

  // -- Active tournament tracking ---------------------------------------

  public void setActiveTournament(int tournamentId) {
    this.activeTournamentId = tournamentId;
    runOnFxThread(() -> {
      teams.clear();
      pendingInvites.clear();
      myTeamId.set(null);
    });
    if (tournamentId != 0) {
      fetchTeamsFromApi(tournamentId);
    }
  }

  // -- Writes (sent via lobby server) -----------------------------------

  public void createTeam(int tournamentId, String name) {
    fafService.tournamentTeamCreate(tournamentId, name);
  }

  public void invitePlayer(int teamId, int inviteeId) {
    fafService.tournamentTeamInvite(teamId, inviteeId);
  }

  public void acceptInvite(int inviteId) {
    fafService.tournamentTeamAcceptInvite(inviteId);
  }

  public void declineInvite(int inviteId) {
    fafService.tournamentTeamDeclineInvite(inviteId);
  }

  public void leaveTeam(int teamId) {
    fafService.tournamentTeamLeave(teamId);
  }

  public void removeMember(int teamId, int targetPlayerId) {
    fafService.tournamentTeamRemoveMember(teamId, targetPlayerId);
  }

  public void disbandTeam(int teamId) {
    fafService.tournamentTeamDisband(teamId);
  }

  // -- Server message handlers ------------------------------------------

  private void onTeamUpdated(TournamentTeamUpdatedMessage message) {
    if (message.getTournamentId() == null) return;
    if (message.getTournamentId() != activeTournamentId) return;
    // Re-fetch from API — the broadcast is just a ping.
    fetchTeamsFromApi(activeTournamentId);
  }

  private void onInviteReceived(TournamentTeamInviteReceivedMessage message) {
    runOnFxThread(() -> {
      pendingInvites.removeIf(existing -> existing.getInviteId() != null
          && existing.getInviteId().equals(message.getInviteId()));
      pendingInvites.add(message);
    });
  }

  private void onInviteResolved(TournamentTeamInviteResolvedMessage message) {
    runOnFxThread(() -> pendingInvites.removeIf(existing ->
        existing.getInviteId() != null
            && existing.getInviteId().equals(message.getInviteId())));
  }

  // -- API fetch --------------------------------------------------------

  private void fetchTeamsFromApi(int tournamentId) {
    int myPlayerId = playerService.getCurrentPlayer()
        .map(p -> p.getId()).orElse(0);

    fafService.getTournamentTeams(tournamentId).thenAccept(apiTeams -> {
      // Convert API DTOs to the Map<String, Object> shape the team panel expects
      List<Map<String, Object>> teamMaps = new ArrayList<>();
      Integer foundMyTeamId = null;
      for (com.faforever.client.api.dto.TournamentTeam t : apiTeams) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", parseId(t.getId()));
        map.put("name", t.getName());
        map.put("seed", t.getSeed());
        map.put("captain_id", t.getCaptain() != null ? parseId(t.getCaptain().getId()) : 0);
        map.put("captain_name", t.getCaptain() != null ? t.getCaptain().getLogin() : "?");

        List<Map<String, Object>> memberMaps = new ArrayList<>();
        if (t.getMembers() != null) {
          for (com.faforever.client.api.dto.TournamentTeamMember m : t.getMembers()) {
            Map<String, Object> mm = new HashMap<>();
            int pid = m.getPlayer() != null ? parseId(m.getPlayer().getId()) : 0;
            mm.put("player_id", pid);
            mm.put("player_name", m.getPlayer() != null ? m.getPlayer().getLogin() : "?");
            memberMaps.add(mm);
            if (pid == myPlayerId) {
              foundMyTeamId = parseId(t.getId());
            }
          }
        }
        map.put("members", memberMaps);
        teamMaps.add(map);
      }

      Integer finalMyTeamId = foundMyTeamId;
      runOnFxThread(() -> {
        myTeamId.set(finalMyTeamId);
        teams.setAll(teamMaps);
      });
    }).exceptionally(t -> {
      log.warn("Failed to fetch teams from API for tournament {}", tournamentId, t);
      return null;
    });

    // Fetch pending invites for the current player
    if (myPlayerId > 0) {
      fafService.getPendingInvitesForPlayer(myPlayerId).thenAccept(apiInvites -> {
        List<TournamentTeamInviteReceivedMessage> rebuilt = new ArrayList<>();
        for (com.faforever.client.api.dto.TournamentTeamInvite inv : apiInvites) {
          // Filter to this tournament
          if (inv.getTournament() == null) continue;
          int invTid = parseId(inv.getTournament().getId());
          if (invTid != tournamentId) continue;

          TournamentTeamInviteReceivedMessage msg = new TournamentTeamInviteReceivedMessage();
          msg.setInviteId(parseId(inv.getId()));
          msg.setTournamentId(invTid);
          msg.setTeamId(inv.getTeam() != null ? parseId(inv.getTeam().getId()) : null);
          msg.setTeamName(inv.getTeam() != null ? inv.getTeam().getName() : "?");
          msg.setInviterId(inv.getInviter() != null ? parseId(inv.getInviter().getId()) : null);
          msg.setInviterName(inv.getInviter() != null ? inv.getInviter().getLogin() : "?");
          rebuilt.add(msg);
        }
        runOnFxThread(() -> pendingInvites.setAll(rebuilt));
      }).exceptionally(t -> {
        log.warn("Failed to fetch invites from API for player {}", myPlayerId, t);
        return null;
      });
    }
  }

  private static int parseId(String id) {
    if (id == null) return 0;
    try { return Integer.parseInt(id); }
    catch (NumberFormatException e) { return 0; }
  }

  private static void runOnFxThread(Runnable r) {
    if (Platform.isFxApplicationThread()) {
      r.run();
    } else {
      Platform.runLater(r);
    }
  }
}
