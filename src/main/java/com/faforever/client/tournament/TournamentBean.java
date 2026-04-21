package com.faforever.client.tournament;

import com.faforever.client.api.dto.Tournament;
import com.faforever.client.api.dto.TournamentMatch;
import com.faforever.client.api.dto.TournamentParticipant;
import com.faforever.client.api.dto.TournamentPlacement;
import com.faforever.client.api.dto.TournamentStanding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TournamentBean {
  private final StringProperty id;
  private final StringProperty name;
  private final StringProperty description;
  private final StringProperty tournamentType;
  private final ObjectProperty<OffsetDateTime> createdAt;
  private final IntegerProperty participantCount;
  private final ObjectProperty<OffsetDateTime> startingAt;
  private final ObjectProperty<OffsetDateTime> completedAt;
  private final BooleanProperty openForSignup;
  private final StringProperty apiState;
  private final ObjectProperty<Status> status;
  private List<String> participantNames;
  private java.util.Map<String, Integer> participantRatings;
  private List<MatchInfo> matches;
  private int swissRounds;
  private int topCut;
  private String topCutFormat;
  private String formatOptions;
  private int playersPerSide;
  private int bestOf;
  private int noshowTimeoutMinutes;
  private Integer minRating;
  private Integer maxRating;
  private String featuredModName;            // display name — for UI labels
  private String featuredModTechnicalName;   // technical name — for ModService.getFeaturedMod / mapService.loadPreview
  private String leaderboardName;
  private String leaderboardTechnicalName;
  private String mapPoolName;
  private Integer createdByPlayerId;
  /** Team names for team tournaments, sorted alphabetically. Empty for solo. */
  private List<String> teamNames;
  /** Tournament-wide rule controlling when planned maps are visible to players/spectators.
   *  Possible values: "always_visible", "hidden_until_tournament_start", "hidden_until_round_start".
   *  Null is treated as "always_visible". */
  private String mapVisibility;
  private String winnerAvatarUrl;
  private String secondPlaceAvatarUrl;
  private String thirdPlaceAvatarUrl;
  private java.util.Map<Integer, List<String>> placements;  // place → list of player names
  private List<StandingInfo> standings;

  public TournamentBean() {
    id = new SimpleStringProperty();
    name = new SimpleStringProperty();
    description = new SimpleStringProperty();
    tournamentType = new SimpleStringProperty();
    createdAt = new SimpleObjectProperty<>();
    participantCount = new SimpleIntegerProperty();
    startingAt = new SimpleObjectProperty<>();
    completedAt = new SimpleObjectProperty<>();
    openForSignup = new SimpleBooleanProperty();
    apiState = new SimpleStringProperty();
    participantNames = Collections.emptyList();
    participantRatings = Collections.emptyMap();
    matches = Collections.emptyList();
    placements = Collections.emptyMap();
    standings = Collections.emptyList();
    status = new SimpleObjectProperty<>();
    status.bind(Bindings.createObjectBinding(() -> {
      String state = getApiState();
      if ("complete".equals(state)) {
        return Status.FINISHED;
      } else if ("cancelled".equals(state)) {
        // Cancelled was previously falling through to the heuristic block
        // below, where a past startingAt would mis-map it to RUNNING and
        // shove it into the In Progress list. Cancelled is a terminal state
        // and should be grouped with completed tournaments.
        return Status.CANCELLED;
      } else if ("underway".equals(state)) {
        return Status.RUNNING;
      } else if ("pending".equals(state)) {
        return Status.OPEN_FOR_REGISTRATION;
      } else if (getCompletedAt() != null) {
        return Status.FINISHED;
      } else if (getStartingAt() != null && getStartingAt().isBefore(OffsetDateTime.now())) {
        return Status.RUNNING;
      } else if (isOpenForSignup()) {
        return Status.OPEN_FOR_REGISTRATION;
      } else {
        return Status.CLOSED_FOR_REGISTRATION;
      }
    }, apiState, startingAt, completedAt, openForSignup));
  }

  public static TournamentBean fromTournamentDto(Tournament tournament) {
    TournamentBean tournamentBean = new TournamentBean();

    tournamentBean.setId(tournament.getId());
    tournamentBean.setName(tournament.getName());
    tournamentBean.setDescription(tournament.getDescription() != null ? tournament.getDescription() : "");
    tournamentBean.setTournamentType(tournament.getFormat() != null ? tournament.getFormat() : "single_elimination");
    tournamentBean.setCreatedAt(tournament.getCreatedAt());
    tournamentBean.setParticipantCount(
        tournament.getParticipants() != null ? tournament.getParticipants().size() : 0);
    // Auto-scheduled tournaments populate scheduled_start_at; use that for pending
    // tournaments (the *intended* start). Once the tournament transitions to
    // underway, started_at is set and we prefer that (the *actual* start).
    java.time.OffsetDateTime startingAt;
    if ("pending".equals(tournament.getState()) && tournament.getScheduledStartAt() != null) {
      startingAt = tournament.getScheduledStartAt();
    } else if (tournament.getStartedAt() != null) {
      startingAt = tournament.getStartedAt();
    } else {
      startingAt = tournament.getScheduledStartAt();  // legacy fallback
    }
    tournamentBean.setStartingAt(startingAt);
    tournamentBean.setCompletedAt(tournament.getCompletedAt());
    tournamentBean.setApiState(tournament.getState());
    tournamentBean.setOpenForSignup("pending".equals(tournament.getState()));
    tournamentBean.setFormatOptions(tournament.getFormatOptions());
    tournamentBean.setPlayersPerSide(tournament.getPlayersPerSide());
    tournamentBean.setBestOf(tournament.getBestOf());
    tournamentBean.setNoshowTimeoutMinutes(tournament.getNoshowTimeoutMinutes());
    tournamentBean.setMinRating(tournament.getMinRating());
    tournamentBean.setMaxRating(tournament.getMaxRating());
    if (tournament.getFeaturedMod() != null) {
      tournamentBean.setFeaturedModName(tournament.getFeaturedMod().getDisplayName());
      tournamentBean.setFeaturedModTechnicalName(tournament.getFeaturedMod().getTechnicalName());
    }
    if (tournament.getLeaderboard() != null) {
      // Use nameKey — production hijacks this field to store display strings.
      // Controller passes through i18n.get() which falls back to the key itself
      // when no translation exists, matching the leaderboards tab behavior.
      String nameKey = tournament.getLeaderboard().getNameKey();
      tournamentBean.setLeaderboardName(nameKey != null ? nameKey
          : tournament.getLeaderboard().getTechnicalName());
      tournamentBean.setLeaderboardTechnicalName(tournament.getLeaderboard().getTechnicalName());
    }
    if (tournament.getMapPool() != null) {
      tournamentBean.setMapPoolName(tournament.getMapPool().getName());
    }
    if (tournament.getCreatedBy() != null) {
      tournamentBean.setCreatedByPlayerId(Integer.parseInt(tournament.getCreatedBy().getId()));
    }
    tournamentBean.setMapVisibility(tournament.getMapVisibility());
    if (tournament.getTeams() != null) {
      List<String> tNames = new ArrayList<>();
      for (com.faforever.client.api.dto.TournamentTeam t : tournament.getTeams()) {
        if (t.getName() != null) tNames.add(t.getName());
      }
      Collections.sort(tNames, String.CASE_INSENSITIVE_ORDER);
      tournamentBean.setTeamNames(tNames);
    } else {
      tournamentBean.setTeamNames(Collections.emptyList());
    }
    if (tournament.getWinnerAvatar() != null) {
      tournamentBean.setWinnerAvatarUrl(tournament.getWinnerAvatar().getUrl());
    }
    if (tournament.getSecondPlaceAvatar() != null) {
      tournamentBean.setSecondPlaceAvatarUrl(tournament.getSecondPlaceAvatar().getUrl());
    }
    if (tournament.getThirdPlaceAvatar() != null) {
      tournamentBean.setThirdPlaceAvatarUrl(tournament.getThirdPlaceAvatar().getUrl());
    }

    // Parse Swiss rounds from format options JSON
    if ("swiss".equals(tournament.getFormat()) && tournament.getFormatOptions() != null) {
      try {
        com.google.gson.JsonObject opts = new com.google.gson.JsonParser()
            .parse(tournament.getFormatOptions()).getAsJsonObject();
        if (opts.has("swiss_rounds")) {
          tournamentBean.setSwissRounds(opts.get("swiss_rounds").getAsInt());
        }
        if (opts.has("top_cut")) {
          tournamentBean.setTopCut(opts.get("top_cut").getAsInt());
        }
        if (opts.has("top_cut_format")) {
          tournamentBean.setTopCutFormat(opts.get("top_cut_format").getAsString());
        }
      } catch (Exception ignored) {}
    }

    if (tournament.getParticipants() != null) {
      List<String> names = new ArrayList<>();
      java.util.Map<String, Integer> ratings = new java.util.HashMap<>();
      for (TournamentParticipant p : tournament.getParticipants()) {
        if (p.getPlayer() != null && p.getPlayer().getLogin() != null) {
          String name = p.getPlayer().getLogin();
          names.add(name);
          if (p.getRating() != null) {
            ratings.put(name, p.getRating());
          }
        }
      }
      Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
      tournamentBean.setParticipantNames(names);

      // For team tournaments, compute aggregate team ratings so the bracket
      // can display a rating next to each team name. The average of the
      // team members' individual participant ratings is used.
      if (tournament.getTeams() != null && tournament.getPlayersPerSide() >= 2) {
        for (com.faforever.client.api.dto.TournamentTeam team : tournament.getTeams()) {
          if (team.getName() == null || team.getMembers() == null) continue;
          int sum = 0, count = 0;
          for (com.faforever.client.api.dto.TournamentTeamMember member : team.getMembers()) {
            if (member.getPlayer() != null && member.getPlayer().getLogin() != null) {
              Integer r = ratings.get(member.getPlayer().getLogin());
              if (r != null) { sum += r; count++; }
            }
          }
          if (count > 0) {
            ratings.put(team.getName(), sum / count);
          }
        }
      }

      tournamentBean.setParticipantRatings(ratings);
    }

    List<MatchInfo> matchInfos = new ArrayList<>();
    if (tournament.getMatches() != null) {
      for (TournamentMatch m : tournament.getMatches()) {
        // Solo matches have player1/player2; team matches have team1/team2.
        // Fall back to team name when the player relationship is null.
        String p1 = m.getPlayer1() != null ? m.getPlayer1().getLogin()
            : (m.getTeam1() != null ? m.getTeam1().getName() : null);
        String p2 = m.getPlayer2() != null ? m.getPlayer2().getLogin()
            : (m.getTeam2() != null ? m.getTeam2().getName() : null);
        String winner = m.getWinner() != null ? m.getWinner().getLogin()
            : (m.getWinnerTeam() != null ? m.getWinnerTeam().getName() : null);

        List<PlannedMapInfo> planned = new ArrayList<>();
        if (m.getPlannedMaps() != null) {
          for (com.faforever.client.api.dto.TournamentMatchPlannedMap pm : m.getPlannedMaps()) {
            if (pm.getMapVersion() == null) continue;
            com.faforever.client.api.dto.MapVersion mv = pm.getMapVersion();
            String mapName = mv.getMap() != null ? mv.getMap().getDisplayName() : mv.getName();
            String folder = null;
            if (mv.getFilename() != null) {
              // Extract folder name from "maps/some_folder/foo.scmap" or similar
              String fn = mv.getFilename();
              int slashIdx = fn.lastIndexOf('/');
              if (slashIdx >= 0) {
                int prevSlash = fn.lastIndexOf('/', slashIdx - 1);
                folder = prevSlash >= 0 ? fn.substring(prevSlash + 1, slashIdx) : fn.substring(0, slashIdx);
              }
            }
            planned.add(new PlannedMapInfo(pm.getGameNumber(), mapName, folder));
          }
          planned.sort(java.util.Comparator.comparingInt(PlannedMapInfo::getGameNumber));
        }

        int matchIdInt = 0;
        try {
          matchIdInt = m.getId() != null ? Integer.parseInt(m.getId()) : 0;
        } catch (NumberFormatException ignored) {}

        List<Integer> playedGameIds = new ArrayList<>();
        if (m.getGames() != null) {
          List<com.faforever.client.api.dto.TournamentMatchGame> orderedGames =
              new ArrayList<>(m.getGames());
          orderedGames.sort(java.util.Comparator.comparingInt(
              com.faforever.client.api.dto.TournamentMatchGame::getGameNumber));
          for (com.faforever.client.api.dto.TournamentMatchGame tmg : orderedGames) {
            if (tmg.getGame() != null && tmg.getGame().getId() != null) {
              try {
                playedGameIds.add(Integer.parseInt(tmg.getGame().getId()));
              } catch (NumberFormatException ignored) {}
            }
          }
        }

        int team1Id = 0, team2Id = 0;
        try {
          if (m.getTeam1() != null && m.getTeam1().getId() != null)
            team1Id = Integer.parseInt(m.getTeam1().getId());
          if (m.getTeam2() != null && m.getTeam2().getId() != null)
            team2Id = Integer.parseInt(m.getTeam2().getId());
        } catch (NumberFormatException ignored) {}

        matchInfos.add(new MatchInfo(m.getRound(), m.getPosition(),
            m.getRole() != null ? m.getRole() : "unknown", m.isPreview(),
            p1, p2, winner, m.getPlayer1Wins(), m.getPlayer2Wins(), m.getState(),
            matchIdInt, planned, playedGameIds, team1Id, team2Id,
            m.getOpenedAt(), m.getTimesOutAt()));
      }
    }
    matchInfos.sort((a, b) -> a.round != b.round ? a.round - b.round : a.position - b.position);
    tournamentBean.setMatches(matchInfos);

    // Placements (server-computed)
    if (tournament.getPlacements() != null) {
      java.util.Map<Integer, List<String>> placementMap = new java.util.HashMap<>();
      for (TournamentPlacement p : tournament.getPlacements()) {
        if (p.getPlayer() != null && p.getPlayer().getLogin() != null) {
          placementMap.computeIfAbsent(p.getPlace(), k -> new ArrayList<>())
              .add(p.getPlayer().getLogin());
        }
      }
      tournamentBean.setPlacements(placementMap);
    }

    // Standings (server-computed Swiss standings with tiebreakers)
    if (tournament.getStandings() != null) {
      List<StandingInfo> standingsList = new ArrayList<>();
      for (TournamentStanding s : tournament.getStandings()) {
        String name = s.getPlayer() != null ? s.getPlayer().getLogin() : "?";
        standingsList.add(new StandingInfo(s.getRank(), name, s.getWins(), s.getLosses(),
            s.getOpponentStrength(), s.getWinStrength()));
      }
      standingsList.sort(java.util.Comparator.comparingInt(StandingInfo::getRank));
      tournamentBean.setStandings(standingsList);
    }

    return tournamentBean;
  }

  public String getId() {
    return id.get();
  }

  public void setId(String id) {
    this.id.set(id);
  }

  public StringProperty idProperty() {
    return id;
  }

  public String getName() {
    return name.get();
  }

  public void setName(String name) {
    this.name.set(name);
  }

  public StringProperty nameProperty() {
    return name;
  }

  public String getDescription() {
    return description.get();
  }

  public void setDescription(String description) {
    this.description.set(description);
  }

  public StringProperty descriptionProperty() {
    return description;
  }

  public String getTournamentType() {
    return tournamentType.get();
  }

  public void setTournamentType(String tournamentType) {
    this.tournamentType.set(tournamentType);
  }

  public StringProperty tournamentTypeProperty() {
    return tournamentType;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt.get();
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt.set(createdAt);
  }

  public ObjectProperty<OffsetDateTime> createdAtProperty() {
    return createdAt;
  }

  public int getParticipantCount() {
    return participantCount.get();
  }

  public void setParticipantCount(int participantCount) {
    this.participantCount.set(participantCount);
  }

  public IntegerProperty participantCountProperty() {
    return participantCount;
  }

  public OffsetDateTime getStartingAt() {
    return startingAt.get();
  }

  public void setStartingAt(OffsetDateTime startingAt) {
    this.startingAt.set(startingAt);
  }

  public ObjectProperty<OffsetDateTime> startingAtProperty() {
    return startingAt;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt.get();
  }

  public void setCompletedAt(OffsetDateTime completedAt) {
    this.completedAt.set(completedAt);
  }

  public ObjectProperty<OffsetDateTime> completedAtProperty() {
    return completedAt;
  }

  public boolean isOpenForSignup() {
    return openForSignup.get();
  }

  public void setOpenForSignup(boolean openForSignup) {
    this.openForSignup.set(openForSignup);
  }

  public BooleanProperty openForSignupProperty() {
    return openForSignup;
  }

  public String getApiState() {
    return apiState.get();
  }

  public void setApiState(String apiState) {
    this.apiState.set(apiState);
  }

  public StringProperty apiStateProperty() {
    return apiState;
  }

  public List<String> getParticipantNames() {
    return participantNames;
  }

  public void setParticipantNames(List<String> participantNames) {
    this.participantNames = participantNames;
  }

  public java.util.Map<String, Integer> getParticipantRatings() {
    return participantRatings;
  }

  public void setParticipantRatings(java.util.Map<String, Integer> participantRatings) {
    this.participantRatings = participantRatings;
  }

  public int getSwissRounds() { return swissRounds; }
  public void setSwissRounds(int swissRounds) { this.swissRounds = swissRounds; }
  public int getTopCut() { return topCut; }
  public void setTopCut(int topCut) { this.topCut = topCut; }
  public String getTopCutFormat() { return topCutFormat; }
  public void setTopCutFormat(String topCutFormat) { this.topCutFormat = topCutFormat; }
  public String getFormatOptions() { return formatOptions; }
  public void setFormatOptions(String formatOptions) { this.formatOptions = formatOptions; }
  public int getPlayersPerSide() { return playersPerSide; }
  public void setPlayersPerSide(int playersPerSide) { this.playersPerSide = playersPerSide; }
  public int getBestOf() { return bestOf; }
  public void setBestOf(int bestOf) { this.bestOf = bestOf; }
  public int getNoshowTimeoutMinutes() { return noshowTimeoutMinutes; }
  public void setNoshowTimeoutMinutes(int v) { this.noshowTimeoutMinutes = v; }
  public Integer getMinRating() { return minRating; }
  public void setMinRating(Integer v) { this.minRating = v; }
  public Integer getMaxRating() { return maxRating; }
  public void setMaxRating(Integer v) { this.maxRating = v; }
  public String getFeaturedModName() { return featuredModName; }
  public void setFeaturedModName(String v) { this.featuredModName = v; }
  public String getFeaturedModTechnicalName() { return featuredModTechnicalName; }
  public void setFeaturedModTechnicalName(String v) { this.featuredModTechnicalName = v; }
  public String getLeaderboardName() { return leaderboardName; }
  public void setLeaderboardName(String v) { this.leaderboardName = v; }
  public String getLeaderboardTechnicalName() { return leaderboardTechnicalName; }
  public void setLeaderboardTechnicalName(String v) { this.leaderboardTechnicalName = v; }
  public String getMapPoolName() { return mapPoolName; }
  public void setMapPoolName(String v) { this.mapPoolName = v; }
  public Integer getCreatedByPlayerId() { return createdByPlayerId; }
  public void setCreatedByPlayerId(Integer v) { this.createdByPlayerId = v; }
  public boolean isPlayerCreated() { return createdByPlayerId != null; }
  public List<String> getTeamNames() { return teamNames; }
  public void setTeamNames(List<String> v) { this.teamNames = v; }
  public String getMapVisibility() { return mapVisibility; }
  public void setMapVisibility(String v) { this.mapVisibility = v; }

  /**
   * True when the planned maps for the given match should be revealed to
   * players and spectators given the tournament's map-visibility rule and
   * current state. Moderators/admins apply no filtering — this is a
   * player-client cosmetic filter only.
   */
  public boolean arePlannedMapsVisible(MatchInfo match) {
    String rule = mapVisibility != null ? mapVisibility : "always_visible";
    switch (rule) {
      case "hidden_until_tournament_start":
        // Revealed as soon as the tournament leaves "pending".
        return !"pending".equals(getApiState());
      case "hidden_until_round_start":
        if ("pending".equals(getApiState())) return false;
        // Revealed as soon as any match in the same round has opened (state != pending).
        int round = match.getRound();
        if (matches == null) return false;
        for (MatchInfo m : matches) {
          if (m.getRound() == round && m.getState() != null && !"pending".equals(m.getState())) {
            return true;
          }
        }
        return false;
      case "always_visible":
      default:
        return true;
    }
  }
  public String getWinnerAvatarUrl() { return winnerAvatarUrl; }
  public void setWinnerAvatarUrl(String url) { this.winnerAvatarUrl = url; }
  public String getSecondPlaceAvatarUrl() { return secondPlaceAvatarUrl; }
  public void setSecondPlaceAvatarUrl(String url) { this.secondPlaceAvatarUrl = url; }
  public String getThirdPlaceAvatarUrl() { return thirdPlaceAvatarUrl; }
  public void setThirdPlaceAvatarUrl(String url) { this.thirdPlaceAvatarUrl = url; }
  public java.util.Map<Integer, List<String>> getPlacements() { return placements; }
  public void setPlacements(java.util.Map<Integer, List<String>> v) { this.placements = v; }
  public List<StandingInfo> getStandings() { return standings; }
  public void setStandings(List<StandingInfo> v) { this.standings = v; }

  public List<MatchInfo> getMatches() {
    return matches;
  }

  public void setMatches(List<MatchInfo> matches) {
    this.matches = matches;
  }

  public int getTotalRounds() {
    int maxRound = 0;
    for (MatchInfo m : matches) {
      int absRound = Math.abs(m.round);
      if (absRound > maxRound) maxRound = absRound;
    }
    return maxRound;
  }

  public Status getStatus() {
    return status.get();
  }

  public ObjectProperty<Status> statusProperty() {
    return status;
  }

  @AllArgsConstructor
  @Getter
  public enum Status {
    FINISHED("tournament.status.finished", 1),
    CANCELLED("tournament.status.cancelled", 1),
    RUNNING("tournament.status.running", 2),
    OPEN_FOR_REGISTRATION("tournament.status.openForRegistration", 4),
    CLOSED_FOR_REGISTRATION("tournament.status.closedForRegistration", 3);

    private final String messageKey;
    private final int sortOrderPriority;
  }

  @Getter
  public static class MatchInfo {
    private final int round;
    private final int position;
    private final String role;
    private final boolean preview;
    private final String player1;
    private final String player2;
    private final String winner;
    private final int player1Wins;
    private final int player2Wins;
    private final String state;
    private final int matchId;
    private final java.util.List<PlannedMapInfo> plannedMaps;
    private final java.util.List<Integer> playedGameIds;
    /** Team IDs for team matches (0 when solo or TBD). */
    private final int team1Id;
    private final int team2Id;
    /** ISO timestamp when the match became open (both slots filled).
     *  Used to compute the noshow countdown. Null for pending/complete/preview. */
    private final String openedAt;
    /** Authoritative forfeit deadline set by the tournament service.
     *  Overrides the openedAt + noshow_timeout heuristic — picks up the
     *  10-minute toilet-break grace after a draw. Null = no active
     *  timer (game is live, match is complete, etc.). Mutable so the
     *  tournament_timer_restarted / tournament_timer_stopped broadcasts
     *  can patch the value without a full tournament refetch. */
    private volatile String timesOutAt;

    public MatchInfo(int round, int position, String role, boolean preview,
                      String player1, String player2, String winner,
                      int player1Wins, int player2Wins, String state) {
      this(round, position, role, preview, player1, player2, winner,
           player1Wins, player2Wins, state, 0, java.util.Collections.emptyList(),
           java.util.Collections.emptyList(), 0, 0, null, null);
    }

    public MatchInfo(int round, int position, String role, boolean preview,
                      String player1, String player2, String winner,
                      int player1Wins, int player2Wins, String state,
                      int matchId,
                      java.util.List<PlannedMapInfo> plannedMaps,
                      java.util.List<Integer> playedGameIds,
                      int team1Id, int team2Id, String openedAt,
                      String timesOutAt) {
      this.round = round;
      this.position = position;
      this.role = role;
      this.preview = preview;
      this.player1 = player1;
      this.player2 = player2;
      this.winner = winner;
      this.player1Wins = player1Wins;
      this.player2Wins = player2Wins;
      this.state = state;
      this.matchId = matchId;
      this.plannedMaps = plannedMaps;
      this.playedGameIds = playedGameIds;
      this.team1Id = team1Id;
      this.team2Id = team2Id;
      this.openedAt = openedAt;
      this.timesOutAt = timesOutAt;
    }

    public int getTeam1Id() { return team1Id; }
    public int getTeam2Id() { return team2Id; }
    public String getOpenedAt() { return openedAt; }
    public String getTimesOutAt() { return timesOutAt; }
    public void setTimesOutAt(String timesOutAt) { this.timesOutAt = timesOutAt; }
  }

  @AllArgsConstructor
  @Getter
  public static class PlannedMapInfo {
    private final int gameNumber;
    private final String mapName;
    private final String mapFolderName;
  }

  @AllArgsConstructor
  @Getter
  public static class StandingInfo {
    private final int rank;
    private final String playerName;
    private final int wins;
    private final int losses;
    private final int opponentStrength;
    private final int winStrength;
  }
}
