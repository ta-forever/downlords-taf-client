package com.faforever.client.remote;

import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.preferences.PreferencesService;
import javafx.scene.image.Image;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static com.github.nocatch.NoCatch.noCatch;


@Lazy
@Service
@RequiredArgsConstructor
public class AssetService {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PreferencesService preferencesService;

  @Nullable
  public Image loadAndCacheImage(URL url, Path cacheSubFolder, @Nullable Supplier<Image> defaultSupplier) {
    return loadAndCacheImage(url, cacheSubFolder, defaultSupplier, 0, 0);
  }

  @Nullable
  public Image loadAndCacheImage(URL url, Path cacheSubFolder, @Nullable Supplier<Image> defaultSupplier, int width, int height) {
    if (url == null) {
      if (defaultSupplier == null) {
        return null;
      }
      return defaultSupplier.get();
    }

    String urlString = url.toString();
    try {
      urlString = java.net.URLDecoder.decode(urlString, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException ignored) {
    }

    String filename = urlString.substring(urlString.lastIndexOf('/') + 1);
    Path cachePath = preferencesService.getCacheDirectory().resolve(cacheSubFolder).resolve(filename);
    if (Files.exists(cachePath)) {
      logger.debug("Using cached image: {}", cachePath);
      return new Image(noCatch(() -> cachePath.toUri().toURL().toExternalForm()), width, height, true, true);
    }

    logger.info("Fetching image from: {}", url);
    Image image = new Image(url.toString(), width, height, true, true, true);
    JavaFxUtil.persistImage(image, cachePath, filename.substring(filename.lastIndexOf('.') + 1));
    return image;
  }
}
