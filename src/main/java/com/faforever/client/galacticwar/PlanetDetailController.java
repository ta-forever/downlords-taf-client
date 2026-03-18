package com.faforever.client.galacticwar;

import com.faforever.client.fx.DefaultImageView;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.Faction;
import com.faforever.client.game.GameService;
import com.faforever.client.game.GameVisibility;
import com.faforever.client.game.LiveReplayOption;
import com.faforever.client.game.NewGameInfo;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.Leaderboard;
import com.faforever.client.main.event.SelectGalacticWarMapEvent;
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.Action.Type;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.theme.UiService;
import com.google.common.eventbus.EventBus;
import com.sun.javafx.charts.Legend;
import com.sun.javafx.charts.Legend.LegendItem;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.application.Platform;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.util.Collections.emptySet;

@Slf4j
@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PlanetDetailController implements Controller<Node> {

  private final UiService uiService;
  private final MapService mapService;
  private final I18n i18n;
  private final GameService gameService;
  private final FafService fafService;
  private final PreferencesService preferencesService;
  private final NotificationService notificationService;
  private final PlayerService playerService;
  private final GalacticWarService galacticWarService;
  private final EventBus eventBus;

  public StackPane planetDetailRoot;
  public ImageView factionImage;
  public DefaultImageView mapImage;
  public Button createGameButton;
  public Label mapLabel;
  public Label mapDescription;
  public Label planetTitleLabel;
  public Label modLabel;
  public HBox scoresContainer;
  public Label scoresAnnotationsLabel;
  public Label contestedPeriodsLabel;
  public Label effectiveThresholdLabel;
  public AnchorPane planetNotSelectedContainer;
  public ScrollPane planetSelectedContainer;
  public TableView<Player> belligerentsTableView;
  public Button setMapButton;

  Planet planet;
  SimpleObjectProperty<MapBean> mapBean;
  SimpleObjectProperty<FeaturedMod> featuredMod;
  SimpleObjectProperty<Leaderboard> leaderboard;
  String galaxyTechnicalName;
  String galaxyDisplayName;
  Optional<Faction> playerFaction;
  List<Faction> galaxyFactions = List.of(Faction.ARM, Faction.CORE);

  void setPlayerFaction(Optional<Faction> playerFaction) {
    this.playerFaction = playerFaction;
  }

  void setGalaxyFactions(List<Faction> galaxyFactions) {
    this.galaxyFactions = galaxyFactions;
  }

  @Override
  public void initialize() {
    this.mapBean = new SimpleObjectProperty<>();
    this.featuredMod = new SimpleObjectProperty<>();
    this.leaderboard = new SimpleObjectProperty<>();
    this.mapImage.setDefaultImage(uiService.getThemeImage(UiService.UNKNOWN_MAP_IMAGE));

    planetSelectedContainer.setVisible(false);
    planetNotSelectedContainer.visibleProperty().bind(planetSelectedContainer.visibleProperty().not());

    createGameButton.disableProperty().bind(
        mapBean.isNull().or(featuredMod.isNull().or(leaderboard.isNull().or(gameService.getCurrentGameProperty().isNotNull()))));
    //createGameButton.managedProperty().bind(createGameButton.visibleProperty());
    //setMapButton.managedProperty().bind(setMapButton.visibleProperty());
    mapDescription.textProperty().bind(Bindings.createStringBinding(
        () -> mapBean.getValue() == null ? "" : mapBean.getValue().getDescription(), mapBean));
    modLabel.textProperty().bind(Bindings.createStringBinding(
        () -> featuredMod.getValue() == null ? "" : featuredMod.getValue().getDisplayName(), featuredMod));
  }

  @Override
  public Node getRoot() {
    return planetDetailRoot;
  }

  public void setGalaxyTechnicalName(String galaxyTechnicalName) {
    this.galaxyTechnicalName = galaxyTechnicalName;
  }

  public void setGalaxyDisplayName(String galaxyDisplayName) {
    this.galaxyDisplayName = galaxyDisplayName;
  }

  public void setPlanet(Planet planet) {
    this.planet = planet;
    if (planet == null) {
      planetSelectedContainer.setVisible(false);
      return;
    }

    createGameButton.setVisible(planet.getControlledBy() == null);

    Optional<Integer> heroicPlayerId = planet.getHeroicPlayerId();
    setMapButton.setVisible(
        planet.getControlledBy() != null &&
        this.playerService.getCurrentPlayer().isPresent() &&
            heroicPlayerId.isPresent() &&
            heroicPlayerId.get() == this.playerService.getCurrentPlayer().get().getId());
    planetTitleLabel.setText(planet.getName());
    mapLabel.setText(planet.getMapName());

    this.mapBean.set(null);
    mapService.getMapLatestVersion(planet.getMapName())
        .thenAccept(map ->
            map.ifPresent(m ->
                JavaFxUtil.runLater(() ->
                    this.mapBean.set(m))));

    this.featuredMod.set(null);
    fafService.getFeaturedMods().thenAccept(featuredMods -> featuredMods.stream()
        .filter(fm -> fm.getTechnicalName().equals(planet.getModTechnical()))
        .findAny()
        .ifPresent(fm -> JavaFxUtil.runLater(() -> this.featuredMod.set(fm))));

    this.leaderboard.set(null);
    fafService.getMatchmakerQueuesByMod(planet.getModTechnical())
        .thenAccept(queues -> queues.stream().findFirst().ifPresent(q ->
            this.leaderboard.set(q.getLeaderboard())));

    mapImage.setBackgroundLoadingImage(mapService.loadPreview(
        planet.getModTechnical(), planet.getMapName(), PreviewType.MINI, 10));
    mapImage.fitWidthProperty().bind(planetDetailRoot.widthProperty().subtract(20));
    mapImage.setVisible(true);

    Faction faction = planet.getControlledBy();
    if (faction != null)  {
      if (faction == Faction.ARM) {
        factionImage.setImage(uiService.getThemeImage(UiService.ARM_ICON_IMAGE_LARGE));
      } else if (faction == Faction.CORE) {
        factionImage.setImage(uiService.getThemeImage(UiService.CORE_ICON_IMAGE_LARGE));
      } else if (faction == Faction.GOK) {
        factionImage.setImage(uiService.getThemeImage(UiService.GOK_ICON_IMAGE_LARGE));
      }
    }
    factionImage.setVisible(faction != null);
    factionImage.fitWidthProperty().bind(planetDetailRoot.widthProperty().subtract(20));

    scoresContainer.getChildren().setAll(createScoresChart(planet));
    scoresContainer.setVisible(faction == null);
    scoresAnnotationsLabel.setVisible(faction == null);
    scoresContainer.managedProperty().bind(scoresContainer.visibleProperty());
    scoresAnnotationsLabel.managedProperty().bind(scoresAnnotationsLabel.visibleProperty());

    boolean contested = faction == null;
    if (contested && planet.getContestedPeriods() != null) {
      String thresholdScore = "?";
      if (planet.getEffectiveThreshold() != null && planet.getScore() != null) {
        double winningScore = planet.getScore().values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double ts = winningScore / planet.getEffectiveThreshold();
        thresholdScore = String.valueOf((int) ts);
      }
      contestedPeriodsLabel.setText(
          i18n.get("galactic_war.planet_detail.contested_periods", planet.getContestedPeriods()));
      effectiveThresholdLabel.setText(
          i18n.get("galactic_war.planet_detail.effective_threshold", thresholdScore));
      contestedPeriodsLabel.setVisible(true);
      effectiveThresholdLabel.setVisible(true);
    } else {
      contestedPeriodsLabel.setVisible(false);
      effectiveThresholdLabel.setVisible(false);
    }
    contestedPeriodsLabel.managedProperty().bind(contestedPeriodsLabel.visibleProperty());
    effectiveThresholdLabel.managedProperty().bind(effectiveThresholdLabel.visibleProperty());
    populateBelligerentsTable(planet);

    planetSelectedContainer.setVisible(true);
  }

  Planet getPlanet() {
    return planet;
  }

  public Node createScoresChart(Planet planet) {
    Map<Faction, Double> scores = planet.getScore();

    List<String> scoresAnnotationsTexts = new ArrayList<>();
    for (Faction faction : planet.getScore().keySet()) {
      scoresAnnotationsTexts.add(String.format("%s:%d",
          faction.getString(),
          planet.getScore().getOrDefault(faction, 0.0).intValue()
      ));
    }
    scoresAnnotationsLabel.setText(String.join(" ", scoresAnnotationsTexts));

    if (scores.isEmpty()) {
      return new Pane();
    }

    Faction winningFaction = Collections.max(scores.entrySet(),
        Map.Entry.comparingByValue()).getKey();
    float threshold = planet.getEffectiveThreshold() != null
        ? planet.getEffectiveThreshold()
        : fallbackDominanceThreshold();

    List<XYChart.Series<Number,String>> seriesList = new ArrayList<>();
    Map<Faction, XYChart.Data<Number,String>> datumByFaction = new HashMap<>();
    for (Faction faction : scores.keySet()) {
      XYChart.Series<Number,String> series = new XYChart.Series<>();
      series.setName(faction.getString());
      XYChart.Data<Number,String> datum = new XYChart.Data<>(
          scores.getOrDefault(faction, 0.0), faction.getString());
      series.getData().add(datum);
      seriesList.add(series);
      datumByFaction.put(faction, datum);
    }

    final CategoryAxis yAxis = new CategoryAxis();
    final NumberAxis xAxis = new NumberAxis();
    final BarChart<Number,String> bc = new BarChart<>(xAxis,yAxis);
    bc.setMinSize(220, 80);
    bc.setPrefSize(220, 80);
    bc.setMaxSize(220, 80);
    bc.getData().addAll(seriesList);
    bc.setLegendVisible(false);
    yAxis.setStartMargin(0.0);
    yAxis.setEndMargin(0.0);
    yAxis.setTickLabelsVisible(false);
    yAxis.setVisible(false);
    bc.setBarGap(0.0);
    bc.setCategoryGap(0.0);

    final Map<String,String> factionColours = Map.of(
        "Arm", "ARM_COLOR",
        "Core", "CORE_COLOR",
        "GoK", "GOK_COLOR");

    for (Faction faction : scores.keySet()) {
      if (factionColours.containsKey(faction.getString())) {
        String colour = factionColours.get(faction.getString());
        for (XYChart.Series<Number,String> series : bc.getData()) {
          if (series.getName().equals(faction.getString())) {
            for (Data<Number,String> data : series.getData()) {
              data.getNode().setStyle(String.format("-fx-bar-fill: %s;", colour));
            }
          }
        }

        for (Node n : bc.getChildrenUnmodifiable()) {
          if (n instanceof Legend) {
            for (LegendItem items : ((Legend) n).getItems()) {
              if (items.getText().equals(faction.getString())) {
                items.getSymbol().setStyle(String.format("-fx-bar-fill: %s;", colour));
              }
            }
          }
        }
      }
    }

    // Overlay pane for white frame outlines, positioned after layout
    Pane overlay = new Pane();
    overlay.setMouseTransparent(true);
    overlay.setStyle("-fx-background-color: transparent;");
    StackPane wrapper = new StackPane(bc, overlay);
    wrapper.setMinSize(220, 80);
    wrapper.setPrefSize(220, 80);
    wrapper.setMaxSize(220, 80);

    final float finalThreshold = threshold;
    bc.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal.getWidth() <= 0 || newVal.getHeight() <= 0) return;
      // Defer until after the current layout pass so bar nodes have their final positions
      Platform.runLater(() -> {
        if (scores.values().stream().distinct().count() <= 1) return;
        if (scores.values().stream().anyMatch(v -> v <= 0)) return;
        XYChart.Data<Number,String> winningDatum = datumByFaction.get(winningFaction);
        if (winningDatum == null || winningDatum.getNode() == null) return;

        // bc and overlay are the same size at the same position inside the StackPane,
        // so bc-local coordinates equal overlay-local coordinates.
        Bounds winningBarBounds = bc.sceneToLocal(
            winningDatum.getNode().localToScene(winningDatum.getNode().getBoundsInLocal()));
        double winningBarWidthPx = winningBarBounds.getWidth();

        overlay.getChildren().clear();
        for (Map.Entry<Faction, XYChart.Data<Number,String>> entry : datumByFaction.entrySet()) {
          XYChart.Data<Number,String> datum = entry.getValue();
          if (datum.getNode() == null) continue;

          boolean isWinning = entry.getKey().equals(winningFaction);
          Bounds barBounds = bc.sceneToLocal(
              datum.getNode().localToScene(datum.getNode().getBoundsInLocal()));

          double frameWidth = isWinning
              ? barBounds.getWidth()
              : winningBarWidthPx / finalThreshold;

          Rectangle frame = new Rectangle(
              barBounds.getMinX(), barBounds.getMinY(),
              frameWidth, barBounds.getHeight());
          frame.setFill(Color.TRANSPARENT);
          frame.setStroke(Color.WHITE);
          frame.setStrokeWidth(1.5);
          overlay.getChildren().add(frame);
        }
      });
    });

    return wrapper;
  }

  private float fallbackDominanceThreshold() {
    Scenario scenario = galaxyTechnicalName != null
        ? galacticWarService.getScenario(galaxyTechnicalName)
        : null;
    return (scenario != null && scenario.getDominanceThreshold() != null)
        ? scenario.getDominanceThreshold()
        : 3.0f;
  }

  private void populateBelligerentsTable(Planet planet) {

    Set<Faction> factions = new HashSet<>();
    planet.getBelligerents().values()
        .forEach(scores -> factions.addAll(scores.keySet()));

    belligerentsTableView.setVisible(false);
    if (factions.isEmpty()) {
      return;
    }

    belligerentsTableView.getColumns().clear();

    playerService.getPlayersByIds(planet.getBelligerents().keySet())
        .thenAccept(players -> {

          // Filter players with positive score in ANY faction
          List<Player> filteredPlayers = players.stream()
              .filter(player -> {
                Map<Faction, GwPlayerScore> scores =
                    planet.getBelligerents().getOrDefault(player.getId(), Map.of());

                return scores.values().stream()
                    .mapToDouble(GwPlayerScore::getCumWinningScores)
                    .anyMatch(total -> total > 0.0);
              })
              .toList();

          belligerentsTableView.setItems(
              FXCollections.observableArrayList(filteredPlayers)
          );

          // Player column
          TableColumn<Player, String> playerNameColumn =
              new TableColumn<>(i18n.get("galactic_war.column.combatants"));

          playerNameColumn.setCellValueFactory(param ->
              new SimpleStringProperty(param.getValue().getAlias())
          );

          belligerentsTableView.getColumns().add(playerNameColumn);

          // Faction score columns
          for (Faction faction : factions) {

            TableColumn<Player, Number> factionScoreColumn =
                new TableColumn<>(faction.getString());

            factionScoreColumn.setCellValueFactory(param -> {

              GwPlayerScore score =
                  planet.getBelligerents()
                      .getOrDefault(param.getValue().getId(), Map.of())
                      .getOrDefault(faction, GwPlayerScore.EMPTY_SCORE);

              Float rawTotal = score.getCumWinningScores();

              // Clip negative scores to 0
              int clippedTotal = (int) Math.max(0, rawTotal);

              return new SimpleIntegerProperty(clippedTotal);
            });

            belligerentsTableView.getColumns().add(factionScoreColumn);
          }

          belligerentsTableView.setVisible(true);
        });
  }


  public void onCreateGameButtonPressed(ActionEvent actionEvent) {
    CompletableFuture<Faction> factionFuture;
    factionFuture = this.playerFaction.map(CompletableFuture::completedFuture).orElseGet(this::promptUserFaction);
    factionFuture.thenAccept(this::createGame);
  }

  public CompletableFuture<Faction> promptUserFaction() {
    CompletableFuture<Faction> future = new CompletableFuture<>();
    List<Action> actions = galaxyFactions.stream().map(faction -> new Action(
        faction.getString(), Type.OK_DONE, a -> future.complete(faction)
        )).toList();

    notificationService.addNotification(new ImmediateNotification(
        i18n.get("galactic_war.select_faction.title"),
        i18n.get("galactic_war.select_faction.text"),
        Severity.INFO, actions));

    return future;
  }

  public void createGame(Faction userFaction) {
    LiveReplayOption lastGameLiveReplayOption = preferencesService.getPreferences().getLastGame().getLastGameLiveReplayOption();
    lastGameLiveReplayOption = lastGameLiveReplayOption == LiveReplayOption.DISABLED
        ? LiveReplayOption.FIVE_MINUTES
        : lastGameLiveReplayOption;

    String planetName = this.getPlanet().getName();
    if (this.galaxyTechnicalName != null && !this.galaxyTechnicalName.isEmpty()) {
      planetName = this.galaxyTechnicalName + "/" + planetName;
    }

    String GALACTIC_WAR_GAME_TITLE_TEMPLATE = "Galactic War: %s is attacking %s!";
    NewGameInfo newGameInfo = new NewGameInfo(
        String.format(GALACTIC_WAR_GAME_TITLE_TEMPLATE, userFaction, this.getPlanet().getName()),
        null,
        this.featuredMod.get(),
        this.featuredMod.get().getGitBranch(),
        this.mapBean.get().getMapName(),
        emptySet(),
        GameVisibility.PUBLIC,
        null, null, false,
        lastGameLiveReplayOption.getDelaySeconds(),
        this.leaderboard.get().getTechnicalName(),
        planetName,
        2);

    mapService.optionalEnsureMapLatestVersion(this.planet.getModTechnical(), this.mapBean.get())
        .exceptionally(throwable -> {
          log.error("error when updating the map", throwable);
          return this.mapBean.get();
        })
        .thenApply(ensuredMap -> ensuredMap == null ? this.mapBean.get() : ensuredMap)
        .thenCompose(mapBean -> gameService.hostGame(newGameInfo)
            .exceptionally(throwable -> {
              log.warn("Game could not be hosted", throwable);
              notificationService.addImmediateErrorNotification(throwable, "game.create.failed");
              return null;
            }));
  }

  public void onSetMapButtonPressed(ActionEvent actionEvent) {
    Scenario scenario = galacticWarService.getScenario(this.galaxyTechnicalName);
    eventBus.post(new SelectGalacticWarMapEvent(
        this.galaxyTechnicalName,
        this.planet,
        scenario.getMapSelectStrategy(),
        scenario.getMapSelectRegexes().getOrDefault(this.planet.getModTechnical(), List.of(".*")),
        scenario.getMapSelectMMQId().getOrDefault(this.planet.getModTechnical(), null)));
  }
}
