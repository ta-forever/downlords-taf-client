package com.faforever.client.update;

import lombok.Data;

import java.net.URL;
import java.util.List;
import java.util.Map;

@Data
// TODO since this class contains both, update info and configuration, the package 'update' doesn't really fit.
/**
 * A representation of a config file read from the faf server on start up. The file on the server allows to dynamically change settings in the client remotely.
 */
public class ClientConfiguration {
  ReleaseInfo latestRelease;
  List<Integer> recommendedMaps;
  List<Endpoints> endpoints;
  GitHubRepo gitHubRepo;
  List<GameFilesWhitelistEntry> gameFilesWhitelist;
  List<String> defaultChatChannels;
  List<String> allChatChannels;
  AutoBalance autoBalance;
  Boolean repairAsymmetricAlliances;
  List<Hotfix> hotfixes;

  @Data
  public static class AutoBalance {
    String metric;   // eg "trueskill"
    Double threshold;// eg 0.8
    Double scale;    // eg 0.03
  }

  @Data
  public static class GameFilesWhitelistEntry {
    String modTechnical;
    String whitelist; // eg "tdraw.dll:c42d5a5c,ca919ee7;totala.exe:deadbeef"
  }

  @Data
  public static class GitHubRepo {
    /**
     * Api URL to the client GitHub Repo
     */
    private String apiUrl;
  }

  @Data
  public static class Endpoints {
    String name;
    SocketEndpoint lobby;
    SocketEndpoint irc;
    SocketEndpoint liveReplay;
    UrlEndpoint api;
    UrlEndpoint galacticWar;
    List<String> galacticWar2;
  }

  @Data
  public static class SocketEndpoint {
    String host;
    int port;
  }

  @Data
  public static class UrlEndpoint {
    String url;
  }

  @Data
  public static class ReleaseInfo {
    String version;
    String minimumVersion;
    URL windowsUrl;
    URL linuxUrl;
    URL macUrl;
    boolean mandatory;
    String message;
    URL releaseNotesUrl;
  }

  public enum HotfixScope {
    /**
     * Replacement of a binary that ships with the client install (e.g. gpgnet4ta.exe).
     * The whole replacement zip is extracted into the user-writable override directory
     * (~/.faforever/hotfix/bin/) so admin elevation is not required even when the
     * client itself is installed under Program Files.
     */
    CLIENT_BINARY,
    /**
     * Replacement of a single file inside a featured-mod's installed directory
     * (e.g. tdraw.dll or taesc.dll). One specific member of the zip is extracted
     * over the bad on-disk file.
     */
    MOD_FILE
  }

  @Data
  public static class Hotfix {
    /** Stable identifier for this hotfix entry — used for logging and idempotency. */
    String id;
    HotfixScope scope;
    /** Required when scope == MOD_FILE. The featured mod's technical name. */
    String modTechnical;
    /**
     * Per-host-OS platform sub-objects. Keys: "windows", "linux", "mac". The client picks
     * the entry matching the running host OS; if no entry exists, the hotfix is skipped on
     * that OS. Each sub-object contains everything that varies between platforms — target
     * filename (e.g. {@code gpgnet4ta.exe} vs {@code gpgnet4ta}), replacement archive URL
     * and hashes, and the inner-archive member path.
     */
    Map<String, HotfixPlatform> platforms;
    /**
     * If true and the hotfix fails to apply, refuse to launch (mod scope) or refuse to
     * complete startup (client scope). If false, log a warning and continue.
     */
    boolean mandatory;
    /** Optional i18n key (or literal text) for the success/failure toast. */
    String message;
    /** Skip applying when running client version is below this. */
    String minClientVersion;
    /**
     * When the running client version is at or above this, the hotfix is considered
     * obsolete: existing CLIENT_BINARY override is removed so the freshly bundled
     * binary is used. No-op for MOD_FILE.
     */
    String maxClientVersion;
  }

  /**
   * Per-host-OS replacement details for one {@link Hotfix} entry. The archive (zip on
   * Windows, tar.gz on Linux) is auto-detected by magic bytes; the client doesn't trust the
   * URL extension.
   *
   * Hashes are CRC32 (8 lowercase hex chars) to match the format already used by
   * {@link GameFilesWhitelistEntry#whitelist}. CRC32 is purely for integrity/idempotency
   * (catches a corrupted download or a wrong file by accident) and is NOT a security control.
   * Authenticity comes from the detached Ed25519 "{@code <replacementUrl>.sig}" sidecar verified
   * against the public key pinned in the client (see {@link UpdateSignatureVerifier}); every
   * hotfix archive must therefore ship a matching {@code .sig}.
   */
  @Data
  public static class HotfixPlatform {
    /**
     * Filename to check on disk for this OS. CLIENT_BINARY: the bin entrypoint
     * ({@code gpgnet4ta.exe} on Windows, {@code gpgnet4ta} on Linux). MOD_FILE: the on-disk
     * name in the mod's install dir ({@code tdraw.dll} or {@code taesc.dll}).
     */
    String targetFile;
    /** CRC32s (any-match, 8 lowercase hex chars) of the live on-disk file to replace. */
    List<String> badCrc32;
    /** URL of the replacement archive (zip or tar.gz). */
    String replacementUrl;
    /** CRC32 (8 lowercase hex chars) of the downloaded archive — verifies download integrity. */
    String replacementArchiveCrc32;
    /**
     * Path inside the archive of the file whose hash is verified against
     * {@code replacementCrc32}. For CLIENT_BINARY the entire archive is extracted; this
     * names the entrypoint to verify. For MOD_FILE only this member is extracted and dropped
     * over {@code targetFile}.
     */
    String replacementMember;
    /** CRC32 (8 lowercase hex chars) of the extracted member; doubles as idempotency key. */
    String replacementCrc32;
  }
}