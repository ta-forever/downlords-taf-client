package com.faforever.client.library;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the Library mods installed in Open Annihilation's user folder ({@code Mods/<id>/oamod.yaml}, or
 * {@code Mods/<id>-<version>} for versions installed side by side), keeping only the mods OA itself lists in
 * {@code --list-content --json}. Pure: folders and canned OA output in, a list out.
 *
 * <p>Dot-folders ({@code .backup}, {@code .oamod-lock}, staging folders) are never mods. A mod with a missing or
 * unreadable {@code oamod.yaml} is skipped.
 *
 * <p>Registry trust and hidden ids are applied afterwards by {@link LibraryTrust}.
 */
@Slf4j
public final class LibraryDiscovery {

  static final String MODS_FOLDER = "Mods";
  static final String PROFILE = "oamod.yaml";
  static final String ORIGIN = ".oa-origin.yaml";
  private static final Pattern NUMBER = Pattern.compile("\\d+");

  private LibraryDiscovery() {
  }

  public static List<LibraryMod> discover(@Nullable Path userFolder, OaContentListing listing) {
    if (userFolder == null) {
      return List.of();
    }
    Path modsFolder = userFolder.resolve(MODS_FOLDER);
    if (!Files.isDirectory(modsFolder)) {
      return List.of();
    }
    List<LibraryMod> mods = new ArrayList<>();
    try (Stream<Path> children = Files.list(modsFolder)) {
      for (Path folder : children.sorted().toList()) {
        String name = folder.getFileName().toString();
        if (name.startsWith(".") || !Files.isDirectory(folder)) {
          continue;
        }
        readMod(folder).filter(mod -> listing.hasMod(mod.id())).ifPresent(mods::add);
      }
    } catch (IOException e) {
      log.warn("Could not list {}", modsFolder, e);
      return List.of();
    }
    mods.sort(Comparator.comparing(LibraryMod::name, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(LibraryMod::id)
        .thenComparing(LibraryMod::version, LibraryDiscovery::compareVersions));
    return mods;
  }

  private static Optional<LibraryMod> readMod(Path folder) {
    Map<String, Object> profile = readYaml(folder.resolve(PROFILE));
    if (profile == null) {
      return Optional.empty();
    }
    String id = text(profile.get("id"));
    if (id == null) {
      log.debug("{} has no mod id, skipping", folder);
      return Optional.empty();
    }
    String name = text(profile.get("name"));
    String version = text(profile.get("version"));
    Integer revision = null;
    if (profile.get("packaging") instanceof Map<?, ?> packaging && packaging.get("revision") instanceof Number n) {
      revision = n.intValue();
    }
    String baseGame = LibraryMod.DEFAULT_BASE_GAME;
    List<String> supported = textList(profile.get("supportedGames"));
    if (!supported.isEmpty()) {
      baseGame = supported.get(0);
    }
    String registry = null;
    Map<String, Object> origin = readYaml(folder.resolve(ORIGIN));
    if (origin != null) {
      registry = text(origin.get("registry"));
    }
    return Optional.of(new LibraryMod(id, name == null ? id : name, version == null ? "" : version,
        revision, registry, baseGame, textList(profile.get("requiredExpansions")), folder));
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private static Map<String, Object> readYaml(Path file) {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
      return loaded instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    } catch (IOException | RuntimeException e) {
      log.debug("Unreadable {}: {}", file, e.getMessage());
      return null;
    }
  }

  @Nullable
  private static String text(Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString().trim();
    return text.isEmpty() ? null : text;
  }

  private static List<String> textList(Object value) {
    List<String> items = new ArrayList<>();
    if (value instanceof List<?> list) {
      for (Object item : list) {
        String text = text(item);
        if (text != null) {
          items.add(text);
        }
      }
    }
    return items;
  }

  /** Compares versions by their numeric parts, so 1.9 sorts before 1.10; falls back to text. */
  static int compareVersions(String a, String b) {
    Matcher x = NUMBER.matcher(a);
    Matcher y = NUMBER.matcher(b);
    while (x.find() && y.find()) {
      int c = Long.compare(Long.parseLong(x.group()), Long.parseLong(y.group()));
      if (c != 0) {
        return c;
      }
    }
    return a.compareTo(b);
  }
}
