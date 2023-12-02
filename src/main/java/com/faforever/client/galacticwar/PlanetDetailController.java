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
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.theme.UiService;
import com.sun.javafx.charts.Legend;
import com.sun.javafx.charts.Legend.LegendItem;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
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
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  public StackPane planetDetailRoot;
  public ImageView factionImage;
  public DefaultImageView mapImage;
  public Button createGameButton;
  public Label mapLabel;
  public Label mapDescription;
  public VBox mapLabelContainer;
  public Label planetTitleLabel;
  public Label modLabel;
  public HBox scoresContainer;
  public AnchorPane planetNotSelectedContainer;
  public ScrollPane planetSelectedContainer;
  public TableView<Player> belligerentsTableView;

  Planet planet;
  SimpleObjectProperty<MapBean> mapBean;
  SimpleObjectProperty<FeaturedMod> featuredMod;
  SimpleObjectProperty<Leaderboard> leaderboard;

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
    mapDescription.textProperty().bind(Bindings.createStringBinding(
        () -> mapBean.getValue() == null ? "" : mapBean.getValue().getDescription(), mapBean));
    modLabel.textProperty().bind(Bindings.createStringBinding(
        () -> featuredMod.getValue() == null ? "" : featuredMod.getValue().getDisplayName(), featuredMod));
  }

  @Override
  public Node getRoot() {
    return planetDetailRoot;
  }

  public void setPlanet(Planet planet) {
    this.planet = planet;
    if (planet == null) {
      planetSelectedContainer.setVisible(false);
      return;
    }

    createGameButton.setVisible(planet.getControlledBy() == null);
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
    populateBelligerentsTable(planet);

    planetSelectedContainer.setVisible(true);
  }

  Planet getPlanet() {
    return planet;
  }

  public Node createScoresChart(Planet planet) {
    List<XYChart.Series<Number,String>> seriesList = new ArrayList<>();
    for (Faction faction: planet.getScore().keySet()) {
      XYChart.Series<Number,String> series = new XYChart.Series<>();
      series.setName(faction.getString());
      XYChart.Data<Number,String> datum = new XYChart.Data<>(
          planet.getScore().getOrDefault(faction, 0.0), faction.getString());
      series.getData().add(datum);
      seriesList.add(series);
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

    for (Faction faction: planet.getScore().keySet()) {
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

    return bc;
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
          belligerentsTableView.setItems(FXCollections.observableArrayList(players));

          TableColumn<Player, String> playerNameColumn = new TableColumn<>("Belligerent");
          playerNameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getAlias()));
          belligerentsTableView.getColumns().add(playerNameColumn);

          for (Faction faction: factions) {
            TableColumn<Player, Number> factionScoreColumn = new TableColumn<>(faction.getString());
            factionScoreColumn.setCellValueFactory(param -> new SimpleIntegerProperty(
                planet.getBelligerents().get(param.getValue().getId()).getOrDefault(faction, 0.0).intValue()));
            belligerentsTableView.getColumns().add(factionScoreColumn);
          }
          belligerentsTableView.setVisible(true);
        });
  }

  public void onCreateGameButtonPressed(ActionEvent actionEvent) {
    LiveReplayOption lastGameLiveReplayOption = preferencesService.getPreferences().getLastGame().getLastGameLiveReplayOption();
    lastGameLiveReplayOption = lastGameLiveReplayOption == LiveReplayOption.DISABLED
        ? LiveReplayOption.FIVE_MINUTES
        : lastGameLiveReplayOption;

    NewGameInfo newGameInfo = new NewGameInfo(
        i18n.get("galactic_war.game_title_template", this.planet.getName()),
        null,
        this.featuredMod.get(),
        this.featuredMod.get().getGitBranch(),
        this.mapBean.get().getMapName(),
        emptySet(),
        GameVisibility.PUBLIC,
        null, null, false,
        lastGameLiveReplayOption.getDelaySeconds(),
        this.leaderboard.get().getTechnicalName(),
        this.planet.getName());

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
}
