package com.faforever.client.vault;

import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.StringListCell;
import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.Tuple;
import com.faforever.client.vault.search.SearchController;
import com.faforever.client.vault.search.SearchController.SearchConfig;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Pagination;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public abstract class VaultEntityController<T> extends AbstractViewController<Node> {

  protected static final int TOP_ELEMENT_COUNT = 7;
  protected final UiService uiService;
  protected final NotificationService notificationService;
  protected final I18n i18n;
  protected final PreferencesService preferencesService;
  protected final ReportingService reportingService;
  protected final ModService modService;
  public Pane root;
  public StackPane vaultRoot;
  public HBox searchBox;
  public VBox searchResultGroup;
  public Pane searchResultPane;
  public Separator searchSeparator;
  public VBox showRoomGroup;
  public VBox loadingPane;
  public Button backButton;
  public Button refreshButton;
  public Button uploadButton;
  public HBox taInstallationBox;
  public ComboBox<TotalAnnihilationPrefs> taInstallationComboBox;
  public HBox paginationGroup;
  public ScrollPane scrollPane;
  public SearchController searchController;
  public Pagination pagination;
  public Button lastPageButton;
  public Button manageVaultButton;
  public Button firstPageButton;
  public SearchType searchType;
  public int pageSize;
  public ComboBox<Integer> perPageComboBox;
  protected ObjectProperty<State> state;
  protected CompletableFuture<Tuple<List<T>, Integer>> currentSupplier;

  public VaultEntityController(UiService uiService, NotificationService notificationService, I18n i18n, PreferencesService preferencesService, ReportingService reportingService, ModService modService) {
    this.uiService = uiService;
    this.notificationService = notificationService;
    this.i18n = i18n;
    this.preferencesService = preferencesService;
    this.reportingService = reportingService;
    this.modService = modService;

    state = new SimpleObjectProperty<>(State.UNINITIALIZED);
  }

  protected abstract void initSearchController();

  protected abstract Node getEntityCard(T t);

  protected abstract List<ShowRoomCategory> getShowRoomCategories();

  protected abstract void setSupplier(SearchConfig searchConfig);

  protected abstract void onUploadButtonClicked();

  protected abstract void onManageVaultButtonClicked();

  protected abstract Node getDetailView();

  protected abstract void onDisplayDetails(T t);

  protected abstract Class<? extends NavigateEvent> getDefaultNavigateEvent();

  protected abstract void handleSpecialNavigateEvent(NavigateEvent navigateEvent);

  @Override
  public void initialize() {
    super.initialize();
    JavaFxUtil.fixScrollSpeed(scrollPane);
    JavaFxUtil.bindManagedToVisible(loadingPane, searchResultGroup, backButton, refreshButton, pagination,
        firstPageButton, lastPageButton, showRoomGroup, searchBox, searchSeparator);

    firstPageButton.disableProperty().bind(pagination.currentPageIndexProperty().isEqualTo(0));
    lastPageButton.disableProperty().bind(pagination.currentPageIndexProperty()
        .isEqualTo(pagination.pageCountProperty().subtract(1)));

    backButton.setOnAction(event -> onBackButtonClicked());
    refreshButton.setOnAction(event -> onRefreshButtonClicked());
    uploadButton.setOnAction(event -> onUploadButtonClicked());
    manageVaultButton.setOnAction(event -> onManageVaultButtonClicked());

    searchController.setSearchListener(this::onSearch);
    perPageComboBox.getItems().addAll(5, 10, 20, 50, 100, 200);
    perPageComboBox.setValue(20);
    perPageComboBox.setOnAction((event -> changePerPageCount()));
    pageSize = perPageComboBox.getValue();

    initSearchController();

    BooleanBinding inSearchableState = Bindings.createBooleanBinding(() -> state.get() != State.SEARCHING, state);
    searchController.setSearchButtonDisabledCondition(inSearchableState);

    pagination.currentPageIndexProperty().addListener((observable, oldValue, newValue) -> {
      if (!oldValue.equals(newValue)) {
        SearchConfig searchConfig = searchController.getLastSearchConfig();
        onPageChange(searchConfig, false);
        pagination.setMaxPageIndicatorCount(10);
      }
        }
    );
    paginationGroup.managedProperty().bind(paginationGroup.visibleProperty());
    firstPageButton.setOnAction(event -> pagination.setCurrentPageIndex(0));
    lastPageButton.setOnAction(event -> pagination.setCurrentPageIndex(pagination.getPageCount() - 1));

    Node detailView = getDetailView();

    detailView.setVisible(false);
    detailView.requestFocus();

    vaultRoot.getChildren().add(detailView);
    AnchorPane.setTopAnchor(detailView, 0d);
    AnchorPane.setRightAnchor(detailView, 0d);
    AnchorPane.setBottomAnchor(detailView, 0d);
    AnchorPane.setLeftAnchor(detailView, 0d);

    taInstallationComboBox.setCellFactory(param -> new StringListCell<>(prefs -> String.format("%s [%s]", prefs.getInstalledPath().toString(), modService.getFeaturedModDisplayName(prefs.getModNameProperty().get()))));
    taInstallationComboBox.setButtonCell(new StringListCell<>(prefs -> String.format("Install Maps Into Folder: %s [%s]", prefs.getInstalledPath().toString(), modService.getFeaturedModDisplayName(prefs.getModNameProperty().get()))));
    ListChangeListener<TotalAnnihilationPrefs> taInstallationsListener = change -> Platform.runLater(
        () -> setTaInstallations(new ArrayList<>(change.getList())));
    preferencesService.getTotalAnnihilationAllMods().addListener(taInstallationsListener);

    setTaInstallations(preferencesService.getTotalAnnihilationAllMods());
    taInstallationComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue != null) {
        preferencesService.getPreferences().getLastGame().setLastGameType(newValue.getModNameProperty().get());
        preferencesService.storeInBackground();
        loadShowRoom();
      }
    });
  }

  protected void setTaInstallations(List<TotalAnnihilationPrefs> installations) {
    List<TotalAnnihilationPrefs> installedMods = installations.stream()
        .filter(prefs -> prefs != null && prefs.getInstalledPath() != null && Files.exists(prefs.getInstalledPath()))
        .distinct()
        .collect(Collectors.toList());
    taInstallationComboBox.getItems().setAll(installedMods);
    selectLastOrDefaultGameType();
  }

  private void selectLastOrDefaultGameType() {
    String lastGameMod = preferencesService.getPreferences().getLastGame().getLastGameType();
    if (lastGameMod == null) {
      lastGameMod = KnownFeaturedMod.DEFAULT.getTechnicalName();
    }

    String finalLastGameMod = lastGameMod;
    taInstallationComboBox.getItems().stream()
        .filter(item -> item.getModNameProperty().get().equals(finalLastGameMod))
        .findAny()
        .ifPresent(item -> {
          taInstallationComboBox.getSelectionModel().select(item);
        });
  }

  public String getSelectedTaModName() {
    TotalAnnihilationPrefs selectedPrefs = taInstallationComboBox.getSelectionModel().getSelectedItem();
    if (selectedPrefs == null) {
      return KnownFeaturedMod.DEFAULT.getTechnicalName();
    }
    else {
      return selectedPrefs.getModNameProperty().get();
    }
  }

  protected void loadShowRoom() {
    JavaFxUtil.assertApplicationThread();
    enterSearchingState();
    showRoomGroup.getChildren().clear();
    Object monitorForAddingFutures = new Object();
    List<ShowRoomCategory> showRoomCategories = getShowRoomCategories();
    AtomicReference<CompletableFuture<?>> loadingEntitiesFutureReference = new AtomicReference<>(CompletableFuture.completedFuture(null));

    List<VBox> childrenToAdd = showRoomCategories.parallelStream()
        .map(showRoomCategory -> {
          VaultEntityShowRoomController vaultEntityShowRoomController = loadShowRoom(showRoomCategory);
          VBox showRoomRoot = vaultEntityShowRoomController.getRoot();
          synchronized (monitorForAddingFutures) {
            loadingEntitiesFutureReference.set(loadingEntitiesFutureReference.get().thenCompose(ignored -> showRoomCategory.getEntitySupplier().get())
                .thenAccept(result -> {
                  showRoomRoot.managedProperty().bind(showRoomRoot.visibleProperty());
                  if (result.getFirst().isEmpty()) {
                    showRoomRoot.setVisible(false);
                  }
                  populate(result.getFirst(), vaultEntityShowRoomController.getPane());
                }));
          }
          return showRoomRoot;
        })
        .collect(Collectors.toList());

    loadingEntitiesFutureReference.get()
        .thenRun(() -> JavaFxUtil.runLater(() -> {
          showRoomGroup.getChildren().addAll(childrenToAdd);
          enterShowRoomState();
        }))
        .exceptionally(throwable -> {
          log.warn("Could not populate show room", throwable);
          return null;
        });
  }

  @NotNull
  private VaultEntityShowRoomController loadShowRoom(ShowRoomCategory showRoomCategory) {
    VaultEntityShowRoomController vaultEntityShowRoomController = uiService.loadFxml("theme/vault/vault_entity_show_room.fxml");
    vaultEntityShowRoomController.getLabel().setText(i18n.get(showRoomCategory.getI18nKey()));
    vaultEntityShowRoomController.getMoreButton().setOnAction(event -> {
      searchType = showRoomCategory.getSearchType();
      onFirstPageOpened(null);
    });
    return vaultEntityShowRoomController;
  }

  protected void changePerPageCount() {
    pageSize = perPageComboBox.getValue();
    if (state.get() == State.RESULT) {
      SearchConfig searchConfig = searchController.getLastSearchConfig();
      onPageChange(searchConfig, true);
    }
  }

  protected void enterSearchingState() {
    state.set(State.SEARCHING);

    showRoomGroup.setVisible(false);
    searchResultGroup.setVisible(false);
    loadingPane.setVisible(true);
    backButton.setVisible(false);
    paginationGroup.setVisible(false);
  }

  protected void enterResultState() {
    state.set(State.RESULT);

    showRoomGroup.setVisible(false);
    searchResultGroup.setVisible(true);
    loadingPane.setVisible(false);
    backButton.setVisible(true);
    paginationGroup.setVisible(true);
  }

  protected void enterShowRoomState() {
    state.set(State.SHOWROOM);

    showRoomGroup.setVisible(true);
    searchResultGroup.setVisible(false);
    loadingPane.setVisible(false);
    backButton.setVisible(false);
    paginationGroup.setVisible(false);
  }

  protected void onPageChange(SearchConfig searchConfig, boolean firstLoad) {
    enterSearchingState();
    setSupplier(searchConfig);
    displayFromSupplier(() -> currentSupplier, firstLoad);
  }

  protected void displaySearchResult(List<T> results) {
    JavaFxUtil.assertBackgroundThread();
    populate(results, searchResultPane);
    JavaFxUtil.runLater(this::enterResultState);
  }

  protected void displayFromSupplier(Supplier<CompletableFuture<Tuple<List<T>, Integer>>> supplier, boolean firstLoad) {
    supplier.get()
        .thenAccept(tuple -> {
          displaySearchResult(tuple.getFirst());
          if (firstLoad) {
            //when theres no search results the page count should be 1, 0 (which is returned) results in infinite pages
            JavaFxUtil.runLater(() -> pagination.setPageCount(Math.max(1, tuple.getSecond())));
          }
        })
        .exceptionally(throwable -> {
          log.error("Vault search error", throwable);
          notificationService.addImmediateErrorNotification(throwable, "vault.searchError");
          enterResultState();
          return null;
        });
  }

  protected void populate(List<T> results, Pane pane) {
    ObservableList<Node> children = pane.getChildren();
    List<Node> childrenToAdd = results.parallelStream()
        .map(this::getEntityCard)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    JavaFxUtil.runLater(() -> {
      children.clear();
      children.addAll(childrenToAdd);
      Object userData = pane.getUserData();
      if (userData == null) {
        return;
      }
      pane.getChildren().add((Node) userData);
    });
  }

  protected void onFirstPageOpened(SearchConfig searchConfig) {
    if (pagination.getCurrentPageIndex() != 0) {
      pagination.setCurrentPageIndex(0);
    }
    onPageChange(searchConfig, true);
  }

  protected void onSearch(SearchConfig searchConfig) {
    searchType = SearchType.SEARCH;
    onFirstPageOpened(searchConfig);
  }

  protected void onRefreshButtonClicked() {
    if (state.get() == State.RESULT) {
      onPageChange(searchController.getLastSearchConfig(), false);
    } else {
      loadShowRoom();
    }
  }

  protected void onBackButtonClicked() {
    loadShowRoom();
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    Class<? extends NavigateEvent> defaultNavigateEvent = getDefaultNavigateEvent();
    if (!(navigateEvent.getClass().equals(defaultNavigateEvent)) && !navigateEvent.getClass().equals(NavigateEvent.class)) {
      handleSpecialNavigateEvent(navigateEvent);
    } else if (state.get() == State.UNINITIALIZED) {
      loadShowRoom();
    }
  }

  @Override
  public Node getRoot() {
    return root;
  }

  protected enum State {
    SEARCHING, RESULT, UNINITIALIZED, SHOWROOM
  }

  public enum SearchType {
    SEARCH, OWN, NEWEST, HIGHEST_RATED, PLAYER, RECOMMENDED, MAP_POOL, PLAYED, HIGHEST_RATED_UI
  }

  @Value
  public class ShowRoomCategory {
    Supplier<CompletableFuture<Tuple<List<T>, Integer>>> entitySupplier;
    SearchType searchType;
    String i18nKey;
  }
}
