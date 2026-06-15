package com.faforever.client.game;

import com.faforever.client.fx.Controller;
import com.faforever.client.i18n.I18n;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.theme.UiService;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single host-only "Manage Game" modal that gathers the per-game host controls
 * into one place: a Teams section (manual +autoteam arrangement) and, when the
 * game uses reserved slots, a Reserved Slots section. Sections are switched with
 * ToggleButtons (the same pattern as the main navigation bar) rather than a
 * TabPane, so clicks on the labels behave normally. Save commits both.
 */
@Component
@Slf4j
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ManageGameController implements Controller<VBox> {

  private final UiService uiService;
  private final GameService gameService;
  private final PlayerService playerService;
  private final I18n i18n;

  public VBox root;
  public HBox tabBar;
  public ToggleGroup tabGroup;
  public ToggleButton teamsToggle;
  public ToggleButton reservedToggle;
  public StackPane contentPane;
  public Button saveButton;
  public Button cancelButton;

  private Game game;
  private ManualTeamArrangementController teamController;
  private ReservedPlayersEditorController reservedController;
  private Runnable onClose;

  @Override
  public void initialize() {
    // A ToggleGroup lets the user deselect the active button (-> none selected);
    // keep one always selected so a section is always shown.
    tabGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
      if (newToggle == null && oldToggle != null) {
        oldToggle.setSelected(true);
      }
    });
  }

  public void setOnClose(Runnable onClose) {
    this.onClose = onClose;
  }

  public void setGame(Game game) {
    setGame(game, false, null);
  }

  /**
   * @param focusReserved show the Reserved Slots section on open (used by the
   *     reserved-card edit shortcut and the join-request approve flow)
   * @param prepopulateReservedName pre-fill the reserved add field (approve flow)
   */
  public void setGame(Game game, boolean focusReserved, String prepopulateReservedName) {
    this.game = game;

    teamController = uiService.loadFxml("theme/play/manual_team_arrangement.fxml");
    teamController.setGame(game);
    Node teamRoot = teamController.getRoot();
    teamRoot.visibleProperty().bind(teamsToggle.selectedProperty());
    teamRoot.managedProperty().bind(teamsToggle.selectedProperty());
    contentPane.getChildren().add(teamRoot);

    boolean reservedEnabled = game.isReservedSlotsEnabled();
    tabBar.setVisible(reservedEnabled);
    tabBar.setManaged(reservedEnabled);
    if (reservedEnabled) {
      reservedController = uiService.loadFxml("theme/play/reserved_players_editor.fxml");
      seedReserved(reservedController, game, prepopulateReservedName);
      Node reservedRoot = reservedController.getRoot();
      reservedRoot.visibleProperty().bind(reservedToggle.selectedProperty());
      reservedRoot.managedProperty().bind(reservedToggle.selectedProperty());
      contentPane.getChildren().add(reservedRoot);
    } else {
      reservedToggle.setVisible(false);
      reservedToggle.setManaged(false);
    }

    if (focusReserved && reservedEnabled) {
      reservedToggle.setSelected(true);
    } else {
      teamsToggle.setSelected(true);
    }
  }

  /** Port of the reserved-editor seeding previously done in GameDetailController. */
  private void seedReserved(ReservedPlayersEditorController editor, Game g, String prepopulateName) {
    editor.maxPlayersProperty().set(g.getMaxPlayers());
    editor.setCurrentGameId(g.getId());

    Optional<Player> hostPlayer = playerService.getPlayerForUsername(g.getHost());
    List<Integer> seedIds = new ArrayList<>();
    Map<Integer, String> seedLogins = new HashMap<>();
    List<Integer> ids = g.getReservedPlayerIds();
    List<String> logins = g.getReservedPlayers();
    int n = Math.min(ids.size(), logins.size());
    Integer hostIdFromIds = null;
    for (int i = 0; i < n; i++) {
      Integer pid = ids.get(i);
      String login = logins.get(i);
      if (pid == null) {
        continue;
      }
      seedIds.add(pid);
      if (login != null) {
        seedLogins.put(pid, login);
      }
      if (login != null && login.equals(g.getHost())) {
        hostIdFromIds = pid;
      }
    }
    editor.setReservedPlayerIds(seedIds);
    editor.setDisplayLogins(seedLogins);

    Integer hostId = hostIdFromIds != null
        ? hostIdFromIds
        : hostPlayer.map(Player::getId).orElse(null);
    if (hostId != null) {
      editor.setHost(hostId, g.getHost());
    }
    if (prepopulateName != null) {
      editor.prepopulateName(prepopulateName);
    }
  }

  public void onSave() {
    if (teamController != null && !teamController.commit()) {
      // Pins can't make equal teams — surface the Teams section, keep dialog open.
      teamsToggle.setSelected(true);
      return;
    }
    if (reservedController != null) {
      // The server promotes any reserved id that was knocking and fires the
      // invite atomically inside command_set_reserved_players.
      gameService.sendReservedPlayers(new ArrayList<>(reservedController.getReservedPlayerIds()));
    }
    close();
  }

  public void onCancel() {
    close();
  }

  private void close() {
    if (onClose != null) {
      onClose.run();
    }
  }

  @Override
  public VBox getRoot() {
    return root;
  }
}
