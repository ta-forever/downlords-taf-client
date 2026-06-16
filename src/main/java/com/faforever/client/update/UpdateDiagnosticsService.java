package com.faforever.client.update;

import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.update.ClientConfiguration.Hotfix;
import com.faforever.client.update.ClientConfiguration.HotfixPlatform;
import com.faforever.client.update.ClientConfiguration.ReleaseInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Read-only diagnostic that exercises the <em>current</em> dfc-config download paths: for the
 * client installer and every hotfix archive it downloads the artifact and its detached
 * "{@code .sig}" sidecar and verifies the Ed25519 signature with {@link UpdateSignatureVerifier}
 * — the same check {@link DownloadUpdateTask}/{@link HotfixService} run before installing — plus
 * the {@code replacementArchiveCrc32} for hotfixes.
 *
 * <p>Crucially it checks <b>all</b> platforms (windows + linux + mac), not just the running host
 * OS, so a single client can confirm that, say, both the Windows and Linux installers and their
 * signatures are correctly published. Nothing is installed, extracted, or written outside a temp
 * file. The version/CRC applicability gates that would normally skip an entry are deliberately
 * ignored, so a maintainer can confirm a freshly published {@code .sig} before clients hit it.
 *
 * <p>The actual (re)install of the running platform goes through the real
 * {@link ClientUpdateService#downloadAndInstallInBackground} path, not here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateDiagnosticsService {

  private static final int HTTP_FOLLOW_LIMIT = 5;

  private final PreferencesService preferencesService;

  public List<UpdateCheckResult> verifyConfiguredDownloads() {
    List<UpdateCheckResult> results = new ArrayList<>();
    ClientConfiguration cfg = preferencesService.getClientRemoteConfiguration();
    if (cfg == null) {
      results.add(UpdateCheckResult.fail("remote configuration", "dfc-config not loaded"));
      return results;
    }

    checkClientRelease(cfg.getLatestRelease(), results);

    List<Hotfix> hotfixes = cfg.getHotfixes();
    if (hotfixes == null || hotfixes.isEmpty()) {
      results.add(UpdateCheckResult.skip("hotfixes", "none configured"));
    } else {
      for (Hotfix h : hotfixes) {
        checkHotfix(h, results);
      }
    }
    return results;
  }

  private void checkClientRelease(ReleaseInfo release, List<UpdateCheckResult> results) {
    if (release == null) {
      results.add(UpdateCheckResult.skip("client update", "no latestRelease in config"));
      return;
    }
    String version = release.getVersion() == null ? "(unversioned)" : release.getVersion();
    // Every platform URL the config declares — checked from any host OS.
    Map<String, URL> urls = new LinkedHashMap<>();
    urls.put("windows", release.getWindowsUrl());
    urls.put("linux", release.getLinuxUrl());
    urls.put("mac", release.getMacUrl());

    boolean any = false;
    for (Map.Entry<String, URL> entry : urls.entrySet()) {
      URL url = entry.getValue();
      if (url == null) {
        continue;
      }
      any = true;
      String label = "client update " + version + " (" + entry.getKey() + ")";
      results.add(verifySignedArtifact(label, url.toExternalForm(), null));
    }
    if (!any) {
      results.add(UpdateCheckResult.skip("client update", "no download URLs in latestRelease"));
    }
  }

  private void checkHotfix(Hotfix h, List<UpdateCheckResult> results) {
    String id = h.getId() == null ? "<no id>" : h.getId();
    Map<String, HotfixPlatform> platforms = h.getPlatforms();
    if (platforms == null || platforms.isEmpty()) {
      results.add(UpdateCheckResult.skip("hotfix " + id, "no platform entries"));
      return;
    }
    boolean any = false;
    for (Map.Entry<String, HotfixPlatform> entry : platforms.entrySet()) {
      HotfixPlatform p = entry.getValue();
      if (p == null || p.getReplacementUrl() == null || p.getReplacementUrl().isBlank()) {
        continue;
      }
      any = true;
      String label = "hotfix " + id + " (" + entry.getKey() + ")";
      results.add(verifySignedArtifact(label, p.getReplacementUrl(), p.getReplacementArchiveCrc32()));
    }
    if (!any) {
      results.add(UpdateCheckResult.skip("hotfix " + id, "no platform has a replacementUrl"));
    }
  }

  /**
   * Downloads {@code url} and its "{@code .sig}" sidecar, verifies the Ed25519 signature, and (if
   * {@code expectedCrc32} is non-null) checks the archive CRC32. Returns a result line; never
   * throws.
   */
  private UpdateCheckResult verifySignedArtifact(String label, String url, String expectedCrc32) {
    Path artifact = null;
    Path signature = null;
    try {
      artifact = Files.createTempFile("dfc-test-", ".bin");
      signature = Files.createTempFile("dfc-test-", ".sig");
      fetch(url, artifact);

      String crcNote = "";
      if (expectedCrc32 != null) {
        String crc = crc32(artifact);
        crcNote = expectedCrc32.equalsIgnoreCase(crc)
            ? ", crc32 ok"
            : ", crc32 MISMATCH (got " + crc + ", expected " + expectedCrc32 + ")";
      }

      fetch(url + ".sig", signature);
      UpdateSignatureVerifier.verify(artifact, signature);
      return UpdateCheckResult.ok(label, "signature valid (" + sizeKiB(artifact) + ")" + crcNote);
    } catch (SecurityException e) {
      return UpdateCheckResult.fail(label, "signature INVALID: " + e.getMessage());
    } catch (IOException e) {
      return UpdateCheckResult.fail(label, "download failed: " + e.getMessage());
    } finally {
      deleteQuietly(artifact);
      deleteQuietly(signature);
    }
  }

  /** Redirect-following download (GitHub release assets 302 to object storage). */
  private void fetch(String url, Path dest) throws IOException {
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
        String location = conn.getHeaderField("Location");
        conn.disconnect();
        if (location == null) {
          throw new IOException("redirect with no Location header");
        }
        current = location;
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

  private String crc32(Path file) throws IOException {
    CRC32 crc = new CRC32();
    byte[] buffer = new byte[64 * 1024];
    try (InputStream in = Files.newInputStream(file)) {
      int n;
      while ((n = in.read(buffer)) > 0) {
        crc.update(buffer, 0, n);
      }
    }
    return String.format(Locale.ROOT, "%08x", crc.getValue());
  }

  private String sizeKiB(Path p) throws IOException {
    return (Files.size(p) / 1024) + " KiB";
  }

  private void deleteQuietly(Path p) {
    if (p == null) {
      return;
    }
    try {
      Files.deleteIfExists(p);
    } catch (IOException e) {
      log.debug("temp cleanup failed: {}", p, e);
    }
  }
}
