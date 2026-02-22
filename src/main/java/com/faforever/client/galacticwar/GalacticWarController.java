package com.faforever.client.galacticwar;

import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.theme.UiService;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GalacticWarController extends AbstractViewController<Node> {

  final private GalacticWarService galacticWarService;
  final private UiService uiService;

  public StackPane rootPane;

  @FXML
  private TabPane galaxyTabs;

  @Override
  public Node getRoot() {
    return rootPane;
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
  }

  @Override
  public void initialize() {
    for (String url : galacticWarService.getGwEndpoints()) {
      Tab tab = createGalaxyTab(url);
      galaxyTabs.getTabs().add(tab);
    }
  }

  private Tab createGalaxyTab(String url) {
    GalaxyViewController controller = uiService.loadFxml("theme/galactic_war/galactic_war_galaxy_view.fxml");
    controller.setEndpointUrl(url);
    controller.updateLatestState();

    Tab tab = new Tab("", controller.getRoot());
    tab.textProperty().bind(controller.getDisplayNameProperty());
    tab.setClosable(false);
    return tab;
  }

}
