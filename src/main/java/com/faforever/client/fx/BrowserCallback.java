package com.faforever.client.fx;

import com.faforever.client.chat.InitiatePrivateChatEvent;
import com.faforever.client.chat.UrlPreviewResolver;
import com.faforever.client.clan.ClanService;
import com.faforever.client.clan.ClanTooltipController;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.main.event.JoinChannelEvent;
import com.faforever.client.main.event.OpenTadaPageEvent;
import com.faforever.client.main.event.ShowReplayEvent;
import com.faforever.client.main.event.ShowTadaReplayEvent;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.StageHolder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.stage.Popup;
import javafx.stage.PopupWindow.AnchorLocation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("WeakerAccess")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BrowserCallback {

  @RequiredArgsConstructor
  @Getter
  class UrlDispatcher {
    final Pattern pattern;
    final BiConsumer<String,Matcher> dispatcher;
  }

  private final PlatformService platformService;
  private final UrlPreviewResolver urlPreviewResolver;
  private final EventBus eventBus;
  private final ClanService clanService;
  private final UiService uiService;
  private final ClientProperties clientProperties;
  final private List<UrlDispatcher> urlDispatchers;
  @VisibleForTesting
  Popup clanInfoPopup;
  private Tooltip linkPreviewTooltip;
  private double lastMouseX;
  private double lastMouseY;

  BrowserCallback(PlatformService platformService, ClientProperties clientProperties,
                  UrlPreviewResolver urlPreviewResolver, EventBus eventBus,
                  ClanService clanService, UiService uiService) {
    this.platformService = platformService;
    this.urlPreviewResolver = urlPreviewResolver;
    this.eventBus = eventBus;
    this.clanService = clanService;
    this.uiService = uiService;
    this.urlDispatchers = new ArrayList<>();
    this.clientProperties = clientProperties;

    addShowTafReplayDispatcher();
  }

  public void addShowTadaReplayDispatcher() {
    Pattern tadaReplayDownloadUrlPattern = Pattern.compile(clientProperties.getTada().getDownloadReplayUrlRegex());
    this.addDispatcher(tadaReplayDownloadUrlPattern, (url, matcher) -> eventBus.post(new ShowTadaReplayEvent(matcher.group(3), matcher.group(4), matcher.group(5))));
  }

  public void addShowTafReplayDispatcher() {
    String urlFormat = clientProperties.getVault().getReplayDownloadUrlFormat();
    String[] splitFormat = urlFormat.split("%s");
    Pattern tafReplayUrlPattern = Pattern.compile(Pattern.quote(splitFormat[0]) + "(\\d+)" + Pattern.compile(splitFormat.length == 2 ? splitFormat[1] : ""));
    this.addDispatcher(tafReplayUrlPattern, (url, matcher) -> eventBus.post(new ShowReplayEvent(Integer.parseInt(matcher.group(1)))));
  }

  public void addOpenTadaPageDispatcher() {
    Pattern tadaPattern = Pattern.compile(clientProperties.getTada().getTadaUrlRegex());
    this.addDispatcher(tadaPattern, (url, matcher) -> eventBus.post(new OpenTadaPageEvent(url)));
  }

  public void addDispatcher(Pattern pattern, BiConsumer<String, Matcher> dispatcher) {
    this.urlDispatchers.add(new UrlDispatcher(pattern, dispatcher));
  }

  /**
   * Called from JavaScript when user clicked a URL.
   */
  @SuppressWarnings("unused")
  public void openUrl(String url) {
    for (UrlDispatcher dispatcher: this.urlDispatchers) {
      Matcher matcher = dispatcher.getPattern().matcher(url);
      if (matcher.matches()) {
        dispatcher.getDispatcher().accept(url, matcher);
        return;
      }
    }

    platformService.showDocument(url);
    return;
  }

  /**
   * Called from JavaScript when user clicked a channel link.
   */
  @SuppressWarnings("unused")
  public void openChannel(String channelName) {
    eventBus.post(new JoinChannelEvent(channelName));
  }

  /**
   * Called from JavaScript when user clicks on user name in chat
   */
  @SuppressWarnings("unused")
  public void openPrivateMessageTab(String username) {
    eventBus.post(new InitiatePrivateChatEvent(username));
  }

  /**
   * Called from JavaScript when user no longer hovers over an URL.
   */
  @SuppressWarnings("unused")
  public void hideUrlPreview() {
    if (linkPreviewTooltip != null) {
      linkPreviewTooltip.hide();
      linkPreviewTooltip = null;
    }
  }

  /**
   * Called from JavaScript when user hovers over an URL.
   */
  @SuppressWarnings("unused")
  public void previewUrl(String urlString) {
    urlPreviewResolver.resolvePreview(urlString).thenAccept(optionalPreview -> optionalPreview.ifPresent(preview -> {
      linkPreviewTooltip = new Tooltip(preview.getDescription());
      linkPreviewTooltip.setAutoHide(true);
      linkPreviewTooltip.setAnchorLocation(AnchorLocation.CONTENT_BOTTOM_LEFT);
      linkPreviewTooltip.setGraphic(preview.getNode());
      linkPreviewTooltip.setContentDisplay(ContentDisplay.TOP);
      JavaFxUtil.runLater(() -> linkPreviewTooltip.show(StageHolder.getStage(), lastMouseX + 20, lastMouseY));
    }));
  }

  /**
   * Called from JavaScript when user hovers over a clan tag.
   */
  @SuppressWarnings("unused")
  public void showClanInfo(String clanTag) {
    clanService.getClanByTag(clanTag).thenAccept(clan -> JavaFxUtil.runLater(() -> {
      if (clan.isEmpty() || clanTag.isEmpty()) {
        return;
      }
      ClanTooltipController clanTooltipController = uiService.loadFxml("theme/chat/clan_tooltip.fxml");
      clanTooltipController.setClan(clan.get());
      clanTooltipController.getRoot().getStyleClass().add("tooltip");

      clanInfoPopup = new Popup();
      clanInfoPopup.getContent().setAll(clanTooltipController.getRoot());
      clanInfoPopup.setAnchorLocation(AnchorLocation.CONTENT_TOP_LEFT);
      clanInfoPopup.setAutoHide(true);
      clanInfoPopup.show(StageHolder.getStage(), lastMouseX, lastMouseY + 10);
    }));
  }

  /**
   * Called from JavaScript when user no longer hovers over a clan tag.
   */
  @SuppressWarnings("unused")
  public void hideClanInfo() {
    if (clanInfoPopup == null) {
      return;
    }
    JavaFxUtil.runLater(() -> {
      clanInfoPopup.hide();
      clanInfoPopup = null;
    });
  }

  /**
   * Called from JavaScript when user clicks on clan tag.
   */
  @SuppressWarnings("unused")
  public void showClanWebsite(String clanTag) {
    clanService.getClanByTag(clanTag).thenAccept(clan -> {
      if (!clan.isPresent()) {
        return;
      }
      platformService.showDocument(clan.get().getWebsiteUrl());
    });
  }

  void setLastMouseX(double screenX) {
    lastMouseX = screenX;
  }

  void setLastMouseY(double screenY) {
    lastMouseY = screenY;
  }
}
