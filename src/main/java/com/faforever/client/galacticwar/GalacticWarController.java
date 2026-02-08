package com.faforever.client.galacticwar;

import com.faforever.client.chat.UserInfoWindowController;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.io.DownloadService;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.ShowUserReplaysEvent;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.GalacticWarUpdateMessage;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.CompletableTask.Priority;
import com.faforever.client.task.TaskService;
import com.faforever.client.theme.UiService;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GalacticWarController extends AbstractViewController<Node> {

  final private ClientProperties clientProperties;
  final private PreferencesService preferencesService;
  final private TaskService taskService;
  final private DownloadService downloadService;
  final private UiService uiService;
  final private FafService fafService;
  final private GameService gameService;
  final private PlayerService playerService;
  private final EventBus eventBus;
  final private I18n i18n;

  public StackPane rootPane;
  public Label loadingIndicator;
  public ScrollPane galacticWarGraphContainer;
  public GalacticMapView galacticMapView;
  public VBox planetDetailContainer;
  public VBox leaderboardContainer;

  TableView<GwLeaderboardRow> leaderboardTable;

  private PlanetDetailController planetDetailController;

  @Override
  public void initialize() {
    planetDetailController = uiService.loadFxml("theme/galactic_war/planet_detail.fxml");
    planetDetailContainer.getChildren().add(planetDetailController.getRoot());
    fafService.addOnMessageListener(GalacticWarUpdateMessage.class, this::onGalacticWarUpdate);
    updateLatestState();
  }

  private void onGalacticWarUpdate(GalacticWarUpdateMessage newTadaReplayMessage) {
    updateLatestState();
    planetDetailController.setPlanet(null);
  }

  @Override
  public Node getRoot() {
    return rootPane;
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
  }

  public void updateLatestState() {
    JavaFxUtil.runLater(() -> {
      loadingIndicator.setVisible(true);
    });

    final Path targetPath = preferencesService.getCacheDirectory().resolve("galactic_war.json");
    taskService.submitTask(new CompletableTask<Void>(Priority.LOW) {
      protected Void call() {
        try {
          Files.deleteIfExists(targetPath);
          downloadService.downloadFile(
              new URL(clientProperties.getGalacticWar().getUrl()),
              targetPath, null);
        } catch (IOException e) {
          log.error("[updateLatestState] unable to retrieve Galactic War state: {}", e.getMessage());
        }
        return null;
      }
    }).getFuture().thenRun(() -> {
      try {
        final String styleSheetFile = uiService.getThemeFile("theme/galactic_war/smartgraph.css");
        final Scenario scenario = Scenario.fromFile(targetPath);
        galacticMapView = GalacticMapView.fromScenario(scenario, styleSheetFile, uiService);

        JavaFxUtil.runLater(() -> {
          populateLeaderboard(scenario);
          galacticMapView.setMousePressedConsumer(optional -> optional.ifPresent(planet -> {
            galacticMapView.setSelected(planet);
            planetDetailController.setPlanet(planet);
          }));

          galacticMapView.setMouseReleasedConsumer(optional -> {
            if (gameService.getCurrentGame() != null) {
              Game currentGame = gameService.getCurrentGame();
              if (currentGame != null) {
                String planetName = currentGame.getGalacticWarPlanetName();
                if (planetName != null) {
                  galacticMapView.getPlanetByName(planetName).ifPresent(planet -> {
                    galacticMapView.setSelected(planetName);
                    planetDetailController.setPlanet(planet);
                  });
                }
              }
            }
          });
          galacticWarGraphContainer.setContent(galacticMapView.getRoot());
        });
      } catch (Exception e) {
        log.error("[updateLatestState] Unable to load Galactic War state: {}", e.getMessage());
      }
    }).thenRun(() -> JavaFxUtil.runLater(() -> loadingIndicator.setVisible(false)));
  }

  public void resetView(ActionEvent actionEvent) {
    this.galacticMapView.resetView();
  }

  private float totalScore(Map<String, GwPlayerScore> scores) {
    return scores.values().stream()
        .map(s -> s != null ? s : GwPlayerScore.EMPTY_SCORE)
        .map(GwPlayerScore::getCumWinningScores)
        .max(Float::compare)
        .orElse(0.0f);
  }

  private GwRank rankForPlayerScore(GwPlayerScore score) {
    List<Integer> th = preferencesService.getClientRemoteConfiguration()
        .getGalacticWarRankThresholds();

    if (th == null) {
      return GwRank.PRIVATE;

    }

    th = th.stream()
        .sorted(java.util.Comparator.reverseOrder()) // sorts in descending order
        .toList();

    float metric = score.getCumWinningScores();
    if (metric >= th.get(0)) return GwRank.COMMANDER;
    if (metric >= th.get(1)) return GwRank.GENERAL;
    if (metric >= th.get(2)) return GwRank.COLONEL;
    if (metric >= th.get(3)) return GwRank.MAJOR;
    if (metric >= th.get(4)) return GwRank.CAPTAIN;
    if (metric >= th.get(5)) return GwRank.LIEUTENANT;
    if (metric >= th.get(6)) return GwRank.SERGEANT;
    if (metric >= th.get(7)) return GwRank.CORPORAL;
    return GwRank.PRIVATE;
  }

  private static final Map<String, Image> MEDAL_IMAGE_CACHE = new ConcurrentHashMap<>();

  private Node medalIcon(String factionName, GwRank rank) {
    factionName = factionName.toUpperCase();
    String iconPath = String.format(
        "images/ranks/RANK_%s%d.png",
        factionName,
        rank.getTier()
    );

    Image image = MEDAL_IMAGE_CACHE.computeIfAbsent(
        iconPath,
        path -> new Image(path, true)
    );

    ImageView icon = new ImageView(image);
    icon.setFitWidth(16);
    icon.setFitHeight(16);
    icon.setPreserveRatio(true);
    icon.setUserData(rank);
    return icon;
  }

  private <T> void setColumnWidthOptions(TableColumn<?, T> column, double width) {
    column.setMinWidth(width/2);
    column.setPrefWidth(width);
    column.setMaxWidth(width*2);
    column.setResizable(true);
  }

  private static final Map<GwRank, Tooltip> RANK_TOOLTIP_CACHE =
      new EnumMap<>(GwRank.class);

  private TableView<GwLeaderboardRow> createLeaderboardTable() {
    TableView<GwLeaderboardRow> table = new TableView<>();
    table.getStyleClass().add("leaderboard-table");
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

    TableColumn<GwLeaderboardRow, Integer> rankCol = new TableColumn<>("#");
    rankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));

    TableColumn<GwLeaderboardRow, Node> medalCol = new TableColumn<>("");
    medalCol.setCellValueFactory(data ->
        new ReadOnlyObjectWrapper<>(data.getValue().getMedal())
    );

    TableColumn<GwLeaderboardRow, String> playerCol = new TableColumn<>(i18n.get("leaderboard.column.name"));
    playerCol.setCellValueFactory(new PropertyValueFactory<>("playerName"));

    TableColumn<GwLeaderboardRow, Long> winScoreCol = new TableColumn<>("XP");
    winScoreCol.setCellValueFactory(new PropertyValueFactory<>("winScore"));

    TableColumn<GwLeaderboardRow, Integer> winsCol = new TableColumn<>(i18n.get("userInfo.wins"));
    winsCol.setCellValueFactory(new PropertyValueFactory<>("wins"));

    TableColumn<GwLeaderboardRow, Integer> lossesCol = new TableColumn<>(i18n.get("userInfo.losses"));
    lossesCol.setCellValueFactory(new PropertyValueFactory<>("losses"));

    //medalCol.setStyle("-fx-alignment: CENTER;");
    setColumnWidthOptions(rankCol, 32);
    setColumnWidthOptions(medalCol, 24);
    setColumnWidthOptions(playerCol, 130);
    setColumnWidthOptions(winScoreCol, 48);
    setColumnWidthOptions(winsCol, 48);
    setColumnWidthOptions(lossesCol, 48);

    medalCol.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(Node item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
          setGraphic(null);
          setTooltip(null);
        } else {
          setGraphic(item);

          GwRank rank = (GwRank) item.getUserData();
          if (rank != null) {
            setTooltip(RANK_TOOLTIP_CACHE.computeIfAbsent(
                rank,
                r -> new Tooltip(r.getDisplayName())
            ));
          } else {
            setTooltip(null);
          }
        }
      }
    });

    table.getColumns().addAll(
        rankCol,
        medalCol,
        playerCol,
        winScoreCol,
        winsCol,
        lossesCol
    );

    table.getColumns().forEach(c -> c.setReorderable(false));
    table.setOnContextMenuRequested(this::openContextMenu);
    return table;
  }

  private void populateLeaderboard(Scenario scenario) {
    leaderboardContainer.getChildren().clear();

    TableView<GwLeaderboardRow> table = createLeaderboardTable();
    leaderboardContainer.getChildren().add(table);

    final Map<Integer, Player> players = new HashMap<>();

    playerService.getPlayersByIds(scenario.getPlayers().keySet())
        .thenAccept(plyrs -> {
          plyrs.forEach(p -> players.put(p.getId(), p));

          JavaFxUtil.runLater(() -> {
            ObservableList<GwLeaderboardRow> rows = FXCollections.observableArrayList();

            final GalacticWarController gwController = this;
            AtomicInteger rankCounter = new AtomicInteger(1);

            scenario.getPlayers().entrySet().stream()
                .sorted((a, b) ->
                    Float.compare(
                        totalScore(b.getValue()),
                        totalScore(a.getValue())))
                .forEachOrdered(entry -> {

                  Integer playerId = entry.getKey();
                  if (!players.containsKey(playerId)) {
                    return;
                  }

                  Player player = players.get(playerId);
                  Map<String, GwPlayerScore> scores = entry.getValue();

                  Optional<Map.Entry<String, GwPlayerScore>> topEntry =
                      scores.entrySet().stream()
                          .max(java.util.Comparator.comparingInt(
                              e -> gwController.rankForPlayerScore(e.getValue()).getTier()));

                  if (topEntry.isEmpty()) {
                    return;
                  }

                  String factionName = topEntry.get().getKey();
                  GwPlayerScore score = topEntry.get().getValue();
                  GwRank gwRank = gwController.rankForPlayerScore(score);

                  rows.add(new GwLeaderboardRow(
                      rankCounter.getAndIncrement(),
                      player.getAlias(),
                      score.getWins(),
                      Math.round(score.getCumWinningScores()),
                      score.getLosses(),
                      Math.round(score.getCumLosingScores()),
                      medalIcon(factionName, gwRank)
                  ));
                });

            table.setItems(rows);
            this.leaderboardTable = table;
          });
        });
  }

  public void openContextMenu(ContextMenuEvent event) {
    int index = leaderboardTable.getSelectionModel().selectedIndexProperty().get();
    String userName = leaderboardTable.getItems().get(index).getPlayerName();
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
