package com.faforever.client.fa.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Brings a freshly started Open Annihilation to its Library screen. OA 0.8.1 has no command-line option for
 * that, so this clicks the Library button on the main menu through the {@code --fark} automation endpoint, then
 * lets go of the endpoint so the player carries on in OA as usual.
 *
 * <p>Everything here is best effort: when the endpoint or the main menu never shows up, or a control is not as
 * expected, OA is simply left where it is. Nothing is thrown and OA is never closed.
 *
 * <p>Found on OA 0.8.1: the main menu ({@code screen = main_menu}) has a button named {@code oa.button} (text
 * "Library, N packages"); clicking it opens the dialog {@code oa.library}, listed under {@code dialogs} of the
 * {@code screen} answer. The names are constants below so they are easy to adjust for a later OA.
 */
@Slf4j
public final class OaLibraryNavigator {

  static final String MAIN_MENU_SCREEN = "main_menu";
  static final String LIBRARY_BUTTON = "oa.button";
  static final String LIBRARY_DIALOG = "oa.library";

  /** How long OA gets to start and show its main menu: the endpoint file appears once the menu is up. */
  static final long ENDPOINT_WAIT_MS = 60_000;
  /** How long to wait for the main menu (and then the dialog) once connected. */
  static final long SCREEN_WAIT_MS = 20_000;
  static final long DIALOG_WAIT_MS = 3_000;
  private static final long POLL_MS = 250;
  private static final int CONNECT_ATTEMPTS = 10;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private OaLibraryNavigator() {
  }

  /**
   * Waits for OA's endpoint file, connects, opens the Library and disconnects. Blocks (up to about a minute), so
   * call it from a background thread. Never throws.
   */
  public static void openLibrary(Path farkFile) {
    try {
      for (long waited = 0; !Files.isRegularFile(farkFile) || Files.size(farkFile) == 0; waited += POLL_MS) {
        if (waited > ENDPOINT_WAIT_MS) {
          log.info("[OaLibraryNavigator] OA's automation endpoint never appeared; leaving OA where it is");
          return;
        }
        Thread.sleep(POLL_MS);
      }
      try (OaFarkClient fark = connect(farkFile)) {
        boolean opened = navigate(fark, Thread::sleep);
        log.info("[OaLibraryNavigator] {}", opened ? "Library opened" : "could not reach the Library; left OA as it is");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.info("[OaLibraryNavigator] leaving OA where it is: {}", e.toString());
    }
  }

  /** The endpoint serves one client at a time, so a busy answer is retried. */
  private static OaFarkClient connect(Path farkFile) throws IOException, InterruptedException {
    IOException last = null;
    for (int attempt = 0; attempt < CONNECT_ATTEMPTS; attempt++) {
      try {
        return new OaFarkClient(farkFile);
      } catch (IOException e) {
        last = e;
        Thread.sleep(300);
      }
    }
    throw last;
  }

  /**
   * Clicks through to the Library.
   *
   * @return whether the Library dialog is open at the end; false on any step that did not work out
   */
  static boolean navigate(OaFark fark, OaPause pause) throws IOException, InterruptedException {
    for (long waited = 0; waited <= SCREEN_WAIT_MS; waited += POLL_MS) {
      JsonNode screen = fark.request(MAPPER.createObjectNode().put("op", "screen"));
      if (hasDialog(screen)) {
        return true;
      }
      if (MAIN_MENU_SCREEN.equals(screen.path("screen").asText())) {
        JsonNode button = enabledControl(fark, LIBRARY_BUTTON);
        if (button != null) {
          click(fark, button);
          return waitForDialog(fark, pause);
        }
      }
      pause.sleep(POLL_MS);
    }
    return false;
  }

  private static boolean waitForDialog(OaFark fark, OaPause pause)
      throws IOException, InterruptedException {
    for (long waited = 0; waited <= DIALOG_WAIT_MS; waited += POLL_MS) {
      if (hasDialog(fark.request(MAPPER.createObjectNode().put("op", "screen")))) {
        return true;
      }
      pause.sleep(POLL_MS);
    }
    return false;
  }

  private static boolean hasDialog(JsonNode screen) {
    for (JsonNode dialog : screen.path("dialogs")) {
      if (LIBRARY_DIALOG.equals(dialog.asText())) {
        return true;
      }
    }
    return false;
  }

  private static JsonNode enabledControl(OaFark fark, String name) throws IOException {
    for (JsonNode control : fark.request(MAPPER.createObjectNode().put("op", "controls")).path("controls")) {
      if (name.equals(control.path("name").asText()) && control.path("enabled").asBoolean(false)
          && control.path("visible").asBoolean(true)) {
        return control;
      }
    }
    return null;
  }

  private static void click(OaFark fark, JsonNode control) throws IOException {
    JsonNode r = control.path("rect");
    int x = r.path(0).asInt() + r.path(2).asInt() / 2;
    int y = r.path(1).asInt() + r.path(3).asInt() / 2;
    ObjectNode message = MAPPER.createObjectNode().put("op", "input");
    ArrayNode events = message.putArray("events");
    events.addObject().put("kind", "pointer_move").put("x", x).put("y", y);
    events.addObject().put("kind", "button_down").put("button", "left").put("x", x).put("y", y);
    events.addObject().put("kind", "button_up").put("button", "left").put("x", x).put("y", y);
    JsonNode answer = fark.request(message);
    if (!answer.path("ok").asBoolean(false)) {
      throw new IOException("fark refused the click: " + answer);
    }
  }
}
