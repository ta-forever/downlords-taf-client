package com.faforever.client.game;

import com.faforever.client.fa.FaStrings;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.DualStringListCell;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.fx.StringListCell;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.HostGameEvent;
import com.faforever.client.main.event.SelectGalacticWarMapEvent;
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapBean.Type;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.map.MapSize;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.LastGamePrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.teammatchmaking.MatchmakingQueue;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.preferences.event.GameDirectoryChooseEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.faforever.client.leaderboard.LeaderboardService.DEFAULT_RATING_TYPE;
import static com.faforever.client.net.ConnectionState.CONNECTED;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static javafx.scene.layout.BackgroundPosition.CENTER;
import static javafx.scene.layout.BackgroundRepeat.NO_REPEAT;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class CreateGameController implements Controller<Pane> {

  public Button mapPreviewContextButton;
  public HBox enforceRankingContainer;
  public HBox rankingRangeContainer;
  String ALL_MAPS_PSUEDO_QUEUE_NAME_KEY = "games.create.mappool.allmaps";

  public static final String STYLE_CLASS_DUAL_LIST_CELL = "create-game-dual-list-cell";
  public static final PseudoClass PSEUDO_CLASS_INVALID = PseudoClass.getPseudoClass("invalid");
  private static final int MAX_RATING_LENGTH = 4;
  private final MapService mapService;
  private final ModService modService;
  private final GameService gameService;
  private final PlayerService playerService;
  private final PreferencesService preferencesService;
  private final I18n i18n;
  private final NotificationService notificationService;
  private final FafService fafService;
  private final UiService uiService;
  private final EventBus eventBus;
  private final PlatformService platformService;

  public Label mapSizeLabel;
  public Label mapPlayersLabel;
  public Label mapDescriptionLabel;
  public TextField mapSearchTextField;
  public TextField titleTextField;
  public TextField passwordTextField;
  public TextField minRankingTextField;
  public TextField maxRankingTextField;
  public CheckBox enforceRankingCheckBox;
  public ListView<FeaturedMod> featuredModListView;
  public ListView<MapBean> mapListView;
  public StackPane createGameRoot;
  public Button createGameButton;
  public Button updateGameButton;
  public Button installGameButton;
  public VBox mapPreview;
  public Pane mapPreviewPane;
  public Label versionLabel;
  public Label hpiArchiveLabel;
  public ComboBox<PreviewType> mapPreviewTypeComboBox;
  public ComboBox<Integer> maxPlayersComboBox;
  public CheckBox onlyForFriendsCheckBox;
  public ComboBox<LiveReplayOption> liveReplayOptionComboBox;
  public ListView<MatchmakingQueue> mapPoolListView;

  public CheckBox rankedEnabledCheckBox;
  public Label rankedEnabledLabel;
  public Button openGameFolderButton;
  public Button randomMapButton;
  @VisibleForTesting
  FilteredList<MapBean> filteredMapBeans;
  private Runnable onCloseButtonClickedListener;
  private boolean suppressTitlePersistence = false;

  private BooleanProperty validatedButtonsDisableProperty;
  private BooleanProperty modVersionUpdateCompletedProperty;
  private StringProperty interactionLevelProperty; // "CREATE", "UPDATE", "BROWSE"
  private BooleanProperty loadingMapsProperty;
  private BooleanProperty rankedMapPoolsAvailableProperty;
  private ObjectProperty<SelectGalacticWarMapEvent> selectGalacticWarMapEvent;

  private ObjectProperty<Game> contextGameProperty; // is player actually creating a new game (contextGame==null), or inspecting settings for an existing game?

  void setSelectingGalacticWarMap(SelectGalacticWarMapEvent selecting) {
    selectGalacticWarMapEvent.set(selecting);
  }

  void setContextGame(Game game) {
    if (game != null) {
      if (preferencesService.isGameExeValid(game.getFeaturedMod())) {
        this.featuredModListView.getItems().stream()
            .filter(mod -> mod.getTechnicalName().equals(game.getFeaturedMod()))
            .findAny()
            .ifPresent(mod -> this.featuredModListView.getSelectionModel().select(mod));
      }
      if (this.featuredModListView.getSelectionModel().getSelectedItem() != null) {
        mapService.optionalEnsureMap(this.featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName(), game.getMapName(), game.getMapCrc(), game.getMapArchiveName(), null, null);
      }
    }
    this.contextGameProperty.set(game);
    selectAppropriateMap();
  }

  String getInteractionLevel() {
    if (interactionLevelProperty.get() == null) {
      return "CREATE";
    }
    else {
      return interactionLevelProperty.get();
    }
  }

  StringProperty getInteractionLevelProperty() {
    return interactionLevelProperty;
  }

  static final private ConcurrentHashMap<String, Pattern> mapFilterRegexPatterns = new ConcurrentHashMap<>();
  private void setFilteredMapBeansPredicate(String filter) {

    if (filter.isEmpty()) {
      filteredMapBeans.setPredicate(null);

    } else if (filter.startsWith("regex:")) {
      final List<Pattern> patterns = new ArrayList<>();
      for (String part : filter.substring(6).split(";")) {
        if (part.isEmpty()) {
          continue;
        }
        try {
          mapFilterRegexPatterns.computeIfAbsent(part, Pattern::compile);
        }
        catch (PatternSyntaxException e) {
          continue;
        }
        patterns.add(mapFilterRegexPatterns.get(part));
      }
      if (patterns.isEmpty()) {
        filteredMapBeans.setPredicate(null);
      }
      else {
        filteredMapBeans.setPredicate(mapInfoBean ->
            patterns.stream()
                .anyMatch(p -> p.matcher(mapInfoBean.getMapName()).find())
        );
      }

    } else {
      filteredMapBeans.setPredicate(mapInfoBean -> mapInfoBean.getMapName().toLowerCase().contains(filter.toLowerCase()));
    }

    if (!filteredMapBeans.isEmpty() && mapListView.getSelectionModel().getSelectedItem() == null) {
      mapListView.getSelectionModel().select(0);
    }
  }

  public void initialize() {
    validatedButtonsDisableProperty = new SimpleBooleanProperty();
    modVersionUpdateCompletedProperty = new SimpleBooleanProperty(false);
    interactionLevelProperty = new SimpleStringProperty();
    contextGameProperty = new SimpleObjectProperty<>();
    loadingMapsProperty = new SimpleBooleanProperty();
    rankedMapPoolsAvailableProperty = new SimpleBooleanProperty();
    selectGalacticWarMapEvent = new SimpleObjectProperty<>();

    JavaFxUtil.addLabelContextMenus(uiService, hpiArchiveLabel, mapDescriptionLabel);
    versionLabel.managedProperty().bind(versionLabel.visibleProperty());
    hpiArchiveLabel.managedProperty().bind(hpiArchiveLabel.visibleProperty());

    liveReplayOptionComboBox.getItems().setAll(LiveReplayOption.values());
    LiveReplayOption lastGameLiveReplayOption = preferencesService.getPreferences().getLastGame().getLastGameLiveReplayOption();
    liveReplayOptionComboBox.getSelectionModel().select(
        lastGameLiveReplayOption == LiveReplayOption.DISABLED
          ? LiveReplayOption.FIVE_MINUTES
          : lastGameLiveReplayOption);
    liveReplayOptionComboBox.setConverter(new StringConverter<>() {
      @Override
      public String toString(LiveReplayOption replayOption) {
        return replayOption == null ? "null" : i18n.get(replayOption.getI18nKey());
      }
      @Override
      public LiveReplayOption fromString(String string) {
        throw new UnsupportedOperationException("Not supported");
      }
    });

    mapPreviewTypeComboBox.getItems().setAll(PreviewType.values());
    mapPreviewTypeComboBox.getSelectionModel().select(0);
    mapPreviewTypeComboBox.setConverter(new StringConverter<>() {
      @Override
      public String toString(PreviewType previewType) {
        return previewType == null ? "null" : i18n.get(previewType.getI18nKey());
      }
      @Override
      public PreviewType fromString(String string) {
        throw new UnsupportedOperationException("Not supported");
      }
    });
    mapPreviewTypeComboBox.getSelectionModel().selectedItemProperty().addListener(
        (observable, oldValue, newValue) -> setSelectedMap(mapListView.getSelectionModel().getSelectedItem(), newValue, maxPlayersComboBox.getSelectionModel().getSelectedItem()));

    maxPlayersComboBox.getItems().setAll(
        IntStream.rangeClosed(2, 10)
            .map(i -> 12 - i) // Maps (2 -> 10), (3 -> 9), ..., (10 -> 2)
            .boxed()
            .collect(Collectors.toList())
    );

    maxPlayersComboBox.getSelectionModel().select(Integer.valueOf(preferencesService.getPreferences().getLastGame().getMaxPlayers()));
    maxPlayersComboBox.setConverter(new StringConverter<>() {
      @Override
      public String toString(Integer maxPlayers) {
        return String.valueOf(maxPlayers);
      }
      @Override
      public Integer fromString(String string) {
        throw new UnsupportedOperationException("Not supported");
      }
    });
    maxPlayersComboBox.getSelectionModel().selectedItemProperty().addListener(
        (observable, oldValue, newValue) -> setSelectedMap(mapListView.getSelectionModel().getSelectedItem(), mapPreviewTypeComboBox.getSelectionModel().getSelectedItem(), newValue));

    mapSearchTextField.textProperty().addListener(
        (observable, oldValue, newValue) -> setFilteredMapBeansPredicate(newValue));
    mapSearchTextField.setOnKeyPressed(event -> {
      MultipleSelectionModel<MapBean> selectionModel = mapListView.getSelectionModel();
      int currentMapIndex = selectionModel.getSelectedIndex();
      int newMapIndex = currentMapIndex;
      if (KeyCode.DOWN == event.getCode()) {
        if (filteredMapBeans.size() > currentMapIndex + 1) {
          newMapIndex++;
        }
        event.consume();
      } else if (KeyCode.UP == event.getCode()) {
        if (currentMapIndex > 0) {
          newMapIndex--;
        }
        event.consume();
      }
      selectionModel.select(newMapIndex);
      mapListView.scrollTo(newMapIndex);
    });

    JavaFxUtil.makeNumericTextField(minRankingTextField, MAX_RATING_LENGTH, true);
    JavaFxUtil.makeNumericTextField(maxRankingTextField, MAX_RATING_LENGTH, true);

    init();
    modVersionUpdateCompletedProperty.set(true);
  }

  public Optional<MapBean> getCurrentSelectedMap() {
    return Optional.ofNullable(mapListView.getSelectionModel().getSelectedItem());
  }

  public void onCloseButtonClicked() {
    selectGalacticWarMapEvent.set(null);
    onCloseButtonClickedListener.run();
  }

  private StringBinding createValidatedButtonTextBinding(String nominalText) {
    return Bindings.createStringBinding(() -> {
      switch (fafService.connectionStateProperty().get()) {
        case DISCONNECTED:
          return i18n.get("game.create.disconnected");
        case CONNECTING:
          return i18n.get("game.create.connecting");
        default:
          break;
      }
      if (Strings.isNullOrEmpty(titleTextField.getText())) {
        return i18n.get("game.create.titleMissing");
      } else if (!validateTitle(titleTextField.getText())) {
        return i18n.get("game.create.titleIllegal");
      } else if (featuredModListView.getSelectionModel().getSelectedItem() == null) {
        return i18n.get("game.create.featuredModMissing");
      } else if (mapListView.getSelectionModel().getSelectedItem() == null) {
        return i18n.get("game.create.mapMissing");
      }
      return nominalText;
    }, titleTextField.textProperty(), featuredModListView.getSelectionModel().selectedItemProperty(),
        fafService.connectionStateProperty(), mapListView.getSelectionModel().selectedItemProperty());
  }

  String calcInteractionLevel() {
    final String currentPlayer = playerService.getCurrentPlayer().isPresent()
        ? playerService.getCurrentPlayer().get().getUsername()
        : "";

    final boolean isCurrentGameSameAsContextGame = contextGameProperty.get() != null &&
            gameService.getCurrentGame() != null &&
            contextGameProperty.get().getId() == gameService.getCurrentGame().getId();

    final boolean isPlayerHost = gameService.getCurrentGame() != null &&
            gameService.getCurrentGame().getHost().equals(currentPlayer);

    boolean isGameStaging = gameService.getCurrentGame() != null &&
            gameService.getCurrentGame().getStatus() == GameStatus.STAGING;

    final boolean isCurrentGameGalacticWar = gameService.getCurrentGame() != null &&
            gameService.getCurrentGame().getGalacticWarPlanetName() != null;

    if (selectGalacticWarMapEvent.get() != null) {
      return "SELECT_MAP_GW";
    }

    if (gameService.getCurrentGame() == null || playerService.getCurrentPlayer().isEmpty() || contextGameProperty.get() == null) {
      if (!isCurrentGameGalacticWar) {
        return "CREATE";
      }
    }

    if (isCurrentGameSameAsContextGame && isPlayerHost && isGameStaging) {
      if (isCurrentGameGalacticWar) {
        setFriendsOnly(false);
        setPassword("");
        return "UPDATE_GW";
      } else {
        return "UPDATE";
      }
    }
    else {
      return "BROWSE";
    }
  }

  private void init() {
    bindGameVisibility();
    bindLiveReplayDelayOption();
    bindMaxPlayersOption();
    initMapSelection(KnownFeaturedMod.DEFAULT.getBaseGameName());
    initFeaturedModList();
    initMapPoolList();
    initRatingBoundaries();
    setGameTitle();
    initPassword();

    titleTextField.textProperty().addListener((observable, oldValue, newValue) -> {
      validateTitle(newValue);
      if (!getInteractionLevel().equals("UPDATE_GW") && !suppressTitlePersistence) {
        preferencesService.getPreferences().getLastGame().setLastGameTitle(newValue);
        preferencesService.storeInBackground();
      }
    });

    createGameButton.textProperty().bind(createValidatedButtonTextBinding(i18n.get("game.create.create")));
    updateGameButton.textProperty().bind(createValidatedButtonTextBinding(i18n.get("game.create.update")));

    validatedButtonsDisableProperty.bind(
        Bindings.createBooleanBinding(() -> !validateTitle(titleTextField.getText()), titleTextField.textProperty())
            .or(featuredModListView.getSelectionModel().selectedItemProperty().isNull())
            .or(fafService.connectionStateProperty().isNotEqualTo(CONNECTED))
            .or(mapListView.getSelectionModel().selectedItemProperty().isNull())
            .or(modVersionUpdateCompletedProperty.not())
            .or(mapPoolListView.getSelectionModel().selectedItemProperty().isNull())
    );
    interactionLevelProperty.bind(Bindings.createStringBinding(
        this::calcInteractionLevel, gameService.getCurrentGameStatusProperty(), contextGameProperty, selectGalacticWarMapEvent));

    createGameButton.disableProperty().bind(validatedButtonsDisableProperty);
    updateGameButton.disableProperty().bind(interactionLevelProperty.isEqualTo("BROWSE"));
    mapPoolListView.disableProperty().bind(interactionLevelProperty.isEqualTo("BROWSE")
        .or(interactionLevelProperty.isEqualTo("UPDATE_GW")));
    liveReplayOptionComboBox.disableProperty().bind(interactionLevelProperty.isEqualTo("BROWSE")
        .or(interactionLevelProperty.isEqualTo("SELECT_MAP_GW")));
    passwordTextField.disableProperty().bind(interactionLevelProperty.isEqualTo("BROWSE")
        .or(interactionLevelProperty.isEqualTo("SELECT_MAP_GW")
            .or(interactionLevelProperty.isEqualTo("UPDATE_GW"))));
    mapListView.disableProperty().bind(interactionLevelProperty.isEqualTo("BROWSE")
            .or(interactionLevelProperty.isEqualTo("UPDATE_GW")).or(loadingMapsProperty));
    randomMapButton.disableProperty().bind(mapListView.disabledProperty());
    mapSearchTextField.disableProperty().bind(mapListView.disabledProperty());

    rankedEnabledCheckBox.setSelected(true);
    rankedEnabledCheckBox.disableProperty().bind(
        interactionLevelProperty.isEqualTo("BROWSE")
            .or(interactionLevelProperty.isEqualTo("UPDATE_GW"))
            .or(interactionLevelProperty.isEqualTo("SELECT_MAP_GW"))
            .or(rankedMapPoolsAvailableProperty.not()));
    rankedEnabledLabel.disableProperty().bind(rankedEnabledCheckBox.disabledProperty());

    createGameButton.visibleProperty().bind(interactionLevelProperty.isEqualTo("CREATE"));
    updateGameButton.visibleProperty().bind(createGameButton.visibleProperty().not());
    titleTextField.disableProperty().bind(updateGameButton.visibleProperty());
    onlyForFriendsCheckBox.disableProperty().bind(updateGameButton.visibleProperty());
    featuredModListView.disableProperty().bind(updateGameButton.visibleProperty());
    installGameButton.disableProperty().bind(updateGameButton.visibleProperty());
    openGameFolderButton.disableProperty().bind(updateGameButton.visibleProperty());
  }

  public void sever() {
    createGameButton.textProperty().unbind();
    updateGameButton.textProperty().unbind();
    validatedButtonsDisableProperty.unbind();
    interactionLevelProperty.unbind();
    onlyForFriendsCheckBox.selectedProperty().unbind();
    enforceRankingCheckBox.selectedProperty().unbind();
  }

  private boolean validateTitle(String gameTitle) {
    boolean invalid = Strings.isNullOrEmpty(gameTitle) ||
        gameTitle.toLowerCase().contains("galactic war") &&
            !getInteractionLevel().equals("UPDATE_GW");
    titleTextField.pseudoClassStateChanged(PSEUDO_CLASS_INVALID, invalid);
    return !invalid;
  }

  private void initPassword() {
    LastGamePrefs lastGamePrefs = preferencesService.getPreferences().getLastGame();
    passwordTextField.setText(lastGamePrefs.getLastGamePassword());
    JavaFxUtil.addListener(passwordTextField.textProperty(), (observable, oldValue, newValue) -> {
      lastGamePrefs.setLastGamePassword(newValue);
      preferencesService.storeInBackground();
    });
  }

  private void bindGameVisibility() {
    onlyForFriendsCheckBox.selectedProperty().bindBidirectional(
        preferencesService.getPreferences()
            .getLastGame()
            .lastGameOnlyFriendsProperty()
    );
    onlyForFriendsCheckBox.selectedProperty().addListener(observable -> preferencesService.storeInBackground());
  }

  private void bindLiveReplayDelayOption() {
    liveReplayOptionComboBox.getSelectionModel().selectedItemProperty().addListener((obs,oldValue,newValue) -> {
      preferencesService.getPreferences().getLastGame().setLastGameLiveReplayOption(newValue);
      preferencesService.storeInBackground();
    });
  }

  private void bindMaxPlayersOption() {
    maxPlayersComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
      preferencesService.getPreferences().getLastGame().setMaxPlayers(newValue);
      preferencesService.storeInBackground();
    });
  }

  protected void initMapSelection(String modTechnical) {
    setAvailableMaps(modTechnical);
    mapListView.setCellFactory(param -> new StringListCell<>(MapBean::getMapName));
    ChangeListener<MapBean> selectedMapChangeListener = (observable, oldValue, newValue) -> JavaFxUtil.runLater(() -> {
      PreviewType previewType = mapPreviewTypeComboBox.getSelectionModel().getSelectedItem();
      Integer maxPlayers = maxPlayersComboBox.getSelectionModel().getSelectedItem();
      setSelectedMap(newValue, previewType, maxPlayers);
    });
    mapListView.getSelectionModel().selectedItemProperty().addListener(selectedMapChangeListener);
    selectedMapChangeListener.changed(null, null, mapListView.getSelectionModel().selectedItemProperty().getValue());
  }

  final Map<String, FilteredList<MapBean> > filteredMapBeansByMod = new HashMap<>();
  final Map<String, FilteredList<MapBean> > filteredMapBeansByQueue = new HashMap<>();
  protected void setAvailableMaps(String modTechnical) {
    if (rankedEnabledCheckBox.isSelected() && mapPoolListView.getSelectionModel().getSelectedItem() != null &&
        !mapService.getInstalledMaps(modTechnical).isEmpty()) {
      MatchmakingQueue selectedQueue = mapPoolListView.getSelectionModel().getSelectedItem();
      if (rankedEnabledCheckBox.isSelected() && selectedQueue != null && !mapService.getInstalledMaps(modTechnical).isEmpty()) {
        if (filteredMapBeansByQueue.containsKey(selectedQueue.getQueueName())) {
          doSetAvailableMaps(modTechnical, filteredMapBeansByQueue.get(selectedQueue.getQueueName()));
        } else {
          loadingMapsProperty.set(true);
          if (selectedQueue.getQueueName().equals(ALL_MAPS_PSUEDO_QUEUE_NAME_KEY)) {
            mapService.getAllRankedMaps().thenAccept(mapList -> {
              FilteredList<MapBean> fl = new FilteredList<>(FXCollections.observableArrayList(mapList)
                  .sorted((o1, o2) -> o1.getMapName().compareToIgnoreCase(o2.getMapName())));
              filteredMapBeansByQueue.put(selectedQueue.getQueueName(), fl);
              doSetAvailableMaps(modTechnical, fl);
              loadingMapsProperty.set(false);
            });
          } else {
            fafService.getMatchmakerQueueMapPools().thenAccept(allQueues -> {
              allQueues.stream()
                  .filter(_q -> _q.getQueueId() == selectedQueue.getQueueId())
                  .findFirst()
                  .ifPresent(q -> {
                    FilteredList<MapBean> fl = new FilteredList<>(FXCollections.observableArrayList(q.getMapPool())
                        .sorted((o1, o2) -> o1.getMapName().compareToIgnoreCase(o2.getMapName())));
                    filteredMapBeansByQueue.put(selectedQueue.getQueueName(), fl);
                    doSetAvailableMaps(modTechnical, fl);
                  });
              loadingMapsProperty.set(false);
            });
          }
        }
      }
    }
    else {
      if (!filteredMapBeansByMod.containsKey(modTechnical)) {
        filteredMapBeansByMod.put(
            modTechnical,
            new FilteredList<>(mapService.getInstalledMaps(modTechnical)
                .filtered(mapBean -> mapBean.getType() == Type.SKIRMISH)
                .sorted((o1, o2) -> o1.getMapName().compareToIgnoreCase(o2.getMapName())))
        );
      }
      doSetAvailableMaps(modTechnical, filteredMapBeansByMod.get(modTechnical));
    }
  }

  protected void doSetAvailableMaps(String modTechnical, FilteredList<MapBean> items) {
    JavaFxUtil.runLater(() -> {
      filteredMapBeans = items;
      setFilteredMapBeansPredicate(mapSearchTextField.getText());

      Path installedExePath = preferencesService.getTotalAnnihilation(modTechnical).getInstalledExePath();
      if (items.isEmpty() && installedExePath != null && Files.isExecutable(installedExePath)) {
        mapListView.setItems(mapService.getOfficialMaps());
      } else {
        mapListView.setItems(items);
      }
      selectAppropriateMap();
    });
  }

  protected void setAvailableMapPools(String modTechnical) {

    mapPoolListView.getItems().clear();
    fafService.getMatchmakerQueueMapPools()
        .thenAccept(queues -> {
          queues = queues.stream().filter(q -> q.getFeaturedMod().getTechnicalName().equals(modTechnical)).toList();
          Stream<MatchmakingQueue> queuesPlusGlobal = Stream.concat(
              Stream.of(MatchmakingQueue.makePsuedoQueue(
                  ALL_MAPS_PSUEDO_QUEUE_NAME_KEY,
                  featuredModListView.getSelectionModel().getSelectedItem(),
                  queues.isEmpty() ? DEFAULT_RATING_TYPE : queues.get(0).getLeaderboard().getTechnicalName())
              ),
              queues.stream()
          );

          queuesPlusGlobal
              .filter(q -> !q.getLeaderboard().getLeaderboardHidden())
              .map(q -> new javafx.util.Pair<>(q,
                  q.getLeaderboard().getNameKey().equals(ALL_MAPS_PSUEDO_QUEUE_NAME_KEY)
                      ? mapService.getAllRankedMaps()
                      : CompletableFuture.completedFuture(q.getMapPool())))
              .forEach(queueAndMapsPair -> queueAndMapsPair.getValue().thenAccept(maps -> JavaFxUtil.runLater(() -> {
                if (maps != null && !maps.isEmpty()
                    && featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName().equals(modTechnical)
                    && !mapPoolListView.getItems().stream()
                    .anyMatch(listItem -> listItem.getQueueId() == queueAndMapsPair.getKey().getQueueId())) {
                  if (queueAndMapsPair.getKey().getLeaderboard().getNameKey().equals(ALL_MAPS_PSUEDO_QUEUE_NAME_KEY)) {
                    mapPoolListView.getItems().add(0, queueAndMapsPair.getKey());
                    mapPoolListView.getSelectionModel().select(0);
                  }
                  else {
                    mapPoolListView.getItems().add(queueAndMapsPair.getKey());
                  }
                }
              })));
        });

    rankedMapPoolsAvailableProperty.bind(Bindings.isNotEmpty(mapPoolListView.getItems()));
  }

  protected void setSelectedMap(MapBean newValue, PreviewType previewType, int maxNumPlayers) {
    JavaFxUtil.assertApplicationThread();

    if (newValue == null) {
      mapPreview.setVisible(false);
      return;
    }
    mapPreview.setVisible(true);

    preferencesService.getPreferences().getLastGame().setLastMap(newValue.getMapName());
    preferencesService.storeInBackground();

    String activeMod;
    if (featuredModListView.getFocusModel().getFocusedItem() != null) {
      activeMod = featuredModListView.getFocusModel().getFocusedItem().getTechnicalName();
    }
    else {
      activeMod = preferencesService.getPreferences().getLastGame().getLastGameType();
    }

    Image preview = mapService.loadPreview(activeMod, newValue.getMapName(), previewType, maxNumPlayers);
    mapPreviewPane.setBackground(new Background(new BackgroundImage(preview, NO_REPEAT, NO_REPEAT, CENTER,
        new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, true, false))));

    MapSize mapSize = newValue.getSize();
    mapSizeLabel.setText(i18n.get("mapPreview.size", mapSize.getWidthInKm(), mapSize.getHeightInKm()));
    mapPlayersLabel.setText(i18n.number(newValue.getPlayers()));
    mapDescriptionLabel.setText(Optional.ofNullable(newValue.getDescription())
        .map(Strings::emptyToNull)
        .map(FaStrings::removeLocalizationTag)
        .orElseGet(() -> i18n.get("map.noDescriptionAvailable")));

    hpiArchiveLabel.setText(newValue.getHpiArchiveName());
    hpiArchiveLabel.setVisible(true);

    ComparableVersion mapVersion = newValue.getVersion();
    if (mapVersion == null) {
      versionLabel.setVisible(false);
    } else {
      versionLabel.setText(i18n.get("map.versionFormat", mapVersion));
    }
  }

  private void initFeaturedModList() {
    Function<FeaturedMod, String> isDefaultModString = mod ->
        Objects.equals(mod.getTechnicalName(), KnownFeaturedMod.DEFAULT.getTechnicalName()) ?
            " " + i18n.get("game.create.defaultGameTypeMarker") : null;

    featuredModListView.setCellFactory(param ->
        new DualStringListCell<>(
            FeaturedMod::getDisplayName,
            isDefaultModString,
            FeaturedMod::getDescription,
            STYLE_CLASS_DUAL_LIST_CELL, uiService
        )
    );

    modService.getFeaturedMods().thenAccept(featuredModBeans -> JavaFxUtil.runLater(() -> {
      featuredModListView.setItems(FXCollections.observableList(featuredModBeans).filtered(FeaturedMod::isVisible));
      featuredModListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
        if (newValue != null) {
          JavaFxUtil.runLater(() -> setButtonStatesForFeaturedMod(newValue));
        }
      });
      selectLastOrDefaultGameType();
    }));
  }

  private void setButtonStatesForFeaturedMod(FeaturedMod featuredMod) {
    setAvailableMapPools(featuredMod.getTechnicalName());
    setAvailableMaps(featuredMod.getTechnicalName());
    Path installedExePath = preferencesService.getTotalAnnihilation(featuredMod.getTechnicalName()).getInstalledExePath();
    installGameButton.setStyle(installedExePath == null || !Files.isExecutable(installedExePath)
        ? "-fx-background-color: -fx-accent"
        : "-fx-background-color: -fx-background");
    installGameButton.setText(i18n.get("game.create.download", featuredMod.getDisplayName()));
    openGameFolderButton.setVisible(installedExePath != null && Files.exists(installedExePath));
    openGameFolderButton.setText(i18n.get("game.create.openModFolder", featuredMod.getDisplayName()));
    preferencesService.getPreferences().getLastGame().setLastGameType(featuredMod.getTechnicalName());
    preferencesService.storeInBackground();
  }

  private void initMapPoolList() {
    mapPoolListView.setCellFactory(param ->
        new StringListCell<>((q) -> i18n.get(q.getLeaderboard().getNameKey()))
    );
    mapPoolListView.visibleProperty().bind(rankedEnabledCheckBox.selectedProperty());
    rankedEnabledCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (featuredModListView.getSelectionModel().getSelectedItem() != null) {
        setAvailableMaps(featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName());
      }
      preferencesService.getPreferences().getLastGame().setLastGameRankedEnabled(newValue);
      preferencesService.storeInBackground();
    });

    mapPoolListView.getSelectionModel().selectedItemProperty().addListener((obs,oldValue,newValue) -> {
      setAvailableMaps(featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName());
    });
  }

  private void initRatingBoundaries() {
    Integer lastGameMinRating = preferencesService.getPreferences().getLastGame().getLastGameMinRating();
    Integer lastGameMaxRating = preferencesService.getPreferences().getLastGame().getLastGameMaxRating();

    if (lastGameMinRating != null) {
      minRankingTextField.setText(i18n.number(lastGameMinRating));
    }

    if (lastGameMaxRating != null) {
      maxRankingTextField.setText(i18n.number(lastGameMaxRating));
    }

    minRankingTextField.textProperty().addListener((observable, oldValue, newValue) -> {
      Integer minRating = null;
      if (!newValue.isEmpty()) {
        minRating = Integer.parseInt(newValue);
      }

      preferencesService.getPreferences().getLastGame().setLastGameMinRating(minRating);
      preferencesService.storeInBackground();
    });
    maxRankingTextField.textProperty().addListener((observable, oldValue, newValue) -> {
      Integer maxRating = null;
      if (!newValue.isEmpty()) {
        maxRating = Integer.parseInt(newValue);
      }
      preferencesService.getPreferences().getLastGame().setLastGameMaxRating(maxRating);
      preferencesService.storeInBackground();
    });

    enforceRankingCheckBox.selectedProperty()
        .bindBidirectional(preferencesService.getPreferences().getLastGame().lastGameEnforceRatingProperty());
    enforceRankingCheckBox.selectedProperty().addListener(observable -> preferencesService.storeInBackground());
    enforceRankingContainer.disableProperty().bind(rankedEnabledCheckBox.selectedProperty().not().or(
        rankedEnabledCheckBox.disabledProperty()));
    rankingRangeContainer.disableProperty().bind(enforceRankingContainer.disabledProperty());
  }

  private void selectAppropriateMap() {
    String someMap = preferencesService.getPreferences().getLastGame().getLastMap();
    if (contextGameProperty.get() != null) {
      someMap = contextGameProperty.get().getMapName();
    }
    else if (gameService.getCurrentGame() != null) {
      someMap = gameService.getCurrentGame().getMapName();
    }
    for (MapBean mapBean : mapListView.getItems()) {
      if (mapBean.getMapName().equalsIgnoreCase(someMap)) {
        mapListView.getSelectionModel().select(mapBean);
        mapListView.scrollTo(mapBean);
        return;
      }
    }

    if (mapListView.getItems().isEmpty()) {
      setSelectedMap(null, null, 10);
    }
    else {
      mapListView.getSelectionModel().selectFirst();
      mapListView.scrollTo(0);
    }
  }

  public void setGameTitle() {
    if ("UPDATE_GW".equals(getInteractionLevel()) && gameService.getCurrentGame() != null) {
      titleTextField.setText(gameService.getCurrentGame().getTitle());
    }
    else {
      titleTextField.setText(Strings.nullToEmpty(preferencesService.getPreferences().getLastGame().getLastGameTitle()));
    }
    validateTitle(titleTextField.getText());
  }

  private void selectLastOrDefaultGameType() {
    JavaFxUtil.assertApplicationThread();
    String lastGameMod = preferencesService.getPreferences().getLastGame().getLastGameType();
    if (gameService.getCurrentGame() != null) {
      lastGameMod = gameService.getCurrentGame().getFeaturedMod();
    }
    if (lastGameMod == null) {
      lastGameMod = KnownFeaturedMod.DEFAULT.getTechnicalName();
    }

    for (FeaturedMod mod : featuredModListView.getItems()) {
      if (Objects.equals(mod.getTechnicalName(), lastGameMod)) {
        featuredModListView.getSelectionModel().select(mod);
        featuredModListView.scrollTo(mod);
        break;
      }
    }
  }

  public void onRandomMapButtonClicked() {
    int mapIndex = (int) (Math.random() * filteredMapBeans.size());
    mapListView.getSelectionModel().select(mapIndex);
    mapListView.scrollTo(mapIndex);
  }

  public void onCreateButtonClicked() {
    MapBean selectedMap = mapListView.getSelectionModel().getSelectedItem();
    onCloseButtonClicked();
    if (mapService.isOfficialMap(selectedMap.getMapName())) {
      hostGame(selectedMap);
    } else {
      String modTechnical = featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName();
      mapService.optionalEnsureMapLatestVersion(modTechnical, selectedMap)
            .exceptionally(throwable -> {
              log.error("error when updating the map", throwable);
              return selectedMap;
            })
            .thenApply(ensuredMap -> ensuredMap == null ? selectedMap : ensuredMap)
            .thenAccept(this::hostGame);
    }
  }

  private void hostGame(MapBean map) {
    Integer minRating = null;
    Integer maxRating = null;

    boolean enforceRating = enforceRankingCheckBox.isSelected() && rankedEnabledCheckBox.isSelected() && !rankedEnabledCheckBox.isDisabled();
    boolean enableRatingRange = rankedEnabledCheckBox.isSelected() && !rankedEnabledCheckBox.isDisabled();

    if (enableRatingRange && !minRankingTextField.getText().isEmpty()) {
      minRating = Integer.parseInt(minRankingTextField.getText());
    }

    if (enableRatingRange && !maxRankingTextField.getText().isEmpty()) {
      maxRating = Integer.parseInt(maxRankingTextField.getText());
    }

    NewGameInfo newGameInfo = new NewGameInfo(
        titleTextField.getText(),
        Strings.emptyToNull(passwordTextField.getText()),
        featuredModListView.getSelectionModel().getSelectedItem(),
        featuredModListView.getSelectionModel().getSelectedItem().getGitBranch(),
        map.getMapName(),
        emptySet(),
        onlyForFriendsCheckBox.isSelected() ? GameVisibility.PRIVATE : GameVisibility.PUBLIC,
        minRating,
        maxRating,
        enforceRating,
        liveReplayOptionComboBox.getSelectionModel().getSelectedItem().getDelaySeconds(),
        rankedEnabledCheckBox.isSelected()
            ? mapPoolListView.getSelectionModel().getSelectedItem().getLeaderboard().getTechnicalName()
            : DEFAULT_RATING_TYPE,
        null,
        maxPlayersComboBox.getValue());

    gameService.hostGame(newGameInfo).exceptionally(throwable -> {
      log.warn("Game could not be hosted", throwable);
      notificationService.addImmediateErrorNotification(throwable, "game.create.failed");
      return null;
    });
  }

  public void onUpdateButtonClicked() {

    if (this.selectGalacticWarMapEvent.get() != null) {
      Optional<MapBean> selectedMap = getCurrentSelectedMap();
      selectedMap.ifPresent(mapBean ->
        notificationService.addNotification(new ImmediateNotification(i18n.get("galactic_war.confirm_change_map.title"),
            i18n.get("galactic_war.confirm_change_map.text",
                selectGalacticWarMapEvent.get().getPlanet().getName(), selectedMap.get().getMapName()),
            Severity.INFO,
            asList(
                new Action(i18n.get("yes"), ev -> {
                  fafService.setGalacticWarMap(
                      selectGalacticWarMapEvent.get().getGalaxyTechnicalName(), selectGalacticWarMapEvent.get().getPlanet().getName(), mapBean.getMapName()
                  );
                  onCloseButtonClicked();
                }),
                new Action(i18n.get("no"), ev -> {
                })
            )))
      );
      return;
    }

    Integer minRating;
    Integer maxRating;

    boolean enforceRating = enforceRankingCheckBox.isSelected() && rankedEnabledCheckBox.isSelected() && !rankedEnabledCheckBox.isDisabled();
    boolean enableRatingRange = rankedEnabledCheckBox.isSelected() && !rankedEnabledCheckBox.isDisabled();

    if (enableRatingRange && !minRankingTextField.getText().isEmpty()) {
      minRating = Integer.parseInt(minRankingTextField.getText());
    } else {
      minRating = null;
    }

    if (enableRatingRange && !maxRankingTextField.getText().isEmpty()) {
      maxRating = Integer.parseInt(maxRankingTextField.getText());
    } else {
      maxRating = null;
    }

    mapService.optionalEnsureMap(
        featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName(),
        mapListView.getSelectionModel().getSelectedItem(), null, null)
        .thenRun(() -> gameService.updateSettingsForStagingGame(
            titleTextField.getText(),
            mapListView.getSelectionModel().getSelectedItem().getMapName(),
            rankedEnabledCheckBox.isSelected()
                ? mapPoolListView.getSelectionModel().getSelectedItem().getLeaderboard().getTechnicalName()
                : DEFAULT_RATING_TYPE,
            liveReplayOptionComboBox.getSelectionModel().getSelectedItem(),
            passwordTextField.getText(),
            maxPlayersComboBox.getValue(),
            enforceRating, minRating, maxRating
            ));
  }

  public Pane getRoot() {
    return createGameRoot;
  }

  void resetMapSearch() {
    mapSearchTextField.clear();
  }

  void setMapSearch(String spec) {
    mapSearchTextField.setText(spec);
  }

  void setMapSearch(List<String> specs) {
    mapSearchTextField.setText("regex:" + String.join(";", specs));
  }

  /**
   * @return returns true of the map was found and false if not
   */
  boolean selectMap(String mapName) {
    Optional<MapBean> mapBeanOptional = mapListView.getItems().stream().filter(mapBean -> mapBean.getMapName().equalsIgnoreCase(mapName)).findAny();
    if (mapBeanOptional.isEmpty()) {
      return false;
    }
    mapListView.getSelectionModel().select(mapBeanOptional.get());
    mapListView.scrollTo(mapBeanOptional.get());
    return true;
  }

  /** Cleanup runnable for the most recent {@link #selectMapWhenAvailable} call. */
  private Runnable pendingMapSelectionCleanup;

  /**
   * Like {@link #selectMap(String)}, but if the map list isn't yet populated (because
   * a featured-mod switch is still loading installed maps asynchronously), installs a
   * one-shot listener that selects the map as soon as it appears. Watches both the
   * items property (replaced when the available-maps list is rebuilt) and the contents
   * of the current items list (mutated as installed maps are scanned).
   *
   * Any prior pending request is cancelled.
   */
  void selectMapWhenAvailable(String mapName) {
    if (pendingMapSelectionCleanup != null) {
      pendingMapSelectionCleanup.run();
      pendingMapSelectionCleanup = null;
    }

    if (selectMap(mapName)) {
      return;
    }

    final javafx.collections.ListChangeListener<MapBean>[] contentListenerRef = new javafx.collections.ListChangeListener[1];
    final ChangeListener<javafx.collections.ObservableList<MapBean>>[] propListenerRef = new ChangeListener[1];

    Runnable cleanup = () -> {
      if (mapListView.getItems() != null && contentListenerRef[0] != null) {
        mapListView.getItems().removeListener(contentListenerRef[0]);
      }
      if (propListenerRef[0] != null) {
        mapListView.itemsProperty().removeListener(propListenerRef[0]);
      }
      pendingMapSelectionCleanup = null;
    };

    contentListenerRef[0] = c -> {
      if (selectMap(mapName)) {
        cleanup.run();
      }
    };
    propListenerRef[0] = (obs, oldList, newList) -> {
      if (oldList != null) oldList.removeListener(contentListenerRef[0]);
      if (newList != null) newList.addListener(contentListenerRef[0]);
      if (selectMap(mapName)) {
        cleanup.run();
      }
    };

    mapListView.itemsProperty().addListener(propListenerRef[0]);
    if (mapListView.getItems() != null) {
      mapListView.getItems().addListener(contentListenerRef[0]);
    }
    pendingMapSelectionCleanup = cleanup;

    // Race guard: items may have been populated between our first try and listener install.
    if (selectMap(mapName)) {
      cleanup.run();
    }
  }

  void setOnCloseButtonClickedListener(Runnable onCloseButtonClickedListener) {
    this.onCloseButtonClickedListener = onCloseButtonClickedListener;
  }

  public void onMapAPreviewPaneClicked(MouseEvent mouseEvent) {
    ContextMenu contextMenu = new ContextMenu();
    MenuItem menuItem = new MenuItem();
    menuItem.setText(i18n.get("map.preview.update"));
    menuItem.setOnAction((param) -> {
      mapService.resetPreviews(mapListView.getSelectionModel().getSelectedItem().getMapName());
      setSelectedMap(
          mapListView.getSelectionModel().getSelectedItem(),
          mapPreviewTypeComboBox.getSelectionModel().getSelectedItem(),
          maxPlayersComboBox.getSelectionModel().getSelectedItem());
    });
    contextMenu.getItems().add(menuItem);

    menuItem = new MenuItem();
    menuItem.setText(i18n.get("map.preview.download"));
    menuItem.setOnAction((param) -> onDownloadMapButtonClicked(null));
    contextMenu.getItems().add(menuItem);

    contextMenu.show(this.getRoot().getScene().getWindow(), mouseEvent.getScreenX(), mouseEvent.getScreenY());
  }

  public void onDownloadMapButtonClicked(ActionEvent actionEvent) {
    mapService.ensureMapLatestVersion(
        featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName(),
        mapListView.getSelectionModel().getSelectedItem())
        .thenRun(() -> {
          mapService.resetPreviews(mapListView.getSelectionModel().getSelectedItem().getMapName());
          JavaFxUtil.runLater(() -> setSelectedMap(
              mapListView.getSelectionModel().getSelectedItem(),
              mapPreviewTypeComboBox.getSelectionModel().getSelectedItem(),
              maxPlayersComboBox.getSelectionModel().getSelectedItem()));
        });
  }

  public void onInstallSelectedModClicked(ActionEvent actionEvent) {
    FeaturedMod fm = null;
    if (featuredModListView.getSelectionModel().getSelectedItem() != null) {
      fm = featuredModListView.getSelectionModel().getSelectedItem();
    }
    if (fm != null) {
      CompletableFuture<Path> future = new CompletableFuture();
      eventBus.post(new GameDirectoryChooseEvent(fm.getTechnicalName(), future));
      future.thenRun(() -> Platform.runLater(() -> setButtonStatesForFeaturedMod(
          featuredModListView.getSelectionModel().getSelectedItem())));
    }
  }

  public void onOpenGameFolderClicked(ActionEvent actionEvent) {
    FeaturedMod fm = null;
    if (featuredModListView.getSelectionModel().getSelectedItem() != null) {
      fm = featuredModListView.getSelectionModel().getSelectedItem();
    }
    if (fm != null) {
      TotalAnnihilationPrefs taPrefs = preferencesService.getTotalAnnihilation(fm.getTechnicalName());
      if (Files.exists(taPrefs.getInstalledPath())) {
        platformService.reveal(taPrefs.getInstalledPath());
      }
    }
  }

  public void showMapPreviewContextButton(MouseEvent mouseEvent) {
    mapPreviewContextButton.setVisible(true);
  }

  public void hideMapPreviewContextButton(MouseEvent mouseEvent) {
    mapPreviewContextButton.setVisible(false);
  }

  public void setRankedEnabled(boolean enabled) {
    rankedEnabledCheckBox.setSelected(enabled);
  }

  public CompletableFuture<Void> setSelectedMod(String modTechnical) {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    modService.getFeaturedMod(modTechnical)
            .thenAccept(fm -> JavaFxUtil.runLater(() -> {
              OptionalInt idx = IntStream.range(0, featuredModListView.getItems().size())
                  .filter(i -> featuredModListView.getItems()
                      .get(i)
                      .getTechnicalName()
                      .equals(fm.getTechnicalName()))
                  .findFirst();
              if (idx.isPresent()) {
                featuredModListView.getSelectionModel().select(idx.getAsInt());
              }
              done.complete(null);
            }));
    return done;
  }

  public void setSelectedMapPool(Integer id) {
    Optional<MatchmakingQueue> queue = mapPoolListView.getItems().stream()
        .filter(q -> q.getQueueId() == id)
        .findAny();
    queue.ifPresent(matchmakingQueue -> mapPoolListView.getSelectionModel().select(matchmakingQueue));
  }

  public void setFriendsOnly(boolean friendsOnly) {
    this.onlyForFriendsCheckBox.setSelected(friendsOnly);
  }

  public void setPassword(String password) {
    this.passwordTextField.setText(password);
  }

  /**
   * Apply optional preset values from a HostGameEvent. Used when launching a tournament
   * game so the dialog comes up with the correct mod, team size, ranked mode, etc.
   * The transient title is set without persisting to the user's last-game preference.
   */
  public void applyHostGamePresets(HostGameEvent event) {
    if (event == null) return;

    if (event.getPresetFeaturedMod() != null) {
      setSelectedMod(event.getPresetFeaturedMod());
    }
    if (event.getPresetMaxPlayers() != null) {
      maxPlayersComboBox.getSelectionModel().select(event.getPresetMaxPlayers());
    }
    if (event.getPresetRanked() != null) {
      rankedEnabledCheckBox.setSelected(event.getPresetRanked());
    }
    if (event.getPresetFriendsOnly() != null) {
      onlyForFriendsCheckBox.setSelected(event.getPresetFriendsOnly());
    }
    if (event.getPresetEnforceRating() != null) {
      enforceRankingCheckBox.setSelected(event.getPresetEnforceRating());
    }
    if (event.getPresetMinRating() != null) {
      minRankingTextField.setText(event.getPresetMinRating());
    }
    if (event.getPresetMaxRating() != null) {
      maxRankingTextField.setText(event.getPresetMaxRating());
    }
    if (event.getPresetPassword() != null) {
      passwordTextField.setText(event.getPresetPassword());
    }
    if (event.getPresetTransientTitle() != null) {
      // Bypass the title-persistence listener so the user's saved title isn't overwritten
      suppressTitlePersistence = true;
      try {
        titleTextField.setText(event.getPresetTransientTitle());
      } finally {
        suppressTitlePersistence = false;
      }
    }
  }
}
