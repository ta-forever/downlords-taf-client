package com.faforever.client.fa.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * Sets up the host's battle room in Open Annihilation by clicking its controls through the
 * {@code --fark} automation endpoint: the map the TAF lobby announced and the commander-death rule.
 *
 * <p>This is a stand-in for the launch file OA does not read yet (upstream request: open-annihilation
 * issue 19); when OA takes the TA launch INI, this class goes away. Every step is verified against the
 * {@code room} request and a failure is reported, never thrown: the host can still pick by hand.
 */
@Slf4j
public class OaHostSetup {

  /** The commander-death choices of the battle room, in the order its button cycles through them. */
  public enum CommanderDeath {
    CONTINUES, ENDS, DEATHMATCH;

    String farkValue() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /**
   * @param map            the map's name as the TAF lobby announced it; null leaves OA's choice
   * @param commanderDeath the rule to select; null leaves OA's default
   */
  public record Settings(String map, CommanderDeath commanderDeath) {
    public static final Settings NONE = new Settings(null, null);

    public boolean isEmpty() {
      return map == null && commanderDeath == null;
    }
  }

  /** One request to the endpoint; {@link OaFarkClient} implements it and a test scripts it. */
  interface Fark {
    JsonNode request(ObjectNode message) throws IOException;
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int MAX_SCROLLS = 400;
  private static final int MAX_COMMANDER_CLICKS = 3;
  private static final long SETTLE_MS = 250;
  private static final long DIALOG_WAIT_MS = 3000;

  private final Fark fark;
  private final Pause pause;

  /** Waits; replaceable so a test does not sleep. */
  @FunctionalInterface
  interface Pause {
    void sleep(long millis) throws InterruptedException;
  }

  OaHostSetup(Fark fark) {
    this(fark, Thread::sleep);
  }

  OaHostSetup(Fark fark, Pause pause) {
    this.fark = fark;
    this.pause = pause;
  }

  /**
   * @return a problem for each step that did not take effect, empty when everything asked for is in place
   * @throws IOException when the endpoint fails
   */
  java.util.List<String> apply(Settings settings) throws IOException {
    java.util.List<String> problems = new java.util.ArrayList<>();
    try {
      if (settings.map() != null) {
        selectMap(settings.map()).ifPresent(problems::add);
      }
      if (settings.commanderDeath() != null) {
        selectCommanderDeath(settings.commanderDeath()).ifPresent(problems::add);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      problems.add("interrupted");
    }
    return problems;
  }

  private Optional<String> selectMap(String map) throws IOException, InterruptedException {
    if (map.equals(room().path("map").asText())) {
      return Optional.empty();
    }
    click("MAP");
    JsonNode list = waitForControl("MAPNAMES");
    if (list == null) {
      return Optional.of("map list did not open (does the install have multiplayer maps?)");
    }
    int index = indexOf(list.path("items"), map);
    if (index < 0) {
      closeMapDialog();
      return Optional.of("map '" + map + "' is not in OA's list of " + list.path("items").size());
    }
    for (int scrolls = 0; scrolls < MAX_SCROLLS; scrolls++) {
      int first = list.path("first_visible").asInt();
      if (index >= first && index < first + list.path("rows").asInt()) {
        break;
      }
      // the wheel's sign: -1 moves the list down (the M0 prototype's choice), +1 up
      wheel(list, index < first ? 1 : -1);
      list = control("MAPNAMES");
      if (list == null) {
        return Optional.of("map list closed while scrolling");
      }
    }
    int first = list.path("first_visible").asInt();
    if (index < first || index >= first + list.path("rows").asInt()) {
      return Optional.of("could not scroll to map '" + map + "'");
    }
    int[] rect = rect(list);
    int rowHeight = list.path("row_height").asInt(14);
    clickAt(rect[0] + 40, rect[1] + (index - first) * rowHeight + rowHeight / 2);
    pause.sleep(SETTLE_MS);
    list = control("MAPNAMES");
    if (list == null || list.path("selected").asInt(-1) != index) {
      return Optional.of("selecting map '" + map + "' did not take");
    }
    click("LOAD");
    pause.sleep(SETTLE_MS);
    String now = room().path("map").asText();
    return map.equals(now) ? Optional.empty() : Optional.of("room map is '" + now + "', wanted '" + map + "'");
  }

  /** Backs out of the map dialog without choosing; a dialog that has already gone is not an error. */
  private void closeMapDialog() throws IOException {
    if (control("PREVMENU") != null) {
      click("PREVMENU");
    }
  }

  private Optional<String> selectCommanderDeath(CommanderDeath wanted) throws IOException, InterruptedException {
    for (int clicks = 0; clicks <= MAX_COMMANDER_CLICKS; clicks++) {
      String now = room().path("options").path("commander").asText();
      if (wanted.farkValue().equals(now)) {
        return Optional.empty();
      }
      if (clicks < MAX_COMMANDER_CLICKS) {
        click("COMMANDER");
        pause.sleep(SETTLE_MS);
      }
    }
    return Optional.of("commander-death rule did not reach '" + wanted.farkValue() + "'");
  }

  private JsonNode room() throws IOException {
    return fark.request(MAPPER.createObjectNode().put("op", "room"));
  }

  private JsonNode control(String name) throws IOException {
    JsonNode controls = fark.request(MAPPER.createObjectNode().put("op", "controls")).path("controls");
    for (JsonNode control : controls) {
      if (name.equals(control.path("name").asText())) {
        return control;
      }
    }
    return null;
  }

  private JsonNode waitForControl(String name) throws IOException, InterruptedException {
    for (long waited = 0; waited <= DIALOG_WAIT_MS; waited += SETTLE_MS) {
      JsonNode control = control(name);
      if (control != null) {
        return control;
      }
      pause.sleep(SETTLE_MS);
    }
    return null;
  }

  private static int indexOf(JsonNode items, String name) {
    for (int i = 0; i < items.size(); i++) {
      if (name.equals(items.get(i).asText())) {
        return i;
      }
    }
    return -1;
  }

  private static int[] rect(JsonNode control) {
    JsonNode r = control.path("rect");
    return new int[]{r.path(0).asInt(), r.path(1).asInt(), r.path(2).asInt(), r.path(3).asInt()};
  }

  private void click(String name) throws IOException {
    JsonNode control = control(name);
    if (control == null) {
      throw new IOException("no control " + name);
    }
    int[] r = rect(control);
    clickAt(r[0] + r[2] / 2, r[1] + r[3] / 2);
  }

  private void clickAt(int x, int y) throws IOException {
    ObjectNode message = MAPPER.createObjectNode().put("op", "input");
    ArrayNode events = message.putArray("events");
    events.addObject().put("kind", "pointer_move").put("x", x).put("y", y);
    events.addObject().put("kind", "button_down").put("button", "left").put("x", x).put("y", y);
    events.addObject().put("kind", "button_up").put("button", "left").put("x", x).put("y", y);
    expectOk(fark.request(message));
  }

  private void wheel(JsonNode list, int dy) throws IOException {
    int[] r = rect(list);
    ObjectNode message = MAPPER.createObjectNode().put("op", "input");
    message.putArray("events").addObject().put("kind", "wheel").put("dx", 0).put("dy", dy)
        .put("x", r[0] + r[2] / 2).put("y", r[1] + r[3] / 2);
    expectOk(fark.request(message));
  }

  private static void expectOk(JsonNode answer) throws IOException {
    if (!answer.path("ok").asBoolean(false)) {
      throw new IOException("fark refused: " + answer.path("error").path("message").asText(answer.toString()));
    }
  }
}
