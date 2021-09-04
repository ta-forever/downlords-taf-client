package com.faforever.client.notification;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Parent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * A notification that requires the user's immediate attention. It is displayed until the user performs a suggested
 * action or dismisses it. The notification consists of a title, a text, an optional image and zero or more actions.
 */
@RequiredArgsConstructor
@Getter
@Setter
public class ImmediateNotification {

  private final String title;
  private final String text;
  private final Severity severity;
  private final Throwable throwable;
  private final List<Action> actions;
  private final Parent customUI;
  private boolean overlayClose = true;  // set to false if clicking outside the notification should NOT dismiss the notification
  private BooleanProperty dismissTrigger;

  /// @brief Retrieve and retain this property if you need to later programmatically dismiss the notification.
  /// Set the property value to true when you want to do that.
  public BooleanProperty getDismissTrigger() {
    if (dismissTrigger == null) {
      dismissTrigger = new SimpleBooleanProperty(false);
    }
    return dismissTrigger;
  }

  public ImmediateNotification(String title, String text, Severity severity) {
    this(title, text, severity, null, null, null);
  }

  public ImmediateNotification(String title, String text, Severity severity, List<Action> actions) {
    this(title, text, severity, null, actions, null);
  }

  public ImmediateNotification(String title, String text, Severity severity, Throwable throwable, List<Action> actions) {
    this(title, text, severity, throwable, actions, null);
  }

  public ImmediateNotification(String title, String text, Severity severity, List<Action> actions, Parent customUI) {
    this(title, text, severity, null, actions, customUI);
  }

}
