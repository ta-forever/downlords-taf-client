package com.faforever.client.game;

import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlayerAutocompleteBinder;
import com.faforever.client.i18n.I18n;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.LastGamePrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Editor pane for the reserved-slots player list. Used both inline inside
 * CreateGameController (before hosting) and as a popup in GameDetailController
 * (after hosting). Exposes its current list as an {@link ObservableList} of
 * player ids so the parent can read/bind.
 *
 * Design notes:
 *  - The list is capped at {@link #maxPlayersProperty()} (defaults to 10);
 *    "Add" actions silently drop overflow.
 *  - The current player (host) is always treated as implicitly reserved and
 *    is not shown in the list — the server adds them on the back end.
 *  - Online/offline/in-another-game status is shown as a colored dot. For
 *    "in this game" we'd need the current game id; the parent can set it via
 *    {@link #setCurrentGameId(Integer)} to enable that state.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class ReservedPlayersEditorController implements Controller<VBox> {

  private final PlayerService playerService;
  private final FafService fafService;
  private final PreferencesService preferencesService;
  private final I18n i18n;

  public VBox root;
  public TextField addPlayerField;
  public Button addPlayerButton;
  public Button addFriendsButton;
  public Button addFromLastGameButton;
  public Button clearButton;
  public Label capacityLabel;
  public ListView<Integer> reservedListView;

  private final ObservableList<Integer> reservedPlayerIds = FXCollections.observableArrayList();
  private final IntegerProperty maxPlayers = new SimpleIntegerProperty(10);
  /** When set, list entries matching this id are styled as "in this game". */
  private Integer currentGameId;
  /** The host of the (possibly-not-yet-created) game. Shown in the list as a
   *  pinned, non-removable row, and counts toward the cap. The server adds
   *  the host implicitly on its end too, so sending the list with or without
   *  the host id is equivalent — we send it for clarity. */
  private Integer hostId;
  /** Caller-supplied fallback id -> login map for cell display. Used when the
   *  player isn't in PlayerService (e.g. offline). Populated by the parent
   *  (typically from the server's reserved_players parallel arrays) AND
   *  extended when the user adds a new player via autocomplete, so future
   *  cell renders for that id can show the login rather than "#42". */
  private final Map<Integer, String> displayLogins = new HashMap<>();

  private PlayerAutocompleteBinder autocompleteBinder;

  @Override
  public VBox getRoot() {
    return root;
  }

  @Override
  public void initialize() {
    autocompleteBinder = new PlayerAutocompleteBinder(playerService, fafService);
    autocompleteBinder.bind(addPlayerField);

    reservedListView.setItems(reservedPlayerIds);
    reservedListView.setCellFactory(lv -> new ReservedPlayerCell());

    // Enter in the field submits — same handler as the + button.
    addPlayerField.setOnAction(e -> onAddPlayerClicked());

    // Buttons disabled when their action is meaningless. The text field is
    // intentionally NOT disabled — the user can still type (e.g. while they
    // figure out who to remove first); we just no-op on Enter via the
    // early-return in onAddPlayerClicked.
    javafx.beans.binding.BooleanBinding atCap =
        javafx.beans.binding.Bindings.size(reservedPlayerIds).greaterThanOrEqualTo(maxPlayers);
    addFriendsButton.disableProperty().bind(atCap);
    addPlayerButton.disableProperty().bind(atCap);
    // Clear is meaningless when only the host (or no one) is in the list,
    // since Clear preserves the host. Recomputed on every list change.
    clearButton.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
        () -> {
          int n = reservedPlayerIds.size();
          int hostCount = hostId != null && reservedPlayerIds.contains(hostId) ? 1 : 0;
          return n - hostCount <= 0;
        },
        reservedPlayerIds));

    updateAddFromLastGameButton();
    updateCapacityLabel();
    reservedPlayerIds.addListener((javafx.collections.ListChangeListener<Integer>) c -> updateCapacityLabel());
    maxPlayers.addListener((obs, o, n) -> updateCapacityLabel());
  }

  /** Player ids currently reserved (excluding host, who is implicit on the server). */
  public ObservableList<Integer> getReservedPlayerIds() {
    return reservedPlayerIds;
  }

  /** Replace the list. Drops anything beyond {@link #maxPlayersProperty()}.
   *  If {@link #setHost} has been called, the host id is always kept at
   *  position 0 regardless of what's in the supplied list. Safe to call
   *  before or after {@link #setHost}. */
  public void setReservedPlayerIds(List<Integer> ids) {
    List<Integer> incoming = ids == null
        ? new ArrayList<>()
        : new ArrayList<>(ids);
    if (hostId != null) {
      incoming.removeIf(pid -> pid != null && pid.equals(hostId));
      incoming.add(0, hostId);
    }
    int cap = maxPlayers.get();
    if (incoming.size() > cap) {
      incoming = incoming.subList(0, cap);
    }
    reservedPlayerIds.setAll(incoming);
  }

  /** Mark a player id as the game's host. Pins them at the top of the list
   *  as a non-removable row that counts toward the cap. */
  public void setHost(int id, String login) {
    this.hostId = id;
    if (login != null && !login.isBlank()) {
      displayLogins.put(id, login);
    }
    reservedPlayerIds.removeIf(pid -> pid != null && pid == id);
    reservedPlayerIds.add(0, id);
    reservedListView.refresh();
  }

  /** Host id, or null if {@link #setHost} hasn't been called. Callers that
   *  persist a non-host selection (e.g. to LastGamePrefs) should filter
   *  this id out of {@link #getReservedPlayerIds()} themselves. */
  public Integer getHostId() {
    return hostId;
  }

  public IntegerProperty maxPlayersProperty() {
    return maxPlayers;
  }

  public void setCurrentGameId(Integer gameId) {
    this.currentGameId = gameId;
    reservedListView.refresh();
  }

  /** Pre-fill the autocomplete field. Used by the "Approve join request"
   *  flow so the host can review the list and slot the requester in. */
  public void prepopulateName(String name) {
    if (name != null && !name.isBlank()) {
      addPlayerField.setText(name);
    }
  }

  /** Caller-supplied id -> login fallback map for the list cells. Replaces
   *  any previously-set entries; existing cell rows that were added by the
   *  user via autocomplete retain their login info too. */
  public void setDisplayLogins(Map<Integer, String> logins) {
    displayLogins.clear();
    if (logins != null) {
      displayLogins.putAll(logins);
    }
    reservedListView.refresh();
  }

  /** Snapshot of the id -> login map for the editor's current selection. The
   *  parent persists this alongside the id list so the next session can show
   *  logins even for players currently offline. May not contain every id in
   *  {@link #getReservedPlayerIds()} if the source never provided a login
   *  (e.g. an Add-Friends bulk-add for a friend the local PlayerService
   *  also doesn't know — rare). */
  public Map<Integer, String> getDisplayLogins() {
    return new HashMap<>(displayLogins);
  }

  public void onAddPlayerClicked() {
    // No-op at cap. Reached via the + button (disabled but defensive) or
    // Enter on the text field (not disabled — typing is still allowed).
    if (reservedPlayerIds.size() >= maxPlayers.get()) {
      return;
    }
    String raw = addPlayerField.getText() == null ? "" : addPlayerField.getText().trim();
    if (raw.isEmpty()) {
      return;
    }
    String bareName = PlayerAutocompleteBinder.stripClanDecoration(raw);
    Optional<Integer> resolved = autocompleteBinder.resolveSelection(raw);
    if (resolved.isPresent()) {
      addIdWithLogin(resolved.get(), bareName);
      addPlayerField.clear();
      return;
    }
    // Unknown locally — try the api before giving up.
    fafService.queryPlayerByName(bareName).thenAccept(opt -> JavaFxUtil.runLater(() -> {
      if (opt.isPresent()) {
        addIdWithLogin(opt.get().getId(), opt.get().getUsername());
        addPlayerField.clear();
      }
    }));
  }

  public void onAddFriendsClicked() {
    for (Integer id : playerService.getFriendIds()) {
      if (reservedPlayerIds.size() >= maxPlayers.get()) break;
      // Friends are usually in PlayerService (online); resolve so the next
      // session can show their login even if they've gone offline since.
      String login = playerService.getPlayerById(id).map(Player::getUsername).orElse(null);
      addIdWithLogin(id, login);
    }
  }

  public void onAddFromLastGameClicked() {
    LastGamePrefs prefs = preferencesService.getPreferences().getLastGame();
    List<Integer> ids = prefs.getLastGameRosterPlayerIds();
    List<String> logins = prefs.getLastGameRosterPlayerLogins();
    int n = Math.min(ids.size(), logins.size());
    for (int i = 0; i < n; i++) {
      if (reservedPlayerIds.size() >= maxPlayers.get()) break;
      addIdWithLogin(ids.get(i), logins.get(i));
    }
  }

  public void onClearClicked() {
    if (hostId != null) {
      reservedPlayerIds.removeIf(pid -> pid != null && !pid.equals(hostId));
    } else {
      reservedPlayerIds.clear();
    }
  }

  private void addId(int id) {
    if (reservedPlayerIds.contains(id)) {
      return;
    }
    if (reservedPlayerIds.size() >= maxPlayers.get()) {
      return;
    }
    reservedPlayerIds.add(id);
  }

  private void addIdWithLogin(int id, String login) {
    if (login != null && !login.isBlank()) {
      displayLogins.put(id, login);
    }
    addId(id);
  }

  private void updateCapacityLabel() {
    capacityLabel.setText(i18n.get("reservedSlots.editor.capacity",
        reservedPlayerIds.size(), maxPlayers.get()));
  }

  private void updateAddFromLastGameButton() {
    LastGamePrefs prefs = preferencesService.getPreferences().getLastGame();
    List<String> logins = new ArrayList<>(prefs.getLastGameRosterPlayerLogins());
    addFromLastGameButton.setDisable(logins.isEmpty());
    if (logins.isEmpty()) {
      addFromLastGameButton.setText(i18n.get("reservedSlots.editor.addFromLastGame.empty"));
    } else {
      String preview = logins.size() > 3
          ? String.join(", ", logins.subList(0, 3)) + ", ..."
          : String.join(", ", logins);
      addFromLastGameButton.setText(i18n.get("reservedSlots.editor.addFromLastGame", preview));
    }
  }

  /**
   * ListView cell rendering one reserved player. Resolves the id to a name
   * and status via {@link PlayerService} at render time. Renaming/relogin
   * is picked up automatically next time the cell is refreshed.
   */
  private class ReservedPlayerCell extends ListCell<Integer> {
    @Override
    protected void updateItem(Integer playerId, boolean empty) {
      super.updateItem(playerId, empty);
      if (empty || playerId == null) {
        setText(null);
        setGraphic(null);
        return;
      }

      Optional<Player> playerOpt = playerService.getPlayerById(playerId);
      String label = playerOpt.map(Player::getUsername)
          .orElseGet(() -> displayLogins.getOrDefault(playerId, "#" + playerId));

      // Status indicator: no dot for offline/idle; green ring for in-this-game
      // (the game is necessarily STAGING/BATTLEROOM here — editor isn't usable
      // on a launched game); amber ring/filled for in-other-game based on
      // whether that game has launched.
      String statusClass = null;
      if (playerOpt.isPresent()) {
        com.faforever.client.game.PlayerStatus pstatus = playerOpt.get().getStatus();
        boolean idle = pstatus == null || pstatus == com.faforever.client.game.PlayerStatus.IDLE;
        boolean inThis = currentGameId != null
            && playerOpt.get().getCurrentGameUid() == currentGameId;
        if (!idle) {
          if (inThis) {
            statusClass = "status-in-this-game-staging";
          } else {
            boolean live = pstatus == com.faforever.client.game.PlayerStatus.PLAYING;
            statusClass = live ? "status-in-other-game-live" : "status-in-other-game-staging";
          }
        }
      }

      Label nameLabel = new Label(label);
      HBox.setHgrow(nameLabel, Priority.ALWAYS);
      nameLabel.setMaxWidth(Double.MAX_VALUE);
      if (playerOpt.isEmpty()) {
        nameLabel.getStyleClass().add("reserved-player-offline-name");
      }

      boolean isHost = hostId != null && hostId.intValue() == playerId.intValue();
      if (isHost) {
        nameLabel.setText(label + i18n.get("reservedSlots.editor.hostSuffix"));
      }

      HBox row = new HBox(6);
      row.setAlignment(Pos.CENTER_LEFT);
      // Always insert the 10x10 placeholder so all rows align the same way.
      Region statusDot = new Region();
      statusDot.getStyleClass().add("reserved-player-status-dot");
      statusDot.setPrefSize(10, 10);
      statusDot.setMinSize(10, 10);
      statusDot.setMaxSize(10, 10);
      if (statusClass != null) {
        statusDot.getStyleClass().add(statusClass);
      }
      row.getChildren().add(statusDot);
      row.getChildren().add(nameLabel);
      if (!isHost) {
        Button removeButton = new Button("x");
        removeButton.getStyleClass().add("reserved-player-remove");
        removeButton.setOnAction(e -> reservedPlayerIds.remove(playerId));
        row.getChildren().add(removeButton);
      }

      setText(null);
      setGraphic(row);
    }
  }
}
