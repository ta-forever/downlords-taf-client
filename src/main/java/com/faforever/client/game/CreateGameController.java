package com.faforever.client.game;

import com.faforever.client.fa.FaStrings;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.DualStringListCell;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.StringListCell;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapBean.Type;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.map.MapSize;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.LastGamePrefs;
import com.faforever.client.preferences.PreferenceUpdateListener;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.GameStatus;
import com.faforever.client.teammatchmaking.MatchmakingQueue;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.preferences.event.GameDirectoryChooseEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
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
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.faforever.client.game.GameService.DEFAULT_RATING_TYPE;
import static com.faforever.client.net.ConnectionState.CONNECTED;
import static java.util.Collections.emptySet;
import static javafx.scene.layout.BackgroundPosition.CENTER;
import static javafx.scene.layout.BackgroundRepeat.NO_REPEAT;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class CreateGameController implements Controller<Pane> {

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
  public Pane createGameRoot;
  public Button createGameButton;
  public Button updateGameButton;
  public Button setGamePathButton;
  public VBox mapPreview;
  public Pane mapPreviewPane;
  public Label versionLabel;
  public Label hpiArchiveLabel;
  public ComboBox<PreviewType> mapPreviewTypeComboBox;
  public ComboBox<Integer> mapPreviewMaxPositionsComboBox;
  public CheckBox onlyForFriendsCheckBox;
  public ComboBox<LiveReplayOption> liveReplayOptionComboBox;
  public ListView<MatchmakingQueue> mapPoolListView;

  public CheckBox rankedEnabledCheckBox;
  public Label rankedEnabledLabel;
  @VisibleForTesting
  FilteredList<MapBean> filteredMapBeans;
  private Runnable onCloseButtonClickedListener;
  private PreferenceUpdateListener preferenceUpdateListener;

  private BooleanProperty validatedButtonsDisableProperty;
  private StringProperty interactionLevelProperty; // "CREATE", "UPDATE", "BROWSE"

  private ObjectProperty<Game> contextGameProperty; // is player actually creating a new game (contextGame==null), or inspecting settings for an existing game?

  /**
   * Remembers if the controller's init method was called, to avoid memory leaks by adding several listeners
   */
  private boolean initialized;

  void setContextGame(Game game) {
    log.info("[setContextGame] {}", game);
    if (game != null) {
      if (preferencesService.isGameExeValid(game.getFeaturedMod())) {
        this.featuredModListView.getItems().stream()
            .filter(mod -> mod.getTechnicalName().equals(game.getFeaturedMod()))
            .findAny()
            .ifPresent(mod -> this.featuredModListView.getSelectionModel().select(mod));
      }
      mapService.ensureMap(this.featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName(), game.getMapName(), game.getMapCrc(), game.getMapArchiveName(), null, null);
    }
    this.contextGameProperty.set(game);
  }

  public void initialize() {
    validatedButtonsDisableProperty = new SimpleBooleanProperty();
    interactionLevelProperty = new SimpleStringProperty();
    contextGameProperty = new SimpleObjectProperty<>();

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
        return previewType == null ? "null" : previewType.getDisplayName();
      }
      @Override
      public PreviewType fromString(String string) {
        throw new UnsupportedOperationException("Not supported");
      }
    });
    mapPreviewTypeComboBox.getSelectionModel().selectedItemProperty().addListener(
        (observable, oldValue, newValue) -> setSelectedMap(mapListView.getSelectionModel().getSelectedItem(), newValue, mapPreviewMaxPositionsComboBox.getSelectionModel().getSelectedItem()));

    mapPreviewMaxPositionsComboBox.getItems().setAll(IntStream.rangeClosed(2,10).boxed().collect(Collectors.toList()));
    mapPreviewMaxPositionsComboBox.getSelectionModel().select(0);
    mapPreviewMaxPositionsComboBox.setConverter(new StringConverter<>() {
      @Override
      public String toString(Integer maxPositions) {
        return String.valueOf(maxPositions);
      }
      @Override
      public Integer fromString(String string) {
        throw new UnsupportedOperationException("Not supported");
      }
    });
    mapPreviewMaxPositionsComboBox.getSelectionModel().selectedItemProperty().addListener(
        (observable, oldValue, newValue) -> setSelectedMap(mapListView.getSelectionModel().getSelectedItem(), mapPreviewTypeComboBox.getSelectionModel().getSelectedItem(), newValue));

    mapSearchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue.isEmpty()) {
        filteredMapBeans.setPredicate(null);
      } else {
        filteredMapBeans.setPredicate(mapInfoBean -> mapInfoBean.getMapName().toLowerCase().contains(newValue.toLowerCase()));
      }
      if (!filteredMapBeans.isEmpty()) {
        mapListView.getSelectionModel().select(0);
      }
    });
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

    final String activeMod;
    if (featuredModListView.getFocusModel().getFocusedItem() == null) {
      activeMod = KnownFeaturedMod.DEFAULT.getTechnicalName();
    }
     else {
      activeMod = featuredModListView.getFocusModel().getFocusedItem().getTechnicalName();
    }

    if (preferencesService.getTotalAnnihilation(activeMod).getInstalledPath() == null) {
      preferenceUpdateListener = preferences -> {
        if (!initialized && preferencesService.getTotalAnnihilation(activeMod).getInstalledPath() != null ) {
          initialized = true;
          JavaFxUtil.runLater(this::init);
        }
      };
      preferencesService.addUpdateListener(new WeakReference<>(preferenceUpdateListener));
    } else {
      init();
    }
  }

  public void onCloseButtonClicked() {
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
      } else if (featuredModListView.getSelectionModel().getSelectedItem() == null) {
        return i18n.get("game.create.featuredModMissing");
      }
      return nominalText;
    }, titleTextField.textProperty(), featuredModListView.getSelectionModel().selectedItemProperty(), fafService.connectionStateProperty());
  }

  String calcInteractionLevel() {
    if (gameService.getCurrentGame() == null || !playerService.getCurrentPlayer().isPresent() || contextGameProperty.get() == null) {
      return "CREATE";
    }
    final String currentPlayer = playerService.getCurrentPlayer().get().getUsername();
    final boolean isCurrentGameSameAsContextGame = contextGameProperty.get().getId() == gameService.getCurrentGame().getId();
    final boolean isPlayerHost = gameService.getCurrentGame().getHost().equals(currentPlayer);
    final boolean isGameStaging = gameService.getCurrentGame().getStatus() == GameStatus.STAGING;
    if (isCurrentGameSameAsContextGame && isPlayerHost && isGameStaging) {
      return "UPDATE";
    }
    else {
      return "BROWSE";
    }
  }

  private void init() {
    bindGameVisibility();
    bindLiveReplayDelayOption();
    initMapSelection(KnownFeaturedMod.DEFAULT.getBaseGameName());
    initFeaturedModList();
    initMapPoolList();
    initRatingBoundaries();
    selectAppropriateMap();
    setLastGameTitle();
    initPassword();
    initMapListListeners();

    titleTextField.textProperty().addListener((observable, oldValue, newValue) -> {
      preferencesService.getPreferences().getLastGame().setLastGameTitle(newValue);
      preferencesService.storeInBackground();
      validateTitle(newValue);
    });
    validateTitle(titleTextField.getText());

    createGameButton.textProperty().bind(createValidatedButtonTextBinding(i18n.get("game.create.create")));
    updateGameButton.textProperty().bind(createValidatedButtonTextBinding(i18n.get("game.create.update")));

    validatedButtonsDisableProperty.bind(
        titleTextField.textProperty().isEmpty()
            .or(featuredModListView.getSelectionModel().selectedItemProperty().isNull())
            .or(fafService.connectionStateProperty().isNotEqualTo(CONNECTED))
            .or(mapListView.getSelectionModel().selectedItemProperty().isNull())
    );
    interactionLevelProperty.bind(Bindings.createStringBinding(() -> calcInteractionLevel(), gameService.getCurrentGameStatusProperty(), contextGameProperty));

    createGameButton.disableProperty().bind(validatedButtonsDisableProperty);
    updateGameButton.disableProperty().bind(interactionLevelProperty.isEqualTo("BROWSE"));
    mapPoolListView.disableProperty().bind(interactionLevelProperty.isEqualTo("BROWSE"));

    rankedEnabledCheckBox.disableProperty().bind(interactionLevelProperty.isEqualTo("BROWSE"));
    rankedEnabledCheckBox.setSelected(preferencesService.getPreferences().getLastGame().getLastGameRankedEnabled());
    rankedEnabledLabel.disableProperty().bind(interactionLevelProperty.isEqualTo("BROWSE"));

    createGameButton.visibleProperty().bind(interactionLevelProperty.isEqualTo("CREATE"));
    updateGameButton.visibleProperty().bind(createGameButton.visibleProperty().not());
    titleTextField.disableProperty().bind(updateGameButton.visibleProperty());
    onlyForFriendsCheckBox.disableProperty().bind(updateGameButton.visibleProperty());
    liveReplayOptionComboBox.disableProperty().bind(updateGameButton.visibleProperty());
    featuredModListView.disableProperty().bind(updateGameButton.visibleProperty());
    setGamePathButton.disableProperty().bind(updateGameButton.visibleProperty());
    passwordTextField.disableProperty().bind(updateGameButton.visibleProperty());
    minRankingTextField.disableProperty().bind(updateGameButton.visibleProperty());
    maxRankingTextField.disableProperty().bind(updateGameButton.visibleProperty());
  }

  public void sever() {
    createGameButton.textProperty().unbind();
    updateGameButton.textProperty().unbind();
    validatedButtonsDisableProperty.unbind();
    interactionLevelProperty.unbind();
    onlyForFriendsCheckBox.selectedProperty().unbind();
    enforceRankingCheckBox.selectedProperty().unbind();
  }

  private void validateTitle(String gameTitle) {
    titleTextField.pseudoClassStateChanged(PSEUDO_CLASS_INVALID, Strings.isNullOrEmpty(gameTitle));
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

  protected void initMapSelection(String modTechnical) {
    setAvailableMaps(modTechnical);
    mapListView.setCellFactory(param -> new StringListCell<>(MapBean::getMapName));
    ChangeListener<MapBean> selectedMapChangeListener = (observable, oldValue, newValue) -> JavaFxUtil.runLater(() -> {
      PreviewType previewType = mapPreviewTypeComboBox.getSelectionModel().getSelectedItem();
      Integer maxPositions = mapPreviewMaxPositionsComboBox.getSelectionModel().getSelectedItem();
      setSelectedMap(newValue, previewType, maxPositions);
    });
    mapListView.getSelectionModel().selectedItemProperty().addListener(selectedMapChangeListener);
    selectedMapChangeListener.changed(null, null, mapListView.getSelectionModel().selectedItemProperty().getValue());
  }

  private final List<ListChangeListener<MapBean>> installedMapsChangeListeners = new ArrayList<>();
  protected void initMapListListeners() {
    try {
      fafService.getFeaturedMods().get().stream().forEach(mod -> {
        ListChangeListener<MapBean> changeListener = ch -> JavaFxUtil.runLater(() -> {
          String activeMod = featuredModListView.getFocusModel().getFocusedItem().getTechnicalName();
          if (activeMod.equals(mod.getTechnicalName())) {
            setAvailableMaps(activeMod);
            selectAppropriateMap();
          }
        });
        installedMapsChangeListeners.add(changeListener);
        mapService.getInstalledMaps(mod.getTechnicalName()).addListener(new WeakListChangeListener<>(changeListener));
      });
    } catch (InterruptedException e) {
      log.error("[initMapListListeners] InterruptedException: {}", e.getMessage());
    } catch (ExecutionException e) {
      log.error("[initMapListListeners] ExecutionException: {}", e.getMessage());
    }
  }

  protected void setAvailableMaps(String modTechnical) {

    if (rankedEnabledCheckBox.isSelected() && mapPoolListView.getSelectionModel().getSelectedItem() != null &&
        !mapService.getInstalledMaps(modTechnical).isEmpty()) {
      try {
        MatchmakingQueue q = mapPoolListView.getSelectionModel().getSelectedItem();
        filteredMapBeans = new FilteredList<>(FXCollections.observableArrayList(mapService.getMatchmakerMaps(q).get())
            .sorted((o1, o2) -> o1.getMapName().compareToIgnoreCase(o2.getMapName())));
        doSetAvailableMaps(modTechnical, filteredMapBeans);

      } catch (Exception e) {
        log.error("[setAvailableMaps] {}", e.getMessage());
        filteredMapBeans = new FilteredList<>(mapService.getInstalledMaps(modTechnical)
            .filtered(mapBean -> mapBean.getType() == Type.SKIRMISH)
            .sorted((o1, o2) -> o1.getMapName().compareToIgnoreCase(o2.getMapName())));
        doSetAvailableMaps(modTechnical, filteredMapBeans);
      }
    }
    else {
      filteredMapBeans = new FilteredList<>(mapService.getInstalledMaps(modTechnical)
          .filtered(mapBean -> mapBean.getType() == Type.SKIRMISH)
          .sorted((o1, o2) -> o1.getMapName().compareToIgnoreCase(o2.getMapName())));
      doSetAvailableMaps(modTechnical, filteredMapBeans);
    }
  }

  protected void doSetAvailableMaps(String modTechnical, ObservableList<MapBean> items) {
    JavaFxUtil.runLater(() -> {
      Path installedExePath = preferencesService.getTotalAnnihilation(modTechnical).getInstalledExePath();
      if (filteredMapBeans.isEmpty() && installedExePath != null && Files.isExecutable(installedExePath)) {
        mapListView.setItems(mapService.getOfficialMaps());
      } else {
        mapListView.setItems(items);
      }
    });
  }

  protected void setAvailableMapPools(String modTechnical) {
    fafService.getMatchingQueuesByMod(modTechnical)
        .thenAccept(queues -> {
          JavaFxUtil.runLater(() -> {
            mapPoolListView.getItems().setAll(queues);
            if (mapPoolListView.getItems().size() > 0) {
              mapPoolListView.getSelectionModel().select(0);
            }
          });
          // just page the maps into cache so they're available should the user select them
          queues.forEach(mapService::getMatchmakerMaps);
        });
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
      featuredModListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> JavaFxUtil.runLater(() -> {
        setAvailableMapPools(newValue.getTechnicalName());
        setAvailableMaps(newValue.getTechnicalName());
        Path installedExePath = preferencesService.getTotalAnnihilation(newValue.getTechnicalName()).getInstalledExePath();
        setGamePathButton.setStyle(installedExePath == null || !Files.isExecutable(installedExePath)
            ? "-fx-background-color: -fx-accent"
            : "-fx-background-color: -fx-background");
        selectAppropriateMap();
        preferencesService.getPreferences().getLastGame().setLastGameType(newValue.getTechnicalName());
        preferencesService.storeInBackground();
      }));
      selectLastOrDefaultGameType();
    }));
  }

  private void initMapPoolList() {
    mapPoolListView.setCellFactory(param ->
        new StringListCell<>((q) -> i18n.get(q.getLeaderboard().getNameKey()))
    );
    mapPoolListView.visibleProperty().bind(rankedEnabledCheckBox.selectedProperty());
    rankedEnabledCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (featuredModListView.getSelectionModel().getSelectedItem() != null) {
        setAvailableMaps(featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName());
        selectAppropriateMap();
      }
      preferencesService.getPreferences().getLastGame().setLastGameRankedEnabled(newValue);
      preferencesService.storeInBackground();
    });

    mapPoolListView.getSelectionModel().selectedItemProperty().addListener((obs,oldValue,newValue) -> {
      setAvailableMaps(featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName());
      selectAppropriateMap();
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

  private void setLastGameTitle() {
    titleTextField.setText(Strings.nullToEmpty(preferencesService.getPreferences().getLastGame().getLastGameTitle()));
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
      mapService.updateLatestVersionIfNecessary(modTechnical, selectedMap)
          .exceptionally(throwable -> {
            log.error("error when updating the map", throwable);
            hostGame(selectedMap);
            return null;
          })
          .thenAccept(this::hostGame);
    }
  }

  private void hostGame(MapBean map) {
    Integer minRating = null;
    Integer maxRating = null;
    boolean enforceRating;

    if (!minRankingTextField.getText().isEmpty()) {
      minRating = Integer.parseInt(minRankingTextField.getText());
    }

    if (!maxRankingTextField.getText().isEmpty()) {
      maxRating = Integer.parseInt(maxRankingTextField.getText());
    }

    enforceRating = enforceRankingCheckBox.isSelected();

    NewGameInfo newGameInfo = new NewGameInfo(
        titleTextField.getText(),
        Strings.emptyToNull(passwordTextField.getText()),
        featuredModListView.getSelectionModel().getSelectedItem(),
        map.getMapName(),
        emptySet(),
        onlyForFriendsCheckBox.isSelected() ? GameVisibility.PRIVATE : GameVisibility.PUBLIC,
        minRating,
        maxRating,
        enforceRating,
        liveReplayOptionComboBox.getSelectionModel().getSelectedItem().getDelaySeconds(),
        rankedEnabledCheckBox.isSelected()
            ? mapPoolListView.getSelectionModel().getSelectedItem().getLeaderboard().getTechnicalName()
            : DEFAULT_RATING_TYPE);

    gameService.hostGame(newGameInfo).exceptionally(throwable -> {
      log.warn("Game could not be hosted", throwable);
      notificationService.addImmediateErrorNotification(throwable, "game.create.failed");
      return null;
    });
  }

  public void onUpdateButtonClicked() {
    gameService.updateSettingsForStagingGame(
        mapListView.getSelectionModel().getSelectedItem().getMapName(),
        rankedEnabledCheckBox.isSelected()
            ? mapPoolListView.getSelectionModel().getSelectedItem().getLeaderboard().getTechnicalName()
            : DEFAULT_RATING_TYPE);
  }

  public Pane getRoot() {
    return createGameRoot;
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

  void setOnCloseButtonClickedListener(Runnable onCloseButtonClickedListener) {
    this.onCloseButtonClickedListener = onCloseButtonClickedListener;
  }

  public void onSetGamePathClicked(ActionEvent actionEvent) {
    String modTechnicalName = KnownFeaturedMod.DEFAULT.getTechnicalName();
    if (featuredModListView.getSelectionModel().getSelectedItem() != null) {
      modTechnicalName = featuredModListView.getSelectionModel().getSelectedItem().getTechnicalName();
    }
    eventBus.post(new GameDirectoryChooseEvent(modTechnicalName));
  }

  public void onMapAPreviewPaneClicked(MouseEvent mouseEvent) {
    ContextMenu contextMenu = new ContextMenu();
    MenuItem menuItem = new MenuItem();
    menuItem.setText("Update preview");
    menuItem.setOnAction((param) -> {
      mapService.resetPreviews(mapListView.getSelectionModel().getSelectedItem().getMapName());
      setSelectedMap(
          mapListView.getSelectionModel().getSelectedItem(),
          mapPreviewTypeComboBox.getSelectionModel().getSelectedItem(),
          mapPreviewMaxPositionsComboBox.getSelectionModel().getSelectedItem());
    });
    contextMenu.getItems().add(menuItem);
    contextMenu.show(this.getRoot().getScene().getWindow(), mouseEvent.getScreenX(), mouseEvent.getScreenY());
  }
}
