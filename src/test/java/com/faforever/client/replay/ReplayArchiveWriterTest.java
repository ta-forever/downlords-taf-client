package com.faforever.client.replay;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

public class ReplayArchiveWriterTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void renamesReplayInsideArchiveAndPreservesOtherEntries() throws Exception {
    byte[] source = zip(Map.of(
        "190504.tad", "replay data".getBytes(StandardCharsets.UTF_8),
        "notes.txt", "notes".getBytes(StandardCharsets.UTF_8)));
    ByteArrayOutputStream destination = new ByteArrayOutputStream();

    ReplayArchiveWriter.rewrite(new ByteArrayInputStream(source), destination,
        "2026-09-04 - The Pass - Alice, Bob.pro");

    Map<String, byte[]> entries = unzip(destination.toByteArray());
    assertThat(entries.keySet(), containsInAnyOrder(
        "2026-09-04 - The Pass - Alice, Bob.pro", "notes.txt"));
    assertThat(new String(entries.get("2026-09-04 - The Pass - Alice, Bob.pro"), StandardCharsets.UTF_8),
        is("replay data"));
    assertThat(new String(entries.get("notes.txt"), StandardCharsets.UTF_8), is("notes"));
  }

  @Test
  public void missingReplayDoesNotOverwriteAnExistingDownload() throws Exception {
    Path destination = folder.newFile("replay.zip").toPath();
    byte[] original = "existing download".getBytes(StandardCharsets.UTF_8);
    Files.write(destination, original);
    byte[] source = zip(Map.of("notes.txt", new byte[] {1, 2, 3}));

    assertThrows(ZipException.class, () -> ReplayArchiveWriter.write(
        new ByteArrayInputStream(source), destination, "replay.pro"));

    assertArrayEquals(original, Files.readAllBytes(destination));
    assertThat(folder.getRoot().list(), is(new String[] {"replay.zip"}));
  }

  private static byte[] zip(Map<String, byte[]> entries) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  private static Map<String, byte[]> unzip(byte[] archive) throws Exception {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        entries.put(entry.getName(), zip.readAllBytes());
      }
    }
    return entries;
  }
}
