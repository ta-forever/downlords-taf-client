package com.faforever.client.game;

import com.faforever.client.fx.Controller;
import com.faforever.client.fx.DecimalCell;
import com.faforever.client.fx.IconCell;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.StringCell;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.mod.ModService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.remote.domain.RatingRange;
import com.faforever.client.theme.UiService;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SortEvent;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.util.Pair;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
@RequiredArgsConstructor
public class GamesTableController implements Controller<Node> {

  private final MapService mapService;
  private final JoinGameHelper joinGameHelper;
  private final GameService gameService;
  private final ModService modService;
  private final I18n i18n;
  private final UiService uiService;
  private final PreferencesService preferencesService;
  public TableView<Game> gamesTable;
  public TableColumn<Game, Image> mapPreviewColumn;
  public TableColumn<Game, String> modsColumn;
  public TableColumn<Game, GameStatus> statusColumn;
  public TableColumn<Game, String> gameTitleColumn;
  public TableColumn<Game, PlayerFill> playersColumn;
  public TableColumn<Game, Number> averageRatingColumn;
  public TableColumn<Game, RatingRange> ratingRangeColumn;
  public TableColumn<Game, String> hostColumn;
  public TableColumn<Game, Boolean> passwordProtectionColumn;
  public TableColumn<Game, String> coopMissionName;
  private final ChangeListener<Boolean> showPasswordProtectedGamesChangedListener = (observable, oldValue, newValue) -> passwordProtectionColumn.setVisible(newValue);
  private GameTooltipController gameTooltipController;
  private Tooltip tooltip;
  private Consumer<Game> onSelectedListener;

  public Node getRoot() {
    return gamesTable;
  }

  public void setOnSelectedListener(Consumer<Game> onSelectedListener) {
    this.onSelectedListener = onSelectedListener;
  }

  public void initializeGameTable(ObservableList<Game> games) {
    initializeGameTable(games, null, true);
  }

