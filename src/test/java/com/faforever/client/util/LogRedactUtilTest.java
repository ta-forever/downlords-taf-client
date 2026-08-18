package com.faforever.client.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Pins the redaction that keeps the user's API access token out of client.log. The token used to be
 * logged verbatim by both the gpgnet4ta launch (argv {@code --hashtoken}) and the battleroom console
 * push ({@code /set_hash_api_token}), into a world-readable directory.
 */
public class LogRedactUtilTest {

  /** Shaped like the real thing: a JWT, so a leak is unmistakable in the assertions below. */
  private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.abcdefghijklmnop";

  @Test
  public void hashTokenValueIsRedactedButOptionSurvives() {
    List<String> command = Arrays.asList(
        "C:\\lib\\bin\\gpgnet4ta.exe",
        "--gameid", "185262",
        "--hashendpoint", "https://api.taforever.com/game/launch_codes",
        "--hashtoken", TOKEN,
        "--israted");

    List<String> redacted = LogRedactUtil.redactCommand(command);

    assertThat(redacted, contains(
        "C:\\lib\\bin\\gpgnet4ta.exe",
        "--gameid", "185262",
        "--hashendpoint", "https://api.taforever.com/game/launch_codes",
        "--hashtoken", LogRedactUtil.REDACTED,
        "--israted"));
    assertThat(String.join(" ", redacted), not(containsToken()));
  }

  @Test
  public void inlineOptionFormIsRedacted() {
    List<String> redacted = LogRedactUtil.redactCommand(List.of("gpgnet4ta.exe", "--hashtoken=" + TOKEN));

    assertThat(redacted, contains("gpgnet4ta.exe", "--hashtoken=" + LogRedactUtil.REDACTED));
  }

  @Test
  public void unknownCredentialLookingOptionsAreRedactedToo() {
    List<String> redacted = LogRedactUtil.redactCommand(
        List.of("tool.exe", "--some-secret", "hunter2", "--api-password", "hunter2"));

    assertThat(redacted, contains(
        "tool.exe", "--some-secret", LogRedactUtil.REDACTED, "--api-password", LogRedactUtil.REDACTED));
  }

  @Test
  public void ordinaryArgumentsAreUntouched() {
    List<String> command = List.of("maptool.exe", "--gamepath", "D:\\games\\TA-Base", "--verify", "whitelist.json");

    assertThat(LogRedactUtil.redactCommand(command), is(command));
  }

  @Test
  public void secretOptionAtEndOfCommandDoesNotThrow() {
    assertThat(LogRedactUtil.redactCommand(List.of("gpgnet4ta.exe", "--hashtoken")),
        contains("gpgnet4ta.exe", "--hashtoken"));
  }

  @Test
  public void nullCommandIsTolerated() {
    assertThat(LogRedactUtil.redactCommand(null), is((List<String>) null));
  }

  @Test
  public void watchTicketInDemoUrlIsRedactedButUrlStaysReadable() {
    List<String> redacted = LogRedactUtil.redactCommand(
        List.of("replayer.exe", "--demourl", "taflive://replay.taforever.com:15000/185262?ticket=YWJjZGVm%2Bxyz"));

    assertThat(redacted, contains(
        "replayer.exe", "--demourl",
        "taflive://replay.taforever.com:15000/185262?ticket=" + LogRedactUtil.REDACTED));
  }

  @Test
  public void consoleTokenPushIsRedacted() {
    assertThat(LogRedactUtil.redactConsoleCommand("/set_hash_api_token " + TOKEN),
        is("/set_hash_api_token " + LogRedactUtil.REDACTED));
  }

  @Test
  public void ordinaryConsoleCommandsStillLogInFull() {
    assertThat(LogRedactUtil.redactConsoleCommand("/startpositions 3,1,2,4"), is("/startpositions 3,1,2,4"));
    assertThat(LogRedactUtil.redactConsoleCommand("/quit"), is("/quit"));
    assertThat(LogRedactUtil.redactConsoleCommand(""), is(""));
    assertThat(LogRedactUtil.redactConsoleCommand(null), is((String) null));
  }

  @Test
  public void bareConsoleVerbWithNoArgumentIsUnchanged() {
    assertThat(LogRedactUtil.redactConsoleCommand("/set_hash_api_token"), is("/set_hash_api_token"));
  }

  private static org.hamcrest.Matcher<String> containsToken() {
    return org.hamcrest.Matchers.containsString(TOKEN);
  }
}
