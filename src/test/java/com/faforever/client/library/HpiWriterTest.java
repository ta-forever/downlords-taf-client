package com.faforever.client.library;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.Inflater;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

public class HpiWriterTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void packedArchiveReadsBackWithTheLayoutMaptoolNeeds() throws Exception {
    Path root = temp.newFolder("pack").toPath();
    Files.createDirectories(root.resolve("maps"));
    Files.createDirectories(root.resolve("previews"));
    Files.createDirectories(root.resolve("empty"));
    byte[] big = new byte[200_000];
    for (int i = 0; i < big.length; i++) {
      big[i] = (byte) (i * 31 + i / 7);
    }
    Files.write(root.resolve("maps/Map One.tnt"), big);
    Files.writeString(root.resolve("maps/Map One.ota"), "[GlobalHeader]\n{\n}\n");
    Files.write(root.resolve("maps/Zero.ota"), new byte[0]);
    Files.writeString(root.resolve("oamap.yaml"), "oamap: 1\n");
    Files.write(root.resolve("previews/Map One.png"), new byte[]{1, 2, 3});
    Path archive = temp.getRoot().toPath().resolve("out/pack.ufo");

    HpiWriter.pack(root, archive, name -> !name.equals("oamap.yaml") && !name.endsWith(".png"));

    Map<String, byte[]> files = new TreeMap<>();
    byte[] bytes = Files.readAllBytes(archive);
    ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII), is("HAPI"));
    assertThat(header.getInt(4), is(0x00010000));
    assertThat(header.getInt(12), is(0)); // not encrypted
    assertThat(header.getInt(16), is(20)); // the root directory comes first
    readDirectory(bytes, 20, "", files);

    assertThat(files.keySet(), contains("maps/Map One.ota", "maps/Map One.tnt", "maps/Zero.ota"));
    assertThat(files.get("maps/Map One.tnt"), is(big));
    assertThat(new String(files.get("maps/Map One.ota"), StandardCharsets.UTF_8), is("[GlobalHeader]\n{\n}\n"));
    assertThat(files.get("maps/Zero.ota").length, is(0));
    try (var listing = Files.list(archive.getParent())) {
      assertThat(listing.count(), is(1L)); // no temp file left behind
    }
  }

  private static void readDirectory(byte[] bytes, int offset, String prefix, Map<String, byte[]> files)
      throws Exception {
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    int count = buffer.getInt(offset);
    int entries = buffer.getInt(offset + 4);
    for (int i = 0; i < count; i++) {
      int nameOffset = buffer.getInt(entries + i * 9);
      int dataOffset = buffer.getInt(entries + i * 9 + 4);
      boolean directory = bytes[entries + i * 9 + 8] == 1;
      int end = nameOffset;
      while (bytes[end] != 0) {
        end++;
      }
      String name = prefix + new String(bytes, nameOffset, end - nameOffset, StandardCharsets.ISO_8859_1);
      if (directory) {
        readDirectory(bytes, dataOffset, name + "/", files);
      } else {
        files.put(name, readFile(bytes, buffer.getInt(dataOffset), buffer.getInt(dataOffset + 4),
            bytes[dataOffset + 8]));
      }
    }
  }

  private static byte[] readFile(byte[] bytes, int offset, int size, byte compression) throws Exception {
    assertThat(compression, is((byte) 2));
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    int chunks = Math.max(1, (size + 65535) / 65536);
    int position = offset + chunks * 4;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int i = 0; i < chunks; i++) {
      int chunkLength = buffer.getInt(offset + i * 4);
      assertThat(new String(bytes, position, 4, StandardCharsets.US_ASCII), is("SQSH"));
      int compressed = buffer.getInt(position + 7);
      int decompressed = buffer.getInt(position + 11);
      assertThat(chunkLength, is(19 + compressed));
      Inflater inflater = new Inflater();
      inflater.setInput(bytes, position + 19, compressed);
      byte[] chunk = new byte[decompressed];
      int read = 0;
      while (read < decompressed) {
        read += inflater.inflate(chunk, read, decompressed - read);
      }
      inflater.end();
      out.write(chunk);
      position += chunkLength;
    }
    return out.toByteArray();
  }
}
