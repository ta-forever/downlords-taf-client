package com.faforever.client.fa.oa;

import java.util.Locale;

/**
 * Maps the host OS/architecture onto the release asset names used by Open Annihilation
 * ({@code open-annihilation-vX.Y.Z-<platform>.zip}).
 */
public final class OaPlatform {

  private OaPlatform() {
  }

  public static String current() {
    return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
  }

  public static String of(String osName, String osArch) {
    String os = osName.toLowerCase(Locale.ROOT);
    String arch = osArch.toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      // windows-arm64 is "experimental" upstream and is not picked automatically
      return arch.equals("x86") || arch.equals("i386") ? "windows-x86" : "windows-x64";
    }
    if (os.contains("mac") || os.contains("darwin")) {
      return "macos-universal";
    }
    if (arch.contains("aarch64") || arch.equals("arm64")) {
      return "linux-arm64";
    }
    if (arch.startsWith("arm")) {
      return "linux-armhf";
    }
    return "linux-x86_64";
  }

  public static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  public static boolean isMac() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    return os.contains("mac") || os.contains("darwin");
  }

  /** File name of the OA executable inside a release zip. */
  public static String executableName(String platform) {
    if (platform.startsWith("windows")) {
      return "open-annihilation.exe";
    }
    return "open-annihilation";
  }
}
