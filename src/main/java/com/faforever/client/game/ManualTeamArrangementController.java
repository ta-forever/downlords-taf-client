package com.faforever.client.game;

import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.LeaderboardRating;
import com.faforever.client.player.Player;
import com.faforever.client.rating.RatingService;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.RatingUtil;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Host-only pane (a tab of {@link ManageGameController}) that lets the host pin
 * specific players to a +autoteam ally group by dragging them onto Team 1 /
 * Team 2. Everyone left in the "Unassigned" pool is balanced around those pins
 * by the auto-balancer. {@link #commit()} stores the resulting assignment,
 * which overrides the auto-balanced {@code /startpositions} solution that
 * gpgnet4ta forwards to tadr-ddraw and that tadr-ddraw consumes when the host
 * types {@code +autoteam} in game.
 *
 * <p>tadr-ddraw assigns ally team {@code i % teamCount} over the ordered player
 * list, so only equal (or off-by-one) two-team partitions can be reproduced.
 * The host's pins must therefore allow an equal split; {@link #commit()}
 * refuses (returns {@code false}) and shows a warning if they don't, so the
 * containing dialog can keep itself open and ask the host to fix the picks.</p>
 */
@Component
@Slf4j
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ManualTeamArrangementController implements Controller<VBox> {

  /** Drop-target bucket indices. */
  private static final int TEAM0 = 0;
  private static final int TEAM1 = 1;
  private static final int POOL = 2;

  private final UiService uiService;
  private final RatingService ratingService;
  private final GameService gameService;
  private final I18n i18n;
  private final com.faforever.client.preferences.PreferencesService preferencesService;

  /** Re-renders the cards when the global displayMetric pref flips (the in-dialog pill). Field-held
   * so the weak listener isn't collected. */
  private javafx.beans.value.ChangeListener<com.faforever.client.preferences.DisplayMetric> displayMetricListener;

  public VBox root;
  public Label hintLabel;
  public VBox team0Pane;
  public VBox team1Pane;
  public VBox poolPane;
  public Label team0Label;
  public Label team1Label;
  public Label poolLabel;
  public VBox team0Box;
  public VBox team1Box;
  public VBox poolBox;
  public Label previewLabel;
  public Button clearButton;
  public Button autoFillButton;
  public HBox maxPlayersBox;
  public Spinner<Integer> maxPlayersSpinner;

  private Game game;
  private String ratingType;
  private final List<Player> team0 = new ArrayList<>();
  private final List<Player> team1 = new ArrayList<>();
  private final List<Player> pool = new ArrayList<>();

  /** The host's player id, if the host is playing (not watching). The host is
   *  always locked to Team 1 and is never draggable. */
  private Integer hostId;
  private Player draggingPlayer;

  @Override
  public void initialize() {
    hintLabel.setText(i18n.get("manualTeams.hint"));
    maxPlayersSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 10, 10));
    setupDropTarget(team0Pane, TEAM0);
    setupDropTarget(team1Pane, TEAM1);
    setupDropTarget(poolPane, POOL);

    // The in-dialog pill flips the global metric; redraw the cards (rating ⇄ rank) when it changes.
    displayMetricListener = (obs, oldValue, newValue) -> {
      if (game != null) {
        render();
      }
    };
    com.faforever.client.fx.JavaFxUtil.addListener(preferencesService.getPreferences().displayMetricProperty(),
        new javafx.beans.value.WeakChangeListener<>(displayMetricListener));
  }

  public void setGame(Game game) {
    this.game = game;
    this.ratingType = game.getRatingType();

    List<Player> roster = ratingService.getBalancedTeams(game);
    if (roster == null) {
      roster = List.of();
    }
    String hostName = game.getHost();
    this.hostId = null;
    Map<Integer, Integer> existing = gameService.getManualTeams(game.getId());
    team0.clear();
    team1.clear();
    pool.clear();
    for (Player p : roster) {
      if (hostName != null && hostName.equals(p.getUsername())) {
        this.hostId = p.getId();
        team0.add(p);   // the host is always on Team 1
        continue;
      }
      Integer t = existing == null ? null : existing.get(p.getId());
      if (t != null && t == TEAM0) {
        team0.add(p);
      } else if (t != null && t == TEAM1) {
        team1.add(p);
      } else {
        pool.add(p);
      }
    }

    // Max-players control only applies while staging (the staging lobby owns
    // the slot count); hide it in the battleroom.
    boolean staging = game.getStatus() == GameStatus.STAGING;
    maxPlayersBox.setVisible(staging);
    maxPlayersBox.setManaged(staging);
    if (staging) {
      int max = Math.max(2, Math.min(10, game.getMaxPlayers()));
      maxPlayersSpinner.getValueFactory().setValue(max);
    }

    render();
    updatePreview();
  }

  private boolean isHost(Player p) {
    return hostId != null && p.getId() == hostId;
  }

  public void onRefresh() {
    reconcileRoster();
  }

  /**
   * Reconcile the buckets against the current game roster: drop players who
   * have left, and add newly-joined players to the Unassigned pool (the host,
   * if newly seen, goes to Team 1). Existing pins are preserved. Invoked
   * on-demand from the Refresh button (the dialog does not auto-update, so the
   * host's in-progress arrangement is never disturbed).
   */
  void reconcileRoster() {
    if (game == null) {
      return;
    }
    List<Player> roster = ratingService.getBalancedTeams(game);
    if (roster == null) {
      roster = List.of();
    }
    Set<Integer> rosterIds = new HashSet<>();
    for (Player p : roster) {
      rosterIds.add(p.getId());
    }

    // Game-info refreshes fire frequently with the same roster — only touch the
    // UI when a player has actually joined or left, otherwise the periodic
    // re-render fights the host's in-progress edits.
    Set<Integer> currentIds = new HashSet<>();
    team0.forEach(p -> currentIds.add(p.getId()));
    team1.forEach(p -> currentIds.add(p.getId()));
    pool.forEach(p -> currentIds.add(p.getId()));
    if (currentIds.equals(rosterIds)) {
      return;
    }

    team0.removeIf(p -> !rosterIds.contains(p.getId()));
    team1.removeIf(p -> !rosterIds.contains(p.getId()));
    pool.removeIf(p -> !rosterIds.contains(p.getId()));

    Set<Integer> present = new HashSet<>();
    team0.forEach(p -> present.add(p.getId()));
    team1.forEach(p -> present.add(p.getId()));
    pool.forEach(p -> present.add(p.getId()));

    String hostName = game.getHost();
    for (Player p : roster) {
      if (present.contains(p.getId())) {
        continue;
      }
      if (hostName != null && hostName.equals(p.getUsername())) {
        this.hostId = p.getId();
        team0.add(p);
      } else {
        pool.add(p);
      }
    }

    render();
    updatePreview();
  }

  /**
   * Run the auto-balancer now with the current pins and adopt its full
   * recommendation: every player is moved into the team the balancer picked
   * (even = Team 1, odd = Team 2) and the Unassigned pool is emptied. When the
   * pins are lopsided this also unpins the lowest-rated non-host players the
   * balancer had to demote, so the buckets always end up as valid even teams.
   * The host stays on Team 1 (the balancer keeps them at start spot 0).
   */
  public void onAutoFill() {
    Map<Integer, Integer> pins = currentPins();
    List<Player> order = pins.isEmpty()
        ? ratingService.getBalancedTeams(game)
        : ratingService.getBalancedTeams(game, pins);
    if (order == null || order.isEmpty()) {
      return;
    }
    team0.clear();
    team1.clear();
    pool.clear();
    for (int i = 0; i < order.size(); i++) {
      (i % 2 == 0 ? team0 : team1).add(order.get(i));
    }
    render();
    updatePreview();
  }

  public void onClear() {
    List<Player> keepHost = new ArrayList<>();
    for (Player p : team0) {
      if (isHost(p)) {
        keepHost.add(p);
      } else {
        pool.add(p);
      }
    }
    pool.addAll(team1);
    team0.clear();
    team0.addAll(keepHost);
    team1.clear();
    render();
    updatePreview();
  }

  /**
   * Store the host's current pins as the team override. Pins are soft: if they
   * can't form equal teams the balancer relaxes the lowest-rated extras at
   * calculation time, so this always succeeds.
   *
   * @return always true
   */
  public boolean commit() {
    gameService.setManualTeams(game, currentPins());
    if (maxPlayersBox.isVisible() && maxPlayersSpinner.getValue() != null
        && maxPlayersSpinner.getValue() != game.getMaxPlayers()) {
      gameService.setMaxPlayersForStagingGame(maxPlayersSpinner.getValue());
    }
    return true;
  }

  private Map<Integer, Integer> currentPins() {
    Map<Integer, Integer> pins = new HashMap<>();
    for (Player p : team0) {
      pins.put(p.getId(), TEAM0);
    }
    for (Player p : team1) {
      pins.put(p.getId(), TEAM1);
    }
    return pins;
  }

  // ---- drag and drop ------------------------------------------------------

  private void setupDropTarget(Region pane, int bucket) {
    pane.setOnDragOver(event -> {
      if (draggingPlayer != null && event.getDragboard().hasString()) {
        event.acceptTransferModes(TransferMode.MOVE);
      }
      event.consume();
    });
    pane.setOnDragEntered(event -> {
      if (draggingPlayer != null) {
        pane.getStyleClass().add("manual-team-drop-hover");
      }
      event.consume();
    });
    pane.setOnDragExited(event -> {
      pane.getStyleClass().remove("manual-team-drop-hover");
      event.consume();
    });
    pane.setOnDragDropped(event -> {
      boolean done = false;
      if (draggingPlayer != null) {
        assignPlayer(draggingPlayer, bucket);
        done = true;
      }
      pane.getStyleClass().remove("manual-team-drop-hover");
      event.setDropCompleted(done);
      event.consume();
    });
  }

  void assignPlayer(Player player, int bucket) {
    if (isHost(player)) {
      return;   // the host is locked to Team 1
    }
    removeFromAll(player);
    bucketList(bucket).add(player);
    draggingPlayer = null;
    render();
    updatePreview();
  }

  private List<Player> bucketList(int bucket) {
    return bucket == TEAM0 ? team0 : bucket == TEAM1 ? team1 : pool;
  }

  private void removeFromAll(Player player) {
    team0.removeIf(p -> p.getId() == player.getId());
    team1.removeIf(p -> p.getId() == player.getId());
    pool.removeIf(p -> p.getId() == player.getId());
  }

  // ---- rendering ----------------------------------------------------------

  private void render() {
    JavaFxUtil.assertApplicationThread();
    renderBucket(team0Box, team0);
    renderBucket(team1Box, team1);
    renderBucket(poolBox, pool);
    team0Label.setText(i18n.get("manualTeams.teamHeader", 1, team0.size(), sumRating(team0)));
    team1Label.setText(i18n.get("manualTeams.teamHeader", 2, team1.size(), sumRating(team1)));
    poolLabel.setText(i18n.get("manualTeams.unassignedHeader", pool.size()));
  }

  private void renderBucket(VBox box, List<Player> players) {
    box.getChildren().clear();
    for (Player p : players) {
      box.getChildren().add(buildRow(p));
    }
  }

  private Node buildRow(Player player) {
    PlayerCardTooltipController card = uiService.loadFxml("theme/player_card_tooltip.fxml");
    card.setPlayer(player, ratingOf(player), null, null);
    Node nameNode = card.getRoot();
    HBox.setHgrow(nameNode, Priority.ALWAYS);

    HBox row = new HBox(nameNode);
    row.getStyleClass().add("manual-team-player-row");
    if (isHost(player)) {
      // Host is locked to Team 1 — mark it and don't make it draggable.
      row.getStyleClass().add("manual-team-host-locked");
      Label hostTag = new Label(i18n.get("reservedSlots.editor.hostSuffix"));
      hostTag.getStyleClass().add("manual-team-host-tag");
      row.getChildren().add(hostTag);
    } else {
      row.setOnDragDetected(event -> {
        Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();
        content.putString(String.valueOf(player.getId()));
        db.setContent(content);
        draggingPlayer = player;
        event.consume();
      });
      // Clear the in-progress flag when the gesture ends (including a cancelled
      // drop), so a later roster refresh isn't blocked forever.
      row.setOnDragDone(event -> {
        draggingPlayer = null;
        event.consume();
      });
    }
    return row;
  }

  private void updatePreview() {
    Map<Integer, Integer> pins = currentPins();
    List<Player> order = pins.isEmpty()
        ? ratingService.getBalancedTeams(game)
        : ratingService.getBalancedTeams(game, pins);
    if (order == null) {
      order = List.of();
    }

    // The order is interleaved: even indices are Team 1, odd indices Team 2.
    int sizeA = 0;
    int sizeB = 0;
    int ratingA = 0;
    int ratingB = 0;
    for (int i = 0; i < order.size(); i++) {
      int rating = ratingOf(order.get(i));
      if (i % 2 == 0) {
        sizeA++;
        ratingA += rating;
      } else {
        sizeB++;
        ratingB += rating;
      }
    }
    previewLabel.setText(i18n.get("manualTeams.preview", sizeA, ratingA, sizeB, ratingB));
    previewLabel.setVisible(true);
  }

  private int sumRating(List<Player> players) {
    int total = 0;
    for (Player p : players) {
      total += ratingOf(p);
    }
    return total;
  }

  private int ratingOf(Player p) {
    LeaderboardRating r = p.getLeaderboardRatings().getOrDefault(ratingType, ratingService.createNewLeaderboardRating());
    return RatingUtil.getRoundedRating(RatingUtil.getRating(r));
  }

  @Override
  public VBox getRoot() {
    return root;
  }
}
