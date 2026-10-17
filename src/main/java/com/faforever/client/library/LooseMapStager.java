package com.faforever.client.library;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Makes the maps that Open Annihilation installs as loose folders ({@code Maps/<pack>/maps/<name>.tnt}) readable
 * by maptool, which only reads archives: the pack is packed into a {@code .ufo} under a TAF-managed directory
 * that is then handed to maptool as its game path. The TA features archive is copied in beside it, as the
 * metal/geothermal/reclaim overlays need it. Approximate by design: nothing else of OA's layering is reproduced.
 */
@Slf4j
public final class LooseMapStager {

  static final String FEATURES_ARCHIVE = "TA_Features_2013.ccx";
  private static final String PACK_SEPARATOR = "@";

  private final Path mapsFolder;
  private final Path stageRoot;
  private final Path featuresArchive;

  /**
   * @param userFolder      OA's user folder (the one holding {@code Maps})
   * @param tafMapsCacheDir TAF's {@code <cache>/maps}, which holds the features archive; the staged archives go
   *                        under {@code library/staged}
   */
  public LooseMapStager(Path userFolder, Path tafMapsCacheDir) {
    this.mapsFolder = userFolder.resolve("Maps");
    this.stageRoot = tafMapsCacheDir.resolve("library").resolve("staged");
    this.featuresArchive = tafMapsCacheDir.resolve(FEATURES_ARCHIVE);
  }

  /** The loose pack folder that holds this map, preferring the pack the key names. */
  public Optional<Path> findPack(String mapKey) {
    String name = LibraryMapPreviews.mapName(mapKey);
    int at = mapKey.indexOf(PACK_SEPARATOR);
    if (at >= 0) {
      Path named = mapsFolder.resolve(mapKey.substring(at + 1));
      if (Files.isDirectory(named) && findFile(named.resolve("maps"), name, ".tnt").isPresent()) {
        return Optional.of(named);
      }
    }
    if (!Files.isDirectory(mapsFolder)) {
      return Optional.empty();
    }
    try (Stream<Path> packs = Files.list(mapsFolder)) {
      return packs.filter(Files::isDirectory).sorted()
          .filter(pack -> findFile(pack.resolve("maps"), name, ".tnt").isPresent())
          .findFirst();
    } catch (IOException e) {
      log.debug("[Library] cannot list {}: {}", mapsFolder, e.getMessage());
      return Optional.empty();
    }
  }

  /** The directory to give maptool as its game path for this map, packed up to date; empty if no loose pack has it. */
  public Optional<Path> stage(String mapKey) throws IOException {
    Optional<Path> found = findPack(mapKey);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    Path pack = found.get();
    String packId = pack.getFileName().toString();
    Path stage = stageRoot.resolve(packId);
    Path archive = stage.resolve(packId + ".ufo");
    synchronized (LooseMapStager.class) {
      if (isStale(pack, archive)) {
        Files.createDirectories(stage);
        HpiWriter.pack(pack, archive, LooseMapStager::packed);
      }
      if (Files.isRegularFile(featuresArchive) && !Files.exists(stage.resolve(FEATURES_ARCHIVE))) {
        Files.copy(featuresArchive, stage.resolve(FEATURES_ARCHIVE), StandardCopyOption.REPLACE_EXISTING);
      }
    }
    return Optional.of(stage);
  }

  /** The pack's own preview picture of the map, if it ships one. */
  public Optional<Path> packPreview(String mapKey) {
    return findPack(mapKey).flatMap(pack -> findFile(pack.resolve("previews"), LibraryMapPreviews.mapName(mapKey), ".png"));
  }

  private static boolean packed(String relativePath) {
    String lower = relativePath.toLowerCase(Locale.ROOT);
    return !lower.equals("oamap.yaml") && !lower.equals(".oa-origin.yaml") && !lower.endsWith(".png");
  }

  private static Optional<Path> findFile(Path folder, String stem, String extension) {
    if (!Files.isDirectory(folder)) {
      return Optional.empty();
    }
    try (Stream<Path> files = Files.list(folder)) {
      return files.filter(file -> file.getFileName().toString().equalsIgnoreCase(stem + extension)).findFirst();
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static boolean isStale(Path pack, Path archive) throws IOException {
    if (!Files.isRegularFile(archive)) {
      return true;
    }
    long built = Files.getLastModifiedTime(archive).toMillis();
    try (Stream<Path> files = Files.walk(pack)) {
      List<Path> newer = files.filter(Files::isRegularFile).filter(file -> {
        try {
          return Files.getLastModifiedTime(file).toMillis() > built;
        } catch (IOException e) {
          return true;
        }
      }).limit(1).toList();
      return !newer.isEmpty();
    }
  }
}
