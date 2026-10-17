package com.faforever.client.library;

import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.library.LibraryJoinVerdict.Outcome;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.DismissAction;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import javafx.beans.property.ObjectProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * What players are told about Library mod games: the reason a game in the list is greyed out, the prompts when
 * a join or host is not possible (with a button that opens Open Annihilation), the version hint, and the notice in
 * Create Game. TAF only explains and opens OA; installing content and OA's first-run setup stay in OA.
 */
@Lazy
@Component
@RequiredArgsConstructor
@Slf4j
public class LibraryPrompts {

  private final I18n i18n;
  private final NotificationService notificationService;
  private final LibraryService libraryService;

  public ObjectProperty<LibrarySnapshot> snapshotProperty() {
    return libraryService.snapshotProperty();
  }

  /**
   * The reason a game cannot be joined ("needs Talon from OA's Library"), or the version hint for a game that can
   * be joined but differs; null when there is nothing to say.
   */
  @Nullable
  public String reasonFor(LibraryJoinVerdict verdict) {
    return switch (verdict.outcome()) {
      case JOINABLE -> null;
      case JOINABLE_WITH_VERSION_HINT ->
          versionHintText(verdict.modName(), new LibraryVersionHint(verdict.hostVersion(), verdict.localVersion()));
      case NEEDS_MOD -> i18n.get("library.reason.needsMod", verdict.modName());
      case NEEDS_SETUP -> i18n.get("library.reason.needsSetup");
      case NEEDS_OA -> verdict.requiredOaVersion() == null
          ? i18n.get("library.reason.needsOaAny")
          : i18n.get("library.reason.needsOa", verdict.requiredOaVersion());
    };
  }

  /**
   * "Host plays Talon 1.03, you have 1.02. Update it in OA's Library." Built from the recorded and the local
   * version only, so other recordings (replays) can reuse it.
   */
  public String versionHintText(String modName, LibraryVersionHint hint) {
    return i18n.get("library.hint.version", modName, hint.recordedVersion(), hint.localVersion());
  }

  /** The judgement of a listed game against the last check of the player's OA; null until there is one. */
  @Nullable
  private LibraryJoinVerdict verdictOf(LibraryGameInfo game) {
    if (game == null) {
      return null;
    }
    libraryService.refreshIfStale();
    LibrarySnapshot snapshot = libraryService.snapshotProperty().get();
    return snapshot == null ? null : snapshot.verdictFor(game);
  }

  /** What the game list appends to a Library game's mod name: " - needs Talon from OA's Library", or "". */
  public String listSuffix(@Nullable LibraryGameInfo game) {
    LibraryJoinVerdict verdict = verdictOf(game);
    String reason = verdict == null ? null : reasonFor(verdict);
    return reason == null ? "" : " - " + reason;
  }

  /** Whether a listed game is greyed out: it cannot be joined as things stand. */
  public boolean isUnavailable(@Nullable LibraryGameInfo game) {
    LibraryJoinVerdict verdict = verdictOf(game);
    return verdict != null && verdict.blocksJoin();
  }

  /**
   * Checks the player's OA afresh and then joins ({@code proceed}) or explains why not. Safe to call from any
   * thread; {@code proceed} runs on the JavaFX thread.
   */
  public void checkJoin(LibraryGameInfo game, Runnable proceed) {
    libraryService.refresh(true)
        .thenAccept(snapshot -> JavaFxUtil.runLater(() -> handle(snapshot.verdictFor(game), proceed)))
        .exceptionally(throwable -> {
          log.warn("[Library] could not check Open Annihilation before joining", throwable);
          notificationService.addImmediateErrorNotification(throwable, "library.checkFailed");
          return null;
        });
  }

  /**
   * Checks the player's OA afresh and then plays the recorded game's replay ({@code proceed}, given the installed
   * copy of the mod to play it with) or explains why not: the mod is missing, or its version differs from the
   * recorded one (the replay may not play correctly). Safe to call from any thread; {@code proceed} runs on the
   * JavaFX thread.
   */
  public void checkReplay(LibraryGameInfo recorded, Consumer<LibraryMod> proceed) {
    libraryService.refresh(true)
        .thenAccept(snapshot -> JavaFxUtil.runLater(() -> handleReplay(snapshot.verdictFor(recorded),
            () -> proceed.accept(LibraryJoinVerdict.localModFor(recorded, snapshot.mods()).orElseThrow()))))
        .exceptionally(throwable -> {
          log.warn("[Library] could not check Open Annihilation before a replay", throwable);
          notificationService.addImmediateErrorNotification(throwable, "library.checkFailed");
          return null;
        });
  }

  /** The same for hosting a Library mod that was offered in Create Game. */
  public void checkHost(LibraryMod mod, Runnable proceed) {
    checkJoin(LibraryGameInfo.of(mod, null), proceed);
  }

