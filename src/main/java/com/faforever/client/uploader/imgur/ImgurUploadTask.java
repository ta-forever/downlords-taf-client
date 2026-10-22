package com.faforever.client.uploader.imgur;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.config.ClientProperties.Imgur.Upload;
import com.faforever.client.i18n.I18n;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.ResourceLocks;
import com.faforever.client.uploader.ImageFileTypes;
import com.faforever.client.uploader.ImageTooLargeException;
import com.faforever.commons.io.ByteCopier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;


@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ImgurUploadTask extends CompletableTask<String> implements InitializingBean {

  private static final int MAX_ERROR_BODY_LENGTH = 500;

  private final Gson gson;

  private final I18n i18n;
  private final ClientProperties clientProperties;

  private Image image;
  private Path file;
  private int maxUploadSize;
  private String baseUrl;
  private String clientId;

  @Inject
  public ImgurUploadTask(I18n i18n, ClientProperties clientProperties) {
    super(Priority.HIGH);
    gson = new GsonBuilder().create();

    this.i18n = i18n;
    this.clientProperties = clientProperties;
  }

  @Override
  public void afterPropertiesSet() {
    updateTitle(i18n.get("chat.imageUploadTask.title"));
    Upload uploadProperties = clientProperties.getImgur().getUpload();
    maxUploadSize = uploadProperties.getMaxSize();
    baseUrl = uploadProperties.getBaseUrl();
    clientId = uploadProperties.getClientId();
  }

  @Override
  protected String call() throws Exception {
    if (file != null) {
      return uploadFile(file);
    }
    return upload(encodePng(image), "image.png", "image/png");
  }

  /** Uploads the file's original bytes; the size is checked before the file is read into memory. */
  String uploadFile(Path file) throws IOException {
    String contentType = ImageFileTypes.contentType(file)
        .orElseThrow(() -> new IOException("Unsupported image file type: " + file.getFileName()));
    long size = Files.size(file);
    if (size > maxUploadSize) {
      throw new ImageTooLargeException(size, maxUploadSize);
    }
    // A fixed name rather than the user's, which may contain characters that break the multipart header.
    String fileName = "image." + ImageFileTypes.extension(file).orElseThrow();
    return upload(Files.readAllBytes(file), fileName, contentType);
  }

  private static byte[] encodePng(Image image) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
    ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
    return byteArrayOutputStream.toByteArray();
  }

  /**
   * Uploads the image as a raw multipart file part (no base64), streamed with a fixed length so neither this
   * method nor {@link HttpURLConnection} holds another copy of the image in memory.
   */
  String upload(byte[] imageBytes, String fileName, String contentType) throws IOException {
    if (imageBytes.length > maxUploadSize) {
      throw new ImageTooLargeException(imageBytes.length, maxUploadSize);
    }

    String boundary = "----TafImageUpload" + UUID.randomUUID().toString().replace("-", "");
    byte[] preamble = ("--" + boundary + "\r\n"
        + "Content-Disposition: form-data; name=\"image\"; filename=\"" + fileName + "\"\r\n"
        + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
    byte[] epilogue = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
    long totalBytes = (long) preamble.length + imageBytes.length + epilogue.length;

    URL url = new URL(baseUrl);
    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

    urlConnection.setDoOutput(true);
    urlConnection.setDoInput(true);
    urlConnection.setRequestMethod("POST");
    urlConnection.setRequestProperty("Authorization", "Client-ID " + clientId);
    urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
    urlConnection.setFixedLengthStreamingMode(totalBytes);
    urlConnection.connect();

    ResourceLocks.acquireUploadLock();
    try (OutputStream outputStream = urlConnection.getOutputStream()) {
      outputStream.write(preamble);
      ByteCopier.from(new ByteArrayInputStream(imageBytes))
          .to(outputStream)
          .totalBytes(imageBytes.length)
          .listener(this::updateProgress)
          .copy();
      outputStream.write(epilogue);
    } finally {
      ResourceLocks.freeUploadLock();
    }

    int responseCode = urlConnection.getResponseCode();
    if (responseCode >= 400) {
      String errorBody = readBody(urlConnection.getErrorStream());
      if (errorBody.length() > MAX_ERROR_BODY_LENGTH) {
        errorBody = errorBody.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
      }
      throw new IOException("Image upload failed, HTTP " + responseCode + ": " + errorBody);
    }

    ImgurRestResponse imgurRestResponse = gson.fromJson(readBody(urlConnection.getInputStream()), ImgurRestResponse.class);

    if (imgurRestResponse == null || !imgurRestResponse.success) {
      throw new IOException("Image upload failed, status code: " + (imgurRestResponse == null ? responseCode : imgurRestResponse.status));
    }

    return imgurRestResponse.data.link;
  }

  private static String readBody(InputStream inputStream) throws IOException {
    if (inputStream == null) {
      return "";
    }
    StringBuilder stringBuilder = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        stringBuilder.append(line).append("\n");
      }
    }
    return stringBuilder.toString();
  }

  public void setImage(Image image) {
    this.image = image;
  }

  public void setFile(Path file) {
    this.file = file;
  }
}
