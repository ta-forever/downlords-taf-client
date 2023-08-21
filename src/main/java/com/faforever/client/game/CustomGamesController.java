package com.faforever.client.game;

import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.GamesTilesContainerController.TilesSortingOrder;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.HostGameEvent;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.domain.GameType;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.dialog.Dialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class CustomGamesController extends AbstractViewController<Node> {

  private static final Predicate<Game> CUSTOM_GAMES_PREDICATE = gameInfoBean ->
      (gameInfoBean.getGameType() != GameType.MATCHMAKER || gameInfoBean.isInProgress()) &&
          //(gameInfoBean.getReplayDelaySeconds() >= 0 || gameInfoBean.isOpen()) &&
          (gameInfoBean.getMapArchiveName() != null);

  private final UiService uiService;
  private final GameService gameService;
  private final PreferencesService preferencesService;
  private final EventBus eventBus;
  private final I18n i18n;

  private GamesTableController gamesTableController;

  public ToggleButton tableButton;
  public ToggleButton tilesButton;
  public ToggleGroup viewToggleGroup;
  public Button createGameButton;
  public Pane gameViewContainer;
  public StackPane gamesRoot;
  public ComboBox<TilesSortingOrder> chooseSortingTypeChoiceBox;
  public StackPane createGameDialogRoot;

  @VisibleForTesting
  FilteredList<Game> filteredItems;

  public CheckBox showPasswordProtectedGamesCheckBox;
  private final ChangeListener<Boolean> filterConditionsChangedListener = (observable, oldValue, newValue) -> updateFilteredItems();
  private GamesTilesContainerController gamesTilesContainerController;

  private Dialog createGameDialog;
  private CreateGameController createGameController;
  private Consumer<Game> onSelectedListener;

  public static CustomGamesController getController(Node node) {
    Object controller;
    do {
      controller = node.getUserData();
      node = node.getParent();
    } while (controller == null && node != null);
    return (CustomGamesController) controller;
  }

  public CustomGamesController(UiService uiService, GameService gameService, PreferencesService preferencesService,
                               EventBus eventBus, I18n i18n) {
    this.uiService = uiService;
    this.gameService = gameService;
    this.preferencesService = preferencesService;
    this.eventBus = eventBus;
    this.i18n = i18n;
  }

  public void setCreateGameDialogRoot(StackPane root) {
    this.createGameDialogRoot = root;
  }

  public void initialize() {
    this.createGameDialogRoot = this.gamesRoot;
    gameService.runningGameUidProperty().addListener((obs,oldValue,newValue) -> JavaFxUtil.runLater(() ->
        createGameButton.setDisable(newValue != null && newValue.longValue() > 0)));

    getRoot().sceneProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue == null) {
        createGameButton.disableProperty().unbind();
      }
    });

    chooseSortingTypeChoiceBox.setVisible(false);
    chooseSortingTypeChoiceBox.managedProperty().bind(chooseSortingTypeChoiceBox.visibleProperty());
    chooseSortingTypeChoiceBox.getItems().addAll(TilesSortingOrder.values());
    chooseSortingTypeChoiceBox.setConverter(new StringConverter<>() {
      @Override
      public String toString(TilesSortingOrder tilesSortingOrder) {
        return tilesSortingOrder == null ? "null" : i18n.get(tilesSortingOrder.getDisplayNameKey());
      }

      @Override
      public TilesSortingOrder fromString(String string) {
        throw new UnsupportedOperationException("Not supported");
      }
    });

    JavaFxUtil.bindBidirectional(showPasswordProtectedGamesCheckBox.selectedProperty(), preferencesService.getPreferences().showPasswordProtectedGamesProperty());

    ObservableList<Game> games = gameService.getGames();
    filteredItems = new FilteredList<>(games, getGamePredicate());
    updateFilteredItems();

    JavaFxUtil.addListener(preferencesService.getPreferences().showPasswordProtectedGamesProperty(), new WeakChangeListener<>(filterConditionsChangedListener));

    if (tilesButton.getId().equals(preferencesService.getPreferences().getGamesViewMode())) {
      viewToggleGroup.selectToggle(tilesButton);
      tilesButton.getOnAction().handle(null);
    } else {
      viewToggleGroup.selectToggle(tableButton);
      tableButton.getOnAction().handle(null);
    }
    viewToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue == null) {
        if (oldValue != null) {
          viewToggleGroup.selectToggle(oldValue);
        } else {
          viewToggleGroup.selectToggle(viewToggleGroup.getToggles().get(0));
        }
        return;
      }
      preferencesService.getPreferences().setGamesViewMode(((ToggleButton) newValue).getId());
      preferencesService.storeInBackground();
    });

    eventBus.register(this);
  }

  public void setOnSelectedListener(Consumer<Game> onSelectedListener) {
    this.onSelectedListener = onSelectedListener;
    if (this.gamesTilesContainerController != null) {
      this.gamesTilesContainerController.setOnSelectedListener(onSelectedListener);
    }
    if (this.gamesTableController != null) {
      this.gamesTableController.setOnSelectedListener(onSelectedListener);
    }
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    if (navigateEvent instanceof HostGameEvent) {
      HostGameEvent hostGameEvent = (HostGameEvent) navigateEvent;
      onCreateGame(hostGameEvent.getMapFolderName(), hostGameEvent.getContextGame());
    }
    updateFilteredItems();
  }

  private void updateFilteredItems() {
    preferencesService.storeInBackground();
    filteredItems.setPredicate(getGamePredicate());
  }

  private Predicate<Game> getGamePredicate() {
    boolean showPasswordProtectedGames = showPasswordProtectedGamesCheckBox.isSelected();
    return CUSTOM_GAMES_PREDICATE.and(gameInfoBean -> (showPasswordProtectedGames || !gameInfoBean.isPasswordProtected()));
  }

  public void onCreateGameButtonClicked() {
    onCreateGame(null, null);
  }

  private void onCreateGame(@Nullable String mapFolderName, @Nullable Game contextGame) {
    if (createGameController == null) {
      createGameController = uiService.loadFxml("theme/play/create_game.fxml");
    }
    createGameController.setContextGame(contextGame);

    if (mapFolderName != null) {
      createGameController.resetMapSearch();
    }

    if (mapFolderName != null && !createGameController.selectMap(mapFolderName)) {
      log.warn("Map with folder name '{}' could not be found in map list", mapFolderName);
    }

    Pane root = createGameController.getRoot();

    String title = i18n.get("games.create");
    switch(createGameController.getInteractionLevel()) {
      case "UPDATE":
        title = i18n.get("games.changeSettings");
        break;
      case "UPDATE_GW":
        title = i18n.get("games.changeSettings");
        break;
      case "BROWSE":
        title = i18n.get("games.browseMaps");
        break;
    }
    createGameDialog = uiService.showInDialog(createGameDialogRoot, root, title);
    createGameController.setOnCloseButtonClickedListener(() -> createGameDialog.close());
    createGameController.setGameTitle();
    root.requestFocus();
  }

  public Node getRoot() {
    return gamesRoot;
  }

  public void onTableButtonClicked() {
    if (gamesTableController == null) {
      gamesTableController = uiService.loadFxml("theme/play/games_table.fxml");
      gamesTableController.setOnSelectedListener(this.onSelectedListener);
      gamesTableController.initializeGameTable(filteredItems);
      Node root = gamesTableController.getRoot();
      populateContainer(root);
    }
    else {
      gameViewContainer.getChildren().setAll(gamesTableController.getRoot());
    }
  }

  private void populateContainer(Node root) {
    chooseSortingTypeChoiceBox.setVisible(false);
    gameViewContainer.getChildren().setAll(root);
    AnchorPane.setBottomAnchor(root, 0d);
    AnchorPane.setLeftAnchor(root, 0d);
    AnchorPane.setRightAnchor(root, 0d);
    AnchorPane.setTopAnchor(root, 0d);
  }

  public void onTilesButtonClicked() {
    if (gamesTilesContainerController == null) {
      gamesTilesContainerController = uiService.loadFxml("theme/play/games_tiles_container.fxml");
      Node root = gamesTilesContainerController.getRoot();
      populateContainer(root);
      gamesTilesContainerController.setOnSelectedListener(this.onSelectedListener);
      gamesTilesContainerController.createTiledFlowPane(filteredItems, chooseSortingTypeChoiceBox);
    }
    else {
      gameViewContainer.getChildren().setAll(gamesTilesContainerController.getRoot());
    }
  }

  @VisibleForTesting
  void setFilteredList(ObservableList<Game> games) {
    filteredItems = new FilteredList<>(games, s -> true);
  }

  @Override
  public void onHide() {
    // Hide all games to free up memory
    filteredItems.setPredicate(game -> false);
  }
}
