package com.faforever.client.chat;

import com.faforever.client.i18n.I18n;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.SocialStatus;
import com.faforever.client.remote.FafService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.util.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The friend/foe dialog behind the "+" on the chat user list's Friends / Foes headers (issue #44): server-side
 * paging and filtering of all players, the local "only mine" list, and add/remove via PlayerService.
 */
public class SocialPlayerPickerControllerTest extends AbstractPlainJavaFxTest {

  @Mock
  private FafService fafService;
  @Mock
  private PlayerService playerService;

  private SocialPlayerPickerController instance;

  private static Player player(String name, int id) {
    Player player = new Player(name);
    player.setId(id);
    return player;
  }

  @Before
  public void setUp() throws Exception {
    // Return the key followed by any arguments, e.g. "chat.socialPicker.page[1, 3]".
    I18n i18n = Mockito.mock(I18n.class, invocation -> {
      if (invocation.getMethod().getReturnType() != String.class) {
        return Mockito.RETURNS_DEFAULTS.answer(invocation);
      }
      Object[] args = invocation.getArguments();
      return args.length <= 1 ? args[0] : args[0] + Arrays.toString(Arrays.copyOfRange(args, 1, args.length));
    });
    // Three pages of all players; each page holds a single player named after the filter and page.
    when(fafService.findPlayersByLoginPrefixWithPageCount(anyString(), anyInt(), anyInt())).thenAnswer(invocation -> {
      String filter = invocation.getArgument(0);
      int page = invocation.getArgument(2);
      return CompletableFuture.completedFuture(new Tuple<>(List.of(player(filter + "player" + page, page)), 3));
    });

    instance = new SocialPlayerPickerController(fafService, playerService, i18n);
    loadFxml("theme/chat/social_player_picker.fxml", clazz -> instance);
  }

  private void open(SocialStatus socialStatus) {
    runOnFxThreadAndWait(() -> instance.setSocialStatus(socialStatus));
    WaitForAsyncUtils.waitForFxEvents();
  }

  private List<String> listedNames() {
    return instance.playersListView.getItems().stream().map(Player::getUsername).collect(Collectors.toList());
  }

  @Test
  public void opensOnFirstPageOfAllPlayers() {
    open(SocialStatus.FRIEND);

    verify(fafService).findPlayersByLoginPrefixWithPageCount("", SocialPlayerPickerController.PAGE_SIZE, 1);
    assertThat(listedNames(), contains("player1"));
    assertThat(instance.pageLabel.getText(), is("chat.socialPicker.page[1, 3]"));
    assertThat(instance.previousPageButton.isDisabled(), is(true));
    assertThat(instance.nextPageButton.isDisabled(), is(false));
    assertThat(instance.statusLabel.isVisible(), is(false));
  }

  @Test
  public void nextAndPreviousPageLoadThatPage() {
    open(SocialStatus.FRIEND);

    runOnFxThreadAndWait(() -> instance.onNextPage());
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(listedNames(), contains("player2"));
    assertThat(instance.pageLabel.getText(), is("chat.socialPicker.page[2, 3]"));
    assertThat(instance.previousPageButton.isDisabled(), is(false));

    runOnFxThreadAndWait(() -> instance.onNextPage());
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(instance.nextPageButton.isDisabled(), is(true));

    runOnFxThreadAndWait(() -> instance.onPreviousPage());
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(listedNames(), contains("player2"));
  }

