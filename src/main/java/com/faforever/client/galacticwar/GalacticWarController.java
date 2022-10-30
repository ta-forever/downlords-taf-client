package com.faforever.client.galacticwar;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameService;
import com.faforever.client.io.DownloadService;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.GalacticWarUpdateMessage;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.CompletableTask.Priority;
import com.faforever.client.task.TaskService;
import com.faforever.client.theme.UiService;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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

  public StackPane rootPane;
  public Label loadingIndicator;
  public ScrollPane galacticWarGraphContainer;
  public GalacticMapView galacticMapView;
  public VBox planetDetailContainer;

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
        final String propertiesFile = uiService.getThemeFile("theme/galactic_war/smartgraph.properties").substring(6);
        final String styleSheetFile = uiService.getThemeFile("theme/galactic_war/smartgraph.css");
        galacticMapView = GalacticMapView.fromFile(targetPath.toString(), propertiesFile, styleSheetFile, uiService);

        JavaFxUtil.runLater(() -> {
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
}
