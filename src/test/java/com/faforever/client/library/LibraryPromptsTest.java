package com.faforever.client.library;

import com.faforever.client.i18n.I18n;
import com.faforever.client.library.LibraryReadiness.Status;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LibraryPromptsTest {

  private NotificationService notificationService;
  private LibraryService libraryService;
  private LibraryPrompts prompts;

  @Before
  public void setUp() {
    // "key[arg, arg]" so that the tests see which message was chosen and with what
    I18n i18n = mock(I18n.class, invocation -> {
      Object[] all = invocation.getArguments();
      String args = Arrays.stream(all).skip(1).map(String::valueOf).collect(Collectors.joining(", "));
      return args.isEmpty() ? all[0] + "" : all[0] + "[" + args + "]";
    });
    notificationService = mock(NotificationService.class);
    libraryService = mock(LibraryService.class);
    when(libraryService.openOa()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
    prompts = new LibraryPrompts(i18n, notificationService, libraryService);
  }

  private static LibraryJoinVerdict verdict(LibraryJoinVerdict.Outcome outcome) {
    return new LibraryJoinVerdict(outcome, "Talon", "1.03", "1.02", "0.8.0");
  }

  @Test
  public void everyBlockingOutcomeHasTheReasonTheTicketWords() {
    assertThat(prompts.reasonFor(verdict(LibraryJoinVerdict.Outcome.NEEDS_OA)), is("library.reason.needsOa[0.8.0]"));
    assertThat(prompts.reasonFor(new LibraryJoinVerdict(LibraryJoinVerdict.Outcome.NEEDS_OA, "Talon", null, null, null)),
        is("library.reason.needsOaAny"));
    assertThat(prompts.reasonFor(verdict(LibraryJoinVerdict.Outcome.NEEDS_MOD)), is("library.reason.needsMod[Talon]"));
    assertThat(prompts.reasonFor(verdict(LibraryJoinVerdict.Outcome.NEEDS_SETUP)), is("library.reason.needsSetup"));
  }

  @Test
  public void aJoinableGameHasNoReasonAndAHintOnlyForAVersionDifference() {
    assertThat(prompts.reasonFor(verdict(LibraryJoinVerdict.Outcome.JOINABLE)), is(nullValue()));
    assertThat(prompts.reasonFor(verdict(LibraryJoinVerdict.Outcome.JOINABLE_WITH_VERSION_HINT)),
        is("library.hint.version[Talon, 1.03, 1.02]"));
  }

  @Test
  public void theHintTextCanBeBuiltFromAnyRecordedVersion() {
    assertThat(prompts.versionHintText("Talon", new LibraryVersionHint("1.03", "1.02")),
        is("library.hint.version[Talon, 1.03, 1.02]"));
  }

  @Test
  public void aJoinableGameJustProceeds() {
    AtomicInteger joined = new AtomicInteger();

    prompts.handle(verdict(LibraryJoinVerdict.Outcome.JOINABLE), joined::incrementAndGet);

    assertThat(joined.get(), is(1));
    verify(notificationService, never()).addNotification(any(ImmediateNotification.class));
  }

  @Test
  public void aMissingModIsExplainedWithAButtonThatOpensOaAndTheJoinDoesNotProceed() {
    AtomicInteger joined = new AtomicInteger();

    prompts.handle(verdict(LibraryJoinVerdict.Outcome.NEEDS_MOD), joined::incrementAndGet);

    ImmediateNotification notification = shown();
    assertThat(notification.getText(), is("library.prompt.needsMod[Talon]"));
    assertThat(notification.getActions().get(0).getTitle(), is("library.prompt.openOa"));
    assertThat(joined.get(), is(0));
    notification.getActions().get(0).call(null);
    verify(libraryService).openOa();
  }

  @Test
  public void anUnfinishedSetupIsExplainedWithAButtonThatOpensOa() {
    prompts.handle(verdict(LibraryJoinVerdict.Outcome.NEEDS_SETUP), () -> {
    });

    ImmediateNotification notification = shown();
    assertThat(notification.getText(), is("library.prompt.needsSetup"));
    assertThat(notification.getActions().get(0).getTitle(), is("library.prompt.openOa"));
  }

  @Test
  public void aMissingOaIsExplainedWithoutAButton() {
    prompts.handle(verdict(LibraryJoinVerdict.Outcome.NEEDS_OA), () -> {
    });

    ImmediateNotification notification = shown();
    assertThat(notification.getText(), is("library.prompt.needsOa[0.8.0]"));
    assertThat(notification.getActions().stream().noneMatch(a -> "library.prompt.openOa".equals(a.getTitle())), is(true));
  }

  @Test
  public void aVersionHintIsShownWithAJoinAnywayThatProceeds() {
    AtomicInteger joined = new AtomicInteger();

    prompts.handle(verdict(LibraryJoinVerdict.Outcome.JOINABLE_WITH_VERSION_HINT), joined::incrementAndGet);

    ImmediateNotification notification = shown();
    assertThat(notification.getText(), is("library.hint.version[Talon, 1.03, 1.02]"));
    assertThat(joined.get(), is(0));
    notification.getActions().get(0).call(null);
    assertThat(joined.get(), is(1));
  }

  @Test
  public void theCreateGameNoticeExplainsEachCaseWhereNothingIsOffered() {
    assertThat(prompts.noticeFor(new LibraryReadiness(Status.READY, "0.8.0", null, "0.8.1")), is(nullValue()));
    assertThat(prompts.noticeFor(new LibraryReadiness(Status.NO_OA, "0.8.0", null, null)),
        is("game.create.libraryNotice.noOa[0.8.0]"));
    assertThat(prompts.noticeFor(new LibraryReadiness(Status.OA_VERSION_NOT_ALLOWED, "0.8.0", null, "0.7.3")),
        is("game.create.libraryNotice.versionNotAllowed[0.7.3, 0.8.0+]"));
    assertThat(prompts.noticeFor(new LibraryReadiness(Status.SETUP_NOT_DONE, "0.8.0", null, "0.8.1")),
        is("game.create.libraryNotice.setup"));
    assertThat(prompts.offersOpenOa(new LibraryReadiness(Status.SETUP_NOT_DONE, "0.8.0", null, "0.8.1")), is(true));
    assertThat(prompts.offersOpenOa(new LibraryReadiness(Status.NO_OA, "0.8.0", null, null)), is(false));
  }

  @Test
  public void theGameListSuffixShowsTheReasonOnlyOnceTheSnapshotIsKnown() {
    LibraryGameInfo game = new LibraryGameInfo("talon", "Talon", "coreprime", "1.03", 2, null, "totala", List.of());
    LibraryMod local = new LibraryMod("talon", "Talon", "1.02", 2, "coreprime", "totala", List.of(), Path.of("Mods", "talon"));
    when(libraryService.snapshotProperty()).thenReturn(new javafx.beans.property.SimpleObjectProperty<>());

    assertThat(prompts.listSuffix(game), is(""));

    libraryService.snapshotProperty().set(new LibrarySnapshot(
        new LibraryReadiness(Status.READY, "0.8.0", null, "0.8.1"), List.of(local)));
    assertThat(prompts.listSuffix(game), is(" - library.hint.version[Talon, 1.03, 1.02]"));
    assertThat(prompts.isUnavailable(game), is(false));

    libraryService.snapshotProperty().set(new LibrarySnapshot(
        new LibraryReadiness(Status.READY, "0.8.0", null, "0.8.1"), List.of()));
    assertThat(prompts.listSuffix(game), is(" - library.reason.needsMod[Talon]"));
    assertThat(prompts.isUnavailable(game), is(true));
  }

  private ImmediateNotification shown() {
    ArgumentCaptor<ImmediateNotification> captor = ArgumentCaptor.forClass(ImmediateNotification.class);
    verify(notificationService).addNotification(captor.capture());
    return captor.getValue();
  }
}
