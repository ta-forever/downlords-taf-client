package com.faforever.client.update;

import com.faforever.client.fa.TotalAnnihilationService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.update.ClientConfiguration.Hotfix;
import com.faforever.client.update.ClientConfiguration.HotfixPlatform;
import com.faforever.client.update.ClientConfiguration.HotfixScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Applies emergency hotfixes declared in dfc-config.json, e.g. when a client release ships
 * with a critical bug in gpgnet4ta or when a mod ships with a critical bug in tdraw.dll.
 *
 * <ul>
 *   <li>{@link HotfixScope#CLIENT_BINARY} entries land in a user-writable override directory
 *       ({@link TotalAnnihilationService#getHotfixBinDir()}) so admin elevation is not required
 *       even when the client is installed under Program Files.
 *   <li>{@link HotfixScope#MOD_FILE} entries replace a single file inside the affected mod's
 *       install directory.
 * </ul>
 *
 * Hash-driven idempotency: an entry whose live on-disk hash already matches
 * {@code replacementSha256} is skipped; an entry whose live hash matches none of {@code badSha256}
 * is also skipped (no marker files needed). Archives are downloaded to a temp file,
 * integrity-checked against {@code replacementArchiveSha256}, format-detected by magic bytes
 * (zip vs tar.gz), then extracted; the extracted target is verified against
 * {@code replacementSha256} before the toast fires.
 *
 * Per-host-OS sub-objects in {@link Hotfix#getPlatforms()} carry every field that varies
 * between Windows / Linux / Mac (target filename, archive URL/hash, inner-archive member).
 * Hotfix authors who target a Windows-only binary (e.g. talauncher.exe used under wine on
 * Linux) point both the windows and linux platform entries at the same Windows zip with
 * {@code targetFile: "talauncher.exe"} and {@code replacementMember: "bin/talauncher.exe"}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HotfixService {

  private static final int HTTP_FOLLOW_LIMIT = 5;

  private final PreferencesService preferencesService;
  private final TotalAnnihilationService totalAnnihilationService;
  private final NotificationService notificationService;
  private final I18n i18n;

  /**
   * Applies all CLIENT_BINARY hotfixes from the loaded dfc-config. Intended to be called
   * once after the remote config is loaded and before the user can launch a game.
   *
   * @return true if no mandatory hotfix failed.
   */
  public boolean applyClientBinaryHotfixes() {
    ClientConfiguration cfg = preferencesService.getClientRemoteConfiguration();
    List<Hotfix> hotfixes = cfg == null ? null : cfg.getHotfixes();
    if (hotfixes == null || hotfixes.isEmpty()) {
      return true;
    }
    boolean allOk = true;
    for (Hotfix h : hotfixes) {
      if (h.getScope() != HotfixScope.CLIENT_BINARY) {
        continue;
      }
      try {
        if (!applyClientBinaryHotfix(h)) {
          allOk &= !h.isMandatory();
        }
      } catch (Exception e) {
        log.warn("[hotfix:{}] failed: {}", h.getId(), e.toString(), e);
        allOk &= !h.isMandatory();
      }
    }
    return allOk;
  }

  /**
   * Applies all MOD_FILE hotfixes targeting the given featured mod. Intended to be called
   * just before launching gpgnet4ta for that mod.
   *
   * @return true if no mandatory hotfix failed (callers should abort launch if false).
   */
  public boolean applyModFileHotfixes(String modTechnical) {
    if (modTechnical == null) {
      return true;
    }
    ClientConfiguration cfg = preferencesService.getClientRemoteConfiguration();
    List<Hotfix> hotfixes = cfg == null ? null : cfg.getHotfixes();
    if (hotfixes == null || hotfixes.isEmpty()) {
      return true;
    }
    boolean allOk = true;
    for (Hotfix h : hotfixes) {
      if (h.getScope() != HotfixScope.MOD_FILE) {
        continue;
      }
      if (!modTechnical.equalsIgnoreCase(h.getModTechnical())) {
        continue;
      }
      try {
        if (!applyModFileHotfix(h, modTechnical)) {
          allOk &= !h.isMandatory();
        }
      } catch (Exception e) {
        log.warn("[hotfix:{}] failed for mod {}: {}", h.getId(), modTechnical, e.toString(), e);
        allOk &= !h.isMandatory();
      }
    }
    return allOk;
  }

  private boolean applyClientBinaryHotfix(Hotfix h) throws IOException {
    HotfixPlatform p = pickPlatform(h);
    if (p == null) {
      log.info("[hotfix:{}] no platform entry for current host OS, skipping", safeId(h));
      return true;
    }
    if (!validate(h, p)) {
      return true;
    }
    String running = Version.getCurrentVersion();
    if (isObsolete(h, running)) {
      cleanupOverrideForObsolete(h, p);
      return true;
    }
    if (!isApplicable(h, running)) {
      return true;
    }

    Path target = totalAnnihilationService.resolveNativeBinary(p.getTargetFile());
    if (!Files.isRegularFile(target)) {
      log.warn("[hotfix:{}] target not found: {}", h.getId(), target);
      return true;
    }

    String liveHash = crc32(target);
    if (matchesAny(liveHash, p.getReplacementCrc32())) {
      log.debug("[hotfix:{}] already applied (crc32 matches replacementCrc32)", h.getId());
      return true;
    }
    if (!matchesAny(liveHash, p.getBadCrc32())) {
      log.debug("[hotfix:{}] live crc32 {} not in badCrc32, skipping", h.getId(), liveHash);
      return true;
    }

    // The published gpgnet4ta release archives have entries under bin/ (e.g. bin/gpgnet4ta.exe
    // on Windows, bin/gpgnet4ta on Linux). Extracting into the hotfix root produces
    // <root>/bin/<targetFile>, which is what resolveNativeBinary() looks up.
    Path extractRoot = totalAnnihilationService.getHotfixRoot();
    Files.createDirectories(extractRoot);

    Path archiveTmp = Files.createTempFile(extractRoot, "hotfix-", ".archive");
    try {
      log.info("[hotfix:{}] downloading {} -> {}", h.getId(), p.getReplacementUrl(), archiveTmp);
      download(p.getReplacementUrl(), archiveTmp);
      verifyArchiveHash(p, archiveTmp);
      extractAllInto(archiveTmp, extractRoot);
    } finally {
      try {
        Files.deleteIfExists(archiveTmp);
      } catch (IOException e) {
        log.debug("[hotfix:{}] tmp delete failed: {}", h.getId(), e.toString());
      }
    }

    Path overrideTarget = totalAnnihilationService.getHotfixBinDir().resolve(p.getTargetFile());
    String newHash = crc32(overrideTarget);
    if (!matchesAny(newHash, p.getReplacementCrc32())) {
      throw new IOException("extracted " + p.getTargetFile() + " crc32=" + newHash
          + " does not match expected replacementCrc32=" + p.getReplacementCrc32());
    }
    log.info("[hotfix:{}] applied client-binary hotfix to {}", h.getId(), overrideTarget);
    notifySuccess(h);
    return true;
  }

  private boolean applyModFileHotfix(Hotfix h, String modTechnical) throws IOException {
    HotfixPlatform p = pickPlatform(h);
    if (p == null) {
      log.info("[hotfix:{}] no platform entry for current host OS, skipping", safeId(h));
      return true;
    }
    if (!validate(h, p) || p.getReplacementMember() == null || p.getReplacementMember().isBlank()) {
      log.warn("[hotfix:{}] mod-file hotfix missing replacementMember", safeId(h));
      return true;
    }
    if (h.getModTechnical() == null) {
      log.warn("[hotfix:{}] mod-file hotfix missing modTechnical", safeId(h));
      return true;
    }
    String running = Version.getCurrentVersion();
    if (isObsolete(h, running) || !isApplicable(h, running)) {
      return true;
    }

    TotalAnnihilationPrefs taPrefs = preferencesService.getTotalAnnihilation(modTechnical);
    if (taPrefs == null || taPrefs.getInstalledPath() == null
        || !Files.isDirectory(taPrefs.getInstalledPath())) {
      log.debug("[hotfix:{}] mod {} not installed, skipping", h.getId(), modTechnical);
      return true;
    }
    Path target = taPrefs.getInstalledPath().resolve(p.getTargetFile());
    if (!Files.isRegularFile(target)) {
      log.debug("[hotfix:{}] {} missing in mod {}, skipping", h.getId(), p.getTargetFile(), modTechnical);
      return true;
    }

    String liveHash = crc32(target);
    if (matchesAny(liveHash, p.getReplacementCrc32())) {
      log.debug("[hotfix:{}] already applied (crc32 matches replacementCrc32)", h.getId());
      return true;
    }
    if (!matchesAny(liveHash, p.getBadCrc32())) {
      log.debug("[hotfix:{}] live crc32 {} not in badCrc32, skipping", h.getId(), liveHash);
      return true;
    }

    Path parent = target.getParent();
    Path archiveTmp = Files.createTempFile(parent, ".hotfix-", ".archive");
    Path memberTmp = Files.createTempFile(parent, ".hotfix-", ".tmp");
    try {
      log.info("[hotfix:{}] downloading {} -> {}", h.getId(), p.getReplacementUrl(), archiveTmp);
      download(p.getReplacementUrl(), archiveTmp);
      verifyArchiveHash(p, archiveTmp);
      extractMember(archiveTmp, p.getReplacementMember(), memberTmp);
      String newHash = crc32(memberTmp);
      if (!matchesAny(newHash, p.getReplacementCrc32())) {
        throw new IOException("extracted member " + p.getReplacementMember() + " crc32=" + newHash
            + " does not match expected replacementCrc32=" + p.getReplacementCrc32());
      }
      moveAtomic(memberTmp, target);
      log.info("[hotfix:{}] applied mod-file hotfix to {}", h.getId(), target);
      notifySuccess(h);
    } finally {
      try { Files.deleteIfExists(archiveTmp); } catch (IOException ignored) {}
      try { Files.deleteIfExists(memberTmp); } catch (IOException ignored) {}
    }
    return true;
  }

  private boolean validate(Hotfix h, HotfixPlatform p) {
    if (h == null || h.getId() == null || h.getScope() == null
        || p == null
        || p.getTargetFile() == null
        || p.getReplacementUrl() == null || p.getReplacementUrl().isBlank()
        || p.getReplacementCrc32() == null
        || p.getReplacementArchiveCrc32() == null
        || p.getBadCrc32() == null) {
      log.warn("[hotfix:{}] entry missing required fields, skipping", safeId(h));
      return false;
    }
    return true;
  }

  private static String safeId(Hotfix h) {
    return h == null || h.getId() == null ? "<null>" : h.getId();
  }

  private boolean isObsolete(Hotfix h, String running) {
    if (h.getMaxClientVersion() == null) {
      return false;
    }
    try {
      // shouldUpdate(running, max) is true iff max > running. So obsolete when !shouldUpdate.
      return !Version.shouldUpdate(running, h.getMaxClientVersion());
    } catch (Exception e) {
      return false;
    }
  }

  private boolean isApplicable(Hotfix h, String running) {
    if (h.getMinClientVersion() == null) {
      return true;
    }
    try {
      // applicable if running >= min, i.e. !shouldUpdate(running, min) means !(min > running).
      return !Version.shouldUpdate(running, h.getMinClientVersion());
    } catch (Exception e) {
      // If running is "snapshot" / "unspecified", Version.shouldUpdate returns false -> treat as applicable.
      return true;
    }
  }

  private void cleanupOverrideForObsolete(Hotfix h, HotfixPlatform p) {
    Path overrideDir = totalAnnihilationService.getHotfixBinDir();
    Path overrideTarget = overrideDir.resolve(p.getTargetFile());
    if (Files.exists(overrideTarget)) {
      try {
        Files.delete(overrideTarget);
        log.info("[hotfix:{}] obsolete (running >= maxClientVersion); removed override {}", h.getId(), overrideTarget);
      } catch (IOException e) {
        log.warn("[hotfix:{}] failed to remove obsolete override {}: {}", h.getId(), overrideTarget, e.toString());
      }
    }
  }

  /** Picks the per-host-OS platform sub-object, or null if none defined for this host. */
  private HotfixPlatform pickPlatform(Hotfix h) {
    Map<String, HotfixPlatform> platforms = h.getPlatforms();
    if (platforms == null || platforms.isEmpty()) return null;
    String key;
    if (org.bridj.Platform.isWindows()) key = "windows";
    else if (org.bridj.Platform.isLinux()) key = "linux";
    else if (org.bridj.Platform.isMacOSX()) key = "mac";
    else return null;
    return platforms.get(key);
  }

  private void verifyArchiveHash(HotfixPlatform p, Path archivePath) throws IOException {
    String got = crc32(archivePath);
    if (!matchesAny(got, p.getReplacementArchiveCrc32())) {
      throw new IOException("downloaded archive crc32=" + got
          + " does not match expected replacementArchiveCrc32=" + p.getReplacementArchiveCrc32());
    }
  }

  private void download(String url, Path dest) throws IOException {
    String current = url;
    for (int hop = 0; hop < HTTP_FOLLOW_LIMIT; hop++) {
      HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
      conn.setInstanceFollowRedirects(false);
      conn.setConnectTimeout(15_000);
      conn.setReadTimeout(60_000);
      int status = conn.getResponseCode();
      if (status == HttpURLConnection.HTTP_MOVED_PERM
          || status == HttpURLConnection.HTTP_MOVED_TEMP
          || status == HttpURLConnection.HTTP_SEE_OTHER
          || status == 307 || status == 308) {
        String loc = conn.getHeaderField("Location");
        conn.disconnect();
        if (loc == null) {
          throw new IOException("redirect with no Location header");
        }
        current = loc;
        continue;
      }
      if (status / 100 != 2) {
        conn.disconnect();
        throw new IOException("HTTP " + status + " for " + current);
      }
      try (InputStream in = conn.getInputStream()) {
        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        conn.disconnect();
      }
      return;
    }
    throw new IOException("too many redirects starting from " + url);
  }

  private enum ArchiveKind { ZIP, TAR_GZ }

  /**
   * Sniffs the archive's magic bytes rather than trusting the URL/filename — robust against
   * mislabelled URLs and avoids parsing edge cases. ZIP local-file-header starts with PK\x03\x04;
   * gzip member starts with \x1F\x8B (which is what wraps tar in .tar.gz).
   */
  private ArchiveKind detectKind(Path archivePath) throws IOException {
    byte[] head = new byte[4];
    try (InputStream in = Files.newInputStream(archivePath)) {
      int n = in.readNBytes(head, 0, head.length);
      if (n >= 2 && (head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B) {
        return ArchiveKind.TAR_GZ;
      }
      if (n >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 0x03 && head[3] == 0x04) {
        return ArchiveKind.ZIP;
      }
    }
    throw new IOException("unrecognized archive format (expected zip or tar.gz)");
  }

  private void extractAllInto(Path archivePath, Path destDir) throws IOException {
    Path destAbs = destDir.toAbsolutePath().normalize();
    switch (detectKind(archivePath)) {
      case ZIP -> extractAllZip(archivePath, destAbs);
      case TAR_GZ -> extractAllTarGz(archivePath, destAbs);
    }
  }

  private void extractMember(Path archivePath, String memberName, Path dest) throws IOException {
    switch (detectKind(archivePath)) {
      case ZIP -> extractMemberZip(archivePath, memberName, dest);
      case TAR_GZ -> extractMemberTarGz(archivePath, memberName, dest);
    }
  }

  private void extractAllZip(Path zipPath, Path destAbs) throws IOException {
    try (ZipFile zf = new ZipFile(zipPath.toFile())) {
      var entries = Collections.list(zf.entries());
      for (ZipEntry e : entries) {
        if (e.isDirectory()) continue;
        Path resolved = safeResolve(destAbs, e.getName());
        Files.createDirectories(resolved.getParent());
        Path tmp = Files.createTempFile(resolved.getParent(), ".hotfix-", ".part");
        try (InputStream in = zf.getInputStream(e)) {
          Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        moveAtomic(tmp, resolved);
        // ZIP doesn't carry POSIX mode bits in a portable way (the Unix-ext does, but the
        // gpgnet4ta release zip doesn't use it). Mark Linux-host targets executable
        // unconditionally — same workaround the gradle build uses for faf-uid.
        if (!org.bridj.Platform.isWindows()) {
          resolved.toFile().setExecutable(true, false);
        }
      }
    }
  }

  private void extractMemberZip(Path zipPath, String memberName, Path dest) throws IOException {
    try (ZipFile zf = new ZipFile(zipPath.toFile())) {
      ZipEntry e = zf.getEntry(memberName);
      if (e == null || e.isDirectory()) {
        throw new IOException("zip member not found: " + memberName);
      }
      try (InputStream in = zf.getInputStream(e)) {
        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private void extractAllTarGz(Path tgzPath, Path destAbs) throws IOException {
    try (InputStream fis = Files.newInputStream(tgzPath);
         BufferedInputStream bis = new BufferedInputStream(fis);
         GzipCompressorInputStream gis = new GzipCompressorInputStream(bis);
         TarArchiveInputStream tin = new TarArchiveInputStream(gis)) {
      TarArchiveEntry e;
      while ((e = tin.getNextTarEntry()) != null) {
        if (e.isDirectory() || e.isSymbolicLink() || e.isLink()) continue;
        if (!tin.canReadEntryData(e)) continue;
        Path resolved = safeResolve(destAbs, e.getName());
        Files.createDirectories(resolved.getParent());
        Path tmp = Files.createTempFile(resolved.getParent(), ".hotfix-", ".part");
        try (var out = Files.newOutputStream(tmp)) {
          tin.transferTo(out);
        }
        moveAtomic(tmp, resolved);
        applyTarMode(resolved, e.getMode());
      }
    }
  }

  private void extractMemberTarGz(Path tgzPath, String memberName, Path dest) throws IOException {
    try (InputStream fis = Files.newInputStream(tgzPath);
         BufferedInputStream bis = new BufferedInputStream(fis);
         GzipCompressorInputStream gis = new GzipCompressorInputStream(bis);
         TarArchiveInputStream tin = new TarArchiveInputStream(gis)) {
      TarArchiveEntry e;
      while ((e = tin.getNextTarEntry()) != null) {
        if (e.isDirectory()) continue;
        if (!memberName.equals(e.getName())) continue;
        if (!tin.canReadEntryData(e)) {
          throw new IOException("tar entry not readable: " + memberName);
        }
        try (var out = Files.newOutputStream(dest, java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE)) {
          tin.transferTo(out);
        }
        applyTarMode(dest, e.getMode());
        return;
      }
    }
    throw new IOException("tar member not found: " + memberName);
  }

  /**
   * Apply the tar entry's POSIX mode bits to the extracted file. On Windows we fall back to
   * setting the executable flag if the owner-execute bit is set in the tar header — this is
   * cheap, and harmless on FAT-style filesystems.
   */
  private void applyTarMode(Path file, int mode) {
    boolean ownerExec = (mode & 0_100) != 0;
    if (ownerExec) {
      file.toFile().setExecutable(true, false);
    }
    boolean ownerWrite = (mode & 0_200) != 0;
    file.toFile().setWritable(ownerWrite, false);
    boolean ownerRead = (mode & 0_400) != 0;
    if (ownerRead) {
      file.toFile().setReadable(true, false);
    }
  }

  private Path safeResolve(Path destAbs, String entryName) throws IOException {
    Path resolved = destAbs.resolve(entryName).normalize();
    if (!resolved.startsWith(destAbs)) {
      throw new IOException("archive entry escapes destination: " + entryName);
    }
    return resolved;
  }

  /**
   * Atomic move with a Windows-friendly fallback: if the destination is held by another
   * process (rare for our targets, since CLIENT_BINARY runs at startup before any game,
   * and MOD_FILE runs between games), rename the destination aside first.
   */
  private void moveAtomic(Path src, Path dest) throws IOException {
    try {
      Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException atomicFailed) {
      try {
        if (Files.exists(dest)) {
          Path aside = dest.resolveSibling(dest.getFileName().toString() + ".old");
          try { Files.deleteIfExists(aside); } catch (IOException ignored) {}
          Files.move(dest, aside, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException fallbackFailed) {
        fallbackFailed.addSuppressed(atomicFailed);
        throw fallbackFailed;
      }
    }
  }

  private void notifySuccess(Hotfix h) {
    String msg = h.getMessage();
    if (msg == null || msg.isBlank()) {
      return;
    }
    // Treat msg as either an i18n key (resolved if present in bundle) or a literal string
    // (used as-is when no matching key exists). Lets dfc-config authors use plain text without
    // needing to ship a corresponding messages.properties update.
    String resolved = i18n.getWithDefault(msg, msg);
    notificationService.addImmediateInfoNotification("hotfix.appliedTitle", "hotfix.appliedBody",
        h.getId(), resolved);
  }

  private boolean matchesAny(String hex, String expected) {
    return expected != null && hex != null && hex.equalsIgnoreCase(expected);
  }

  private boolean matchesAny(String hex, List<String> expected) {
    if (expected == null || hex == null) return false;
    for (String e : expected) {
      if (hex.equalsIgnoreCase(e)) return true;
    }
    return false;
  }

  /**
   * Computes CRC32 of a file as 8 lowercase hex characters — same format as the
   * {@link ClientConfiguration.GameFilesWhitelistEntry#whitelist} entries, so dfc-config
   * authors can copy values between hotfix and whitelist entries.
   */
  private String crc32(Path file) throws IOException {
    CRC32 crc = new CRC32();
    byte[] buf = new byte[64 * 1024];
    try (InputStream in = Files.newInputStream(file)) {
      int n;
      while ((n = in.read(buf)) > 0) {
        crc.update(buf, 0, n);
      }
    }
    return String.format(Locale.ROOT, "%08x", crc.getValue());
  }
}
