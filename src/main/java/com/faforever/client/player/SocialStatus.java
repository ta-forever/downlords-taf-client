package com.faforever.client.player;

public enum SocialStatus {
  FRIEND("friend", "chat.category.friends"),
  FOE("foe", "chat.category.foes"),
  OTHER("other", "chat.category.others"),
  SELF("self", "chat.category.self");

  private String cssClass;
  private final String i18nKey;

  SocialStatus(String cssClass, String i18nKey) {
    this.cssClass = cssClass;
    this.i18nKey = i18nKey;
  }

  public String getCssClass() {
    return cssClass;
  }

  public String getI18nKey() { return i18nKey; }
}
