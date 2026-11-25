package com.faforever.client.uploader;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Image file types that can be uploaded as-is (e.g. dropped into chat), keyed by file extension. */
public final class ImageFileTypes {

  private static final Map<String, String> CONTENT_TYPES = Map.of(
      "png", "image/png",
      "apng", "image/apng",
      "jpg", "image/jpeg",
      "jpeg", "image/jpeg",
      "gif", "image/gif",
      "bmp", "image/bmp",
      "webp", "image/webp",
      "tif", "image/tiff",
      "tiff", "image/tiff"
  );

  private ImageFileTypes() {
  }

  public static boolean isSupported(Path file) {
    return contentType(file).isPresent();
  }

  public static Optional<String> contentType(Path file) {
    return extension(file).map(CONTENT_TYPES::get);
  }

  public static Optional<String> extension(Path file) {
    String fileName = file.getFileName().toString();
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return Optional.empty();
    }
    return Optional.of(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
  }
}
