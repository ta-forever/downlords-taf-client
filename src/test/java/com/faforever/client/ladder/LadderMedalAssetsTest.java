package com.faforever.client.ladder;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Every medal code the cabinet can display must have art and a localized name, in the base
 * bundle and in every locale mirror.
 *
 * <p>This is not a style check. A code with no PNG used to take down the whole surface it
 * appeared on — {@code UiService.getThemeFile} resolved the missing resource with
 * {@code new ClassPathResource(...).getURL()}, which throws — so shipping a server-side medal
 * before its icon broke the medal cabinet, the battle report, the chat avatar list and the
 * avatar picker for anyone who held one. The render sites fall back to a default badge now,
 * but a missing asset still means a player earns an anonymous grey coin, so catch it here.
 */
public class LadderMedalAssetsTest {

  private static final String[] LOCALES =
      {"ca", "cs", "de", "es", "fr", "he", "it", "ko", "nl", "pl", "pt", "ru", "tr", "uk", "zh"};

  private static List<String> allCodes() {
    List<String> codes = new ArrayList<>();
    LadderUiUtil.MEDAL_CLASSES.forEach(medalClass -> codes.addAll(medalClass.codes()));
    return codes;
  }

  /**
   * Read a bundle from the source tree rather than the classpath: {@code src/test/resources/i18n}
   * holds a 38-byte {@code messages_de.properties} stub for another test, and test resources
   * shadow main ones, so a classloader lookup of the German bundle returns the stub and every
   * key looks missing.
   */
  private static Properties bundle(String suffix) throws IOException {
    Path path = Paths.get("src", "main", "resources", "i18n", "messages" + suffix + ".properties");
    assertTrue("missing bundle " + path.toAbsolutePath(), Files.isRegularFile(path));
    try (InputStream in = Files.newInputStream(path)) {
      Properties properties = new Properties();
      // The bundles are UTF-8 (Spring ResourceBundleMessageSource), not ISO-8859-1.
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
      return properties;
    }
  }

  @Test
  public void everyMedalHasAnIcon() {
    for (String code : allCodes()) {
      String path = LadderUiUtil.medalIconPath(code);
      URL url = getClass().getClassLoader().getResource(path);
      assertNotNull("no icon for medal '" + code + "' (expected " + path + ")", url);
    }
  }

  @Test
  public void everyMedalHasANameAndDescriptionInEveryLocale() throws IOException {
    List<String> codes = allCodes();
    List<String> missing = new ArrayList<>();
    for (String suffix : names()) {
      Properties properties = bundle(suffix);
      for (String code : codes) {
        for (String key : new String[]{"medal." + code + ".name", "medal." + code + ".desc"}) {
          if (!properties.containsKey(key)) {
            missing.add((suffix.isEmpty() ? "base" : suffix.substring(1)) + ": " + key);
          }
        }
      }
    }
    assertTrue("missing medal translations: " + missing, missing.isEmpty());
  }

  @Test
  public void everyCabinetTierHasALabelInEveryLocale() throws IOException {
    List<String> missing = new ArrayList<>();
    for (String suffix : names()) {
      Properties properties = bundle(suffix);
      LadderUiUtil.MEDAL_CLASSES.stream()
          .map(LadderUiUtil.MedalClass::labelKey)
          .filter(key -> !properties.containsKey(key))
          .forEach(key -> missing.add((suffix.isEmpty() ? "base" : suffix.substring(1)) + ": " + key));
    }
    assertTrue("missing cabinet tier labels: " + missing, missing.isEmpty());
  }

  private static List<String> names() {
    List<String> suffixes = new ArrayList<>();
    suffixes.add("");
    for (String locale : LOCALES) {
      suffixes.add("_" + locale);
    }
    return suffixes;
  }
}
