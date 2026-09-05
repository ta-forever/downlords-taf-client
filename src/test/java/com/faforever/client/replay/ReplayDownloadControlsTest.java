package com.faforever.client.replay;

import ch.micheljung.fxwindow.FxStage;
import ch.micheljung.waitomo.WaitomoTheme;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import javafx.scene.layout.HBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class ReplayDownloadControlsTest extends AbstractPlainJavaFxTest {

  @Test
  public void downloadMatchesNeighbouringButtonsWithTheApplicationTheme() throws Exception {
    ReplayDetailController controller = mock(ReplayDetailController.class);
    loadFxml("theme/vault/replay/replay_detail.fxml",
        type -> type == ReplayDetailController.class ? controller : mock(type));

    runOnFxThreadAndWait(() -> {
      HBox actions = (HBox) controller.replayAvailableContainer.getParent();
      getRoot().getChildren().setAll(actions);
      getScene().getStylesheets().setAll(
          FxStage.BASE_CSS.toExternalForm(),
          WaitomoTheme.WAITOMO_CSS.toExternalForm(),
          getThemeFileUrl("theme/colors.css").toExternalForm(),
          getThemeFileUrl("theme/icons.css").toExternalForm(),
          getThemeFileUrl("theme/style.css").toExternalForm(),
          getThemeFileUrl("theme/style_extension.css").toExternalForm());
      actions.applyCss();
      actions.autosize();
      actions.layout();
    });

    assertEquals(controller.watchButton.getHeight(), controller.downloadButton.getHeight(), 0.01);
    assertEquals(controller.unhideButton.getHeight(), controller.downloadButton.getHeight(), 0.01);
    assertEquals(4, controller.downloadButton.getItems().size());
    assertNull(controller.downloadButton.getTooltip());
  }
}
