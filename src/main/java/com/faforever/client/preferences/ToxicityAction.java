package com.faforever.client.preferences;

public enum ToxicityAction {
  HIDE("chat.toxicity.filter.hide"),
  CENSOR("chat.toxicity.filter.censor"),
  ALLOW("chat.toxicity.filter.allow");

  private final String i18nKey;

  ToxicityAction(String i18nKey) {
    this.i18nKey = i18nKey;
  }

  public String getI18nKey() {
    return i18nKey;
  }

}
