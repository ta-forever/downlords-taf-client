package com.faforever.client.library;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The part of a registry's {@code catalogue.json} that map previews need: for every pack map, the URL of its
 * preview PNG (these are downloadable without a token).
 */
public final class RegistryMapPreviews {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Map<String, URI> previews;

  private RegistryMapPreviews(Map<String, URI> previews) {
    this.previews = previews;
  }

  /**
   * @param baseUrl the catalogue's own URL; the preview paths are resolved against it
   * @throws IllegalArgumentException when the text is not a catalogue
   */
  public static RegistryMapPreviews parse(String baseUrl, String json) {
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unreadable catalogue", e);
    }
    Map<String, URI> previews = new HashMap<>();
    for (JsonNode pack : root.path("packages")) {
      for (JsonNode map : pack.path("maps")) {
        String key = map.path("map").asText("");
        String preview = map.path("preview").asText("");
        if (!key.isEmpty() && !preview.isEmpty()) {
          previews.put(key, URI.create(baseUrl).resolve(preview));
        }
      }
    }
    return new RegistryMapPreviews(previews);
  }

  public Optional<URI> previewOf(String mapKey) {
    return Optional.ofNullable(previews.get(mapKey));
  }
}
