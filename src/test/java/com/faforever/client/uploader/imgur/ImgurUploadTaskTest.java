package com.faforever.client.uploader.imgur;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.i18n.I18n;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.uploader.ImageTooLargeException;
import javafx.scene.image.WritableImage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Pins the multipart Imgur upload behind chat image paste (issue #51): the raw PNG is sent unencoded,
 * Imgur errors carry the HTTP status, and oversized images are rejected before anything is sent.
 */
public class ImgurUploadTaskTest extends AbstractPlainJavaFxTest {

  private static final String SUCCESS_BODY = "{\"data\":{\"link\":\"https://i.imgur.com/abc.png\"},\"success\":true,\"status\":200}";

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock
  private I18n i18n;

  private MockWebServer server;
  private ClientProperties clientProperties;
  private ImgurUploadTask instance;

  @Before
  public void setUp() throws Exception {
    server = new MockWebServer();
    server.start();

    clientProperties = new ClientProperties();
    clientProperties.getImgur().getUpload().setBaseUrl(server.url("/3/image").toString());
    clientProperties.getImgur().getUpload().setClientId("test-client-id");

    instance = newTask();
  }

  @After
  public void tearDown() throws Exception {
    server.shutdown();
  }

  private ImgurUploadTask newTask() {
    ImgurUploadTask task = new ImgurUploadTask(i18n, clientProperties);
    task.afterPropertiesSet();
    return task;
  }

  @Test
  public void uploadSendsRawPngAsMultipartAndReturnsLink() throws Exception {
    server.enqueue(new MockResponse().setBody(SUCCESS_BODY));
    byte[] png = new byte[4096];
    for (int i = 0; i < png.length; i++) {
      png[i] = (byte) i;
    }

    String link = instance.upload(png, "image.png", "image/png");

    assertThat(link, is("https://i.imgur.com/abc.png"));
    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod(), is("POST"));
    assertThat(request.getHeader("Authorization"), is("Client-ID test-client-id"));
    assertThat(request.getHeader("Content-Type"), startsWith("multipart/form-data; boundary="));
    byte[] body = request.getBody().readByteArray();
    assertThat(new String(body, StandardCharsets.ISO_8859_1), containsString("name=\"image\""));
    assertTrue("request body should contain the PNG bytes unencoded", indexOf(body, png) >= 0);
  }

  @Test
  public void callEncodesImageAsPng() throws Exception {
    server.enqueue(new MockResponse().setBody(SUCCESS_BODY));
    instance.setImage(new WritableImage(4, 4));

    instance.call();

    byte[] body = server.takeRequest().getBody().readByteArray();
    byte[] pngSignature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    assertTrue("request body should contain a PNG", indexOf(body, pngSignature) >= 0);
  }

  @Test
  public void imgurErrorIncludesHttpStatusAndMessage() {
    server.enqueue(new MockResponse().setResponseCode(400)
        .setBody("{\"data\":{\"error\":\"File is over the size limit\"},\"success\":false,\"status\":400}"));

    IOException exception = assertThrows(IOException.class, () -> instance.upload(new byte[16], "image.png", "image/png"));

    assertThat(exception.getMessage(), containsString("HTTP 400"));
    assertThat(exception.getMessage(), containsString("File is over the size limit"));
  }

  @Test
  public void imageOverMaxSizeIsRejectedWithoutUploading() {
    clientProperties.getImgur().getUpload().setMaxSize(10);
    instance = newTask();

    ImageTooLargeException exception = assertThrows(ImageTooLargeException.class, () -> instance.upload(new byte[11], "image.png", "image/png"));

    assertThat(exception.getSize(), is(11L));
    assertThat(exception.getMaxSize(), is(10L));
    assertThat(server.getRequestCount(), is(0));
  }

  @Test
  public void droppedFileIsUploadedUnchangedWithItsContentType() throws Exception {
    server.enqueue(new MockResponse().setBody(SUCCESS_BODY));
    byte[] gif = "GIF89a-animated-frames".getBytes(StandardCharsets.US_ASCII);
    Path file = temporaryFolder.newFile("My funny clip (1).GIF").toPath();
    Files.write(file, gif);
    instance.setFile(file);

    String link = instance.call();

    assertThat(link, is("https://i.imgur.com/abc.png"));
    byte[] body = server.takeRequest().getBody().readByteArray();
    String bodyText = new String(body, StandardCharsets.ISO_8859_1);
    assertThat(bodyText, containsString("filename=\"image.gif\""));
    assertThat(bodyText, containsString("Content-Type: image/gif"));
    assertTrue("request body should contain the file bytes unchanged", indexOf(body, gif) >= 0);
  }

  @Test
  public void droppedFileOverMaxSizeIsRejectedWithoutUploading() throws Exception {
    clientProperties.getImgur().getUpload().setMaxSize(10);
    instance = newTask();
    Path file = temporaryFolder.newFile("big.png").toPath();
    Files.write(file, new byte[11]);

    ImageTooLargeException exception = assertThrows(ImageTooLargeException.class, () -> instance.uploadFile(file));

    assertThat(exception.getSize(), is(11L));
    assertThat(server.getRequestCount(), is(0));
  }

  @Test
  public void droppedFileOfUnsupportedTypeIsRejected() throws Exception {
    Path file = temporaryFolder.newFile("notes.txt").toPath();

    assertThrows(IOException.class, () -> instance.uploadFile(file));
    assertThat(server.getRequestCount(), is(0));
  }

  private static int indexOf(byte[] haystack, byte[] needle) {
    outer:
    for (int i = 0; i <= haystack.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }
}
