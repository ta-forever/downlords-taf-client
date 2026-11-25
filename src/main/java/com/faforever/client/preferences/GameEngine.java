package com.faforever.client.preferences;

/**
 * The engine used to run a featured mod. Chosen per mod in the featured-mod install dialog.
 * See OPEN_ANNIHILATION_INTEGRATION.md.
 */
public enum GameEngine {
  CAVEDOG("installFeaturedMod.engine.cavedog"),
  OPEN_ANNIHILATION("installFeaturedMod.engine.openAnnihilation");

  private final String i18nKey;

  GameEngine(String i18nKey) {
    this.i18nKey = i18nKey;
  }

  public String getI18nKey() {
    return i18nKey;
  }
}
