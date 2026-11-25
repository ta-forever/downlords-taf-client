package com.faforever.client.library;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.Deflater;

/**
 * Packs a folder into a TA archive ({@code .ufo}/{@code .hpi}) that maptool can read. maptool only reads
 * archives, while maps downloaded inside Open Annihilation are loose folders.
 * <p>
 * The layout is the one real archives have, which maptool needs: the header, then the whole directory region
 * with the root directory at offset 20, then the file data. Every file is zlib compressed in 64 KiB chunks
 * (compression 2); an uncompressed variant and a data-first layout are not read by maptool.
 */
public final class HpiWriter {

  private static final int CHUNK_SIZE = 65536;
  private static final int HEADER_SIZE = 20;
  private static final int ENTRY_SIZE = 9;

  private HpiWriter() {
  }

  /** One directory of the archive, in the order its entries are written. */
  private static final class Dir {
    final List<String> names = new ArrayList<>();
    final List<Object> children = new ArrayList<>(); // Dir or Path

    boolean isEmpty() {
      return children.isEmpty();
    }
  }

  private static final class FileRecord {
    final Path source;
    byte[] data;
    int recordPosition; // inside the directory region
    int uncompressedSize;

    FileRecord(Path source) {
      this.source = source;
    }
  }

  /**
   * Packs {@code root} into {@code archive}, written atomically. Empty folders are left out.
   *
   * @param include decides per file, by its path relative to {@code root} ({@code /} separated)
   */
  public static void pack(Path root, Path archive, Predicate<String> include) throws IOException {
    Dir tree = scan(root, root, include);
    Path parent = archive.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temp = Files.createTempFile(parent, archive.getFileName().toString(), ".tmp");
    try {
      Files.write(temp, build(tree));
      Files.move(temp, archive, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private static Dir scan(Path root, Path folder, Predicate<String> include) throws IOException {
    Dir dir = new Dir();
    List<Path> entries;
    try (Stream<Path> listing = Files.list(folder)) {
      entries = listing.sorted().toList();
    }
    for (Path entry : entries) {
      String relative = root.relativize(entry).toString().replace('\\', '/');
      if (Files.isDirectory(entry)) {
        Dir sub = scan(root, entry, include);
        if (!sub.isEmpty()) {
          dir.names.add(entry.getFileName().toString());
          dir.children.add(sub);
        }
      } else if (include.test(relative)) {
        dir.names.add(entry.getFileName().toString());
        dir.children.add(new FileRecord(entry));
      }
    }
    return dir;
  }

  private static byte[] build(Dir tree) throws IOException {
    ByteArrayOutputStream directory = new ByteArrayOutputStream();
    List<FileRecord> files = new ArrayList<>();
    writeDirectory(tree, directory, files);
    byte[] directoryBytes = directory.toByteArray();

    int dataStart = HEADER_SIZE + directoryBytes.length;
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    for (FileRecord file : files) {
      int offset = dataStart + body.size();
      body.write(file.data);
      ByteBuffer record = ByteBuffer.wrap(directoryBytes, file.recordPosition, ENTRY_SIZE)
          .order(ByteOrder.LITTLE_ENDIAN);
      record.putInt(offset).putInt(file.uncompressedSize).put((byte) 2);
    }

    ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    header.put("HAPI".getBytes(StandardCharsets.US_ASCII)).putInt(0x00010000).putInt(dataStart).putInt(0)
        .putInt(HEADER_SIZE);

    ByteArrayOutputStream out = new ByteArrayOutputStream(dataStart + body.size());
    out.write(header.array());
    out.write(directoryBytes);
    out.write(body.toByteArray());
    return out.toByteArray();
  }

  /** Appends this directory (header, names, children, then its entry table) and returns its offset in the file. */
  private static int writeDirectory(Dir dir, ByteArrayOutputStream region, List<FileRecord> files)
      throws IOException {
    int headerOffset = HEADER_SIZE + region.size();
    region.write(new byte[8]);

    int count = dir.names.size();
    int[] nameOffsets = new int[count];
    for (int i = 0; i < count; i++) {
      nameOffsets[i] = HEADER_SIZE + region.size();
      region.write(dir.names.get(i).getBytes(StandardCharsets.ISO_8859_1));
      region.write(0);
    }
    int[] dataOffsets = new int[count];
    boolean[] isDirectory = new boolean[count];
    for (int i = 0; i < count; i++) {
      Object child = dir.children.get(i);
      if (child instanceof Dir sub) {
        isDirectory[i] = true;
        dataOffsets[i] = writeDirectory(sub, region, files);
      } else {
        FileRecord file = (FileRecord) child;
        byte[] content = Files.readAllBytes(file.source);
        file.data = compress(content);
        file.uncompressedSize = content.length;
        file.recordPosition = region.size();
        dataOffsets[i] = HEADER_SIZE + region.size();
        region.write(new byte[ENTRY_SIZE]);
        files.add(file);
      }
    }

    int entriesOffset = HEADER_SIZE + region.size();
    ByteBuffer entries = ByteBuffer.allocate(count * ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < count; i++) {
      entries.putInt(nameOffsets[i]).putInt(dataOffsets[i]).put((byte) (isDirectory[i] ? 1 : 0));
    }
    region.write(entries.array());

    // the directory header went in first; patch it in place once the entry table's position is known
    byte[] bytes = region.toByteArray();
    ByteBuffer.wrap(bytes, headerOffset - HEADER_SIZE, 8).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(count).putInt(entriesOffset);
    region.reset();
    region.write(bytes);
    return headerOffset;
  }

  /** Chunk size table, then one SQSH chunk per 64 KiB of content (at least one). */
  private static byte[] compress(byte[] content) throws IOException {
    int chunks = Math.max(1, (content.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
    List<byte[]> compressed = new ArrayList<>(chunks);
    for (int i = 0; i < chunks; i++) {
      int from = i * CHUNK_SIZE;
      int length = Math.min(CHUNK_SIZE, content.length - from);
      byte[] zlib = deflate(content, from, length);
      long checksum = 0;
      for (byte b : zlib) {
        checksum += b & 0xFF;
      }
      ByteBuffer chunk = ByteBuffer.allocate(19 + zlib.length).order(ByteOrder.LITTLE_ENDIAN);
      chunk.put("SQSH".getBytes(StandardCharsets.US_ASCII)).put((byte) 2).put((byte) 2).put((byte) 0)
          .putInt(zlib.length).putInt(length).putInt((int) checksum).put(zlib);
      compressed.add(chunk.array());
    }
    ByteBuffer table = ByteBuffer.allocate(chunks * 4).order(ByteOrder.LITTLE_ENDIAN);
    compressed.forEach(chunk -> table.putInt(chunk.length));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(table.array());
    for (byte[] chunk : compressed) {
      out.write(chunk);
    }
    return out.toByteArray();
  }

  private static byte[] deflate(byte[] content, int offset, int length) {
    Deflater deflater = new Deflater();
    try {
      deflater.setInput(content, offset, length);
      deflater.finish();
      ByteArrayOutputStream out = new ByteArrayOutputStream(length / 2 + 64);
      byte[] buffer = new byte[8192];
      while (!deflater.finished()) {
        out.write(buffer, 0, deflater.deflate(buffer));
      }
      return out.toByteArray();
    } finally {
      deflater.end();
    }
  }
}
