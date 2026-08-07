package com.faforever.client.fx;

import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.BrowserOption;
import com.faforever.client.preferences.PreferencesService;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opens a URL in the browser the user chose for it.
 *
 * <p>Motivation: Chrome crashes for some users while watching in the 3D viewer — clearing the
 * cache fixes it, but not permanently — while Firefox is fine for the same users. We cannot tell
 * from here which browser will behave, so the client asks the first time, offers to remember the
 * answer, and keeps the choice editable in the settings menu.
 *
 * <p>Only the WATCH flow routes through here. Every other link in the client still goes to the
 * system default, because this preference is about a specific rendering problem in one page, not
 * about hijacking the user's browser choice generally.
 */
@Lazy
@Service
@Slf4j
@RequiredArgsConstructor
public class BrowserLauncher {

  private final PreferencesService preferencesService;
  private final PlatformService platformService;
  private final NotificationService notificationService;
  private final I18n i18n;

  /**
   * Candidate install locations per browser, most likely first. Deliberately a plain path probe
   * rather than a registry read: it needs no native calls, works the same on all three platforms,
   * and a miss degrades to the system default rather than failing.
   */
  private static List<String> candidatePaths(BrowserOption option) {
    String localAppData = System.getenv("LOCALAPPDATA");
    switch (option) {
      case CHROME:
        return List.of(
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
            localAppData == null ? "" : localAppData + "/Google/Chrome/Application/chrome.exe",
            "/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser",
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
      case FIREFOX:
        return List.of(
            "C:/Program Files/Mozilla Firefox/firefox.exe",
            "C:/Program Files (x86)/Mozilla Firefox/firefox.exe",
            "/usr/bin/firefox",
            "/Applications/Firefox.app/Contents/MacOS/firefox");
      case EDGE:
        return List.of(
            "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
            "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
            "/usr/bin/microsoft-edge",
            "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge");
      default:
        return List.of();
    }
  }

  /** The executable for a browser, or null when it isn't installed here. */
  private static Path resolve(BrowserOption option) {
    for (String candidate : candidatePaths(option)) {
      if (candidate.isEmpty()) {
        continue;
      }
      Path path = Paths.get(candidate);
      if (Files.isRegularFile(path)) {
        return path;
      }
    }
    return null;
  }

  /** Installed browsers, in menu order. Empty when none are found — then we only offer the OS. */
  public Map<BrowserOption, Path> installedBrowsers() {
    Map<BrowserOption, Path> found = new LinkedHashMap<>();
    for (BrowserOption option : List.of(BrowserOption.FIREFOX, BrowserOption.CHROME, BrowserOption.EDGE)) {
      Path path = resolve(option);
      if (path != null) {
        found.put(option, path);
      }
    }
    return found;
  }

  /**
   * Open a URL, asking which browser to use when the preference says {@link BrowserOption#ASK}.
   *
   * <p>Never throws: a browser that won't launch falls back to the system default, because
   * failing to open the viewer at all is a worse outcome than opening it in the wrong browser.
   */
  public void open(String url) {
    BrowserOption preference = preferencesService.getPreferences().getBrowserForWatch();
    if (preference == BrowserOption.ASK) {
      askThenOpen(url);
      return;
    }
    launch(preference, url);
  }

  private void launch(BrowserOption option, String url) {
    if (!option.isSpecificBrowser()) {
      platformService.showDocument(url);
      return;
    }
    Path exe = resolve(option);
    if (exe == null) {
      log.warn("{} is the chosen browser but is not installed here; using the system default", option);
      platformService.showDocument(url);
      return;
    }
    try {
      new ProcessBuilder(exe.toString(), url).start();
      log.info("Opened the viewer in {} ({})", option, exe);
    } catch (IOException e) {
      log.warn("Could not launch {} at {}; using the system default", option, exe, e);
      platformService.showDocument(url);
    }
  }

  /**
   * Prompt for a browser, then open. The dialog carries a "remember my choice" checkbox that is
   * OFF by default — the user asked for the choice to be per-occasion unless they opt in, so a
   * one-off "let me try Firefox this time" must not silently become permanent.
   */
  private void askThenOpen(String url) {
    Map<BrowserOption, Path> installed = installedBrowsers();

    CheckBox remember = new CheckBox(i18n.get("browserChoice.remember"));
    remember.setSelected(false);
    VBox customUi = new VBox(remember);
    customUi.setPadding(new Insets(6, 0, 0, 0));

    List<Action> actions = new ArrayList<>();
    for (BrowserOption option : installed.keySet()) {
      actions.add(new Action(i18n.get(option.getI18nKey()), event -> {
        if (remember.isSelected()) {
          persist(option);
        }
        launch(option, url);
      }));
    }
    // Always offered, and last: it is the fallback that cannot be missing.
    actions.add(new Action(i18n.get(BrowserOption.SYSTEM_DEFAULT.getI18nKey()), event -> {
      if (remember.isSelected()) {
        persist(BrowserOption.SYSTEM_DEFAULT);
      }
      launch(BrowserOption.SYSTEM_DEFAULT, url);
    }));

    ImmediateNotification notification = new ImmediateNotification(
        i18n.get("browserChoice.title"), i18n.get("browserChoice.text"),
        Severity.INFO, null, actions, customUi);
    notificationService.addNotification(notification);
  }

  private void persist(BrowserOption option) {
    preferencesService.getPreferences().setBrowserForWatch(option);
    preferencesService.storeInBackground();
    log.info("Remembered browser choice for watching: {}", option);
  }
}
