package com.faforever.client.i18n;

import com.faforever.client.preferences.PreferencesService;
import com.google.common.base.Strings;
import javafx.beans.property.ReadOnlySetWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class I18n implements InitializingBean {
  private static final Pattern MESSAGES_FILE_PATTERN = Pattern.compile("(.*[/\\\\]messages)(?:_([a-z]{2}))(?:_([a-z]{2}))?\\.properties", Pattern.CASE_INSENSITIVE);
  private final ReloadableResourceBundleMessageSource messageSource;
  private final PreferencesService preferencesService;
  private final ObservableSet<Locale> availableLanguages;

  private Locale userSpecificLocale;

  public I18n(ReloadableResourceBundleMessageSource messageSource, PreferencesService preferencesService) {
    this.messageSource = messageSource;
    this.preferencesService = preferencesService;
    availableLanguages = FXCollections.observableSet(new HashSet<>());
  }

  @Override
  public void afterPropertiesSet() throws IOException {
    Locale locale = preferencesService.getPreferences().getLocalization().getLanguage();
    if (locale != null) {
      userSpecificLocale = new Locale(locale.getLanguage(), locale.getCountry());
    } else {
      userSpecificLocale = Locale.getDefault();
    }

    loadAvailableLanguages();
  }

  private void loadAvailableLanguages() throws IOException {
    // These are the default languages shipped with the client
    availableLanguages.addAll(Set.of(
        Locale.US,    // 2750 users
        Locale.FRENCH,// 414 users
        Locale.GERMAN,  // 208 users
        new Locale("nl"), // 172 users
        new Locale("pt"), // portugese 150 users
        new Locale("pl"), // 140 users
        new Locale("ru"), // 98 users
        new Locale("es"), // 77 users
        new Locale("sv"), // swedish 76 users
        Locale.ITALIAN, // 72 users
        Locale.CHINESE, // 61 users
        // finish 47 users
        // danish 39 users

        // hungarian 35 users
        // norwegian 29 users
        new Locale("cs"), // 27 users
        // korean 27 users
        // malay 26 users
        new Locale("ca"), // 23 users
        new Locale("uk"), // 20 users
        new Locale("tr"), // 18 users
        // irish 16 users
        // arabic 16 users
        // afrikaans/zulu/xhosa 14 users
        // croatian 14 users
        // tamil 12 users
        // romanian 11 users
        // serbian 10 users
        new Locale("he") // 10 users
        // filipino 10 users
        // slovak 10 users
        // greek 10 users
        // estonian 7 users
        //new Locale("ja") // 7 users
        // luxembourgish 7 users
        // slovene 6 users
        // indonesian 6 users
        // swahili 5 users
        // thai 5 users
        // hokkien 5 users
        // belarusian 4 users
        // latvian 3 users
        // bosnian 2 users
        // khazak 2 users
        // khmer 2 users
        // armenian 2 users
        // georgian 1 user
        // lithuanian 1 user
        // urdu 1 user
        // montenegrin 1 user
        // belizian 1 user


    ));

    Path languagesDirectory = preferencesService.getLanguagesDirectory();
    if (Files.notExists(languagesDirectory)) {
      return;
    }

    Set<String> currentBaseNames = messageSource.getBasenameSet();
    Set<String> newBaseNames = new LinkedHashSet<>();
    try (Stream<Path> dir = Files.list(languagesDirectory)) {
      dir
          .map(path -> MESSAGES_FILE_PATTERN.matcher(path.toString()))
          .filter(Matcher::matches)
          .forEach(matcher -> {
            newBaseNames.add(Paths.get(matcher.group(1)).toUri().toString());
            availableLanguages.add(new Locale(matcher.group(2), Strings.nullToEmpty(matcher.group(3))));
          });
    }
    // Make sure that current base names are added last; the files above have precedence
    newBaseNames.addAll(currentBaseNames);
    messageSource.setBasenames(newBaseNames.toArray(new String[0]));
  }

  public String get(String key, Object... args) {
    return get(userSpecificLocale, key, args);
  }

  public String get(Locale locale, String key, Object... args) {
    try {
      return messageSource.getMessage(key, args, locale);
    } catch (Exception e) {
      log.debug("Could not load message {} with locale {} defaulting to US english", key, locale, e);
      return messageSource.getMessage(key, args, key, Locale.US);
    }
  }

  public String getWithDefault(String defaultMessage, String key, Object... args) {
    return getWithDefault(userSpecificLocale, defaultMessage, key, args);
  }

  public String getWithDefault(Locale locale, String defaultMessage, String key, Object... args) {
    try {
      return messageSource.getMessage(key, args, defaultMessage, locale);
    } catch (Exception e) {
      log.debug("Could not load message {} with locale {} defaulting to US english", key, locale, e);
      return messageSource.getMessage(key, args, defaultMessage, Locale.US);
    }
  }

  public Locale getUserSpecificLocale() {
    return this.userSpecificLocale;
  }

  public String getCountryNameLocalized(String isoCode) {
    if (isoCode == null) {
      return "";
    }
    return new Locale("", isoCode).getDisplayCountry(this.userSpecificLocale);
  }

  public String getQuantized(String singularKey, String pluralKey, long arg) {
    Object[] args = {arg};
    if (Math.abs(arg) == 1) {
      return messageSource.getMessage(singularKey, args, userSpecificLocale);
    }
    return messageSource.getMessage(pluralKey, args, userSpecificLocale);
  }

  public String number(int number) {
    return String.format(userSpecificLocale, "%d", number);
  }

  public String numberWithSign(int number) {
    return String.format(userSpecificLocale, "%+d", number);
  }

  public String rounded(double number, int digits) {
    return String.format(userSpecificLocale, "%." + digits + "f", number);
  }

  public ReadOnlySetWrapper<Locale> getAvailableLanguages() {
    return new ReadOnlySetWrapper<>(availableLanguages);
  }
}
