package com.faforever.client.library;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Chooses where the preview of a Library mod map comes from. The sources are tried in this order and the first
 * hit wins: (1) maptool over the Library content folders in OA's layer order (mod, then its expansions, then the
 * base game); (2) the preview of a TAF map of the same name; (3) for map pack maps, the registry catalogue's
 * preview PNG; (4) none. A source that fails counts as a miss. The sources are injected, so this holds only
 * the order and the fallbacks.
 */
@Slf4j
public final class LibraryMapPreviews {

  /** Makes a preview of the named map from the archives of one content folder; empty when the folder has no such map. */
  @FunctionalInterface
  public interface LayerSource {
    Optional<URI> find(Path layer, String mapName) throws Exception;
  }

  /** The preview of the TAF map with this name, if TAF has one. */
  @FunctionalInterface
  public interface TafMapSource {
    Optional<URI> find(String mapName) throws Exception;
  }

  /** The registry's preview of a pack map, by its key ({@code name@pack}). */
  @FunctionalInterface
  public interface RegistrySource {
    Optional<URI> find(String mapKey) throws Exception;
  }

  private static final String PACK_SEPARATOR = "@";

  private final LayerSource layerSource;
  private final TafMapSource tafMapSource;
  private final RegistrySource registrySource;

  public LibraryMapPreviews(LayerSource layerSource, TafMapSource tafMapSource, RegistrySource registrySource) {
    this.layerSource = layerSource;
    this.tafMapSource = tafMapSource;
    this.registrySource = registrySource;
  }

  /**
   * @param mapKey the map as OA names it: {@code name}, or {@code name@pack} for a map pack map
   * @param layers the content folders in OA's layer order, see {@link #layers}
   */
  public Optional<URI> find(String mapKey, List<Path> layers) {
    String name = mapName(mapKey);
    for (Path layer : layers) {
      Optional<URI> found = attempt("maptool in " + layer, () -> layerSource.find(layer, name));
      if (found.isPresent()) {
        return found;
      }
    }
    Optional<URI> found = attempt("TAF map " + name, () -> tafMapSource.find(name));
    if (found.isPresent() || !mapKey.contains(PACK_SEPARATOR)) {
      return found;
    }
    return attempt("registry " + mapKey, () -> registrySource.find(mapKey));
  }

  /** The map's own name: the key without its {@code @pack}. */
  public static String mapName(String mapKey) {
    int at = mapKey.indexOf(PACK_SEPARATOR);
    return at < 0 ? mapKey : mapKey.substring(0, at);
  }

  /**
   * The content folders of a mod's launch in OA's layer order: the mod's own folder, the folders of the
   * expansions it requires, then the base game. Folders that don't exist are left out.
   */
  public static List<Path> layers(Path userFolder, LibraryMod mod) {
    List<Path> candidates = new ArrayList<>();
    candidates.add(mod.folder());
    for (String expansion : mod.requiredExpansions()) {
      candidates.add(userFolder.resolve("Expansions").resolve(expansion));
    }
    candidates.add(userFolder.resolve("Games").resolve(mod.baseGame()));
    return candidates.stream().filter(Files::isDirectory).toList();
  }

  @FunctionalInterface
  private interface Lookup {
    Optional<URI> run() throws Exception;
  }

  private static Optional<URI> attempt(String what, Lookup lookup) {
    try {
      return lookup.run();
    } catch (IOException e) {
      log.debug("[Library] no preview from {}: {}", what, e.getMessage());
    } catch (Exception e) {
      log.warn("[Library] preview source failed: {}", what, e);
    }
    return Optional.empty();
  }
}
