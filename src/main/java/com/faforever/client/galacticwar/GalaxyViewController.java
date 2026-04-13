package com.faforever.client.galacticwar;

import com.faforever.client.chat.UserInfoWindowController;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.galacticwar.Scenario.FactionScoreRank;
import com.faforever.client.game.Faction;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.ShowUserReplaysEvent;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.GalacticWarUpdateMessage;
import com.faforever.client.theme.UiService;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.URISyntaxException;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GalaxyViewController extends AbstractViewController<Node> {

  final private GalacticWarService galacticWarService;
  final private UiService uiService;
  final private FafService fafService;
  final private GameService gameService;
  final private PlayerService playerService;
  final private NotificationService notificationService;
  private final EventBus eventBus;
  final private I18n i18n;
  final private com.faforever.client.fx.PlatformService platformService;

  public StackPane rootPane;
  public Label loadingIndicator;
  public ScrollPane galacticWarGraphContainer;
  public GalacticMapView galacticMapView;
  public VBox planetDetailContainer;
  public VBox leaderboardContainer;
  public Label leaderboardTitle;

  final private SimpleStringProperty technicalName = new SimpleStringProperty("<galaxy>");
  final private SimpleStringProperty displayName = new SimpleStringProperty("<galaxy>");
  private Scenario currentScenario;

  String endpointUrl;

  TableView<GwLeaderboardRow> leaderboardTable;

  private PlanetDetailController planetDetailController;

  @Override
  public Node getRoot() {
    return rootPane;
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    updateLatestState();
  }

  @Override
  public void initialize() {
    planetDetailController = uiService.loadFxml("theme/galactic_war/planet_detail.fxml");
    planetDetailContainer.getChildren().add(planetDetailController.getRoot());
    fafService.addOnMessageListener(GalacticWarUpdateMessage.class, this::onGalacticWarUpdate);
  }

  public void setEndpointUrl(String url) {
    endpointUrl = url;
  }

  public void setTechnicalName(String technicalName) {
    this.technicalName.set(technicalName);
  }

  public String getTechnicalName() {
    return this.technicalName.get();
  }

  public StringProperty getTechnicalNameProperty() {
    return this.technicalName;
  }

  public void setDisplayName(String displayName) {
    this.displayName.set(displayName);
  }

  public String getDisplayName() {
    return this.displayName.get();
  }

  public StringProperty getDisplayNameProperty() {
    return this.displayName;
  }

  private void onGalacticWarUpdate(GalacticWarUpdateMessage msg) {
    if (msg.getGalaxyNames() == null || msg.getGalaxyNames().isEmpty()) {
      updateLatestState();
      planetDetailController.setPlanet(null);
      return;
    }

    for (String galaxyName: msg.getGalaxyNames()) {
      if (galaxyName != null && galaxyName.equals(this.technicalName.get())) {
        updateLatestState();
        planetDetailController.setPlanet(null);
        return;
      }
    }
  }

  public void updateLatestState() {
    if (endpointUrl == null) {
      log.error("[updateLatestState] can't update because endpoint is null");
      return;
    }

    CompletableFuture<Scenario> scenarioFuture;
    try {
      scenarioFuture = galacticWarService.fetchScenario(endpointUrl);
    } catch (URISyntaxException e) {
      log.error("[updateLatestState] Unable to load Galactic War state", e);
      return;
    }

    JavaFxUtil.runLater(() -> loadingIndicator.setVisible(true));
    scenarioFuture.thenAccept((scenario) -> {
      try {
        final String styleSheetFile = uiService.getThemeFile("theme/galactic_war/smartgraph.css");
        galacticMapView = GalacticMapView.fromScenario(scenario, styleSheetFile, uiService);

        JavaFxUtil.runLater(() -> {
          currentScenario = scenario;
          setTechnicalName(scenario.getTechnicalName());
          setDisplayName(scenario.getDisplayName());
          planetDetailController.setGalaxyTechnicalName(scenario.getTechnicalName());
          planetDetailController.setGalaxyDisplayName(scenario.getDisplayName());
          planetDetailController.setGalaxyFactions(scenario.getFactions());
          planetDetailController.setPlayerFaction(
              playerService.getCurrentPlayer().flatMap(
                  player -> scenario.getPlayerFaction(player.getId()))
          );

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
        log.error("[updateLatestState] Unable to load Galactic War state", e);
      }
    }).thenRun(() -> JavaFxUtil.runLater(() -> loadingIndicator.setVisible(false)))
        .exceptionally((throwable) -> {
          notificationService.addImmediateErrorNotification(throwable, "galactic_war.error.cannot_update",
              endpointUrl);
          return null;
        });
  }

  public void resetView(ActionEvent actionEvent) {
    this.galacticMapView.resetView();
  }

  public void onGuideButtonPressed(ActionEvent actionEvent) {
    if (endpointUrl == null) return;
    String guideUrl = endpointUrl.substring(0, endpointUrl.lastIndexOf('/') + 1) + "gw_guide.html";
    platformService.showDocument(guideUrl);
  }

  public void onSettingsButtonPressed(ActionEvent actionEvent) {
    Scenario s = currentScenario;
    if (s == null) return;

    String cardStyle = "-fx-background-color: -fx-control-inner-background; "
        + "-fx-background-radius: 6; -fx-padding: 10; "
        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 4, 0, 0, 1);";
    String sectionTitleStyle = "-fx-font-weight: bold; -fx-font-size: 1.1em; -fx-padding: 0 0 4 0;";

    javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10);
    root.setPadding(new javafx.geometry.Insets(6));

    // === General Settings card ===
    javafx.scene.layout.VBox generalCard = new javafx.scene.layout.VBox(4);
    generalCard.setStyle(cardStyle);
    javafx.scene.control.Label generalTitle = new javafx.scene.control.Label(
        i18n.get("galacticWar.settings.generalSection"));
    generalTitle.setStyle(sectionTitleStyle);
    generalCard.getChildren().add(generalTitle);

    javafx.scene.layout.GridPane generalGrid = new javafx.scene.layout.GridPane();
    generalGrid.setHgap(12);
    generalGrid.setVgap(4);
    int row = 0;
    row = addSettingRow(generalGrid, row, i18n.get("galacticWar.settings.galaxy"), s.getDisplayName());
    row = addSettingRow(generalGrid, row, i18n.get("galacticWar.settings.iteration"),
        String.valueOf(s.getIteration() != null ? s.getIteration() : 1));
    row = addSettingRow(generalGrid, row, i18n.get("galacticWar.settings.factions"),
        s.getFactions() != null ? s.getFactions().stream()
            .map(f -> f.name()).collect(java.util.stream.Collectors.joining(", ")) : "ARM, CORE");
    row = addSettingRow(generalGrid, row, i18n.get("galacticWar.settings.planets"),
        String.valueOf(s.getPlanets() != null ? s.getPlanets().size() : 0));

    if (s.getDominanceDecayPeriod() != null && s.getDominanceDecayThresholds() != null
        && !s.getDominanceDecayThresholds().isEmpty()) {
      row = addSettingRow(generalGrid, row, i18n.get("galacticWar.settings.captureSchedule"),
          i18n.get("galacticWar.settings.captureScheduleValue",
              s.getDominanceDecayPeriod(),
              s.getDominanceDecayThresholds().stream()
                  .map(v -> String.format("%.1fx", v))
                  .collect(java.util.stream.Collectors.joining(" \u2192 "))));
    } else {
      row = addSettingRow(generalGrid, row, i18n.get("galacticWar.settings.captureThreshold"),
          s.getDominanceThreshold() != null
              ? String.format("%.1fx", s.getDominanceThreshold()) : "3.0x");
    }

    if (s.getUpdateCrontab() != null && !s.getUpdateCrontab().isBlank()) {
      row = addSettingRow(generalGrid, row, i18n.get("galacticWar.settings.updateFrequency"),
          describeCrontab(s.getUpdateCrontab()));
    }

    if (s.getLastGalaxyWinner() != null) {
      row = addSettingRow(generalGrid, row, i18n.get("galacticWar.settings.lastWinner"),
          s.getLastGalaxyWinner());
    }

    if (s.getRankThresholds() != null) {
      String ranks = s.getRankThresholds().stream()
          .map(String::valueOf)
          .collect(java.util.stream.Collectors.joining(", "));
      row = addSettingRow(generalGrid, row, i18n.get("galacticWar.settings.rankThresholds"), ranks);
    }

    generalCard.getChildren().add(generalGrid);
    root.getChildren().add(generalCard);

    // === Scoring tables (only if config values are available) ===
    if (s.getStakesMaxScore() != null && s.getStakesRankFactor() != null
        && s.getPlanetAdjMinMax() != null && s.getRankThresholds() != null) {
      int numTiers = 1 + s.getRankThresholds().size();
      double maxScore = s.getStakesMaxScore();
      double rankFactor = s.getStakesRankFactor();
      double minAdj = s.getPlanetAdjMinMax().get(0);
      double maxAdj = s.getPlanetAdjMinMax().get(1);

      // XP card
      javafx.scene.layout.VBox xpCard = new javafx.scene.layout.VBox(4);
      xpCard.setStyle(cardStyle);
      javafx.scene.control.Label xpTitle = new javafx.scene.control.Label(
          i18n.get("galacticWar.settings.xpTable"));
      xpTitle.setStyle(sectionTitleStyle);
      xpCard.getChildren().add(xpTitle);
      xpCard.getChildren().add(buildScoringTable(numTiers, (winnerRank, loserRank) -> {
        double diff = winnerRank - loserRank;
        return maxScore / (1.0 + Math.exp(diff / rankFactor));
      }));
      root.getChildren().add(xpCard);

      // Planet damage card
      javafx.scene.layout.VBox planetCard = new javafx.scene.layout.VBox(4);
      planetCard.setStyle(cardStyle);
      javafx.scene.control.Label planetTitle = new javafx.scene.control.Label(
          i18n.get("galacticWar.settings.planetDamageTable"));
      planetTitle.setStyle(sectionTitleStyle);
      planetCard.getChildren().add(planetTitle);
      planetCard.getChildren().add(buildScoringTable(numTiers, (rank1, rank2) -> {
        int minRank = Math.min(rank1, rank2);
        double adj = minAdj + (maxAdj - minAdj) * minRank / Math.max(1, numTiers - 1);
        return Math.min(adj, maxScore);
      }));
      root.getChildren().add(planetCard);
    }

    javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(root);
    scroll.setFitToWidth(true);
    scroll.setPrefHeight(500);
    scroll.setPrefWidth(550);
    uiService.showInDialog(rootPane, scroll,
        i18n.get("galacticWar.settings.title", s.getDisplayName()));
  }

  private javafx.scene.layout.GridPane buildScoringTable(
      int numTiers, java.util.function.BiFunction<Integer, Integer, Double> valueFn) {
    javafx.scene.layout.GridPane table = new javafx.scene.layout.GridPane();
    table.setPadding(new javafx.geometry.Insets(4, 0, 4, 0));
    String headerStyle = "-fx-font-weight: bold; -fx-font-size: 0.85em; "
        + "-fx-alignment: center; -fx-padding: 2 6 2 6; "
        + "-fx-background-color: derive(-fx-control-inner-background, -10%);";
    String cellStyle = "-fx-font-family: monospace; -fx-font-size: 0.85em; "
        + "-fx-alignment: center; -fx-padding: 2 6 2 6;";
    String cellAltStyle = cellStyle
        + " -fx-background-color: derive(-fx-control-inner-background, -5%);";

    // Corner label
    javafx.scene.control.Label corner = new javafx.scene.control.Label(
        i18n.get("galacticWar.settings.tableCorner"));
    corner.setStyle(headerStyle);
    table.add(corner, 0, 0);

    // Column headers
    for (int col = 0; col < numTiers; col++) {
      javafx.scene.control.Label h = new javafx.scene.control.Label(String.valueOf(col));
      h.setStyle(headerStyle);
      h.setMinWidth(28);
      table.add(h, col + 1, 0);
    }

    // Rows
    for (int r = 0; r < numTiers; r++) {
      javafx.scene.control.Label rowLabel = new javafx.scene.control.Label(String.valueOf(r));
      rowLabel.setStyle(headerStyle);
      rowLabel.setMinWidth(28);
      table.add(rowLabel, 0, r + 1);
      for (int c = 0; c < numTiers; c++) {
        double val = valueFn.apply(r, c);
        javafx.scene.control.Label cell = new javafx.scene.control.Label(
            String.valueOf(Math.round(val)));
        cell.setStyle(r % 2 == 0 ? cellStyle : cellAltStyle);
        cell.setMinWidth(28);
        table.add(cell, c + 1, r + 1);
      }
    }
    return table;
  }

  private int addSettingRow(javafx.scene.layout.GridPane grid, int row, String label, String value) {
    javafx.scene.control.Label l = new javafx.scene.control.Label(label);
    l.setStyle("-fx-font-weight: bold;");
    javafx.scene.control.Label v = new javafx.scene.control.Label(value);
    v.setWrapText(true);
    v.setMaxWidth(300);
    grid.add(l, 0, row);
    grid.add(v, 1, row);
    return row + 1;
  }

  /**
   * Convert a simple cron expression to a human-readable description.
   * Handles common GW patterns; falls back to showing the raw cron.
   */
  private String describeCrontab(String cron) {
    if (cron == null) return "?";
    String[] parts = cron.trim().split("\\s+");
    if (parts.length != 5) return cron;
    String min = parts[0], hour = parts[1];
    // "*/N * * * *" → "Every N minutes"
    if (min.startsWith("*/") && "*".equals(hour) && "*".equals(parts[2])
        && "*".equals(parts[3]) && "*".equals(parts[4])) {
      try {
        int n = Integer.parseInt(min.substring(2));
        return i18n.get("galacticWar.settings.everyNMinutes", n);
      } catch (NumberFormatException ignored) {}
    }
    // "0 */N * * *" → "Every N hours"
    if ("0".equals(min) && hour.startsWith("*/") && "*".equals(parts[2])
        && "*".equals(parts[3]) && "*".equals(parts[4])) {
      try {
        int n = Integer.parseInt(hour.substring(2));
        return i18n.get("galacticWar.settings.everyNHours", n);
      } catch (NumberFormatException ignored) {}
    }
    return cron;
  }

  private float totalScore(Map<String, GwPlayerScore> scores) {
    return scores.values().stream()
        .map(s -> s != null ? s : GwPlayerScore.EMPTY_SCORE)
        .map(GwPlayerScore::getCumWinningScores)
        .max(Float::compare)
        .orElse(0.0f);
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
    playerCol.setCellValueFactory(cellData -> cellData.getValue().playerNameProperty());

    TableColumn<GwLeaderboardRow, Long> winScoreCol = new TableColumn<>("XP");
    winScoreCol.setCellValueFactory(new PropertyValueFactory<>("winScore"));

    TableColumn<GwLeaderboardRow, Integer> winsCol = new TableColumn<>(i18n.get("userInfo.wins"));
    winsCol.setCellValueFactory(new PropertyValueFactory<>("wins"));

    TableColumn<GwLeaderboardRow, Integer> lossesCol = new TableColumn<>(i18n.get("userInfo.losses"));
    lossesCol.setCellValueFactory(new PropertyValueFactory<>("losses"));

    medalCol.setStyle("-fx-alignment: CENTER;");
    setColumnWidthOptions(rankCol, 32);
    setColumnWidthOptions(medalCol, 28);
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
    VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
    table.setMaxHeight(Double.MAX_VALUE);

    leaderboardContainer.getChildren().add(table);

    ObservableList<GwLeaderboardRow> rows = FXCollections.observableArrayList();
    AtomicInteger rankCounter = new AtomicInteger(1);

    scenario.getPlayers().entrySet().stream().sorted(
        (a, b) -> Float.compare(totalScore(b.getValue()), totalScore(a.getValue())))
        .forEachOrdered(entry -> {
          Integer playerId = entry.getKey();
          Optional<FactionScoreRank> fsr = scenario.getFactionScoreRank(entry.getValue());
          if (fsr.isEmpty()) {
            return;
          }

          Map<String, GwPlayerScore> scores = entry.getValue();
          Optional<Map.Entry<String, GwPlayerScore>> topEntry =
              scores.entrySet().stream()
                  .max(java.util.Comparator.comparingInt(
                      e -> scenario.rankForPlayerScore(e.getValue()).getTier()));
          if (topEntry.isEmpty()) {
            return;
          }

          String factionName = topEntry.get().getKey();
          Faction faction = Faction.fromString(factionName);
          GwPlayerScore score = topEntry.get().getValue();
          GwRank gwRank = scenario.rankForPlayerScore(score);

          rows.add(new GwLeaderboardRow(
              rankCounter.getAndIncrement(),
              galacticWarService.getPlayerNameProperty(playerId),
              score.getWins(),
              Math.round(score.getCumWinningScores()),
              score.getLosses(),
              Math.round(score.getCumLosingScores()),
              galacticWarService.getMedalIcon(scenario, factionName, gwRank),
              faction
          ));
        });

    table.setItems(rows);
    this.leaderboardTable = table;
  }

  public void openContextMenu(ContextMenuEvent event) {
    int index = leaderboardTable.getSelectionModel().selectedIndexProperty().get();
    String userName = leaderboardTable.getItems().get(index).getPlayerName();
    playerService.getPlayerByName(userName)
        .thenAccept(optionalPlayer -> {
          optionalPlayer.ifPresent(player -> JavaFxUtil.runLater(() -> {
            ContextMenu contextMenu = new ContextMenu();

            MenuItem userInfoMenuItem = new MenuItem(i18n.get("chat.userContext.userInfo"));
            userInfoMenuItem.setOnAction(e -> showUserInfo(player));
            contextMenu.getItems().add(userInfoMenuItem);

            MenuItem viewReplaysMenuItem = new MenuItem(i18n.get("chat.userContext.viewReplays"));
            viewReplaysMenuItem.setOnAction(e -> showUserReplays(player));
            contextMenu.getItems().add(viewReplaysMenuItem);

            contextMenu.show(this.getRoot().getScene().getWindow(), event.getScreenX(), event.getScreenY());
          }));
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
