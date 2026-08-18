package com.faforever.client.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers {@link AbstractChatTabController#jsQuote(String)}, which is what lets every Java -> chat
 * document crossing go through {@code executeScript} instead of the JVM-crashing
 * {@code JSObject.call} (see the {@code callJs} javadoc). Since the quoting is now the only thing
 * standing between attacker controlled chat text and script execution in the chat WebView, the
 * escapes are pinned here.
 * <p>
 * Characters that are JS line terminators are built with casts rather than written literally: a
 * {@code \\u2028} escape in Java source is expanded before lexing and would break the source file
 * itself.
 */
public class ChatJsQuoteTest {

  @Test
  public void plainTextIsWrappedInSingleQuotes() {
    assertEquals("'hello'", AbstractChatTabController.jsQuote("hello"));
  }

  @Test
  public void nullBecomesEmptyLiteral() {
    assertEquals("''", AbstractChatTabController.jsQuote(null));
  }

  @Test
  public void singleQuoteIsEscaped() {
    assertEquals("'it\\'s'", AbstractChatTabController.jsQuote("it's"));
  }

  /**
   * The bug in the old {@code replace("'", "\\'")}: a trailing backslash escaped the escape, so
   * {@code \'} closed the literal and everything after it ran as script.
   */
  @Test
  public void backslashCannotEscapeOutOfTheLiteral() {
    assertEquals("'\\\\\\'; alert(1); //'", AbstractChatTabController.jsQuote("\\'; alert(1); //"));
  }

  @Test
  public void newlinesDoNotTerminateTheStatement() {
    assertEquals("'a\\nb\\r\\nc'", AbstractChatTabController.jsQuote("a\nb\r\nc"));
  }

  /** U+2028 / U+2029 are line terminators in JS but ordinary characters in Java. */
  @Test
  public void unicodeLineSeparatorsAreEscaped() {
    String input = "a" + ((char) 0x2028) + "b" + ((char) 0x2029) + "c";
    assertEquals("'a\\u2028b\\u2029c'", AbstractChatTabController.jsQuote(input));
  }

  @Test
  public void otherControlCharactersAreEscaped() {
    String input = "a" + ((char) 0x00) + "b" + ((char) 0x1f) + "c";
    assertEquals("'a\\u0000b\\u001fc'", AbstractChatTabController.jsQuote(input));
  }

  @Test
  public void nonAsciiTextIsPassedThrough() {
    String input = "über 中文";
    assertEquals("'" + input + "'", AbstractChatTabController.jsQuote(input));
  }
}
