package com.faforever.client.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips credentials out of process command lines and console commands before they are written to
 * the log.
 * <p>
 * The client launches {@code gpgnet4ta} with {@code --hashtoken <api access token>} and, once the
 * battleroom opens, pushes the same token over the gpgnet4ta console socket as
 * {@code /set_hash_api_token <api access token>}. Both call sites used to log the command verbatim,
 * so a live 1804-character API access JWT ended up in
 * {@code C:\ProgramData\TAForever\logs\client.log} several times per session.
 * {@code C:\ProgramData} is world-readable by default on Windows, and log files routinely get
 * attached to bug reports and pasted into chat.
 * <p>
 * This only closes the <em>logging</em> hole. Passing the token in argv remains readable from the
 * process table for the lifetime of the child process; moving it to an environment variable or
 * stdin is the real fix and is deliberately out of scope here.
 */
public final class LogRedactUtil {

  /** Stand-in written in place of a secret. Deliberately not a fixed-length mask of the original. */
  public static final String REDACTED = "***REDACTED***";

  /** argv option names whose <em>following</em> element is a secret. */
  private static final Set<String> SECRET_OPTIONS = Set.of("--hashtoken");

  /** Console verbs whose remaining arguments are a secret. */
  private static final Set<String> SECRET_CONSOLE_VERBS = Set.of("/set_hash_api_token");

  /**
   * Substrings that mark an option name as credential-bearing. Belt and braces for options added
   * later: a new {@code --something-token} is redacted without anyone having to remember this
   * class exists. Kept narrow so it can't swallow ordinary diagnostics — {@code --hashendpoint}
   * and {@code --verify} do not match.
   */
  private static final List<String> SECRET_NAME_HINTS = List.of("token", "secret", "password", "passwd", "apikey");

  /**
   * Credential query parameters carried <em>inside</em> an argument value. {@code replayer.exe} is
   * launched with {@code --demourl taflive://host:port/<gameId>?ticket=<hmac>}; the live-watch
   * ticket is short-lived but it is still a bearer credential, and leaking it into a pasteable log
   * would undermine the watch-attribution it exists to provide. Only the value is masked, so the
   * host, port and game id stay visible for debugging.
   */
  private static final Pattern SECRET_QUERY_PARAM =
      Pattern.compile("([?&](?:ticket|token|secret|password|apikey)=)[^&\\s]*", Pattern.CASE_INSENSITIVE);

  private LogRedactUtil() {
    // utility class
  }

  /**
   * Returns a copy of {@code command} safe to log, with the value of every credential-bearing
   * option replaced by {@link #REDACTED}. Handles the separate-element form ({@code --hashtoken},
   * {@code <value>}), the inline form ({@code --hashtoken=<value>}) and credentials embedded in a
   * URL query string. The caller keeps the original list for {@code ProcessBuilder}.
   */
  public static List<String> redactCommand(List<String> command) {
    if (command == null) {
      return null;
    }

    List<String> redacted = new ArrayList<>(command.size());
    boolean nextIsSecret = false;

    for (String argument : command) {
      if (nextIsSecret) {
        redacted.add(REDACTED);
        nextIsSecret = false;
        continue;
      }

      if (argument == null) {
        redacted.add(null);
        continue;
      }

      int equals = argument.indexOf('=');
      if (equals > 0 && isSecretOption(argument.substring(0, equals))) {
        redacted.add(argument.substring(0, equals + 1) + REDACTED);
        continue;
      }

      redacted.add(redactQuerySecrets(argument));
      nextIsSecret = isSecretOption(argument);
    }

    return redacted;
  }

  /**
   * Returns {@code command} safe to log, with the arguments of a credential-bearing console verb
   * replaced by {@link #REDACTED}. Non-secret commands are returned unchanged, so ordinary console
   * traffic still logs in full.
   */
  public static String redactConsoleCommand(String command) {
    if (command == null || command.isEmpty()) {
      return command;
    }

    int endOfVerb = 0;
    while (endOfVerb < command.length() && !Character.isWhitespace(command.charAt(endOfVerb))) {
      endOfVerb++;
    }

    String verb = command.substring(0, endOfVerb);
    if (!SECRET_CONSOLE_VERBS.contains(verb.toLowerCase(Locale.ROOT))) {
      return command;
    }
    if (endOfVerb == command.length()) {
      return command;
    }

    // Keep the separator so the logged line still reads like the command that was sent.
    return verb + command.charAt(endOfVerb) + REDACTED;
  }

  /**
   * Masks the value of any credential query parameter embedded in {@code value}, leaving the rest
   * of the string (scheme, host, path, other parameters) intact.
   */
  public static String redactQuerySecrets(String value) {
    if (value == null || value.indexOf('=') < 0) {
      return value;
    }
    return SECRET_QUERY_PARAM.matcher(value).replaceAll("$1" + Matcher.quoteReplacement(REDACTED));
  }

  private static boolean isSecretOption(String name) {
    String normalised = name.toLowerCase(Locale.ROOT);
    if (SECRET_OPTIONS.contains(normalised)) {
      return true;
    }
    if (!normalised.startsWith("-")) {
      return false;
    }
    return SECRET_NAME_HINTS.stream().anyMatch(normalised::contains);
  }
}