  @Test
  public void typingFiltersOnTheServerAfterAShortPauseAndGoesBackToPageOne() throws Exception {
    open(SocialStatus.FRIEND);
    runOnFxThreadAndWait(() -> instance.onNextPage());

    runOnFxThreadAndWait(() -> instance.filterTextField.setText("ax"));
    // Not sent on every keystroke...
    verify(fafService, never()).findPlayersByLoginPrefixWithPageCount("ax", SocialPlayerPickerController.PAGE_SIZE, 1);

    // ...but once typing pauses.
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> listedNames().equals(List.of("axplayer1")));
    verify(fafService).findPlayersByLoginPrefixWithPageCount("ax", SocialPlayerPickerController.PAGE_SIZE, 1);
  }

  @Test
  public void noMatchesShowsNoResults() {
    when(fafService.findPlayersByLoginPrefixWithPageCount(anyString(), anyInt(), anyInt()))
        .thenReturn(CompletableFuture.completedFuture(new Tuple<>(List.of(), 0)));

    open(SocialStatus.FRIEND);

    assertThat(instance.statusLabel.isVisible(), is(true));
    assertThat(instance.statusLabel.getText(), is("chat.socialPicker.noResults"));
    assertThat(instance.pageLabel.getText(), is("chat.socialPicker.page[0, 0]"));
    assertThat(instance.nextPageButton.isDisabled(), is(true));
  }

  @Test
  public void apiFailureShowsLoadFailed() {
    when(fafService.findPlayersByLoginPrefixWithPageCount(anyString(), anyInt(), anyInt()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("API down")));

    open(SocialStatus.FRIEND);

    assertThat(instance.statusLabel.isVisible(), is(true));
    assertThat(instance.statusLabel.getText(), is("chat.socialPicker.loadFailed"));
  }

  @Test
  public void onlyMineListsOwnFriendsSortedAndFiltersThemLocally() throws Exception {
    when(playerService.getFriendIds()).thenReturn(List.of(1, 2, 3));
    when(playerService.getPlayersByIds(anyCollection())).thenReturn(CompletableFuture.completedFuture(
        List.of(player("carol", 3), player("Alice", 1), player("bob", 2))));
    open(SocialStatus.FRIEND);

    runOnFxThreadAndWait(() -> instance.onlyMineCheckBox.setSelected(true));
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> listedNames().size() == 3);
    assertThat(listedNames(), contains("Alice", "bob", "carol"));
    assertThat(instance.pageLabel.getText(), is("chat.socialPicker.page[1, 1]"));

    runOnFxThreadAndWait(() -> {
      instance.filterTextField.setText("B");
      instance.reload();
    });
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> listedNames().equals(List.of("bob")));
    verify(fafService, never()).findPlayersByLoginPrefixWithPageCount("B", SocialPlayerPickerController.PAGE_SIZE, 1);
  }

  @Test
  public void onlyMineForFoesUsesFoeList() throws Exception {
    when(playerService.getFoeIds()).thenReturn(List.of(9));
    when(playerService.getPlayersByIds(anyCollection())).thenReturn(CompletableFuture.completedFuture(List.of(player("griefer", 9))));
    open(SocialStatus.FOE);

    runOnFxThreadAndWait(() -> instance.onlyMineCheckBox.setSelected(true));

    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> listedNames().equals(List.of("griefer")));
    assertThat(instance.onlyMineCheckBox.getText(), is("chat.socialPicker.onlyFoes"));
  }

  @Test
  public void addingAFriendWhoIsAFoeRemovesTheFoeFirst() {
    Player player = player("alice", 1);
    when(playerService.isFoe(1)).thenReturn(true);
    open(SocialStatus.FRIEND);

    runOnFxThreadAndWait(() -> instance.toggle(player));

    InOrder inOrder = inOrder(playerService);
    inOrder.verify(playerService).removeFoe(player);
    inOrder.verify(playerService).addFriend(player);
  }

  @Test
  public void togglingAnExistingFriendRemovesThem() {
    Player player = player("alice", 1);
    when(playerService.isFriend(1)).thenReturn(true);
    open(SocialStatus.FRIEND);

    runOnFxThreadAndWait(() -> instance.toggle(player));

    verify(playerService).removeFriend(player);
    verify(playerService, never()).addFriend(player);
  }

  @Test
  public void closeButtonRunsCloseAction() {
    Runnable closeAction = Mockito.mock(Runnable.class);
    open(SocialStatus.FRIEND);
    instance.setCloseButtonAction(closeAction);

    runOnFxThreadAndWait(() -> instance.closeButton.fire());

    verify(closeAction).run();
  }

  @Test
  public void foeDialogAddsFoeAndRemovesFriendFirst() {
    Player player = player("alice", 1);
    when(playerService.isFriend(1)).thenReturn(true);
    open(SocialStatus.FOE);

    runOnFxThreadAndWait(() -> instance.toggle(player));

    InOrder inOrder = inOrder(playerService);
    inOrder.verify(playerService).removeFriend(player);
    inOrder.verify(playerService).addFoe(player);
  }
}
