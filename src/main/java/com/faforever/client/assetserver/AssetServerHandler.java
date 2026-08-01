package com.faforever.client.assetserver;

import com.faforever.client.preferences.PreferencesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Contract A endpoints, all under {@code /<token>/}:
 * <ul>
 *   <li>{@code GET manifest?mod=<technical>} — installed archives for a featured mod, in load
 *       precedence order ({@code priority}: higher wins, mirroring TA's VFS: gp3 &gt; ccx &gt;
 *       ufo &gt; hpi, loose files above all).</li>
 *   <li>{@code GET archive/<name>?mod=<technical>} — raw archive bytes; supports Range/ETag.</li>
 *   <li>{@code GET file/<relpath>?mod=<technical>} — loose file under the install dir.</li>
 * </ul>
 */
@Slf4j
class AssetServerHandler implements HttpHandler {

  /** Archive extensions TA's VFS mounts, in ascending precedence. */
  private static final String[] ARCHIVE_EXTENSIONS_ASCENDING = {".hpi", ".ufo", ".ccx", ".gp3"};
  private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d*)-(\\d*)");
  private static final ObjectMapper JSON = new ObjectMapper();

  private final byte[] tokenBytes;
  private final String allowedOrigin;
  private final PreferencesService preferencesService;

  AssetServerHandler(String token, String allowedOrigin, PreferencesService preferencesService) {
    this.tokenBytes = token.getBytes(StandardCharsets.UTF_8);
    this.allowedOrigin = allowedOrigin;
    this.preferencesService = preferencesService;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      addCorsHeaders(exchange);

      if ("OPTIONS".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(204, -1);
        return;
      }
      if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }

      // Path: /<token>/<endpoint>[/...]
      String rawPath = exchange.getRequestURI().getRawPath();
      String[] parts = rawPath.split("/", 3);  // ["", token, rest]
      if (parts.length < 3 || !MessageDigest.isEqual(tokenBytes, parts[1].getBytes(StandardCharsets.UTF_8))) {
        exchange.sendResponseHeaders(404, -1);
        return;
      }
      String rest = parts[2];
      Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());

      if (rest.equals("mods")) {
        handleMods(exchange);
      } else if (rest.equals("manifest")) {
        handleManifest(exchange, query.get("mod"));
      } else if (rest.startsWith("archive/")) {
        handleArchive(exchange, query.get("mod"), URLDecoder.decode(rest.substring("archive/".length()), StandardCharsets.UTF_8));
      } else if (rest.startsWith("file/")) {
        handleLooseFile(exchange, query.get("mod"), URLDecoder.decode(rest.substring("file/".length()), StandardCharsets.UTF_8));
      } else {
        exchange.sendResponseHeaders(404, -1);
      }
    } catch (Exception e) {
      log.warn("Asset server request failed: {}", exchange.getRequestURI(), e);
      try {
        exchange.sendResponseHeaders(500, -1);
      } catch (IOException ignored) {
        // headers already sent
      }
    } finally {
      exchange.close();
    }
  }

  private void addCorsHeaders(HttpExchange exchange) {
    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Range, If-None-Match");
    exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "Content-Range, Accept-Ranges, ETag, Content-Length");
    exchange.getResponseHeaders().set("Vary", "Origin");
  }

  private Path installPath(String mod) {
    if (mod == null || mod.isBlank()) {
      return null;
    }
    return preferencesService.getTotalAnnihilation(mod).getInstalledPath();
  }

  /**
   * Lists featured mods with a usable local installation. Used by the viewer to resolve which
   * installed mod matches a replay whose mod isn't known up front (e.g. TADA replays) — it
   * fetches each candidate's manifest and matches the demo's unit-table fingerprint.
   */
  private void handleMods(HttpExchange exchange) throws IOException {
    ObjectNode root = JSON.createObjectNode();
    ArrayNode mods = root.putArray("mods");
    for (var taPrefs : preferencesService.getPreferences().getTotalAnnihilationAllMods()) {
      Path installDir = taPrefs.getInstalledPath();
      if (taPrefs.getBaseGameName() != null && installDir != null && Files.isDirectory(installDir)) {
        mods.add(taPrefs.getBaseGameName());
      }
    }
    sendJson(exchange, 200, root);
  }

  private void handleManifest(HttpExchange exchange, String mod) throws IOException {
    Path installDir = installPath(mod);
    if (installDir == null || !Files.isDirectory(installDir)) {
      sendJson(exchange, 404, JSON.createObjectNode().put("error", "mod not installed: " + mod));
      return;
    }

    ObjectNode root = JSON.createObjectNode();
    root.put("mod", mod);
    ArrayNode archives = root.putArray("archives");
    try (Stream<Path> entries = Files.list(installDir)) {
      entries.filter(Files::isRegularFile)
          .filter(path -> archivePriority(path.getFileName().toString()) >= 0)
          .sorted((a, b) -> {
            int byPriority = Integer.compare(
                archivePriority(b.getFileName().toString()), archivePriority(a.getFileName().toString()));
            return byPriority != 0 ? byPriority
                : a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
          })
          .forEach(path -> {
            try {
              archives.addObject()
                  .put("name", path.getFileName().toString())
                  .put("size", Files.size(path))
                  .put("mtime", Files.getLastModifiedTime(path).toMillis())
                  .put("priority", archivePriority(path.getFileName().toString()));
            } catch (IOException e) {
              log.warn("Skipping unreadable archive {}", path, e);
            }
          });
    }

    // Loose files OUTRANK all archives in native TA — fopen_HPI tries the real filesystem
    // before walking the archive banks (InitTAHPIAry @0x41d4c0 plate). The mod-tweak feature
    // relies on this. Enumerated here so the viewer's VFS can consult the set without paying
    // a probe request per lookup; dot-directories (git-managed installs) and the archives
    // themselves are excluded.
    ArrayNode loose = root.putArray("loose");
    try (Stream<Path> tree = Files.walk(installDir)) {
      tree.filter(Files::isRegularFile)
          .filter(path -> {
            Path rel = installDir.relativize(path);
            for (Path part : rel) {
              if (part.toString().startsWith(".")) {
                return false;
              }
            }
            return archivePriority(path.getFileName().toString()) < 0;
          })
          .limit(20_000)
          .forEach(path -> {
            try {
              loose.addObject()
                  .put("path", installDir.relativize(path).toString().replace('\\', '/'))
                  .put("size", Files.size(path))
                  .put("mtime", Files.getLastModifiedTime(path).toMillis());
            } catch (IOException e) {
              log.warn("Skipping unreadable loose file {}", path, e);
            }
          });
    }
    sendJson(exchange, 200, root);
  }

  /**
   * Load precedence as an explicit int so the JS VFS never needs TA's rules: higher wins.
   * Extension class dominates (gp3 > ccx > ufo > hpi); TAForever.gp3 additionally outranks all
   * other archives — the client's own updater guarantees it is mounted above everything else
   * (it carries TAF-mandated overrides). Returns -1 for non-archive files.
   */
  private static int archivePriority(String fileName) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (lower.equals("taforever.gp3")) {
      return ARCHIVE_EXTENSIONS_ASCENDING.length + 1;
    }
    for (int i = 0; i < ARCHIVE_EXTENSIONS_ASCENDING.length; i++) {
      if (lower.endsWith(ARCHIVE_EXTENSIONS_ASCENDING[i])) {
        return i + 1;
      }
    }
    return -1;
  }

  private void handleArchive(HttpExchange exchange, String mod, String name) throws IOException {
    Path installDir = installPath(mod);
    if (installDir == null || name.contains("/") || name.contains("\\") || name.contains("..")
        || archivePriority(name) < 0) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    serveFile(exchange, installDir.resolve(name), installDir);
  }

  private void handleLooseFile(HttpExchange exchange, String mod, String relPath) throws IOException {
    Path installDir = installPath(mod);
    if (installDir == null || relPath.contains("..") || relPath.contains(":") || relPath.startsWith("/")) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    serveFile(exchange, resolveIgnoringCase(installDir, relPath.replace('\\', '/')), installDir);
  }

  /**
   * Resolve a relative path under {@code base}, falling back to a case-INSENSITIVE match per
   * component when the exact path does not exist.
   *
   * TA is case-blind about file names — it is a DOS-era game and HPI lookups ignore case,
   * which is why the viewer's VFS lower-cases every path it handles. On Windows the filesystem
   * happens to agree, so a lower-cased request for a real `Icon/AIR.PCX` just works. On Linux
   * it does not: EVERY loose file whose real name is not already lower-case 404s. The visible
   * symptom was strategic icons falling back to plain circles for every unit on a Linux ProTA
   * install — the pack is a loose `Icon/` directory holding `iconcfg.ini` and files like
   * `AIR.PCX` and `ARM.pcx`, and none of them could be fetched.
   *
   * Costs nothing where the name already matches (the exact hit returns immediately), so the
   * directory listing only happens on the path that was previously a hard failure.
   */
  private static Path resolveIgnoringCase(Path base, String relPath) {
    Path exact = base.resolve(relPath);
    if (Files.exists(exact)) {
      return exact;
    }
    Path current = base;
    for (String part : relPath.split("/")) {
      if (part.isEmpty() || ".".equals(part)) {
        continue;
      }
      Path next = current.resolve(part);
      if (Files.exists(next)) {
        current = next;
        continue;
      }
      Path match = null;
      try (Stream<Path> children = Files.list(current)) {
        match = children.filter(p -> p.getFileName().toString().equalsIgnoreCase(part))
            .findFirst().orElse(null);
      } catch (IOException e) {
        return exact;   // unreadable directory — let serveFile answer 404
      }
      if (match == null) {
        return exact;   // genuinely absent, whatever the case
      }
      current = match;
    }
    return current;
  }

  private void serveFile(HttpExchange exchange, Path file, Path mustBeUnder) throws IOException {
    Path normalized = file.normalize();
    if (!normalized.startsWith(mustBeUnder.normalize()) || !Files.isRegularFile(normalized)) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }

    long size = Files.size(normalized);
    String etag = "\"" + size + "-" + Files.getLastModifiedTime(normalized).toMillis() + "\"";
    exchange.getResponseHeaders().set("ETag", etag);
    exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");

    if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
      exchange.sendResponseHeaders(304, -1);
      return;
    }

    long from = 0;
    long to = size - 1;
    int status = 200;
    String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
    if (rangeHeader != null) {
      Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
      if (!matcher.matches() || (matcher.group(1).isEmpty() && matcher.group(2).isEmpty())) {
        exchange.getResponseHeaders().set("Content-Range", "bytes */" + size);
        exchange.sendResponseHeaders(416, -1);
        return;
      }
      if (matcher.group(1).isEmpty()) {
        // suffix form: bytes=-N (last N bytes)
        long suffix = Long.parseLong(matcher.group(2));
        from = Math.max(0, size - suffix);
      } else {
        from = Long.parseLong(matcher.group(1));
        if (!matcher.group(2).isEmpty()) {
          to = Long.parseLong(matcher.group(2));
        }
      }
      if (from > to || from >= size) {
        exchange.getResponseHeaders().set("Content-Range", "bytes */" + size);
        exchange.sendResponseHeaders(416, -1);
        return;
      }
      to = Math.min(to, size - 1);
      exchange.getResponseHeaders().set("Content-Range", "bytes " + from + "-" + to + "/" + size);
      status = 206;
    }

    long length = to - from + 1;
    if ("HEAD".equals(exchange.getRequestMethod())) {
      exchange.getResponseHeaders().set("Content-Length", Long.toString(length));
      exchange.sendResponseHeaders(status, -1);
      return;
    }

    exchange.sendResponseHeaders(status, length);
    try (FileChannel channel = FileChannel.open(normalized, StandardOpenOption.READ);
         OutputStream out = exchange.getResponseBody()) {
      ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
      long position = from;
      long remaining = length;
      while (remaining > 0) {
        buffer.clear();
        buffer.limit((int) Math.min(buffer.capacity(), remaining));
        int read = channel.read(buffer, position);
        if (read <= 0) {
          break;
        }
        out.write(buffer.array(), 0, read);
        position += read;
        remaining -= read;
      }
    }
  }

  private void sendJson(HttpExchange exchange, int status, ObjectNode body) throws IOException {
    byte[] bytes = JSON.writeValueAsBytes(body);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static Map<String, String> parseQuery(String rawQuery) {
    Map<String, String> result = new HashMap<>();
    if (rawQuery == null) {
      return result;
    }
    for (String pair : rawQuery.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        result.put(
            URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
            URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
      }
    }
    return result;
  }
}
