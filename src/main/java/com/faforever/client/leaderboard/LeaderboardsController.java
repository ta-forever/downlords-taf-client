package com.faforever.client.leaderboard;

import com.faforever.client.chat.UserInfoWindowController;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.StringCell;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.ShowUserReplaysEvent;
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.Validator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

import static javafx.collections.FXCollections.observableList;


@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class LeaderboardsController extends AbstractViewController<Node> {

  private final LeaderboardService leaderboardService;
  private final NotificationService notificationService;
  private final ModService modService;
  private final UiService uiService;
  private final PlayerService playerService;
  private final EventBus eventBus;
  private final I18n i18n;
  public Pane leaderboardRoot;
  public TableColumn<LeaderboardEntry, Number> rankColumn;
  public TableColumn<LeaderboardEntry, String> nameColumn;
  public TableColumn<LeaderboardEntry, Number> winRateColumn;
  public TableColumn<LeaderboardEntry, Number> recentWinRateColumn;
  public TableColumn<LeaderboardEntry, String> recentModColumn;
  public TableColumn<LeaderboardEntry, Number> streakColumn;
  public TableColumn<LeaderboardEntry, Number> gamesPlayedColumn;
  public TableColumn<LeaderboardEntry, Number> ratingColumn;
  public TableView<LeaderboardEntry> ratingTable;
  public ComboBox<Leaderboard> leaderboardComboBox;
  public TextField searchTextField;
  public Pane connectionProgressPane;
  public Pane contentPane;

  @VisibleForTesting
  protected AutoCompletionBinding<String> usernamesAutoCompletion;

  @Override
  public void initialize() {
    super.initialize();
    leaderboardService.getLeaderboards().thenApply(leaderboards -> {
      JavaFxUtil.runLater(() -> {
        leaderboardComboBox.getItems().clear();
        leaderboardComboBox.setConverter(leaderboardStringConverter());
        leaderboardComboBox.getItems().addAll(leaderboards);
        leaderboardComboBox.getSelectionModel().selectFirst();
      });
      return null;
    });

    rankColumn.setCellValueFactory(param -> new SimpleIntegerProperty(ratingTable.getItems().indexOf(param.getValue()) + 1));
    rankColumn.setCellFactory(param -> new StringCell<>(rank -> i18n.number(rank.intValue())));

    nameColumn.setCellValueFactory(param -> param.getValue().usernameProperty());
    nameColumn.setCellFactory(param -> new StringCell<>(name -> name));

    winRateColumn.setCellValueFactory(param -> new SimpleFloatProperty(param.getValue().getWinRate()));
    winRateColumn.setCellFactory(param -> new StringCell<>(number -> i18n.get("percentage", number.floatValue() * 100)));

    recentWinRateColumn.setCellValueFactory(param -> new SimpleFloatProperty(param.getValue().getRecentWinRate()));
    recentWinRateColumn.setCellFactory(param -> new StringCell<>(number -> i18n.get("percentage", number.floatValue() * 100)));

    streakColumn.setCellValueFactory(param -> param.getValue().streakProperty());
    streakColumn.setCellFactory(param -> new StringCell<>(streak -> i18n.number(streak.intValue())));

    recentModColumn.setCellValueFactory(param -> param.getValue().recentModProperty());
    recentModColumn.setCellFactory(param -> new StringCell<>(mod -> modService.getFeaturedModDisplayName(mod)));

    gamesPlayedColumn.setCellValueFactory(param -> param.getValue().gamesPlayedProperty());
    gamesPlayedColumn.setCellFactory(param -> new StringCell<>(count -> i18n.number(count.intValue())));

    ratingColumn.setCellValueFactory(param -> param.getValue().ratingProperty());
    ratingColumn.setCellFactory(param -> new StringCell<>(rating -> i18n.number(rating.intValue())));

    contentPane.managedProperty().bind(contentPane.visibleProperty());
    connectionProgressPane.managedProperty().bind(connectionProgressPane.visibleProperty());
    connectionProgressPane.visibleProperty().bind(contentPane.visibleProperty().not());

    searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
      if (Validator.isInt(newValue)) {
        ratingTable.scrollTo(Integer.parseInt(newValue) - 1);
      } else {
        LeaderboardEntry foundPlayer = null;
        for (LeaderboardEntry leaderboardEntry : ratingTable.getItems()) {
          if (leaderboardEntry.getUsername().toLowerCase().startsWith(newValue.toLowerCase())) {
            foundPlayer = leaderboardEntry;
            break;
          }
        }
        if (foundPlayer == null) {
          for (LeaderboardEntry leaderboardEntry : ratingTable.getItems()) {
            if (leaderboardEntry.getUsername().toLowerCase().contains(newValue.toLowerCase())) {
              foundPlayer = leaderboardEntry;
              break;
            }
          }
        }
        if (foundPlayer != null) {
          ratingTable.scrollTo(foundPlayer);
          ratingTable.getSelectionModel().select(foundPlayer);
        } else {
          ratingTable.getSelectionModel().select(null);
        }
      }
    });
  }

  @NotNull
  private StringConverter<Leaderboard> leaderboardStringConverter() {
    return new StringConverter<>() {
      @Override
      public String toString(Leaderboard leaderboard) {
        return i18n.getWithDefault(leaderboard.getTechnicalName(), leaderboard.getNameKey());
      }

      @Override
      public Leaderboard fromString(String string) {
        return null;
      }
    };
  }

  public void onLeaderboardSelected() {
    contentPane.setVisible(false);
    searchTextField.clear();
    if (usernamesAutoCompletion != null) {
      usernamesAutoCompletion.dispose();
    }
    leaderboardService.getEntries(leaderboardComboBox.getValue()).thenAccept(leaderboardEntryBeans -> {
      ratingTable.setItems(observableList(leaderboardEntryBeans));
      usernamesAutoCompletion = TextFields.bindAutoCompletion(searchTextField,
          leaderboardEntryBeans.stream().map(LeaderboardEntry::getUsername).collect(Collectors.toList()));
      usernamesAutoCompletion.setDelay(0);
      contentPane.setVisible(true);
    }).exceptionally(throwable -> {
      contentPane.setVisible(false);
      log.warn("Error while loading leaderboard entries", throwable);
      notificationService.addImmediateErrorNotification(throwable, "leaderboard.failedToLoad");
      return null;
    });
  }

  public Node getRoot() {
    return leaderboardRoot;
  }

  public void openContextMenu(ContextMenuEvent event) {
    int index = ratingTable.getSelectionModel().selectedIndexProperty().get();
    String userName = ratingTable.getItems().get(index).getUsername();
    playerService.getPlayerByName(userName)
        .thenAccept(optionalPlayer -> {
          if (optionalPlayer.isPresent()) JavaFxUtil.runLater(() -> {
            ContextMenu contextMenu = new ContextMenu();

            MenuItem userInfoMenuItem = new MenuItem(i18n.get("chat.userContext.userInfo"));
            userInfoMenuItem.setOnAction(e -> showUserInfo(optionalPlayer.get()));
            contextMenu.getItems().add(userInfoMenuItem);

            //MenuItem viewReplaysMenuItem = new MenuItem(i18n.get("chat.userContext.viewReplays"));
            //viewReplaysMenuItem.setOnAction(e -> showUserReplays(optionalPlayer.get()));
            //contextMenu.getItems().add(viewReplaysMenuItem);

            contextMenu.show(this.getRoot().getScene().getWindow(), event.getScreenX(), event.getScreenY());
          });
        });
  }

  public void showUserInfo(Player player) {
    UserInfoWindowController userInfoWindowController = uiService.loadFxml("theme/user_info_window.fxml");
    userInfoWindowController.setPlayer(player);
    userInfoWindowController.setOwnerWindow(this.getRoot().getScene().getWindow());
    userInfoWindowController.show();
  }

  public void showUserReplays(Player player) {
    eventBus.post(new ShowUserReplaysEvent(player.getId()));
  }

}
