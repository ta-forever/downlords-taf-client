package com.faforever.client.game;

import com.faforever.client.fx.DefaultImageView;
import com.faforever.client.fx.Controller;
import com.faforever.client.theme.UiService;
import javafx.scene.control.TableCell;
import javafx.scene.image.Image;

public class MapPreviewTableCell extends TableCell<Game, Image> {

  private final DefaultImageView imageView;

  public MapPreviewTableCell(UiService uiService) {
    Controller<DefaultImageView> controller = uiService.loadFxml("theme/vault/map/map_preview_table_cell.fxml");
    imageView = controller.getRoot();
    setGraphic(imageView);
  }

  public MapPreviewTableCell setDefaultImage(Image image) {
    imageView.setDefaultImage(image);
    return this;
  }

  @Override
  protected void updateItem(Image item, boolean empty) {
    super.updateItem(item, empty);

    if (empty || item == null) {
      setText(null);
      setGraphic(null);
    } else {
      imageView.setBackgroundLoadingImage(item);
      setGraphic(imageView);
    }
  }
}

