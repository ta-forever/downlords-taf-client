package com.faforever.client.tada;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.fx.WebViewConfigurer;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.NavigateEvent;

import com.faforever.client.main.event.OpenTadaPageEvent;
import com.faforever.client.main.event.ShowTadaReplayEvent;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TadaIntegrationOption;
import com.faforever.client.util.ClipboardUtil;
import com.google.common.eventbus.EventBus;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebView;
import lombok.extern.slf4j.Slf4j;
import netscape.javascript.JSObject;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class TadaController extends AbstractViewController<Node> {

  private final WebViewConfigurer webViewConfigurer;
  private final I18n i18n;
  private final PreferencesService preferencesService;
  private final ClientProperties clientProperties;
  private final PlatformService platformService;
  private final NotificationService notificationService;
  private final EventBus eventBus;
  public Pane rootPane;
  public Pane tadaPane;
  public Node integratedBrowseControls;
  public Node externalBrowseControls;
  public Pane watchControlsPane;
  public TextField urlTextField;
  public WebView webView;
  public Control loadingIndicator;
  private final Pattern downloadReplayUrlPattern;
  private final Pattern browseReplayUrlPattern;
  private final Pattern replayUrlPattern;
  private final Pattern tadaUrlPattern;
  private final ChangeListener<Boolean> loadingIndicatorListener;

  public TadaController(WebViewConfigurer webViewConfigurer, I18n i18n, PreferencesService preferencesService,
                        ClientProperties clientProperties, PlatformService platformService, EventBus eventBus,
                        NotificationService notificationService) {

    this.webViewConfigurer = webViewConfigurer;
    this.clientProperties = clientProperties;
    this.platformService = platformService;
    this.preferencesService = preferencesService;
    this.notificationService = notificationService;
    this.eventBus = eventBus;
    this.i18n = i18n;
    this.downloadReplayUrlPattern = Pattern.compile(clientProperties.getTada().getDownloadReplayUrlRegex());
    this.browseReplayUrlPattern = Pattern.compile(clientProperties.getTada().getBrowseReplayUrlRegex());
    this.replayUrlPattern = Pattern.compile(clientProperties.getTada().getReplayUrlRegex());
    this.tadaUrlPattern = Pattern.compile(clientProperties.getTada().getTadaUrlRegex());
    this.loadingIndicatorListener = (observable, oldValue, newValue)
        -> loadingIndicator.getParent().getChildrenUnmodifiable().stream()
        .filter(node -> node != loadingIndicator)
        .filter(node -> !node.visibleProperty().isBound())
        .forEach(node -> node.setVisible(!newValue));
  }

  @Override
  public Node getRoot() {
    return rootPane;
  }

  @Override
  public void initialize() {
    webView.setContextMenuEnabled(false);
    webViewConfigurer.configureWebView(webView, this::substituteHrefs)
      .addShowTadaReplayDispatcher();

    loadingIndicator.managedProperty().bind(loadingIndicator.visibleProperty());
    loadingIndicator.visibleProperty().addListener(loadingIndicatorListener);
    loadingIndicatorListener.changed(loadingIndicator.visibleProperty(), null, true);

    loadingIndicator.getParent().getChildrenUnmodifiable()
        .forEach(node -> node.managedProperty().bind(node.visibleProperty()));
    webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
      if (newState == Worker.State.SUCCEEDED) {
        onLoadingStop();
      }
    });

    webView.getEngine().setOnStatusChanged((x) -> {
      Document document = webView.getEngine().getDocument();
      if (document != null && "WEB_STATUS_CHANGED".equals(x.getEventType().getName()) &&
          downloadReplayUrlPattern.matcher(x.getData()).matches()) {
        // user is potentially about to click on a download link
        // we better quickly substitute the document's hrefs to intercept it
        NodeList nodeList = document.getElementsByTagName("a");
        webViewConfigurer.substituteHrefs(nodeList, this::substituteHrefs);
      }
    });

    Document document = webView.getEngine().getDocument();
    if (document != null) {
      NodeList nodeList = document.getElementsByTagName("a");
      webViewConfigurer.substituteHrefs(nodeList, this::substituteHrefs);
    }

    integratedBrowseControls.visibleProperty().bind(Bindings.createBooleanBinding(
        () -> preferencesService.getPreferences().getTadaIntegrationOption().equals(TadaIntegrationOption.INTEGRATED),
        preferencesService.getPreferences().tadaIntegrationOptionProperty()));
    integratedBrowseControls.managedProperty().bind(integratedBrowseControls.visibleProperty());
    externalBrowseControls.visibleProperty().bind(integratedBrowseControls.visibleProperty().not());
    externalBrowseControls.managedProperty().bind(externalBrowseControls.visibleProperty());
    webView.visibleProperty().bind(integratedBrowseControls.visibleProperty());

    installContextMenu();
  }

  private String substituteHrefs(String href) {
    final Set<String> handledHrefs = Set.of("#download", "/upload", "https://www.fnta.xyz");
    if (handledHrefs.contains(href)) {
      if (href.startsWith("/")) {
        return String.format("javascript:java.openUrl('%s%s');", clientProperties.getTada().getRootUrl(), href);
      }
      else if (href.startsWith("#")) {
        return String.format("javascript:java.openUrl('%s%s');", webView.getEngine().getLocation(), href);
      }
      else {
        return String.format("javascript:java.openUrl('%s');", href);
      }
    }
    else {
      return href;
    }
  }

  private void installContextMenu() {
    MenuItem menuItem = new MenuItem(i18n.get("tada.toClipboard"));
    ContextMenu contextMenu = new ContextMenu(menuItem);
    Pattern extractLinkPattern = Pattern.compile("(javascript:java.openUrl\\(')?(http[^']*)('\\);)?");
    webView.setOnMousePressed(e -> {
      if (e.getButton() == MouseButton.SECONDARY) {
        String tagName = (String)webView.getEngine().executeScript(
            String.format("document.elementFromPoint(%f,%f).tagName;", e.getX(), e.getY()));
        JSObject jsObject = (JSObject) webView.getEngine().executeScript(
            String.format("document.elementFromPoint(%f,%f);", e.getX(), e.getY()));

        log.info(tagName);
        log.info(jsObject.toString());
        if ("A".equals(tagName)) {
          menuItem.setOnAction(x -> {
            Matcher matcher = extractLinkPattern.matcher(jsObject.toString());
            if (matcher.matches()) {
              log.info("match {}", matcher.group(2));
              ClipboardUtil.copyToClipboard(matcher.group(2));
            }
            else {
              log.info("no match");
            }
          });
        }
        else {
          menuItem.setOnAction(x -> {
            ClipboardUtil.copyToClipboard(webView.getEngine().getLocation());
          });
        }
        contextMenu.show(webView, e.getScreenX(), e.getScreenY());

      } else {
        contextMenu.hide();
      }
    });
  }

  private void onLoadingStart() {
    JavaFxUtil.runLater(() -> loadingIndicator.setVisible(true));
  }

  private void onLoadingStop() {
    JavaFxUtil.runLater(() -> loadingIndicator.setVisible(false));
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    TadaIntegrationOption tadaIntegrationOption = preferencesService.getPreferences().getTadaIntegrationOption();
    if (tadaIntegrationOption == TadaIntegrationOption.ASK) {
      doPromptIntegrationMode(navigateEvent);
    }
    else if (navigateEvent instanceof OpenTadaPageEvent) {
      onDisplayEvent(((OpenTadaPageEvent) navigateEvent).getUrl());
    }
    else {
      onDisplayEvent();
    }
  }

  private void onDisplayEvent() {
    if (preferencesService.getPreferences().getTadaIntegrationOption() == TadaIntegrationOption.INTEGRATED &&
        webView.getEngine().getHistory().getEntries().size() == 0) {
      onLoadingStart();
      doLoadInWebView(clientProperties.getTada().getRootUrl());
    }
    else {
      onLoadingStop();
    }
  }

  private void onDisplayEvent(String url) {
    if (preferencesService.getPreferences().getTadaIntegrationOption() == TadaIntegrationOption.INTEGRATED) {
      doLoadInWebView(url);
    }
    else {
      doSetUrlTextField(url);
      onLoadingStop();
    }
  }

  public void onUrlTextFieldKeyReleased(KeyEvent event) {
    if (event.getCode() == KeyCode.ENTER){
      onPlayButton();
    }
  }

  public void onPlayButton() {
    String url = this.urlTextField.getText();
    if (preferencesService.getPreferences().getTadaIntegrationOption() == TadaIntegrationOption.INTEGRATED) {
      Matcher matcher = this.tadaUrlPattern.matcher(url);
      if (matcher.matches()) {
        doLoadInWebView(url);
        return;
      }
    }
    else {
      Matcher matcher = this.replayUrlPattern.matcher(url);
      if (matcher.matches()) {
        doStartReplay(matcher.group(3));
        return;
      }
      matcher = this.tadaUrlPattern.matcher(url);
      if (matcher.matches()) {
        doLoadInBrowser(url);
        return;
      }
    }
    notificationService.addImmediateInfoNotification("tada.badTadaUrl", "tada.badTadaUrl.description");
  }

  public void onBrowseButton() {
    if (preferencesService.getPreferences().getTadaIntegrationOption() == TadaIntegrationOption.INTEGRATED) {
      if (webView.getEngine().getHistory().getEntries().size() == 0) {
        doLoadInBrowser(clientProperties.getTada().getRootUrl());
        return;
      }
      else {
        doLoadInBrowser(webView.getEngine().getLocation());
        return;
      }
    }
    else {
      String url = this.urlTextField.getText();
      Matcher matcher = this.tadaUrlPattern.matcher(url);
      if (matcher.matches()) {
        doLoadInBrowser(url);
        return;
      }
    }
    notificationService.addImmediateInfoNotification("tada.badTadaUrl", "tada.badTadaUrl.description");
  }

  public void onNavigateHomeButton() {
    if (preferencesService.getPreferences().getTadaIntegrationOption() == TadaIntegrationOption.INTEGRATED) {
      doLoadInWebView(clientProperties.getTada().getRootUrl());
    }
  }

  public void onNavigatePreviousButton() {
    if (preferencesService.getPreferences().getTadaIntegrationOption() == TadaIntegrationOption.INTEGRATED) {
      doNavigate(-1);
    }
  }

  public void onNavigateNextButton() {
    if (preferencesService.getPreferences().getTadaIntegrationOption() == TadaIntegrationOption.INTEGRATED) {
      doNavigate(+1);
    }
  }

  private void doPromptIntegrationMode(NavigateEvent navigateEvent) {
    notificationService.addNotification(new ImmediateNotification(
        i18n.get("settings.general.tada"), i18n.get("settings.general.tada.description"),
        Severity.INFO, Arrays.asList(
        new Action(i18n.get("settings.tada.integrated"), event -> {
          preferencesService.getPreferences().setTadaIntegrationOption(TadaIntegrationOption.INTEGRATED);
          preferencesService.storeInBackground();
          onDisplay(navigateEvent);
        }),
        new Action(i18n.get("settings.tada.browser"), event -> {
          preferencesService.getPreferences().setTadaIntegrationOption(TadaIntegrationOption.BROWSER);
          preferencesService.storeInBackground();
          onDisplay(navigateEvent);
        }))
    ));
  }

  private void doLoadInWebView(String url) {
    JavaFxUtil.runLater(() -> webView.getEngine().load(
        url.replace("#download","")));
  }

  private void doLoadInBrowser(String url) {
    platformService.showDocument(
        url.replace("#download",""));
  }

  private void doStartReplay(String replayId) {
    this.eventBus.post(new ShowTadaReplayEvent(replayId));
  }

  private void doSetUrlTextField(String url) {
    JavaFxUtil.runLater(() -> this.urlTextField.setText(url));
  }

  private void doNavigate(int step) {
    JavaFxUtil.runLater(() -> {
      try {
        webView.getEngine().getHistory().go(step);
      }
      catch (IndexOutOfBoundsException e) {
      }
    });
  }
}
