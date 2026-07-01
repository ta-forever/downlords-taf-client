package com.faforever.client.game;

import com.faforever.client.chat.CountryFlagService;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.ladder.LadderPointsService;
import com.faforever.client.ladder.LadderUiUtil;
import com.faforever.client.ladder.SeasonStanding;
import com.faforever.client.player.Player;
import com.faforever.client.player.SocialStatus;
import com.faforever.client.preferences.DisplayMetric;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.theme.UiService;
import com.google.common.annotations.VisibleForTesting;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class PlayerCardTooltipController implements Controller<Node> {

  @VisibleForTesting
  static final Image RANDOM_IMAGE = new Image("/images/factions/random.png");
  private final CountryFlagService countryFlagService;
  private final I18n i18n;
  private final PreferencesService preferencesService;
  private final LadderPointsService ladderPointsService;
  private final UiService uiService;
  public Label playerInfo;
  public ImageView countryImageView;
  public Label foeIconText;
  public HBox root;
  public Label friendIconText;
  public StackPane factionIconContainer;
  public Region factionIcon;
  public ImageView factionImage;
  /** When set, the display context fixes the game format: the LP-mode ladder rank is resolved for
   * this exact board only (blank if the player has no placement there), instead of a most-played
   * fallback across boards. Null = context-free (inline badge), which does fall back. */
  private String leaderboardTechnicalName;
  /** When true, the container (e.g. the team card) resolves the ladder rank in one batched lookup
   * and pushes it via {@link #applyLadderRank}, so this row doesn't fire its own per-player query. */
  private boolean deferRankToContainer;
  /** Whether this row would show an LP-mode ladder rank (LP mode + a visible rating). Gates both the
   * self-fetch and the container-pushed {@link #applyLadderRank}. */
  private boolean rankShown;
  private Player player;

  /** Fix the ladder-rank board to the current game's rating type. Call before {@link #setPlayer}. */
  public void setLeaderboardContext(String leaderboardTechnicalName) {
    this.leaderboardTechnicalName = leaderboardTechnicalName;
  }

  /** Let the container supply the LP-mode ladder rank from a batched lookup (one query per team
   * card) instead of this row self-fetching it. Call before {@link #setPlayer}, then push the
   * resolved rank with {@link #applyLadderRank}. */
  public void setDeferRankToContainer(boolean deferRankToContainer) {
    this.deferRankToContainer = deferRankToContainer;
  }

  /** Container-driven ladder rank from a batched lookup. No-op unless this row shows a rank and the
   * player has a placement (rank &gt; 0) — otherwise the bare name stands. */
  public void applyLadderRank(int rank) {
    if (!rankShown || rank <= 0 || player == null) {
      return;
    }
    playerInfo.setText(i18n.get("userInfo.tooltipFormat.withRank", player.getUsername(), rank));
  }

  public void setPlayer(Player player, Integer rating, Faction faction, Image gwMedalIcon) {
    if (player == null) {
      return;
    }
    this.player = player;
    countryFlagService.loadCountryFlag(player.getCountry()).ifPresent(image -> countryImageView.setImage(image));
    if (gwMedalIcon != null) {
      factionImage.setImage(gwMedalIcon);
      factionImage.setVisible(true);
      factionIcon.setVisible(true);
    }

    // The displayMetric pref swaps the per-player gauge (LADDER_POINTS_DESIGN §13.3): Season Ladder
    // shows the player's ladder rank (most-played board, §13.5), Skill Rating shows the rating number.
    boolean lpMode = preferencesService.getPreferences().getDisplayMetric() != DisplayMetric.RATINGS;
    String playerInfoLocalized;
    if (!lpMode && rating != null) {
      playerInfoLocalized = i18n.get("userInfo.tooltipFormat.withRating", player.getUsername(), rating);
    } else {
      // LP mode renders the rating as a division (filled in async); show the bare name meanwhile so
      // the skill rating never flashes (and is never shown at all in LP mode).
      playerInfoLocalized = i18n.get("userInfo.tooltipFormat.noRating", player.getUsername());
    }
    //setFactionIcon(faction);
    playerInfo.setText(playerInfoLocalized);
    foeIconText.visibleProperty().bind(Bindings.createBooleanBinding(() -> player.getSocialStatus() == SocialStatus.FOE, player.socialStatusProperty()));
    friendIconText.visibleProperty().bind(Bindings.createBooleanBinding(() -> player.getSocialStatus() == SocialStatus.FRIEND, player.socialStatusProperty()));

    // Only show a ladder rank where a rating would also be shown — i.e. not on the hidden global
    // "just for fun" board (rating == null means ratings are hidden for this board).
    rankShown = lpMode && rating != null;
    // When a container batches the whole card's ranks in one query it pushes ours via
    // applyLadderRank; otherwise (a standalone card) fall back to self-fetching this one row.
    if (rankShown && !deferRankToContainer) {
      loadRankLabel(player);
    }
  }

  /** LP mode: resolve the player's ladder rank and show it in place of the rating. When a game
   * format is fixed ({@link #setLeaderboardContext}, e.g. the game-lobby team cards) the rank is
   * for that exact board only — no most-played fallback, so a player with no placement on this
   * board is left as the bare name. Cold start (no standings / unranked) also leaves the bare name
   * — never a fabricated standing. */
  private void loadRankLabel(Player player) {
    ladderPointsService.getStandingsForPlayerCached(player.getId())
        .thenAccept(standings -> {
          SeasonStanding standing = leaderboardTechnicalName != null
              ? LadderUiUtil.forBoard(standings, leaderboardTechnicalName)
              : LadderUiUtil.mostPlayed(standings);
          if (standing == null || standing.getRank() <= 0) {
            return;
          }
          String text = i18n.get("userInfo.tooltipFormat.withRank", player.getUsername(), standing.getRank());
          JavaFxUtil.runLater(() -> playerInfo.setText(text));
        })
        .exceptionally(throwable -> {
          log.warn("Could not resolve ladder rank for player {}", player.getId(), throwable);
          return null;
        });
  }

  /** Replace the country flag (the player-name label's leading graphic) with
   *  another node, e.g. a +autoteam pin badge, to save horizontal space. */
  public void replaceCountryFlag(Node graphic) {
    playerInfo.setGraphic(graphic);
  }

  /** Fallback leading icon for team-card rows that have neither a faction/GW icon nor a country flag
   *  (e.g. non-GW replay detail): without one the name sits flush-left and the column looks ragged.
   *  Drop a neutral "playing" status icon into the faction slot (reusing its 16px sizing) so every
   *  row has a consistent leading graphic. Call after {@link #setPlayer}. No-op if either slot is set. */
  public void showPlayingStatusIconIfNoIcons() {
    if (countryImageView.getImage() == null && !factionIconContainer.isVisible()) {
      factionImage.setImage(uiService.getThemeImage(UiService.CHAT_LIST_STATUS_PLAYING));
      factionImage.setVisible(true);
    }
  }

  /** Hide the friend/foe status icons (used in the team cards, which shouldn't
   *  show social status). Unbinds first since their visibility is bound to the
   *  player's social status in {@link #setPlayer}. */
  public void hideSocialStatusIcons() {
    foeIconText.visibleProperty().unbind();
    foeIconText.setVisible(false);
    friendIconText.visibleProperty().unbind();
    friendIconText.setVisible(false);
  }

  public Node getRoot() {
    return root;
  }

  @Override
  public void initialize() {
    factionImage.managedProperty().bind(factionImage.visibleProperty());
    factionIcon.managedProperty().bind(factionIcon.visibleProperty());
    // Faction icons are rarely shown in team cards (only GW rank icons for GW games). Collapse the
    // 24px container (unmanaged + invisible) whenever neither icon is in use, freeing horizontal space.
    factionIconContainer.visibleProperty().bind(factionImage.visibleProperty().or(factionIcon.visibleProperty()));
    factionIconContainer.managedProperty().bind(factionIconContainer.visibleProperty());
    foeIconText.managedProperty().bind(foeIconText.visibleProperty());
    foeIconText.setTooltip(new Tooltip(i18n.get("userInfo.foe")));
    friendIconText.managedProperty().bind(friendIconText.visibleProperty());
    friendIconText.setTooltip(new Tooltip(i18n.get("userInfo.friend")));
  }

  private void setFactionIcon(Faction faction) {
    if (faction == null) {
      return;
    }

    factionIcon.setVisible(true);
    switch (faction) {
      case CORE:
        factionIcon.getStyleClass().add(UiService.CORE_STYLE_CLASS);
        break;
      case GOK:
        factionIcon.getStyleClass().add(UiService.GOK_STYLE_CLASS);
        break;
      case ARM:
        factionIcon.getStyleClass().add(UiService.ARM_STYLE_CLASS);
        break;
      default:
        factionIcon.setVisible(false);
        factionImage.setVisible(true);
        factionImage.setImage(RANDOM_IMAGE);
        break;
    }
  }
}
