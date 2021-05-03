package com.faforever.client.game;

import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.theme.UiService;
import com.google.common.annotations.VisibleForTesting;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
public class GamesTilesContainerController implements Controller<Node> {

  private final UiService uiService;
  private final ListChangeListener<Game> gameListChangeListener;
  private final PreferencesService preferencesService;
  private final GameService gameService;
  public FlowPane tiledFlowPane;
  public ScrollPane tiledScrollPane;
  private final ChangeListener<? super TilesSortingOrder> sortingListener;
  private final ObjectProperty<Game> selectedGame;
  private Comparator<Node> appliedComparator;
  @VisibleForTesting
  Map<Integer, GameTileController> uidToGameCard;
  private GameTooltipController gameTooltipController;
  private Tooltip tooltip;

  public GamesTilesContainerController(UiService uiService, PreferencesService preferencesService, GameService gameService) {
    this.uiService = uiService;
    this.preferencesService = preferencesService;
    this.gameService = gameService;
    selectedGame = new SimpleObjectProperty<>();

    sortingListener = (observable, oldValue, newValue) -> {
      if (newValue == null) {
        return;
      }
      preferencesService.getPreferences().setGameTileSortingOrder(newValue);
      preferencesService.storeInBackground();
      appliedComparator = newValue.getComparator();
      sortNodes();
    };

    gameListChangeListener = change -> {
      JavaFxUtil.assertApplicationThread();
        while (change.next()) {
          change.getRemoved().forEach(gameInfoBean -> removeGameCard(gameInfoBean));
          change.getAddedSubList().forEach(GamesTilesContainerController.this::addGameCard);
          sortNodes();
        }
    };

    gameService.getCurrentGameStatusProperty().addListener((obs,newValue,oldValue) -> selectCurrentGame());
    gameService.getCurrentGameProperty().addListener((obs,newValue,oldValue) -> selectCurrentGame());
  }

  private void sortNodes() {
    ObservableList<Node> sortedChildren = tiledFlowPane.getChildren().sorted(appliedComparator);

    // current game is always first
    Game currentGame = gameService.getCurrentGame();
    if (currentGame != null) {
      Stream<Node> withPlayer = sortedChildren.stream().filter((o) -> currentGame.getId() == ((Game) o.getUserData()).getId());
      Stream<Node> withoutPlayer = sortedChildren.stream().filter((o) -> currentGame.getId() != ((Game) o.getUserData()).getId());
      List<Node> sor = Stream.concat(withPlayer, withoutPlayer).collect(Collectors.toCollection(ArrayList::new));
      tiledFlowPane.getChildren().setAll(sor);
    }
    else {
      tiledFlowPane.getChildren().setAll(sortedChildren);
    }
  }

  public void initialize() {
    gameTooltipController = uiService.loadFxml("theme/play/game_tooltip.fxml");
    tooltip = JavaFxUtil.createCustomTooltip(gameTooltipController.getRoot());
    tooltip.showingProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue) {
        gameTooltipController.displayGame();
      } else {
        gameTooltipController.setGame(null);
      }
    });

    JavaFxUtil.fixScrollSpeed(tiledScrollPane);
    selectCurrentGame();
  }

  ReadOnlyObjectProperty<Game> selectedGameProperty() {
    return this.selectedGame;
  }

  @VisibleForTesting
  void createTiledFlowPane(ObservableList<Game> games, ComboBox<TilesSortingOrder> choseSortingTypeChoiceBox) {
    JavaFxUtil.assertApplicationThread();
    initializeChoiceBox(choseSortingTypeChoiceBox);
    uidToGameCard = new HashMap<>();

    //No lock is needed here because game updates are always done on the Application thread
    games.forEach(this::addGameCard);
    JavaFxUtil.addListener(games, new WeakListChangeListener<>(gameListChangeListener));

    selectCurrentGame();
    sortNodes();
  }

  private void initializeChoiceBox(ComboBox<TilesSortingOrder> sortingTypeChoiceBox) {
    sortingTypeChoiceBox.setVisible(true);
    sortingTypeChoiceBox.getSelectionModel().selectedItemProperty().addListener(new WeakChangeListener<>(sortingListener));
    sortingTypeChoiceBox.getSelectionModel().select(preferencesService.getPreferences().getGameTileSortingOrder());
  }

  private void selectFirstGame() {
    ObservableList<Node> cards = tiledFlowPane.getChildren();
    if (!cards.isEmpty()) {
      selectedGame.set((Game) cards.get(0).getUserData());
    }
  }

  private void selectCurrentGame() {
    ObservableList<Node> cards = tiledFlowPane.getChildren();
    Game currentGame = gameService.getCurrentGame();
    if (currentGame != null && !cards.isEmpty()) {
      Platform.runLater(() -> {
        selectedGame.set(currentGame);
      });
    }
    else {
      selectFirstGame();
    }
  }

  private void addGameCard(Game game) {
    GameTileController gameTileController = uiService.loadFxml("theme/play/game_card.fxml");
    gameTileController.setGame(game);
    gameTileController.setOnSelectedListener(selection -> selectedGame.set(selection));

    Node root = gameTileController.getRoot();
    root.setUserData(game);
    tiledFlowPane.getChildren().add(root);
    uidToGameCard.put(game.getId(), gameTileController);

    root.setOnMouseEntered(event -> {
      gameTooltipController.setGame(game);
      if (tooltip.isShowing()) {
        gameTooltipController.displayGame();
      }
    });
    Tooltip.install(root, tooltip);
  }

  private void removeGameCard(Game game) {
    GameTileController gameTileController = uidToGameCard.remove(game.getId());
    gameTileController.sever();
    Node card = gameTileController.getRoot();

    if (card != null) {
      Tooltip.uninstall(card, tooltip);
      boolean remove = tiledFlowPane.getChildren().remove(card);
      if (!remove) {
        log.error("Tried to remove game tile that did not exist in UI.");
      }
    } else {
      log.error("Tried to remove game tile that did not exist.");
    }
  }

  public Node getRoot() {
    return tiledScrollPane;
  }

  public enum TilesSortingOrder {
    PLAYER_DES(Comparator.comparingInt(o -> ((Game) o.getUserData()).getNumPlayers()), true, "tiles.comparator.playersDescending"),
    PLAYER_ASC(Comparator.comparingInt(o -> ((Game) o.getUserData()).getNumPlayers()), false, "tiles.comparator.playersAscending"),
    AVG_RATING_DES(Comparator.comparingDouble(o -> ((Game) o.getUserData()).getAverageRating()), true, "tiles.comparator.averageRatingDescending"),
    AVG_RATING_ASC(Comparator.comparingDouble(o -> ((Game) o.getUserData()).getAverageRating()), false, "tiles.comparator.averageRatingAscending"),
    NAME_DES(Comparator.comparing(o -> ((Game) o.getUserData()).getTitle().toLowerCase(Locale.US)), true, "tiles.comparator.nameDescending"),
    NAME_ASC(Comparator.comparing(o -> ((Game) o.getUserData()).getTitle().toLowerCase(Locale.US)), false, "tiles.comparator.nameAscending");

    @Getter
    private final Comparator<Node> comparator;
    @Getter
    private final String displayNameKey;

    TilesSortingOrder(Comparator<Node> comparator, boolean reversed, String displayNameKey) {
      this.displayNameKey = displayNameKey;
      this.comparator = reversed ? comparator.reversed() : comparator;
    }
  }
}