  /** Runs {@code proceed} if the verdict allows it, else tells the player what to fix. */
  void handle(LibraryJoinVerdict verdict, Runnable proceed) {
    switch (verdict.outcome()) {
      case JOINABLE -> proceed.run();
      case JOINABLE_WITH_VERSION_HINT -> notificationService.addNotification(new ImmediateNotification(
          i18n.get("library.title"), reasonFor(verdict), Severity.INFO,
          List.of(new Action(i18n.get("library.prompt.joinAnyway"), event -> proceed.run()),
              new Action(i18n.get("game.cancel")))));
      case NEEDS_MOD -> blocked(verdict, i18n.get("library.prompt.needsMod", verdict.modName()), true);
      case NEEDS_SETUP -> blocked(verdict, i18n.get("library.prompt.needsSetup"), true);
      case NEEDS_OA -> blocked(verdict, verdict.requiredOaVersion() == null
          ? i18n.get("library.prompt.needsOaAny")
          : i18n.get("library.prompt.needsOa", verdict.requiredOaVersion()), false);
    }
  }

  /** {@link #handle} for a replay: the words are about watching, and a version difference may break playback. */
  void handleReplay(LibraryJoinVerdict verdict, Runnable proceed) {
    switch (verdict.outcome()) {
      case JOINABLE -> proceed.run();
      case JOINABLE_WITH_VERSION_HINT -> notificationService.addNotification(new ImmediateNotification(
          i18n.get("library.title"),
          i18n.get("library.hint.replayVersion", verdict.modName(), verdict.hostVersion(), verdict.localVersion()),
          Severity.INFO,
          List.of(new Action(i18n.get("library.prompt.watchAnyway"), event -> proceed.run()),
              new Action(i18n.get("game.cancel")))));
      case NEEDS_MOD -> blocked(verdict, i18n.get("library.prompt.replayNeedsMod", verdict.modName()), true);
      default -> handle(verdict, proceed);
    }
  }

  private void blocked(LibraryJoinVerdict verdict, String text, boolean offerOpenOa) {
    List<Action> actions = offerOpenOa
        ? List.of(openOaAction(), new DismissAction(i18n))
        : List.of(new DismissAction(i18n));
    notificationService.addNotification(new ImmediateNotification(i18n.get("library.title"), text, Severity.WARN, actions));
  }

  private Action openOaAction() {
    return new Action(i18n.get("library.prompt.openOa"), event -> openOa());
  }

  /** Opens Open Annihilation, telling the player if that fails. */
  public void openOa() {
    libraryService.openOa().exceptionally(throwable -> {
      log.warn("[Library] could not open Open Annihilation", throwable);
      notificationService.addImmediateErrorNotification(throwable, "library.openFailed");
      return null;
    });
  }

  /** Create Game's explanation of why the Library section offers nothing; null when OA is ready. */
  @Nullable
  public String noticeFor(LibraryReadiness readiness) {
    return switch (readiness.status()) {
      case READY -> null;
      case NO_OA -> readiness.requiredVersion() == null
          ? i18n.get("game.create.libraryNotice.noOaAny")
          : i18n.get("game.create.libraryNotice.noOa", readiness.requiredVersion());
      case OA_VERSION_NOT_ALLOWED ->
          i18n.get("game.create.libraryNotice.versionNotAllowed", readiness.foundVersion(), allowedRange(readiness));
      case SETUP_NOT_DONE -> i18n.get("game.create.libraryNotice.setup");
    };
  }

  /**
   * What Create Game's Library section lists: the section header and the installed mods, or, when OA is ready but
   * has no mod to offer, a header and a placeholder (so Open Annihilation can still be reached from the list).
   */
  public record EntryPlan(boolean header, List<LibraryMod> mods, boolean placeholder) {
  }

  public static EntryPlan entryPlan(LibrarySnapshot snapshot) {
    boolean placeholder = snapshot.readiness().isReady() && snapshot.mods().isEmpty();
    return new EntryPlan(!snapshot.mods().isEmpty() || placeholder, snapshot.mods(), placeholder);
  }

  /** Whether the game detail pane offers "Open Open Annihilation": for the player's own running Library game. */
  public static boolean offersOpenOaInGame(boolean libraryGame, boolean currentGame, boolean gameProcessRunning) {
    return libraryGame && currentGame && gameProcessRunning;
  }

  /** Whether the game detail pane's Open OA button is usable: not while the game's own OA is up. */
  public static boolean canOpenOaInGame(boolean oaRunning) {
    return !oaRunning;
  }

  /** Whether opening OA is the fix for this readiness (its first-run setup). */
  public boolean offersOpenOa(LibraryReadiness readiness) {
    return readiness.status() == LibraryReadiness.Status.SETUP_NOT_DONE;
  }

  private static String allowedRange(LibraryReadiness readiness) {
    String min = readiness.requiredVersion();
    String max = readiness.maxVersion();
    if (min != null && max != null) {
      return min + " - " + max;
    }
    return min != null ? min + "+" : max != null ? "<= " + max : "?";
  }
}
