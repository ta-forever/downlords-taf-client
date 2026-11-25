package com.faforever.client.uploader;

import javafx.scene.image.Image;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface ImageUploadService {

  CompletableFuture<String> uploadImageInBackground(Image image);

  /** Uploads the file's original bytes (so e.g. animated GIFs stay animated). See {@link ImageFileTypes}. */
  CompletableFuture<String> uploadImageFileInBackground(Path file);
}
