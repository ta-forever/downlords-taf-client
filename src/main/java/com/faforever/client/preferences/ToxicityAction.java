package com.faforever.client.preferences;

public enum ToxicityAction {
  SUPPRESS("chat.toxicity.filter.suppress"),
  MASK("chat.toxicity.filter.mask"),
  DISPLAY("chat.toxicity.filter.display");

  private final String i18nKey;

  ToxicityAction(String i18nKey) {
    this.i18nKey = i18nKey;
  }

  public String getI18nKey() {
    return i18nKey;
  }

}
