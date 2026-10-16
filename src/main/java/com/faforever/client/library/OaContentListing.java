package com.faforever.client.library;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * What {@code open-annihilation --list-content --json} prints: the maps, mods and expansions OA can play.
 * Map {@code key}s are what a launch file names ({@code name@pack} for a pack map); {@code shownName} is what
 * players see.
 */
public record OaContentListing(List<ListedMap> maps, List<ListedItem> mods, List<ListedItem> expansions) {

  public record ListedMap(String shownName, String key, int players, String size) {
  }

  public record ListedItem(String id, String name) {
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * @param output the process's standard output. OA's log lines may come before the listing, which is the last
   *               line that begins with an opening brace.
   * @throws IllegalArgumentException when there is no listing in the output
   */
  public static OaContentListing parse(String output) {
    String json = null;
    for (String line : output.split("\\R")) {
      if (line.stripLeading().startsWith("{")) {
        json = line;
      }
    }
    if (json == null) {
      throw new IllegalArgumentException("No content listing in: " + abbreviate(output));
    }
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unreadable content listing: " + abbreviate(json), e);
    }
    List<ListedMap> maps = new ArrayList<>();
    for (JsonNode map : root.path("maps")) {
      maps.add(new ListedMap(map.path("shown-name").asText(map.path("key").asText()), map.path("key").asText(),
          map.path("players").asInt(), map.path("size").asText("")));
    }
    return new OaContentListing(maps, items(root.path("mods")), items(root.path("expansions")));
  }

  private static List<ListedItem> items(JsonNode array) {
    List<ListedItem> items = new ArrayList<>();
    for (JsonNode item : array) {
      items.add(new ListedItem(item.path("id").asText(), item.path("name").asText(item.path("id").asText())));
    }
    return items;
  }

  public boolean hasMod(String id) {
    return mods.stream().anyMatch(mod -> mod.id().equals(id));
  }

  private static String abbreviate(String text) {
    return text.length() <= 200 ? text : text.substring(0, 200) + "...";
  }
}
