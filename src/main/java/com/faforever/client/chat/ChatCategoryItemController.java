package com.faforever.client.chat;

import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.player.SocialStatus;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.dialog.Dialog;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.ref.WeakReference;

/** Represents a header in the chat user list, like "Moderators". */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ChatCategoryItemController implements Controller<Node> {
  private final I18n i18n;
  private final UiService uiService;
  private final PreferencesService preferencesService;
  public HBox chatUserCategoryRoot;
  public Label categoryLabel;
  public Button addPlayerButton;
  private WeakReference<ChatCategoryContextMenuController> contextMenuController = null;
  private ChatUserCategory chatUserCategory;

  void setChatUserCategory(@Nullable ChatUserCategory chatUserCategory) {
    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();
    JavaFxUtil.unbind(categoryLabel.styleProperty());

    this.chatUserCategory = chatUserCategory;
    boolean social = chatUserCategory == ChatUserCategory.FRIEND || chatUserCategory == ChatUserCategory.FOE;
    addPlayerButton.setVisible(social);
    addPlayerButton.setManaged(social);
    if (social) {
      addPlayerButton.setTooltip(new Tooltip(i18n.get(chatUserCategory == ChatUserCategory.FOE
          ? "chat.category.manageFoes" : "chat.category.manageFriends")));
    }
    if (chatUserCategory == null) {
      categoryLabel.setText(null);
      return;
    }

    categoryLabel.setText(i18n.get(chatUserCategory.getI18nKey()));
    JavaFxUtil.bind(categoryLabel.styleProperty(), Bindings.createStringBinding(() -> {
      Color color = chatPrefs.getGroupToColor().getOrDefault(chatUserCategory, null);
      if (color != null) {
        return String.format("-fx-text-fill: %s", JavaFxUtil.toRgbCode(color));
      } else {
        return "";
      }
    }, chatPrefs.groupToColorProperty()));
  }

  public void onContextMenuRequested(ContextMenuEvent event) {
    if (contextMenuController != null) {
      ChatCategoryContextMenuController controller = contextMenuController.get();
      if (controller != null) {
        controller.getContextMenu().show(chatUserCategoryRoot.getScene().getWindow(), event.getScreenX(), event.getScreenY());
        return;
      }
    }

    ChatCategoryContextMenuController controller = uiService.loadFxml("theme/chat/chat_category_context_menu.fxml");
    controller.setCategory(chatUserCategory);
    controller.getContextMenu().show(chatUserCategoryRoot.getScene().getWindow(), event.getScreenX(), event.getScreenY());

    contextMenuController = new WeakReference<>(controller);
  }

  /** Opens the dialog for adding or removing friends / foes, including players who are offline. */
  public void onAddPlayerClicked() {
    StackPane dialogParent = findOutermostStackPane();
    if (dialogParent == null) {
      return;
    }
    SocialStatus socialStatus = chatUserCategory == ChatUserCategory.FOE ? SocialStatus.FOE : SocialStatus.FRIEND;
    SocialPlayerPickerController controller = uiService.loadFxml("theme/chat/social_player_picker.fxml");
    controller.setSocialStatus(socialStatus);
    Dialog dialog = uiService.showInDialog(dialogParent, controller.getRoot(),
        i18n.get(socialStatus == SocialStatus.FOE ? "chat.socialPicker.foesTitle" : "chat.socialPicker.friendsTitle"));
    controller.setCloseButtonAction(dialog::close);
  }

  /** The main window's root, so the dialog covers the whole window rather than just the user list. */
  @Nullable
  private StackPane findOutermostStackPane() {
    StackPane result = null;
    for (Node node = chatUserCategoryRoot; node != null; node = node.getParent()) {
      if (node instanceof StackPane stackPane) {
        result = stackPane;
      }
    }
    return result;
  }

  @Override
  public Node getRoot() {
    return chatUserCategoryRoot;
  }
}
