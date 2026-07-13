package com.faforever.client.game;


import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.galacticwar.GalacticWarService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.ladder.LadderPointsService;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.DisplayMetric;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.replay.Replay.PlayerStats;
import com.faforever.client.rating.RatingService;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.RatingUtil;
import javafx.collections.ObservableMap;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TeamCardController implements Controller<Node> {
  private final UiService uiService;
  private final PlayerService playerService;
  private final GalacticWarService galacticWarService;
  private final I18n i18n;
  private final PreferencesService preferencesService;
  private final LadderPointsService ladderPointsService;
  public Pane teamPaneRoot;
  public VBox teamPane;
  public Label teamNameLabel;
  /** Host's +autoteam pins (player id -> team index 0/1); pinned players get a badge. */
  private Map<Integer, Integer> pinnedTeams = Map.of();
  /** Players' start-position role requests (player id -> role 0..4); requesters get a badge. */
  private Map<Integer, Integer> positionRequests = Map.of();
  /** The game's rating type (leaderboard technical name), so LP-mode player rows show the ladder
   * rank for this exact board only. Null when the display context has no fixed game format. */
  private String ratingType;
  /** Replay detail opt-in: when a row has neither a faction/GW icon nor a country flag, show a
   *  neutral "playing" status icon so names don't sit flush-left. Off elsewhere. */
  private boolean showPlayingStatusIconFallback = false;
  /** Per-player rating-change label (legacy MMR delta), revealed by {@link #showRatingChange}. */
  private final Map<Integer, RatingChangeLabelController> ratingChangeControllersByPlayerId = new HashMap<>();

  public TeamCardController(UiService uiService, PlayerService playerService, GalacticWarService galacticWarService, I18n i18n, PreferencesService preferencesService, LadderPointsService ladderPointsService) {
    this.uiService = uiService;
    this.playerService = playerService;
    this.galacticWarService = galacticWarService;
    this.i18n = i18n;
    this.preferencesService = preferencesService;
    this.ladderPointsService = ladderPointsService;
  }

  /** Replay detail opt-in: fall back to a "playing" status icon on rows that have neither a
   *  faction/GW icon nor a country flag, so player names stay aligned. Set before
   *  {@link #setPlayersInTeam}. */
  public void setShowPlayingStatusIconFallback(boolean enabled) {
    this.showPlayingStatusIconFallback = enabled;
  }

  /** Fix the game's rating type (leaderboard technical name) so LP-mode player rows show the ladder
   *  rank for this exact board only — never a most-played-elsewhere fallback. Set before
   *  {@link #setPlayersInTeam}. ({@link #createAndAdd} sets this itself.) */
  public void setRatingType(String ratingType) {
    this.ratingType = ratingType;
  }

  /**
   * Creates a new {@link TeamCardController} and adds its root to the specified {@code teamsPane}.
   *
   * @param teamsList a mapping of team name (e.g. "2") to a list of player names that are in that team
   * @param ratingType the type of rating used for the game sent from the server
   * @param playerService the service to use to look up players by name
   */
  static void createAndAdd(ObservableMap<? extends String, ? extends List<String>> teamsList, String ratingType,
                           PlayerService playerService, UiService uiService, RatingService ratingService,
                           GalacticWarService galacticWarService,
                           Pane teamsPane, Boolean hidePlayerRatings, String galacticWarPlanetName) {
    createAndAdd(teamsList, ratingType, playerService, uiService, ratingService, galacticWarService,
        teamsPane, hidePlayerRatings, galacticWarPlanetName, Map.of(), Map.of());
  }

  /**
   * @param pinnedTeams host's +autoteam pins (player id -&gt; team index 0/1); a
   *     pinned player gets a small "Team N" badge so everyone can see the host's
   *     arrangement. Pass an empty map for none.
   * @param positionRequests players' start-position role requests (player id -&gt;
   *     role 0..4); a requester gets a small "positions 2r+1/2r+2" badge. Pass an
   *     empty map for none.
   */
  static void createAndAdd(ObservableMap<? extends String, ? extends List<String>> teamsList, String ratingType,
                           PlayerService playerService, UiService uiService, RatingService ratingService,
                           GalacticWarService galacticWarService,
                           Pane teamsPane, Boolean hidePlayerRatings, String galacticWarPlanetName,
                           Map<Integer, Integer> pinnedTeams, Map<Integer, Integer> positionRequests) {
    JavaFxUtil.assertApplicationThread();
    for (Map.Entry<? extends String, ? extends List<String>> entry : teamsList.entrySet()) {
      List<Player> players = entry.getValue().stream()
          .map(playerService::getPlayerForUsername)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());

      TeamCardController teamCardController = uiService.loadFxml("theme/team_card.fxml");
      teamCardController.pinnedTeams = pinnedTeams != null ? pinnedTeams : Map.of();
      teamCardController.positionRequests = positionRequests != null ? positionRequests : Map.of();
      teamCardController.ratingType = ratingType;
      Function<Player, Image> medalIconProvider = player -> {
        ImageView imv = galacticWarService.getMedalIcon(player.getId(), galacticWarPlanetName);
        if (imv != null) {
          return imv.getImage();
        }
        else {
          return null;
        }
      };
      teamCardController.setPlayersInTeam(
          entry.getKey(), players,
          player -> player.getLeaderboardRatings().getOrDefault(ratingType, ratingService.createNewLeaderboardRating()),
          null,
          galacticWarPlanetName != null ? medalIconProvider : null,
          RatingPrecision.ROUNDED, hidePlayerRatings);
      teamsPane.getChildren().add(teamCardController.getRoot());
    }
  }

  public void setPlayersInTeam(
      String team, List<Player> playerList,
      Function<Player, LeaderboardRating> ratingProvider,
      Function<Player, Faction> playerFactionProvider,
      Function<Player, Image> playerGwMedalProvider,
      RatingPrecision ratingPrecision, Boolean hidePlayerRatings) {
    int totalRating = 0;
    // LP (Season Ladder) mode shows a ladder rank per row. Resolve the whole card's ranks in ONE
    // board-scoped query rather than a per-row fetch: lighter on the server, and the card fills in
    // one batch instead of ranks trickling in one at a time.
    boolean lpMode = preferencesService.getPreferences().getDisplayMetric() != DisplayMetric.RATINGS;
    boolean batchRanks = lpMode && ratingType != null && !Boolean.TRUE.equals(hidePlayerRatings);
    Map<Integer, PlayerCardTooltipController> cardsByPlayerId = new HashMap<>();
    for (Player player : playerList) {
      // If the server wasn't bugged, this would never be the case.
      if (player == null) {
        continue;
      }
      PlayerCardTooltipController playerCardTooltipController = uiService.loadFxml("theme/player_card_tooltip.fxml");
      Integer playerRating = RatingUtil.getRating(ratingProvider.apply(player));
      if (playerRating != null) {
        totalRating += playerRating;

        if (ratingPrecision == RatingPrecision.ROUNDED) {
          playerRating = RatingUtil.getRoundedRating(playerRating);
        }
      }
      Faction faction = null;
      if (playerFactionProvider != null) {
        faction = playerFactionProvider.apply(player);
      }
      Image gwMedalIcon = null;
      if (playerGwMedalProvider != null) {
        gwMedalIcon = playerGwMedalProvider.apply(player);
      }
      // Fix the LP-mode ladder rank to this game's board, so a player without a placement in this
      // exact format shows no rank rather than a most-played-elsewhere fallback.
      playerCardTooltipController.setLeaderboardContext(ratingType);
      // When batching, the row waits for the shared lookup below instead of self-fetching.
      playerCardTooltipController.setDeferRankToContainer(batchRanks);
      playerCardTooltipController.setPlayer(player, hidePlayerRatings ? null : playerRating, faction, gwMedalIcon);
      cardsByPlayerId.put(player.getId(), playerCardTooltipController);
      // Team cards hide friend/foe status by default; users can opt back in via a setting.
      if (!preferencesService.getPreferences().isShowFriendFoeInTeamCards()) {
        playerCardTooltipController.hideSocialStatusIcons();
      }
      // If the host pinned this player, show the pin (pin.png + team number) in
      // place of the country flag to conserve space; the flag's fixed position
      // also keeps the pins aligned across rows.
      Integer pinnedTeam = pinnedTeams.get(player.getId());
      if (pinnedTeam != null) {
        playerCardTooltipController.replaceCountryFlag(buildPinBadge(pinnedTeam));
      } else if (showPlayingStatusIconFallback) {
        // Replay detail: keep names aligned when a row has no faction/GW icon and no country flag.
        playerCardTooltipController.showPlayingStatusIconIfNoIcons();
      }
      playerCardTooltipController.getRoot().setOnContextMenuRequested(event -> {
        TeamCardPlayerContextMenuController ctrl = uiService.loadFxml("theme/play/team_card_player_context_menu.fxml");
        ctrl.setPlayer(player);
        ContextMenu cm = ctrl.getContextMenu();
        if (cm.getItems().stream().anyMatch(MenuItem::isVisible)) {
          cm.show(this.getRoot().getScene().getWindow(), event.getScreenX(), event.getScreenY());
        }
      });

      RatingChangeLabelController ratingChangeLabelController = uiService.loadFxml("theme/rating_change_label.fxml");
      ratingChangeControllersByPlayerId.put(player.getId(), ratingChangeLabelController);
      // The position-pair badge and rating-change delta join the card's right-aligned tag cluster,
      // which floats on top of the (full-width) name. Cluster order left->right: friend/foe, 3/4 badge,
      // rating-change, rank/rating — so add the badge first, then the rating-change (both land left of
      // the rank).
      Integer requestedRole = positionRequests.get(player.getId());
      if (requestedRole != null) {
        playerCardTooltipController.addTrailingTag(buildPositionRequestBadge(requestedRole));
      }
      playerCardTooltipController.addTrailingTag(ratingChangeLabelController.getRoot());
      teamPane.getChildren().add(playerCardTooltipController.getRoot());
    }

    // One board-scoped query for the whole card's ranks, pushed back to each row on the FX thread.
    if (batchRanks && !cardsByPlayerId.isEmpty()) {
      ladderPointsService.getStandingsForPlayersOnBoard(cardsByPlayerId.keySet(), ratingType)
          .thenAccept(standingsByPlayerId -> JavaFxUtil.runLater(() ->
              standingsByPlayerId.forEach((playerId, standing) -> {
                PlayerCardTooltipController card = cardsByPlayerId.get(playerId);
                if (card != null) {
                  card.applyLadderRank(standing.getRank());
                }
              })));
    }

    String teamTitle;
    if ("1".equals(team) || "null".equals(team) || team == null) {
      teamTitle = i18n.get("game.tooltip.teamTitleNoTeam");
    } else if ("-1".equals(team)) {
      teamTitle = i18n.get("game.tooltip.observers");
    } else if (hidePlayerRatings){
      teamTitle = i18n.get("replay.team", Integer.parseInt(team) - 1);
    } else {
      teamTitle = i18n.get("game.tooltip.teamTitle", Integer.parseInt(team) - 1, totalRating);
    }
    teamNameLabel.setText(teamTitle);
  }

  /** Reveal each player's legacy rating change (before -> after MMR) on their card, for a rated
   * replay. Gated upstream by the {@code showLegacyRating} cutover flag. */
  public void showRatingChange(Map<String, List<PlayerStats>> teams) {
    teams.values().stream()
        .flatMap(List::stream)
        .filter(playerStats -> ratingChangeControllersByPlayerId.containsKey(playerStats.getPlayerId()))
        .forEach(playerStats -> ratingChangeControllersByPlayerId.get(playerStats.getPlayerId())
            .setRatingChange(playerStats));
  }

  /** A team-coloured pin icon followed by the team number, shown for a
   *  host-pinned player (blue = Team 1, red = Team 2). */
  private Node buildPinBadge(int teamIndex) {
    String image = teamIndex == 0 ? "theme/images/pin-blue.png" : "theme/images/pin-red.png";
    ImageView pin = new ImageView(uiService.getThemeImage(image));
    pin.setFitWidth(16);
    pin.setFitHeight(16);
    pin.setPreserveRatio(true);
    Label number = new Label(String.valueOf(teamIndex + 1));
    number.getStyleClass().add("pinned-team-badge-label");
    HBox badge = new HBox(2.0, pin, number);
    badge.setAlignment(Pos.CENTER_LEFT);
    badge.getStyleClass().add("pinned-team-badge");
    Tooltip.install(badge, new Tooltip(i18n.get("game.pinnedTeamBadge.tooltip", teamIndex + 1)));
    return badge;
  }

  /** A small "3/4" chip shown for a player who preselected a start-position
   *  role (a pair of mirrored map start positions, one per team). */
  private Node buildPositionRequestBadge(int role) {
    Label badge = new Label(i18n.get("game.positionRequestBadge.format", 2 * role + 1, 2 * role + 2));
    badge.getStyleClass().add("position-request-badge");
    // Never let the badge squish to just "…" when the name is long — keep it wide enough for the pair.
    badge.setMinWidth(Region.USE_PREF_SIZE);
    Tooltip.install(badge, new Tooltip(i18n.get("game.positionRequestBadge.tooltip", 2 * role + 1, 2 * role + 2)));
    return badge;
  }

  public Node getRoot() {
    return teamPaneRoot;
  }
}
