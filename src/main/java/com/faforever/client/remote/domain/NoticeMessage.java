package com.faforever.client.remote.domain;

import com.faforever.client.notification.Severity;


public class NoticeMessage extends FafServerMessage {

  private String text;
  private String style;
  private String i18nKey;

  public NoticeMessage() {
    super(FafServerMessageType.NOTICE);
  }

  public Severity getSeverity() {
    if (style == null) {
      return Severity.INFO;
    }
    switch (style) {
      case "error":
        return Severity.ERROR;
      case "warning":
        return Severity.WARN;
      case "info":
        return Severity.INFO;
      default:
        return Severity.INFO;
    }
  }

  public String getText() { return text; }

  public void setText(String text) {
    this.text = text;
  }

  public String getI18nKey() { return this.i18nKey; }

  public void setI18nKey(String i18nKey) {
    this.i18nKey = i18nKey;
  }

  public void setStyle(String style) {
    this.style = style;
  }

  public String getStyle() {
    return style;
  }
}
