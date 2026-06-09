package com.faforever.client.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

public class LogTailUtilTest {

  private static final long CAP = 10_000;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private File writeLog(String name, int lines) throws IOException {
    File f = temp.newFile(name);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lines; i++) {
      sb.append("line ").append(i).append(" some padding text to add bytes\n");
    }
    Files.writeString(f.toPath(), sb.toString());
    return f;
  }

  @Test
  public void smallLogPassesThroughUnchanged() throws IOException {
    File small = writeLog("client.log", 5);
    Path tempDir = temp.newFolder("out").toPath();

    File result = LogTailUtil.tailIfTooLarge(small, tempDir, CAP);

    assertThat(result, is(small));
  }

  @Test
  public void largeTextLogIsTailedToUnderCapWithHeader() throws IOException {
    File big = writeLog("client.log", 5000); // well over CAP
    assertThat(big.length(), is(greaterThan(CAP)));
    Path tempDir = temp.newFolder("out").toPath();

    File result = LogTailUtil.tailIfTooLarge(big, tempDir, CAP);

    assertThat(result, is(not(big)));
    assertThat(result.getName(), is("client.log")); // entry name preserved
    // tail copy holds roughly CAP bytes plus the one-line header, never the whole original
    assertThat(result.length(), is(lessThanOrEqualTo(CAP + 200)));
    String content = Files.readString(result.toPath(), StandardCharsets.UTF_8);
    assertThat(content.startsWith("[log truncated for upload:"), is(true));
    // keeps the END of the file (most recent lines), not the start
    assertThat(content.contains("line 4999 "), is(true));
    assertThat(content.contains("line 0 some padding"), is(false));
    // starts on a clean line boundary after the header
    assertThat(content.lines().skip(1).findFirst().orElse("").startsWith("line "), is(true));
  }

  @Test
  public void jvmCrashLogIsNeverTailed() throws IOException {
    File hsErr = writeLog("hs_err_pid1234.log", 5000);
    Path tempDir = temp.newFolder("out").toPath();

    File result = LogTailUtil.tailIfTooLarge(hsErr, tempDir, CAP);

    assertThat(result, is(hsErr)); // head matters for crash logs; left whole
  }

  @Test
  public void nonTextFileIsNeverTailed() throws IOException {
    File dump = temp.newFile("crash.dmp");
    Files.write(dump.toPath(), new byte[(int) CAP * 3]);
    Path tempDir = temp.newFolder("out").toPath();

    File result = LogTailUtil.tailIfTooLarge(dump, tempDir, CAP);

    assertThat(result, is(dump));
  }

  @Test
  public void nullAndMissingFilesArePassedThrough() throws IOException {
    Path tempDir = temp.newFolder("out").toPath();
    assertThat(LogTailUtil.tailIfTooLarge(null, tempDir, CAP), is((File) null));
    File missing = new File(temp.getRoot(), "nope.log");
    assertThat(LogTailUtil.tailIfTooLarge(missing, tempDir, CAP), is(missing));
  }

  @Test
  public void tailLargeTextLogsMapsArrayInOrder() throws IOException {
    File small = writeLog("game_1.log", 2);
    File big = writeLog("ice-adapter.log", 5000);
    Path tempDir = temp.newFolder("out").toPath();

    File[] result = LogTailUtil.tailLargeTextLogs(new File[]{small, big}, tempDir, CAP);

    assertThat(result[0], is(small));
    assertThat(result[1], is(not(big)));
    assertThat(result[1].getName(), is("ice-adapter.log"));
  }

  @Test
  public void deleteRecursivelyQuietlyRemovesTempDir() throws IOException {
    Path dir = temp.newFolder("toDelete").toPath();
    Files.writeString(dir.resolve("a.log"), "x");

    LogTailUtil.deleteRecursivelyQuietly(dir);

    assertThat(Files.exists(dir), is(false));
  }

  @Test
  public void deleteRecursivelyQuietlyToleratesNull() {
    LogTailUtil.deleteRecursivelyQuietly(null); // must not throw
  }
}
