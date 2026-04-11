package com.faforever.client.tournament;

import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.ModService;
import com.faforever.client.preferences.PreferencesService;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TournamentsRootController extends AbstractViewController<Node> {

  private final I18n i18n;
  private final ModService modService;
  private final PreferencesService preferencesService;

  public AnchorPane tournamentsRootPane;
  public TabPane tournamentsTabPane;
  public ComboBox<ModFilterEntry> modFilterCombo;
  public javafx.scene.layout.HBox modFilterBar;

  public Node bracketsTab;
  public TournamentsController bracketsTabController;
  public Node hallOfFameTab;
  public HallOfFameController hallOfFameTabController;

  private NavigateEvent lastNavigateEvent;

  public TournamentsRootController(I18n i18n, ModService modService, PreferencesService preferencesService) {
    this.i18n = i18n;
    this.modService = modService;
    this.preferencesService = preferencesService;
  }

  @Override
  public Node getRoot() {
    return tournamentsRootPane;
  }

  @Override
  public void initialize() {
    if (bracketsTabController != null) {
      bracketsTabController.setRootController(this);
    }
    setupModFilterCombo();


    tournamentsTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
      if (newTab == null || lastNavigateEvent == null) return;
      AbstractViewController<?> controller = controllerForTab(newTab);
      if (controller != null) {
        controller.display(lastNavigateEvent);
      }
    });
  }

  private void setupModFilterCombo() {
    modFilterCombo.setButtonCell(new ListCell<>() {
      @Override protected void updateItem(ModFilterEntry item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? "" : item.label);
      }
    });
    modFilterCombo.setCellFactory(lv -> new ListCell<>() {
      @Override protected void updateItem(ModFilterEntry item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? "" : item.label);
      }
    });

    modService.getFeaturedMods().thenAccept(mods -> {
      com.faforever.client.fx.JavaFxUtil.runLater(() -> {
        List<ModFilterEntry> options = new ArrayList<>();
        options.add(new ModFilterEntry(null, null, i18n.get("hallOfFame.modFilter.allMods")));

        List<FeaturedMod> sorted = new ArrayList<>(mods);
        sorted.sort(Comparator.comparing(
            FeaturedMod::getDisplayName,
            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        for (FeaturedMod mod : sorted) {
          try {
            int modId = Integer.parseInt(mod.getId());
            options.add(new ModFilterEntry(modId, mod.getDisplayName(), mod.getDisplayName()));
          } catch (NumberFormatException ignored) {}
        }

        modFilterCombo.setItems(FXCollections.observableArrayList(options));

        // Restore persisted selection
        Integer savedModId = preferencesService.getPreferences().getTournament().getHallOfFameModId();
        ModFilterEntry initial = options.stream()
            .filter(e -> Objects.equals(e.modId, savedModId))
            .findFirst()
            .orElse(options.get(0));
        modFilterCombo.getSelectionModel().select(initial);

        // Notify sub-controllers of the initial value
        notifySubControllers(initial);

        // Listen for changes
        modFilterCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
          if (newVal == null) return;
          preferencesService.getPreferences().getTournament().setHallOfFameModId(newVal.modId);
          preferencesService.storeInBackground();
          notifySubControllers(newVal);
        });
      });
    }).exceptionally(throwable -> {
      log.warn("Failed to load featured mods for filter combo", throwable);
      return null;
    });
  }

  private void notifySubControllers(ModFilterEntry entry) {
    if (bracketsTabController != null) {
      bracketsTabController.onModFilterChanged(entry);
    }
    if (hallOfFameTabController != null) {
      hallOfFameTabController.onModFilterChanged(entry);
    }
  }

  /**
   * Called by sub-controllers when they need to force the filter to "All mods"
   * (e.g. when a notification navigates to a tournament not matching the current filter).
   */
  public void selectAllMods() {
    if (modFilterCombo.getItems().isEmpty()) return;
    modFilterCombo.getSelectionModel().selectFirst();
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    lastNavigateEvent = navigateEvent;
    Tab selected = tournamentsTabPane.getSelectionModel().getSelectedItem();
    AbstractViewController<?> controller = controllerForTab(selected);
    if (controller != null) {
      controller.display(navigateEvent);
    }
  }

  @Override
  public void onHide() {
    Tab selected = tournamentsTabPane.getSelectionModel().getSelectedItem();
    AbstractViewController<?> controller = controllerForTab(selected);
    if (controller != null) {
      controller.hide();
    }
  }

  private AbstractViewController<?> controllerForTab(Tab tab) {
    if (tab == null) return null;
    Node content = tab.getContent();
    if (content == bracketsTab) return bracketsTabController;
    if (content == hallOfFameTab) return hallOfFameTabController;
    return null;
  }

}
