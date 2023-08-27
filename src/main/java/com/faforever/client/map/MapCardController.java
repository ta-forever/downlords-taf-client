package com.faforever.client.map;

import com.faforever.client.fx.DefaultImageView;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.CompletableTask.Priority;
import com.faforever.client.task.TaskService;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.IdenticonUtil;
import com.faforever.client.vault.review.Review;
import com.faforever.client.vault.review.StarsController;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Consumer;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class MapCardController implements Controller<Node> {

  private final MapService mapService;
  private final NotificationService notificationService;
  private final PreferencesService preferencesService;
  private final UiService uiService;
  private final I18n i18n;
  private final TaskService taskService;

  public DefaultImageView thumbnailImageView;
  public Label nameLabel;
  public Node mapTileRoot;
  public Label mapVersionLabel;
  public Label mapHpiArchiveNameLabel;
  public StarsController starsController;
  public Label numberOfReviewsLabel;
  public Label numberOfPlaysLabel;
  public Label sizeLabel;
  public Label maxPlayersLabel;
  public Button installButton;
  public Button uninstallButton;

  private MapBean map;

  private Consumer<MapBean> onOpenDetailListener;
  private ListChangeListener<MapBean> installStatusChangeListener;
  private final InvalidationListener reviewsChangedListener = observable -> populateReviews();

  public void initialize() {
    thumbnailImageView.setDefaultImage(uiService.getThemeImage(UiService.UNKNOWN_MAP_IMAGE));
    installButton.managedProperty().bind(installButton.visibleProperty());
    uninstallButton.managedProperty().bind(uninstallButton.visibleProperty());
    installStatusChangeListener = change -> {
      while (change.next()) {
        for (MapBean unInstalledMapBean : change.getRemoved()) {
          if (map.getMapName().equals(unInstalledMapBean.getMapName())) {
            setInstalled(false);
            return;
          }
        }
        for (MapBean installedMapBean : change.getAddedSubList()) {
          if (map.getMapName().equals(installedMapBean.getMapName())) {
            // it's potentially expensive to get the crc for the installed maps
            // so we only query the crc for installed maps if vault is actually being displayed
            if (getRoot().isVisible()) {
                String modTechnical = preferencesService.getPreferences().getLastGame().getLastGameType();
                mapService.isInstalled(modTechnical, map.getMapName(), map.getCrcValue()).thenAccept(
                    isInstalled -> JavaFxUtil.runLater(() -> setInstalled(isInstalled)));
            }
            return;
          }
        }
      }
    };

    // here we query the map's crc when we transition to displayed
    getRoot().visibleProperty().addListener((obs, oldValue, newValue) -> {
      if (newValue) {
        String modTechnical = preferencesService.getPreferences().getLastGame().getLastGameType();
        mapService.isInstalled(modTechnical, map.getMapName(), map.getCrcValue()).thenAccept(
            isInstalled -> JavaFxUtil.runLater(() -> setInstalled(isInstalled)));
      }
    });
  }

  public void setMap(MapBean map) {
    String modTechnical = preferencesService.getPreferences().getLastGame().getLastGameType();
    this.map = map;
    Image image;
    if (map.getThumbnailUrl() != null) {
      image = mapService.loadPreview(modTechnical, map, PreviewType.MINI, 10);
    } else {
      image = IdenticonUtil.createIdenticon(map.getId());
    }
    thumbnailImageView.setBackgroundLoadingImage(image);
    nameLabel.setText(map.getMapName());
    mapHpiArchiveNameLabel.setText(Optional.ofNullable(map.getHpiArchiveName()).orElse("<unknown archive>"));
    numberOfPlaysLabel.setText(i18n.number(map.getNumberOfPlays()));

    MapSize size = map.getSize();
    sizeLabel.setText(i18n.get("mapPreview.size", size.getWidthInKm(), size.getHeightInKm()));
    maxPlayersLabel.setText(i18n.number(map.getPlayers()));

    ObservableList<Review> reviews = map.getReviews();
    JavaFxUtil.addListener(reviews, new WeakInvalidationListener(reviewsChangedListener));
    reviewsChangedListener.invalidated(reviews);

    mapVersionLabel.setText(String.format("v%s / CRC32 %s",
        map.getVersion() != null ? map.getVersion().toString() : "?",
        map.getCrcValue() != null ? map.getCrcValue() : "????????"));
    mapVersionLabel.setVisible(map.getVersion() != null || map.getCrcValue() != null);

    if (mapService.isOfficialMap(map.getMapName())) {
      installButton.setVisible(false);
      uninstallButton.setVisible(false);
    } else {
      ObservableList<MapBean> installedMaps = mapService.getInstalledMaps(modTechnical);
      JavaFxUtil.addListener(installedMaps, new WeakListChangeListener<>(installStatusChangeListener));
      mapService.isInstalled(modTechnical, map.getMapName(), map.getCrcValue()).thenAccept(
          isInstalled -> JavaFxUtil.runLater(() -> setInstalled(isInstalled)));
    }
  }

  private void populateReviews() {
    JavaFxUtil.runLater(() -> {
      numberOfReviewsLabel.setText(i18n.number(map.getReviewsSummary().getReviews()));
      starsController.setValue(map.getReviewsSummary().getScore() / map.getReviewsSummary().getReviews());
    });
  }

  public void onInstallButtonClicked() {
    mapService.ensureMap(preferencesService.getPreferences().getLastGame().getLastGameType(), map, null, null)
        .thenRun(() -> setInstalled(true))
        .exceptionally(throwable -> {
          log.error("Map installation failed", throwable);
          notificationService.addImmediateErrorNotification(throwable, "mapVault.installationFailed",
              map.getMapName(), throwable.getLocalizedMessage());
          setInstalled(false);
          return null;
        });
  }

  public void onUninstallButtonClicked() {
    mapService.uninstallMap(preferencesService.getPreferences().getLastGame().getLastGameType(), map.getMapName(), map.getCrcValue())
        .thenRun(() -> setInstalled(false))
        .exceptionally(throwable -> {
          log.error("Could not delete map", throwable);
          notificationService.addImmediateErrorNotification(throwable, "mapVault.couldNotDeleteMap",
              map.getMapName(), throwable.getLocalizedMessage());
          setInstalled(true);
          return null;
        });
  }

  private void setInstalled(boolean installed) {
    installButton.setVisible(!installed);
    uninstallButton.setVisible(installed);
  }

  public Node getRoot() {
    return mapTileRoot;
  }

  public void setOnOpenDetailListener(Consumer<MapBean> onOpenDetailListener) {
    this.onOpenDetailListener = onOpenDetailListener;
  }

  public void onShowMapDetail() {
    onOpenDetailListener.accept(map);
  }
}
