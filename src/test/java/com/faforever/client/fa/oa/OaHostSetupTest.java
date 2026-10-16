package com.faforever.client.fa.oa;

import com.faforever.client.fa.oa.OaHostSetup.CommanderDeath;
import com.faforever.client.fa.oa.OaHostSetup.Settings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class OaHostSetupTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** A battle room that answers like OA's {@code --fark} endpoint for the controls the set-up touches. */
  private static class FakeRoom implements OaHostSetup.Fark {
    final List<String> maps;
    final int rows = 10;
    final int rowHeight = 14;
    final int[] mapButton = {404, 39, 96, 20};
    final int[] commanderButton = {510, 94, 120, 20};
    final int[] loadButton = {300, 400, 60, 20};
    final int[] backButton = {20, 400, 60, 20};
    final int[] list = {20, 100, 300, 140};
    final boolean mapDialogWorks;

    String roomMap;
    CommanderDeath commander = CommanderDeath.CONTINUES;
    boolean dialogOpen;
    int firstVisible;
    int selected = -1;
    int wheelEvents;

    FakeRoom(String roomMap, boolean mapDialogWorks, String... maps) {
      this.roomMap = roomMap;
      this.mapDialogWorks = mapDialogWorks;
      this.maps = List.of(maps);
    }

    @Override
    public JsonNode request(ObjectNode message) {
      ObjectNode answer = MAPPER.createObjectNode().put("ok", true);
      switch (message.path("op").asText()) {
        case "room" -> {
          answer.put("map", roomMap);
          answer.putObject("options").put("commander", commander.farkValue());
        }
        case "controls" -> {
          ArrayNode controls = answer.putArray("controls");
          control(controls, "MAP", mapButton);
          control(controls, "COMMANDER", commanderButton);
          if (dialogOpen) {
            control(controls, "LOAD", loadButton);
            control(controls, "PREVMENU", backButton);
            ObjectNode l = control(controls, "MAPNAMES", list);
            ArrayNode items = l.putArray("items");
            maps.forEach(items::add);
            l.put("first_visible", firstVisible).put("rows", rows).put("row_height", rowHeight)
                .put("selected", selected);
          }
        }
        case "input" -> handle(message.path("events"));
        default -> answer.put("ok", false);
      }
      return answer;
    }

    private static ObjectNode control(ArrayNode controls, String name, int[] rect) {
      ObjectNode c = controls.addObject().put("name", name);
      ArrayNode r = c.putArray("rect");
      for (int v : rect) {
        r.add(v);
      }
      return c;
    }

    private void handle(JsonNode events) {
      for (JsonNode event : events) {
        int x = event.path("x").asInt();
        int y = event.path("y").asInt();
        switch (event.path("kind").asText()) {
          case "wheel" -> {
            wheelEvents++;
            firstVisible = Math.max(0, Math.min(maps.size() - rows, firstVisible - event.path("dy").asInt()));
          }
          case "button_up" -> click(x, y);
          default -> {
          }
        }
      }
    }

    private void click(int x, int y) {
      if (inside(mapButton, x, y) && !dialogOpen) {
        dialogOpen = mapDialogWorks;
      } else if (inside(commanderButton, x, y)) {
        commander = CommanderDeath.values()[(commander.ordinal() + 1) % 3];
      } else if (dialogOpen && inside(backButton, x, y)) {
        dialogOpen = false;
      } else if (dialogOpen && inside(loadButton, x, y)) {
        if (selected >= 0) {
          roomMap = maps.get(selected);
        }
        dialogOpen = false;
      } else if (dialogOpen && inside(list, x, y)) {
        selected = firstVisible + (y - list[1]) / rowHeight;
      }
    }

    private static boolean inside(int[] r, int x, int y) {
      return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }
  }

  private static List<String> numbered(int count) {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      names.add(String.format("Map %03d", i));
    }
    return names;
  }

  private static OaHostSetup setupFor(FakeRoom room) {
    return new OaHostSetup(room, millis -> {
    });
  }

  @Test
  public void selectsAMapBelowTheFirstPageAndEndsOnCommanderDeath() throws IOException {
    FakeRoom room = new FakeRoom("Map 000", true, numbered(95).toArray(new String[0]));

    List<String> problems = setupFor(room).apply(new Settings("Map 077", CommanderDeath.ENDS));

    assertThat(problems, is(empty()));
    assertThat(room.roomMap, is("Map 077"));
    assertThat(room.commander, is(CommanderDeath.ENDS));
  }

  @Test
  public void scrollsBackUpToAMapAboveTheCurrentPage() throws IOException {
    FakeRoom room = new FakeRoom("Map 090", true, numbered(95).toArray(new String[0]));
    room.dialogOpen = false;

    // opening the dialog starts at the top, so the first target needs scrolling; the second needs it the other way
    assertThat(setupFor(room).apply(new Settings("Map 060", null)), is(empty()));
    room.firstVisible = 80;
    room.dialogOpen = false;
    assertThat(setupFor(room).apply(new Settings("Map 003", null)), is(empty()));
    assertThat(room.roomMap, is("Map 003"));
  }

  @Test
  public void leavesTheRoomAloneWhenItAlreadyHasTheMapAndRule() throws IOException {
    FakeRoom room = new FakeRoom("Map 004", true, numbered(20).toArray(new String[0]));
    room.commander = CommanderDeath.ENDS;

    List<String> problems = setupFor(room).apply(new Settings("Map 004", CommanderDeath.ENDS));

    assertThat(problems, is(empty()));
    assertThat(room.wheelEvents, is(0));
    assertThat(room.dialogOpen, is(false));
  }

  @Test
  public void cyclesTheCommanderRuleToDeathmatch() throws IOException {
    FakeRoom room = new FakeRoom("Map 000", true, numbered(5).toArray(new String[0]));

    assertThat(setupFor(room).apply(new Settings(null, CommanderDeath.DEATHMATCH)), is(empty()));
    assertThat(room.commander, is(CommanderDeath.DEATHMATCH));
  }

  @Test
  public void reportsAMapThatOaDoesNotList() throws IOException {
    FakeRoom room = new FakeRoom("Map 000", true, numbered(20).toArray(new String[0]));

    List<String> problems = setupFor(room).apply(new Settings("Not A Map", CommanderDeath.ENDS));

    assertThat(problems, hasSize(1));
    assertThat(problems.get(0), containsString("not in OA's list"));
    assertThat(room.dialogOpen, is(false));
    // the other setting is still applied
    assertThat(room.commander, is(CommanderDeath.ENDS));
  }

  @Test
  public void reportsAMapDialogThatNeverOpens() throws IOException {
    FakeRoom room = new FakeRoom("A Plethora of Ponds", false);

    List<String> problems = setupFor(room).apply(new Settings("Acid Foursome", null));

    assertThat(problems, contains("map list did not open (does the install have multiplayer maps?)"));
  }
}
