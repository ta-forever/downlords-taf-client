package com.faforever.client.fx;

import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.theme.UiService;
import javafx.concurrent.Worker.State;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import lombok.extern.slf4j.Slf4j;
import netscape.javascript.JSObject;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Set;
import java.util.function.Function;

@Component
@Slf4j
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WebViewConfigurer {

  /**
   * This is the member name within the JavaScript code that provides access to the Java callback instance.
   */
  private static final String JAVA_REFERENCE_IN_JAVASCRIPT = "java";
  private static final double ZOOM_STEP = 0.2d;

  private final UiService uiService;
  private final ApplicationContext applicationContext;
  private final PreferencesService preferencesService;

  public WebViewConfigurer(UiService uiService, ApplicationContext applicationContext, PreferencesService preferencesService) {
    this.uiService = uiService;
    this.applicationContext = applicationContext;
    this.preferencesService = preferencesService;
  }

  private String defaultSubstituteHrefs(String href) {
    return String.format("javascript:java.openUrl('%s');", href);
  }

  public BrowserCallback configureWebView(WebView webView) {
    return configureWebView(webView, this::defaultSubstituteHrefs);
  }

  public BrowserCallback configureWebView(WebView webView, Function<String,String> hrefTransformer) {

    WebEngine engine = webView.getEngine();
//    Accessor.getPageFor(engine).setBackgroundColor(0);
    webView.setContextMenuEnabled(false);
    webView.setOnScroll(event -> {
      if (event.isControlDown()) {
        webView.setZoom(webView.getZoom() + ZOOM_STEP * Math.signum(event.getDeltaY()));
      }
    });
    webView.setOnKeyPressed(event -> {
      if (event.isControlDown() && (event.getCode() == KeyCode.DIGIT0 || event.getCode() == KeyCode.NUMPAD0)) {
        webView.setZoom(1);
      }
    });

    BrowserCallback browserCallback = applicationContext.getBean(BrowserCallback.class);
    EventHandler<MouseEvent> moveHandler = event -> {
      browserCallback.setLastMouseX(event.getScreenX());
      browserCallback.setLastMouseY(event.getScreenY());
    };
    webView.addEventHandler(MouseEvent.MOUSE_MOVED, moveHandler);

    engine.setUserDataDirectory(preferencesService.getCacheDirectory().toFile());
    engine.setUserAgent("downlords-faf-client"); // removes faforever.com header and footer
    uiService.registerWebView(webView);
    JavaFxUtil.addListener(engine.getLoadWorker().stateProperty(), (observable, oldValue, newValue) -> {
      if (newValue != State.SUCCEEDED) {
        return;
      }
      uiService.registerWebView(webView);
      ((JSObject) engine.executeScript("window")).setMember(JAVA_REFERENCE_IN_JAVASCRIPT, browserCallback);

      Document document = webView.getEngine().getDocument();
      if (document != null) {
        NodeList nodeList = document.getElementsByTagName("a");
        substituteHrefs(nodeList, hrefTransformer);
      }
    });
    return browserCallback;
  }

  public void substituteHrefs(NodeList nodeList, Function<String,String> hrefTransformer) {
    for (int i = 0; i < nodeList.getLength(); i++) {
      Element link = (Element) nodeList.item(i);
      String href = link.getAttribute("href");
      if (href == null) {
        continue;
      }

//      link.setAttribute("onMouseOver", "java.previewUrl('" + href + "')");
//      link.setAttribute("onMouseOut", "java.hideUrlPreview()");
      link.setAttribute("href", hrefTransformer.apply(href));
    }
  }
}
