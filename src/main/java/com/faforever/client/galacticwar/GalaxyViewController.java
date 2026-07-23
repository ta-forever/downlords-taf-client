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
  final private com.faforever.client.preferences.PreferencesService preferencesService;

  public StackPane rootPane;
  public Label loadingIndicator;
  public ScrollPane galacticWarGraphContainer;
  public GalacticMapView galacticMapView;
  public VBox planetDetailContainer;
  public VBox leaderboardContainer;
  public Label leaderboardTitle;
  public javafx.scene.control.CheckBox leaderboardWarXpToggle;
  public StackPane victoryOverlay;
  public javafx.scene.image.ImageView victoryBackgroundImage;
  public javafx.scene.image.ImageView victoryFactionImage;
  public Label victoryTitle;
  public Label victorySubtitle;
  public Label victoryMvps;
  public javafx.scene.layout.HBox victoryHonours;

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
    victoryBackgroundImage.fitWidthProperty().bind(victoryOverlay.widthProperty());
    victoryBackgroundImage.fitHeightProperty().bind(victoryOverlay.heightProperty());
    victoryBackgroundImage.setPreserveRatio(false);
    leaderboardWarXpToggle.setSelected(
        preferencesService.getPreferences().isGalacticWarLeaderboardThisWar());
  }

  public void onLeaderboardXpModeToggled(ActionEvent actionEvent) {
    preferencesService.getPreferences()
        .setGalacticWarLeaderboardThisWar(leaderboardWarXpToggle.isSelected());
    preferencesService.storeInBackground();
    if (currentScenario != null) {
      populateLeaderboard(currentScenario);
    }
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
          galacticWarService.consumeDebugVictorySplashRequest().ifPresentOrElse(
              faction -> showVictorySplash(scenario, faction, faction.name().toLowerCase(), true),
              () -> maybeShowVictorySplash(scenario));
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

  private javafx.scene.image.Image getFactionImage(Faction faction) {
    if (faction == Faction.ARM) {
      return uiService.getThemeImage(UiService.ARM_ICON_IMAGE_LARGE);
    }
    if (faction == Faction.CORE) {
      return uiService.getThemeImage(UiService.CORE_ICON_IMAGE_LARGE);
    }
    return uiService.getThemeImage(UiService.GOK_ICON_IMAGE_LARGE);
  }

  /**
   * Show the full-view victory splash if this galaxy's war has just been won and the user
   * hasn't dismissed the splash for this iteration yet. Runs on the FX thread.
   */
  private void maybeShowVictorySplash(Scenario scenario) {
    if (scenario.getLastGalaxyWinner() == null) {
      return;
    }
    int iteration = scenario.getIteration() != null ? scenario.getIteration() : 1;
    Integer celebrated = preferencesService.getPreferences()
        .getGalacticWarCelebratedIterations()
        .get(scenario.getTechnicalName());
    if (celebrated != null && celebrated >= iteration) {
      return;
    }

    Faction faction = Faction.fromString(scenario.getLastGalaxyWinner());
    showVictorySplash(scenario, faction, scenario.getLastGalaxyWinner(), false);
  }

  private boolean victorySplashIsDebugPreview;

  /**
   * @param winnerName the winner's faction key as stored in scenario data (lowercase, e.g. "arm");
   *                   used for the MVP lookup and as display fallback when {@code faction} is null
   * @param debugPreview when true (snapshot-build settings tool) the dismissal is not persisted
   */
  private void showVictorySplash(Scenario scenario, Faction faction, String winnerName, boolean debugPreview) {
    victorySplashIsDebugPreview = debugPreview;
    int iteration = scenario.getIteration() != null ? scenario.getIteration() : 1;
    String factionDisplayName = faction != null ? faction.getString() : winnerName;

    // Per-faction backdrop: burning-planet aftermath for an ARM win, galaxy-over-battlefield
    // for CORE (and anyone else); the winner's emblem is stamped on top via victoryFactionImage.
    // A theme that doesn't ship the art degrades to the overlay's plain black background.
    String backdrop = faction == Faction.ARM
        ? "theme/images/galactic_war/victory_splash_arm.png"
        : "theme/images/galactic_war/victory_splash_core.png";
    victoryBackgroundImage.setImage(
        uiService.themeFileExists(backdrop) ? uiService.getThemeImage(backdrop) : null);
    victoryFactionImage.setImage(getFactionImage(faction));
    victoryTitle.setText(i18n.get("galacticWar.victory.title",
        factionDisplayName, scenario.getDisplayName()).toUpperCase());
    victorySubtitle.setText(i18n.get("galacticWar.victory.subtitle", iteration));

    victoryMvps.textProperty().unbind();
    victoryMvps.setText("");
    Scenario.HistoryEntry lastWar = (scenario.getHistory() != null && !scenario.getHistory().isEmpty())
        ? scenario.getHistory().get(scenario.getHistory().size() - 1) : null;

    Map<String, java.util.List<Scenario.Contributor>> honours = null;
    Map<String, Integer> medalCounts = null;
    if (lastWar != null && lastWar.getTopContributors() != null && !lastWar.getTopContributors().isEmpty()) {
      honours = lastWar.getTopContributors();
      medalCounts = lastWar.getMedalCounts();
    } else if (debugPreview) {
      // No concluded war on this server yet — fake the honours from the live leaderboard so
      // the debugging preview still exercises the panel.
      honours = computeHonoursFromScenario(scenario);
    }

    if (honours != null && winnerName != null) {
      java.util.List<Scenario.Contributor> winners = honours.get(winnerName);
      if (winners != null && !winners.isEmpty() && winners.get(0).getPlayerId() != null) {
        Scenario.Contributor hero = winners.get(0);
        StringProperty heroName = galacticWarService.getPlayerNameProperty(hero.getPlayerId());
        long heroScore = hero.getScore() != null ? Math.round(hero.getScore()) : 0;
        victoryMvps.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
            () -> i18n.get("galacticWar.victory.hero", heroName.get(), heroScore), heroName));
      }
    }

    populateVictoryHonours(honours, winnerName, medalCounts);
    victoryOverlay.setVisible(true);
  }

  /** How many placements per faction to list on the victory splash. */
  private static final int VICTORY_HONOURS_PLACEMENTS = 5;

  /** Fallback for the debug preview: rank this scenario's players per faction by XP. */
  private Map<String, java.util.List<Scenario.Contributor>> computeHonoursFromScenario(Scenario scenario) {
    Map<String, java.util.List<Scenario.Contributor>> byFaction = new java.util.HashMap<>();
    if (scenario.getPlayers() == null) {
      return byFaction;
    }
    scenario.getPlayers().forEach((playerId, factionScores) -> factionScores.forEach((factionName, score) -> {
      if (score != null && score.getCumWinningScores() > 0) {
        byFaction.computeIfAbsent(factionName, key -> new java.util.ArrayList<>())
            .add(new Scenario.Contributor(playerId, score.getCumWinningScores()));
      }
    }));
    byFaction.values().forEach(contributors ->
        contributors.sort((a, b) -> Float.compare(b.getScore(), a.getScore())));
    return byFaction;
  }

  /**
   * Fill the splash honours panel: one column per faction (winner first), top placements
   * with the GW medal icon marking players who actually received an end-of-war medal.
   */
  private void populateVictoryHonours(Map<String, java.util.List<Scenario.Contributor>> honours,
                                      String winnerName, Map<String, Integer> medalCounts) {
    victoryHonours.getChildren().clear();
    if (honours != null && winnerName != null) {
      victoryHonours.getChildren().addAll(buildHonoursColumns(honours, winnerName, medalCounts, null,
          "-fx-text-fill: white; -fx-font-weight: bold;", "-fx-text-fill: #dddddd;"));
    }
    victoryHonours.setVisible(!victoryHonours.getChildren().isEmpty());
  }

  /**
   * Build one column per faction (winner first, then alphabetical): a header, optionally a
   * fighters count (when {@code participants} is given), and the top placements as
   * "1. Name — 1,234 XP" rows. The GW medal icon marks players who actually received an
   * end-of-war medal — gw_conqueror for the winning faction, gw_last_stand for defeated
   * factions — per the recorded {@code medalCounts} cutoffs. A war without a victor
   * ({@code winnerName} null) awarded no medals, so no icons are shown.
   */
  private java.util.List<javafx.scene.layout.VBox> buildHonoursColumns(
      Map<String, java.util.List<Scenario.Contributor>> honours,
      String winnerName,
      Map<String, Integer> medalCounts,
      Map<String, Integer> participants,
      String headerStyle,
      String rowStyle) {
    java.util.List<javafx.scene.layout.VBox> columns = new java.util.ArrayList<>();
    if (honours == null || honours.isEmpty()) {
      return columns;
    }
    int conquerorCutoff = medalCounts != null
        ? medalCounts.getOrDefault(com.faforever.client.ladder.LadderUiUtil.GW_CONQUEROR, 10) : 10;
    int lastStandCutoff = medalCounts != null
        ? medalCounts.getOrDefault(com.faforever.client.ladder.LadderUiUtil.GW_LAST_STAND, 3) : 3;

    java.util.List<String> factionOrder = new java.util.ArrayList<>(honours.keySet());
    factionOrder.sort(java.util.Comparator.comparing((String name) -> !name.equals(winnerName))
        .thenComparing(java.util.Comparator.naturalOrder()));

    for (String factionName : factionOrder) {
      java.util.List<Scenario.Contributor> contributors = honours.get(factionName);
      if (contributors == null || contributors.isEmpty()) {
        continue;
      }
      boolean isWinner = factionName.equals(winnerName);
      Faction faction = Faction.fromString(factionName);
      String factionDisplayName = faction != null ? faction.getString() : factionName;

      javafx.scene.layout.VBox panel = new javafx.scene.layout.VBox(4);
      panel.setAlignment(javafx.geometry.Pos.TOP_LEFT);

      String headerText = winnerName == null
          ? factionDisplayName
          : i18n.get(isWinner ? "galacticWar.victory.honours.winner" : "galacticWar.victory.honours.loser",
              factionDisplayName);
      Label header = new Label(headerText.toUpperCase());
      header.setStyle(headerStyle);
      panel.getChildren().add(header);

      if (participants != null && participants.getOrDefault(factionName, 0) > 0) {
        Label fighters = new Label(i18n.get("galacticWar.hallOfVictors.fighters",
            participants.get(factionName)));
        fighters.setStyle(rowStyle + " -fx-opacity: 0.7; -fx-font-size: 0.9em;");
        panel.getChildren().add(fighters);
      }

      String medalCode = isWinner
          ? com.faforever.client.ladder.LadderUiUtil.GW_CONQUEROR
          : com.faforever.client.ladder.LadderUiUtil.GW_LAST_STAND;
      String medalIconPath = com.faforever.client.ladder.LadderUiUtil.medalIconPath(medalCode);
      javafx.scene.image.Image medalImage = uiService.themeFileExists(medalIconPath)
          ? uiService.getThemeImage(medalIconPath) : null;
      // a war with no victor awarded no medals at all
      int medalCutoff = winnerName == null ? 0 : (isWinner ? conquerorCutoff : lastStandCutoff);

      for (int i = 0; i < Math.min(contributors.size(), VICTORY_HONOURS_PLACEMENTS); i++) {
        Scenario.Contributor contributor = contributors.get(i);
        if (contributor.getPlayerId() == null) {
          continue;
        }
        int placement = i + 1;
        long score = contributor.getScore() != null ? Math.round(contributor.getScore()) : 0;
        StringProperty nameProperty = galacticWarService.getPlayerNameProperty(contributor.getPlayerId());
        Label row = new Label();
        row.setStyle(rowStyle);
        row.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
            () -> i18n.get("galacticWar.victory.honours.row", placement, nameProperty.get(), score),
            nameProperty));
        if (medalImage != null && placement <= medalCutoff) {
          javafx.scene.image.ImageView medalIcon = new javafx.scene.image.ImageView(medalImage);
          medalIcon.setFitWidth(16);
          medalIcon.setFitHeight(16);
          medalIcon.setPreserveRatio(true);
          row.setGraphic(medalIcon);
          Tooltip.install(row, new Tooltip(
              com.faforever.client.ladder.LadderUiUtil.medalDisplayName(i18n, medalCode)));
        }
        panel.getChildren().add(row);
      }
      columns.add(panel);
    }
    return columns;
  }

  public void onVictoryDismissed(ActionEvent actionEvent) {
    victoryOverlay.setVisible(false);
    if (victorySplashIsDebugPreview) {
      victorySplashIsDebugPreview = false;
      return;
    }
    Scenario scenario = currentScenario;
    if (scenario == null) {
      return;
    }
    int iteration = scenario.getIteration() != null ? scenario.getIteration() : 1;
    preferencesService.getPreferences()
        .getGalacticWarCelebratedIterations()
        .put(scenario.getTechnicalName(), iteration);
    preferencesService.storeInBackground();
  }

  public void onHallOfVictorsButtonPressed(ActionEvent actionEvent) {
    Scenario scenario = currentScenario;
    if (scenario == null) {
      return;
    }

    javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10);
    root.setPadding(new javafx.geometry.Insets(6));

    java.util.List<Scenario.HistoryEntry> history = scenario.getHistory();
    if (history == null || history.isEmpty()) {
      javafx.scene.control.Label empty = new javafx.scene.control.Label(
          i18n.get("galacticWar.hallOfVictors.empty"));
      empty.setWrapText(true);
      root.getChildren().add(empty);
    } else {
      // newest war first
      for (int i = history.size() - 1; i >= 0; i--) {
        root.getChildren().add(buildHallOfVictorsCard(history.get(i)));
      }
    }

    javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(root);
    scroll.setFitToWidth(true);
    scroll.setPrefHeight(500);
    scroll.setPrefWidth(550);
    uiService.showInDialog(rootPane, scroll,
        i18n.get("galacticWar.hallOfVictors.title", scenario.getDisplayName()));
  }

  private Node buildHallOfVictorsCard(Scenario.HistoryEntry entry) {
    String cardStyle = "-fx-background-color: -fx-control-inner-background; "
        + "-fx-background-radius: 6; -fx-padding: 10; "
        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 4, 0, 0, 1);";

    javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(4);
    card.setStyle(cardStyle);

    Faction winner = entry.getWinner() != null ? Faction.fromString(entry.getWinner()) : null;
    String outcome = winner != null
        ? i18n.get("galacticWar.hallOfVictors.winner", winner.getString())
        : i18n.get("galacticWar.hallOfVictors.noWinner");

    javafx.scene.layout.HBox header = new javafx.scene.layout.HBox(8);
    header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    if (winner != null) {
      javafx.scene.image.ImageView icon = new javafx.scene.image.ImageView(getFactionImage(winner));
      icon.setFitWidth(24);
      icon.setPreserveRatio(true);
      header.getChildren().add(icon);
    }
    javafx.scene.control.Label title = new javafx.scene.control.Label(
        i18n.get("galacticWar.hallOfVictors.war",
            entry.getIteration() != null ? entry.getIteration() : 0, outcome));
    title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
    header.getChildren().add(title);
    card.getChildren().add(header);

    if (entry.getStartedAt() != null && entry.getEndedAt() != null) {
      javafx.scene.control.Label dates = new javafx.scene.control.Label(
          i18n.get("galacticWar.hallOfVictors.dates", entry.getStartedAt(), entry.getEndedAt()));
      dates.setStyle("-fx-opacity: 0.7; -fx-font-size: 0.9em;");
      card.getChildren().add(dates);
    }

    java.util.List<javafx.scene.layout.VBox> columns = buildHonoursColumns(
        entry.getTopContributors(), entry.getWinner(), entry.getMedalCounts(),
        entry.getParticipants(), "-fx-font-weight: bold;", "");
    if (!columns.isEmpty()) {
      javafx.scene.layout.HBox honoursRow = new javafx.scene.layout.HBox(32);
      honoursRow.setPadding(new javafx.geometry.Insets(6, 0, 0, 0));
      honoursRow.getChildren().addAll(columns);
      card.getChildren().add(honoursRow);
    }

    return card;
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
                  .map(v -> String.format("%.2fx", v))
                  .collect(java.util.stream.Collectors.joining(" \u2192 "))));
    } else {
      row = addSettingRow(generalGrid, row, i18n.get("galacticWar.settings.captureThreshold"),
          s.getDominanceThreshold() != null
              ? String.format("%.2fx", s.getDominanceThreshold()) : "3.00x");
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

    // Career mode (default) shows/orders by all-time XP; "this war" mode shows/orders by the
    // XP delta earned in the current galaxy. Faction identity and the rank medal stay
    // career-based in both modes — ranks are for careers, the toggle only changes the numbers.
    boolean thisWarOnly = leaderboardWarXpToggle != null && leaderboardWarXpToggle.isSelected();

    java.util.function.BiFunction<Integer, Map<String, GwPlayerScore>, Float> sortScore =
        (playerId, scores) -> scores.entrySet().stream()
            .map(e -> {
              GwPlayerScore s = e.getValue() != null ? e.getValue() : GwPlayerScore.EMPTY_SCORE;
              if (thisWarOnly) {
                s = scenario.getCurrentWarScore(playerId, e.getKey(), s);
              }
              return s.getCumWinningScores() != null ? s.getCumWinningScores() : 0.0f;
            })
            .max(Float::compare)
            .orElse(0.0f);

    ObservableList<GwLeaderboardRow> rows = FXCollections.observableArrayList();
    AtomicInteger rankCounter = new AtomicInteger(1);

    scenario.getPlayers().entrySet().stream().sorted(
        (a, b) -> Float.compare(sortScore.apply(b.getKey(), b.getValue()),
            sortScore.apply(a.getKey(), a.getValue())))
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
          GwPlayerScore careerScore = topEntry.get().getValue();
          GwRank gwRank = scenario.rankForPlayerScore(careerScore);

          GwPlayerScore displayed = thisWarOnly
              ? scenario.getCurrentWarScore(playerId, factionName, careerScore)
              : careerScore;
          if (thisWarOnly && displayed.getWins() == 0 && displayed.getLosses() == 0) {
            // pre-seeded veterans who haven't fought this war yet
            return;
          }

          rows.add(new GwLeaderboardRow(
              rankCounter.getAndIncrement(),
              galacticWarService.getPlayerNameProperty(playerId),
              displayed.getWins(),
              Math.round(displayed.getCumWinningScores()),
              displayed.getLosses(),
              Math.round(displayed.getCumLosingScores()),
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
