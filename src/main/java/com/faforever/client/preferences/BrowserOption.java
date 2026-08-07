package com.faforever.client.preferences;

/**
 * Which browser the client should open the 3D web viewer in.
 *
 * <p>This exists because Chrome crashes for some users while watching (clearing the cache helps,
 * but only until it happens again) whereas Firefox is reliable for them. Rather than guess, the
 * client asks the first time and remembers the answer only if the user says so — {@link #ASK} is
 * the default, and the persisted choice is always changeable in the settings menu.
 *
 * <p>{@link #SYSTEM_DEFAULT} is the historical behaviour: hand the URL to the OS
 * ({@code HostServices.showDocument}) and let it pick.
 */
public enum BrowserOption {
  ASK("settings.browser.ask"),
  SYSTEM_DEFAULT("settings.browser.systemDefault"),
  CHROME("settings.browser.chrome"),
  FIREFOX("settings.browser.firefox"),
  EDGE("settings.browser.edge");

  private final String i18nKey;

  BrowserOption(String i18nKey) {
    this.i18nKey = i18nKey;
  }

  public String getI18nKey() {
    return i18nKey;
  }

  /** True for the options that name an actual browser to launch (i.e. not ASK/SYSTEM_DEFAULT). */
  public boolean isSpecificBrowser() {
    return this == CHROME || this == FIREFOX || this == EDGE;
  }
}