  public void initializeGameTable(ObservableList<Game> games, Function<String, String> coopMissionNameProvider, boolean listenToFilterPreferences) {

    gameTooltipController = uiService.loadFxml("theme/play/game_tooltip.fxml");
    tooltip = JavaFxUtil.createCustomTooltip(gameTooltipController.getRoot());
    tooltip.showingProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue) {
        gameTooltipController.displayGame();
      } else {
        gameTooltipController.setGame(null);
      }
    });

    SortedList<Game> sortedList = new SortedList<>(games);
    sortedList.comparatorProperty().bind(gamesTable.comparatorProperty());
    gamesTable.setPlaceholder(new Label(i18n.get("games.noGamesAvailable")));
    gamesTable.setRowFactory(param1 -> gamesRowFactory());
    gamesTable.setItems(sortedList);

    applyLastSorting(gamesTable);
    gamesTable.setOnSort(this::onColumnSorted);

    JavaFxUtil.addListener(sortedList, (Observable observable) -> JavaFxUtil.runLater(() -> selectCurrentGame()));
    selectCurrentGame();

    passwordProtectionColumn.setCellValueFactory(param -> param.getValue().passwordProtectedProperty());
    passwordProtectionColumn.setCellFactory(param -> passwordIndicatorColumn());
    passwordProtectionColumn.setVisible(preferencesService.getPreferences().isShowPasswordProtectedGames());
    mapPreviewColumn.setCellFactory(param -> new MapPreviewTableCell(uiService));

    mapPreviewColumn.setCellValueFactory(param -> Bindings.createObjectBinding(
        () -> mapService
            .loadPreview(param.getValue().getFeaturedMod(), param.getValue().getMapName(), PreviewType.MINI, 10),
        param.getValue().mapNameProperty()
    ));

    gameTitleColumn.setCellValueFactory(param -> param.getValue().titleProperty());
    gameTitleColumn.setCellFactory(param -> new StringCell<>(title -> title));
    playersColumn.setCellValueFactory(param -> Bindings.createObjectBinding(
        () -> new PlayerFill(param.getValue().getNumPlayers(), param.getValue().getMaxPlayers()),
        param.getValue().numPlayersProperty(), param.getValue().maxPlayersProperty())
    );
    playersColumn.setCellFactory(param -> playersCell());
    statusColumn.setCellValueFactory(param -> param.getValue().statusProperty());
    ratingRangeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(new RatingRange(param.getValue().getMinRating(), param.getValue().getMaxRating())));
    ratingRangeColumn.setCellFactory(param -> ratingTableCell());
    hostColumn.setCellValueFactory(param -> param.getValue().hostProperty());
    hostColumn.setCellFactory(param -> new StringCell<>(String::toString));
    modsColumn.setCellValueFactory(this::modCell);
    modsColumn.setCellFactory(param -> new StringCell<>(String::toString));

    coopMissionName.setVisible(coopMissionNameProvider != null);

    if (averageRatingColumn != null) {
      averageRatingColumn.setCellValueFactory(param -> param.getValue().averageRatingProperty());
      averageRatingColumn.setCellFactory(param -> new DecimalCell<>(
          new DecimalFormat("0"),
          number -> Math.round(number.doubleValue() / 100.0) * 100.0)
      );
    }

    if (coopMissionNameProvider != null) {
      coopMissionName.setCellFactory(param -> new StringCell<>(name -> name));
      coopMissionName.setCellValueFactory(param -> new SimpleObjectProperty<>(coopMissionNameProvider.apply(param.getValue().getMapName())));
    }

    gamesTable.setOnMousePressed(e -> onSelectedListener.accept(gamesTable.getSelectionModel().selectedItemProperty().get()));

    gamesTable.setOnMouseReleased(e -> {
      Game currentGame = gameService.getCurrentGame();
      Game autoJoinGame = gameService.getAutoJoinRequestedGameProperty().get();
      if (currentGame != null) {
        onSelectedListener.accept(currentGame);
        gamesTable.getSelectionModel().select(currentGame);
      }
      else if (autoJoinGame != null) {
        onSelectedListener.accept(autoJoinGame);
        gamesTable.getSelectionModel().select(autoJoinGame);
      }
    });

    //bindings do not work as that interferes with some bidirectional bindings in the TableView itself
    if (listenToFilterPreferences && coopMissionNameProvider == null) {
      passwordProtectionColumn.setVisible(preferencesService.getPreferences().isShowPasswordProtectedGames());
      JavaFxUtil.addListener(preferencesService.getPreferences().showPasswordProtectedGamesProperty(), new WeakChangeListener<>(showPasswordProtectedGamesChangedListener));
    }
  }

  private void applyLastSorting(TableView<Game> gamesTable) {
    final Map<String, SortType> lookup = new HashMap<>();
    final ObservableList<TableColumn<Game, ?>> sortOrder = gamesTable.getSortOrder();
    preferencesService.getPreferences().getGameListSorting().forEach(sorting -> lookup.put(sorting.getKey(), sorting.getValue()));
    sortOrder.clear();
    gamesTable.getColumns().forEach(gameTableColumn -> {
      if (lookup.containsKey(gameTableColumn.getId())) {
        gameTableColumn.setSortType(lookup.get(gameTableColumn.getId()));
        sortOrder.add(gameTableColumn);
      }
    });
  }

  private void onColumnSorted(@NotNull SortEvent<TableView<Game>> event) {
    List<Pair<String, SortType>> sorters = event.getSource().getSortOrder()
        .stream()
        .map(column -> new Pair<>(column.getId(), column.getSortType()))
        .collect(Collectors.toList());

    preferencesService.getPreferences().getGameListSorting().setAll(sorters);
    preferencesService.storeInBackground();
  }

  @NotNull
  private ObservableValue<String> modCell(CellDataFeatures<Game, String> param) {
    String modTechnical = param.getValue().getFeaturedMod();
    String displayName = modService.getFeaturedModDisplayName(modTechnical);
    return new SimpleStringProperty(displayName);
  }

  private void selectFirstGame() {
    TableView.TableViewSelectionModel<Game> selectionModel = gamesTable.getSelectionModel();
    if (selectionModel.getSelectedItem() == null && !gamesTable.getItems().isEmpty()) {
      JavaFxUtil.runLater(() -> selectionModel.select(0));
    }
  }

  private void selectCurrentGame() {
    TableView.TableViewSelectionModel<Game> selectionModel = gamesTable.getSelectionModel();
    Game currentGame = gameService.getCurrentGame();
    if (currentGame != null && gamesTable.getItems().contains(currentGame)) {
      selectionModel.select(currentGame);
    }
    else {
      selectFirstGame();
    }
  }

  @NotNull
  private TableRow<Game> gamesRowFactory() {
    TableRow<Game> row = new TableRow<>() {
      @Override
      protected void updateItem(Game game, boolean empty) {
        super.updateItem(game, empty);
        if (empty || game == null) {
          setTooltip(null);
        } else {
          setTooltip(tooltip);
        }
      }
    };
    row.setOnMouseClicked(event -> {
      if (event.getClickCount() == 2) {
        Game game = row.getItem();
        joinGameHelper.join(game);
      }
    });
    row.setOnMouseEntered(event -> {
      if (row.getItem() == null) {
        return;
      }
      Game game = row.getItem();
      gameTooltipController.setGame(game);
      if (tooltip.isShowing()) {
        gameTooltipController.displayGame();
      }
    });
    return row;
  }

  private TableCell<Game, Boolean> passwordIndicatorColumn() {
    return new IconCell<>(
        isPasswordProtected -> isPasswordProtected ? "lock-icon" : "");
  }

  private TableCell<Game, PlayerFill> playersCell() {
    return new StringCell<>(playerFill -> i18n.get("game.players.format",
        playerFill.getPlayers(), playerFill.getMaxPlayers()));
  }

  private TableCell<Game, RatingRange> ratingTableCell() {
    return new StringCell<>(ratingRange -> {
      if (ratingRange.getMin() == null && ratingRange.getMax() == null) {
        return "";
      }

      if (ratingRange.getMin() != null && ratingRange.getMax() != null) {
        return i18n.get("game.ratingFormat.minMax", ratingRange.getMin(), ratingRange.getMax());
      }

      if (ratingRange.getMin() != null) {
        return i18n.get("game.ratingFormat.minOnly", ratingRange.getMin());
      }

      return i18n.get("game.ratingFormat.maxOnly", ratingRange.getMax());
    });
  }
}
