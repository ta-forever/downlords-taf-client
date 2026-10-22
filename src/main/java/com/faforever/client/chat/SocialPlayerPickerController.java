package com.faforever.client.chat;

import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.SocialStatus;
import com.faforever.client.remote.FafService;
import com.faforever.client.util.Tuple;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.faforever.client.player.SocialStatus.FOE;

/**
 * Dialog behind the "+" on the Friends / Foes headers of the chat user list (issue #44): a paged list of all
 * players, offline ones included, filtered by name as you type, to add to or remove from the friend or foe list.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class SocialPlayerPickerController implements Controller<VBox> {

  static final int PAGE_SIZE = 20;
  private static final Duration FILTER_DELAY = Duration.millis(300);

  private final FafService fafService;
  private final PlayerService playerService;
  private final I18n i18n;

  public VBox socialPlayerPickerRoot;
  public TextField filterTextField;
  public CheckBox onlyMineCheckBox;
  public ListView<Player> playersListView;
  public Label statusLabel;
  public Button previousPageButton;
  public Label pageLabel;
  public Button nextPageButton;
  public Button closeButton;

  private final PauseTransition filterDelay = new PauseTransition(FILTER_DELAY);
  private Runnable closeButtonAction = () -> {};
  private SocialStatus socialStatus = SocialStatus.FRIEND;
  private int page = 1;
  private int totalPages;
  /** Bumped on every request so a slow response for an older filter or page can't replace a newer one. */
  private int requestId;
  /** The player's own friends or foes, loaded from the API when "only mine" is ticked; null until then. */
  private volatile List<Player> ownSocialPlayers;

  @Override
  public void initialize() {
    playersListView.setCellFactory(listView -> new PlayerCell());
    filterDelay.setOnFinished(event -> reload());
    filterTextField.textProperty().addListener((observable, oldValue, newValue) -> filterDelay.playFromStart());
    onlyMineCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
      ownSocialPlayers = null;
      reload();
    });
    JavaFxUtil.runLater(filterTextField::requestFocus);
  }

  /** Whether this dialog manages friends or foes. Call once after loading; it loads the first page. */
  public void setSocialStatus(SocialStatus socialStatus) {
    this.socialStatus = socialStatus;
    onlyMineCheckBox.setText(i18n.get(socialStatus == FOE ? "chat.socialPicker.onlyFoes" : "chat.socialPicker.onlyFriends"));
    reload();
  }

  /** What the Close button does, typically closing the dialog this is shown in. */
  public void setCloseButtonAction(Runnable closeButtonAction) {
    this.closeButtonAction = closeButtonAction;
  }

  public void onCloseButtonClicked() {
    filterDelay.stop();
    closeButtonAction.run();
  }

  public void onPreviousPage() {
    if (page > 1) {
      page--;
      loadPage();
    }
  }

  public void onNextPage() {
    if (page < totalPages) {
      page++;
      loadPage();
    }
  }

  /** Loads page 1 for the current filter right away, skipping the typing delay. */
  void reload() {
    filterDelay.stop();
    page = 1;
    loadPage();
  }

  private void loadPage() {
    int thisRequest = ++requestId;
    statusLabel.setText(i18n.get("chat.socialPicker.loading"));
    statusLabel.setVisible(true);
    previousPageButton.setDisable(true);
    nextPageButton.setDisable(true);

    String filter = filterTextField.getText().trim();
    CompletableFuture<Tuple<List<Player>, Integer>> result = onlyMineCheckBox.isSelected()
        ? loadOwnSocialPage(filter, page)
        : fafService.findPlayersByLoginPrefixWithPageCount(filter, PAGE_SIZE, page);

    result.thenAccept(players -> JavaFxUtil.runLater(() -> {
      if (thisRequest == requestId) {
        showPage(players.getFirst(), players.getSecond());
      }
    })).exceptionally(throwable -> {
      log.warn("Could not load players for filter '{}'", filter, throwable);
      JavaFxUtil.runLater(() -> {
        if (thisRequest == requestId) {
          showError();
        }
      });
      return null;
    });
  }

  private CompletableFuture<Tuple<List<Player>, Integer>> loadOwnSocialPage(String filter, int page) {
    List<Player> cached = ownSocialPlayers;
    CompletableFuture<List<Player>> players = cached != null ? CompletableFuture.completedFuture(cached) : loadOwnSocialPlayers();
    return players.thenApply(all -> {
      String lowerCaseFilter = filter.toLowerCase(Locale.ROOT);
      List<Player> matching = all.stream()
          .filter(player -> player.getUsername().toLowerCase(Locale.ROOT).startsWith(lowerCaseFilter))
          .collect(Collectors.toList());
      int pages = (matching.size() + PAGE_SIZE - 1) / PAGE_SIZE;
      int from = Math.min((page - 1) * PAGE_SIZE, matching.size());
      return new Tuple<>(matching.subList(from, Math.min(from + PAGE_SIZE, matching.size())), pages);
    });
  }

  private CompletableFuture<List<Player>> loadOwnSocialPlayers() {
    List<Integer> ids = socialStatus == FOE ? playerService.getFoeIds() : playerService.getFriendIds();
    if (ids.isEmpty()) {
      ownSocialPlayers = List.of();
      return CompletableFuture.completedFuture(ownSocialPlayers);
    }
    // getPlayersByIds calls the API on the calling thread, so keep it off the FX thread.
    return CompletableFuture.supplyAsync(() -> playerService.getPlayersByIds(ids).join())
        .thenApply(players -> {
          List<Player> sorted = players.stream()
              .sorted(Comparator.comparing(player -> player.getUsername().toLowerCase(Locale.ROOT)))
              .collect(Collectors.toList());
          ownSocialPlayers = sorted;
          return sorted;
        });
  }

  private void showPage(List<Player> players, int totalPages) {
    this.totalPages = totalPages;
    playersListView.getItems().setAll(players);
    statusLabel.setText(i18n.get("chat.socialPicker.noResults"));
    statusLabel.setVisible(players.isEmpty());
    pageLabel.setText(i18n.get("chat.socialPicker.page", totalPages == 0 ? 0 : page, totalPages));
    previousPageButton.setDisable(page <= 1);
    nextPageButton.setDisable(page >= totalPages);
  }

  private void showError() {
    playersListView.getItems().clear();
    statusLabel.setText(i18n.get("chat.socialPicker.loadFailed"));
    statusLabel.setVisible(true);
    pageLabel.setText("");
    previousPageButton.setDisable(page <= 1);
    nextPageButton.setDisable(true);
  }

  /** Adds the player to, or removes them from, this dialog's list. Adding moves them off the other list first. */
  void toggle(Player player) {
    int id = player.getId();
    if (socialStatus == FOE) {
      if (playerService.isFoe(id)) {
        playerService.removeFoe(player);
      } else {
        if (playerService.isFriend(id)) {
          playerService.removeFriend(player);
        }
        playerService.addFoe(player);
      }
    } else {
      if (playerService.isFriend(id)) {
        playerService.removeFriend(player);
      } else {
        if (playerService.isFoe(id)) {
          playerService.removeFoe(player);
        }
        playerService.addFriend(player);
      }
    }
    playersListView.refresh();
  }

  private boolean isSelf(Player player) {
    return playerService.getCurrentPlayer().map(Player::getId).filter(id -> id == player.getId()).isPresent();
  }

  @Override
  public VBox getRoot() {
    return socialPlayerPickerRoot;
  }

  private class PlayerCell extends ListCell<Player> {
    private final Label nameLabel = new Label();
    private final Label tagLabel = new Label();
    private final Button actionButton = new Button();
    private final HBox content;

    PlayerCell() {
      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      tagLabel.getStyleClass().add("social-player-picker-tag");
      actionButton.setOnAction(event -> {
        if (getItem() != null) {
          toggle(getItem());
        }
      });
      content = new HBox(8, nameLabel, tagLabel, spacer, actionButton);
      content.setAlignment(Pos.CENTER_LEFT);
    }

    @Override
    protected void updateItem(Player player, boolean empty) {
      super.updateItem(player, empty);
      setText(null);
      if (empty || player == null) {
        setGraphic(null);
        return;
      }

      boolean self = isSelf(player);
      boolean friend = playerService.isFriend(player.getId());
      boolean foe = playerService.isFoe(player.getId());
      nameLabel.setText(player.getUsername());
      if (self) {
        tagLabel.setText(i18n.get("chat.socialPicker.you"));
      } else if (friend) {
        tagLabel.setText(i18n.get("chat.socialPicker.friendTag"));
      } else if (foe) {
        tagLabel.setText(i18n.get("chat.socialPicker.foeTag"));
      } else {
        tagLabel.setText("");
      }

      actionButton.setVisible(!self);
      boolean onThisList = socialStatus == FOE ? foe : friend;
      if (socialStatus == FOE) {
        actionButton.setText(i18n.get(onThisList ? "chat.socialPicker.removeFoe" : "chat.socialPicker.addFoe"));
      } else {
        actionButton.setText(i18n.get(onThisList ? "chat.socialPicker.removeFriend" : "chat.socialPicker.addFriend"));
      }
      setGraphic(content);
    }
  }
}
