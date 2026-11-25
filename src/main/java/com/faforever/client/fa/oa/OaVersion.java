package com.faforever.client.fa.oa;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An Open Annihilation release number such as {@code 0.7.3}. Accepts a leading {@code v}
 * and ignores any suffix after the third number.
 */
public record OaVersion(int major, int minor, int patch) implements Comparable<OaVersion> {

  private static final Pattern VERSION = Pattern.compile("v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

  /** @return the first version number found in {@code text}, or null if there is none */
  public static OaVersion find(String text) {
    if (text == null) {
      return null;
    }
    Matcher matcher = VERSION.matcher(text);
    if (!matcher.find()) {
      return null;
    }
    return new OaVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
        matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)));
  }

  @Override
  public int compareTo(OaVersion other) {
    if (major != other.major) {
      return Integer.compare(major, other.major);
    }
    if (minor != other.minor) {
      return Integer.compare(minor, other.minor);
    }
    return Integer.compare(patch, other.patch);
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + patch;
  }
}
