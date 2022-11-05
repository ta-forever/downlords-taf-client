package com.faforever.client.fx;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class DefaultImageView extends ImageView {

  ObjectProperty<Image> backgroundLoadingImage;
  Image defaultImage;

  public DefaultImageView() {
    backgroundLoadingImage = new SimpleObjectProperty<>();
    backgroundLoadingImage.addListener((obs, oldImage, newImage) -> JavaFxUtil.runLater(() -> {
      if (newImage == null) {
        this.setImage(defaultImage);
      }
      else if (!newImage.isBackgroundLoading() && !newImage.isError()) {
        this.setImage(newImage);
      }
      else if (newImage.isBackgroundLoading()) {
        this.setImage(defaultImage);

        final DefaultImageView theImageView = this;
        ChangeListener<Number> imageProgressChangeListener = new ChangeListener<>() {
          @Override
          public void changed(ObservableValue<? extends Number> observable, Number oldProgress, Number newProgress) {
            if (newProgress.intValue() >= 1 && !newImage.isError()) {
              JavaFxUtil.runLater(() -> theImageView.setImage(newImage));
            }
            else if (newProgress.intValue() >= 1) {
              newImage.progressProperty().removeListener(this);
            }
          }
        };
        newImage.progressProperty().addListener(imageProgressChangeListener);
      }
    }));
  }

  public void setDefaultImage(Image image) {
    this.defaultImage = image;
    if (backgroundLoadingImage.get() == null || backgroundLoadingImage.get().isError()) {
      this.setImage(image);
    }
  }

  public void setBackgroundLoadingImage(Image image) {
    backgroundLoadingImage.setValue(image);
  }

  public Image getBackgroundLoadingImage() {
    return backgroundLoadingImage.getValue();
  }

  public ObjectProperty<Image> backgroundLoadingImageProperty() {
    return backgroundLoadingImage;
  }
}
