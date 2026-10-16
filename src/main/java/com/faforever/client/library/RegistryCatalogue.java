package com.faforever.client.library;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** The mods listed by a registry's catalogue ({@code catalogue.json}); other package kinds are ignored. */
public record RegistryCatalogue(List<Entry> mods) {

  public record Entry(String id, String version, @Nullable String simHash) {
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** @throws IllegalArgumentException when the text is not a catalogue */
  public static RegistryCatalogue parse(String json) {
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unreadable catalogue", e);
    }
    if (root == null || !root.path("packages").isArray()) {
      throw new IllegalArgumentException("Not a catalogue");
    }
    List<Entry> mods = new ArrayList<>();
    for (JsonNode pkg : root.path("packages")) {
      if ("oamod".equals(pkg.path("kind").asText()) && pkg.hasNonNull("id")) {
        mods.add(new Entry(pkg.path("id").asText(), pkg.path("version").asText(""),
            pkg.hasNonNull("sim_hash") ? pkg.path("sim_hash").asText() : null));
      }
    }
    return new RegistryCatalogue(mods);
  }

  public boolean matches(LibraryMod mod, @Nullable String simHash) {
    return mods.stream().anyMatch(entry -> entry.id().equals(mod.id())
        && (entry.version().equals(mod.version()) || (simHash != null && simHash.equals(entry.simHash()))));
  }
}
