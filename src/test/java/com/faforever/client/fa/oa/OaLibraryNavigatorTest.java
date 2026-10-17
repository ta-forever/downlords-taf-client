package com.faforever.client.fa.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class OaLibraryNavigatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** An OA that answers like the {@code --fark} endpoint: a loading screen, then the main menu with its Library button. */
  private static class FakeOa implements OaHostSetup.Fark {
    int loadingAnswers;
    boolean buttonEnabled = true;
    boolean clickOpensLibrary = true;
    boolean dialogOpen;
    final List<int[]> clicks = new ArrayList<>();
    int screenRequests;

    FakeOa(int loadingAnswers) {
      this.loadingAnswers = loadingAnswers;
    }

    @Override
    public JsonNode request(ObjectNode message) throws IOException {
      ObjectNode answer = MAPPER.createObjectNode().put("ok", true);
      switch (message.path("op").asText()) {
        case "screen" -> {
          screenRequests++;
          boolean loading = loadingAnswers-- > 0;
          answer.put("screen", loading ? "intro" : "main_menu");
          ArrayNode dialogs = answer.putArray("dialogs");
          if (dialogOpen) {
            dialogs.add("oa.library");
          }
        }
        case "controls" -> {
          ArrayNode controls = answer.putArray("controls");
          control(controls, "SINGLE", new int[]{139, 393, 96, 20}, true);
          control(controls, "oa.button", new int[]{520, 420, 48, 48}, buttonEnabled);
        }
        case "input" -> {
          ArrayNode events = (ArrayNode) message.path("events");
          JsonNode down = events.get(1);
          clicks.add(new int[]{down.path("x").asInt(), down.path("y").asInt()});
          if (clickOpensLibrary) {
            dialogOpen = true;
          }
        }
        default -> answer.put("ok", false);
      }
      return answer;
    }

    private static void control(ArrayNode controls, String name, int[] rect, boolean enabled) {
      ObjectNode control = controls.addObject().put("name", name).put("enabled", enabled).put("visible", true);
      ArrayNode r = control.putArray("rect");
      for (int value : rect) {
        r.add(value);
      }
    }
  }

  @Test
  public void clicksTheCentreOfTheLibraryButtonOnceTheMainMenuIsUp() throws Exception {
    FakeOa oa = new FakeOa(3);

    assertThat(OaLibraryNavigator.navigate(oa, millis -> {
    }), is(true));

    assertThat(oa.clicks.size(), is(1));
    assertThat(oa.clicks.get(0)[0], is(520 + 24));
    assertThat(oa.clicks.get(0)[1], is(420 + 24));
  }

  @Test
  public void waitsWhileTheLibraryButtonIsDisabled() throws Exception {
    FakeOa oa = new FakeOa(0);
    oa.buttonEnabled = false;
    List<Long> pauses = new ArrayList<>();

    assertThat(OaLibraryNavigator.navigate(oa, pauses::add), is(false));

    assertThat(oa.clicks.isEmpty(), is(true));
    assertThat(pauses.isEmpty(), is(false));
  }

  @Test
  public void givesUpWithoutClickingWhenTheMainMenuNeverComes() throws Exception {
    FakeOa oa = new FakeOa(Integer.MAX_VALUE);

    assertThat(OaLibraryNavigator.navigate(oa, millis -> {
    }), is(false));

    assertThat(oa.clicks.isEmpty(), is(true));
  }

  @Test
  public void reportsFailureWhenTheClickDoesNotOpenTheDialog() throws Exception {
    FakeOa oa = new FakeOa(0);
    oa.clickOpensLibrary = false;

    assertThat(OaLibraryNavigator.navigate(oa, millis -> {
    }), is(false));

    assertThat(oa.clicks.size(), is(1));
  }

  @Test
  public void doesNotClickWhenTheLibraryIsAlreadyOpen() throws Exception {
    FakeOa oa = new FakeOa(0);
    oa.dialogOpen = true;

    assertThat(OaLibraryNavigator.navigate(oa, millis -> {
    }), is(true));

    assertThat(oa.clicks.isEmpty(), is(true));
  }
}
