package com.faforever.client.leaderboard;

import com.faforever.client.chat.UserInfoWindowController;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.StringCell;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.ShowUserReplaysEvent;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.ModService;
import com.faforever.client.remote.FafService;
import com.faforever.client.teammatchmaking.MatchmakingQueue;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.Validator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
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
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static javafx.collections.FXCollections.observableList;

import javafx.collections.FXCollections;


@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class LeaderboardsController extends AbstractViewController<Node> {

  private final LeaderboardService leaderboardService;
  private final NotificationService notificationService;
  private final ModService modService;
  private final FafService fafService;
  private final UiService uiService;
  private final PlayerService playerService;
  private final PreferencesService preferencesService;
  private final EventBus eventBus;
  private final I18n i18n;
  public Pane leaderboardRoot;
  public TableColumn<LeaderboardEntry, Number> rankColumn;
  public TableColumn<LeaderboardEntry, String> nameColumn;
  public TableColumn<LeaderboardEntry, Number> ratingColumn;
  public TableColumn<LeaderboardEntry, Number> gamesPlayedColumn;
  public TableColumn<LeaderboardEntry, Number> winRateColumn;
  public TableColumn<LeaderboardEntry, String> allResultsColumn;
  public TableColumn<LeaderboardEntry, String> recentResultsColumn;
  public TableColumn<LeaderboardEntry, Number> streakColumn;
  public TableColumn<LeaderboardEntry, Number> bestStreakColumn;
  public TableView<LeaderboardEntry> ratingTable;
  public ComboBox<Leaderboard> leaderboardComboBox;
  public ComboBox<String> modComboBox;
  public TextField searchTextField;
  public Pane connectionProgressPane;
  public Pane contentPane;
  public CheckBox friendsOnlyCheckBox;

  @VisibleForTesting
  protected AutoCompletionBinding<String> usernamesAutoCompletion;

  private List<Leaderboard> allLeaderboards;
  private Map<String, String> leaderboardToModTech = new HashMap<>();

  @Override
  public void initialize() {
    super.initialize();
    friendsOnlyCheckBox.setSelected(preferencesService.getPreferences().getLastLeaderboardFriendsOnlySelection());

    leaderboardService.getLeaderboards()
        .thenCombine(modService.getFeaturedMods(), (leaderboards, featuredMods) -> {
          Map<String, String> lbTechToModTech = new HashMap<>();
          leaderboards.stream()
              .filter(lb -> "global".equals(lb.getTechnicalName()))
              .forEach(lb -> lbTechToModTech.put("global", "global"));

          List<CompletableFuture<?>> queueFutures = new ArrayList<>();
          for (FeaturedMod mod : featuredMods) {
            String modTech = mod.getTechnicalName();
            queueFutures.add(
                fafService.getMatchmakerQueuesByMod(modTech).thenAccept(queues -> {
                  for (MatchmakingQueue q : queues) {
                    if (q.getLeaderboard() != null) {
                      String lbTech = q.getLeaderboard().getTechnicalName();
                      if (lbTech != null) {
                        lbTechToModTech.put(lbTech, modTech);
                      }
                    }
                  }
                })
            );
          }

          CompletableFuture.allOf(queueFutures.toArray(new CompletableFuture[0]))
              .thenRun(() -> JavaFxUtil.runLater(() ->
                  initialiseModAndLeaderboardComboBoxes(leaderboards, featuredMods, lbTechToModTech)
              ));
          return null;
        });

    rankColumn.setCellValueFactory(param -> new SimpleIntegerProperty(ratingTable.getItems().indexOf(param.getValue()) + 1));
    rankColumn.setCellFactory(param -> new StringCell<>(rank -> i18n.number(rank.intValue())));

    nameColumn.setCellValueFactory(param -> param.getValue().usernameProperty());
    nameColumn.setCellFactory(param -> new StringCell<>(name -> name));

    winRateColumn.setCellValueFactory(param -> new SimpleFloatProperty(param.getValue().getWinRate()));
    winRateColumn.setCellFactory(param -> new StringCell<>(number -> i18n.get("percentage", number.floatValue() * 100)));

    recentResultsColumn.setCellValueFactory(param -> param.getValue().recentResultsProperty());
    recentResultsColumn.setCellFactory(param -> new StringCell<>(rate -> rate));

    allResultsColumn.setCellValueFactory(param -> param.getValue().allResultsProperty());
    allResultsColumn.setCellFactory(param -> new StringCell<>(results -> results));

    streakColumn.setCellValueFactory(param -> param.getValue().streakProperty());
    streakColumn.setCellFactory(param -> new StringCell<>(streak -> i18n.number(streak.intValue())));

    bestStreakColumn.setCellValueFactory(param -> param.getValue().bestStreakProperty());
    bestStreakColumn.setCellFactory(param -> new StringCell<>(streak -> i18n.number(streak.intValue())));

    gamesPlayedColumn.setCellValueFactory(param -> param.getValue().totalGamesProperty());
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

  private void initialiseModAndLeaderboardComboBoxes(List<Leaderboard> leaderboards, List<FeaturedMod> featuredMods, Map<String, String> lbTechToModTech) {
    allLeaderboards = leaderboards;
    this.leaderboardToModTech = (lbTechToModTech != null) ? lbTechToModTech : new HashMap<>();

    Map<String, String> modDisplayNames = featuredMods.stream()
        .collect(Collectors.toMap(FeaturedMod::getTechnicalName,
            fm -> fm.getDisplayName() != null ? fm.getDisplayName() : fm.getTechnicalName(),
            (a, b) -> a));

    Set<String> modTechs = leaderboards.stream()
        .map(lb -> lbTechToModTech.getOrDefault(lb.getTechnicalName(), extractModFromLeaderboard(lb.getTechnicalName())))
        .collect(Collectors.toCollection(LinkedHashSet::new));

    List<String> modList = new ArrayList<>();
    if (modTechs.contains("global")) {
      modList.add("global");
      modTechs.remove("global");
    }
    modList.addAll(modTechs);

    modComboBox.setConverter(new StringConverter<>() {
      @Override
      public String toString(String modTech) {
        if (modTech == null) {
          return "";
        }
        if ("global".equals(modTech)) {
          return "Global";
        }
        return modDisplayNames.getOrDefault(modTech, modTech);
      }

      @Override
      public String fromString(String string) {
        return null;
      }
    });

    modComboBox.setItems(FXCollections.observableArrayList(modList));

    leaderboardComboBox.setConverter(leaderboardStringConverter());

    modComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue != null) {
        preferencesService.getPreferences().setLastLeaderboardModSelection(newValue);
        preferencesService.storeInBackground();
        updateLeaderboardItemsForMod(newValue);
      }
    });

    // initial select will trigger the listener above to populate lb combo + load
    String lastMod = preferencesService.getPreferences().getLastLeaderboardModSelection();
    if (lastMod != null && modList.contains(lastMod)) {
      modComboBox.getSelectionModel().select(lastMod);
    } else if (!modList.isEmpty()) {
      modComboBox.getSelectionModel().selectFirst();
    } else {
      // fallback
      leaderboardComboBox.setItems(FXCollections.observableArrayList(leaderboards));
      selectAppropriateLeaderboard();
      onLeaderboardSelected();
    }
  }

  private String extractModFromLeaderboard(String technicalName) {
    if (technicalName == null) {
      return "";
    }
    int idx = technicalName.lastIndexOf('_');
    return idx >= 0 ? technicalName.substring(idx + 1) : technicalName;
  }

  private void updateLeaderboardItemsForMod(String modTech) {
    List<Leaderboard> filtered = allLeaderboards.stream()
        .filter(lb -> {
          String resolved = leaderboardToModTech.getOrDefault(lb.getTechnicalName(), extractModFromLeaderboard(lb.getTechnicalName()));
          return resolved.equals(modTech);
        })
        .collect(Collectors.toList());
    leaderboardComboBox.setItems(FXCollections.observableArrayList(filtered));
    selectAppropriateLeaderboard();
    onLeaderboardSelected();
  }

  // kept for fallback in other places if needed, but main filter uses the map now
  private boolean matchesMod(String technicalName, String modTech) {
    if (technicalName == null || modTech == null) {
      return false;
    }
    if ("global".equals(modTech)) {
      return "global".equals(technicalName);
    }
    return technicalName.endsWith("_" + modTech) || technicalName.equals(modTech);
  }

  private void selectAppropriateLeaderboard() {
    leaderboardComboBox.getItems().stream()
        .filter(lbe -> lbe.getTechnicalName().equals(preferencesService.getPreferences().getLastLeaderboardSelection()))
        .findAny()
        .ifPresentOrElse(
            lbe -> leaderboardComboBox.getSelectionModel().select(lbe),
            () -> leaderboardComboBox.getSelectionModel().selectFirst());
  }

  @NotNull
  private StringConverter<Leaderboard> leaderboardStringConverter() {
    return new StringConverter<>() {
      @Override
      public String toString(Leaderboard leaderboard) {
        if (leaderboard != null) {
          return i18n.get(leaderboard.getNameKey());
        } else {
          return "<none>";
        }
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

    preferencesService.getPreferences().setLastLeaderboardSelection(leaderboardComboBox.getValue().getTechnicalName());
    preferencesService.getPreferences().setLastLeaderboardFriendsOnlySelection(friendsOnlyCheckBox.isSelected());
    preferencesService.storeInBackground();

    leaderboardService.getEntries(leaderboardComboBox.getValue()).thenAccept(leaderboardEntryBeans -> {
      if (friendsOnlyCheckBox.isSelected()) {
        leaderboardEntryBeans = leaderboardEntryBeans.stream()
            .filter(leaderboardEntry -> playerService.isFriend(leaderboardEntry.getUserId()))
            .collect(Collectors.toList());
      }
      List<LeaderboardEntry> finalLeaderboardEntryBeans = leaderboardEntryBeans;
      JavaFxUtil.runLater(() -> {
        ratingTable.setItems(observableList(finalLeaderboardEntryBeans));
        usernamesAutoCompletion = TextFields.bindAutoCompletion(
            searchTextField,
            finalLeaderboardEntryBeans.stream().map(LeaderboardEntry::getUsername).collect(Collectors.toList()))
        ;
        usernamesAutoCompletion.setDelay(0);
        contentPane.setVisible(true);
      });
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

            MenuItem viewReplaysMenuItem = new MenuItem(i18n.get("chat.userContext.viewReplays"));
            viewReplaysMenuItem.setOnAction(e -> showUserReplays(optionalPlayer.get()));
            contextMenu.getItems().add(viewReplaysMenuItem);

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
