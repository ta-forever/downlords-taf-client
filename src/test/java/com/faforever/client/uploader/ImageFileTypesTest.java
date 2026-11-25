package com.faforever.client.uploader;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Pins which dropped files the chat box accepts for upload, and the content type each is sent with. */
public class ImageFileTypesTest {

  @Test
  public void imageExtensionsAreSupportedCaseInsensitively() {
    assertThat(ImageFileTypes.contentType(Path.of("C:\\pics\\clip.GIF")), is(Optional.of("image/gif")));
    assertThat(ImageFileTypes.contentType(Path.of("photo.jpeg")), is(Optional.of("image/jpeg")));
    assertThat(ImageFileTypes.contentType(Path.of("photo.jpg")), is(Optional.of("image/jpeg")));
    assertThat(ImageFileTypes.contentType(Path.of("shot.png")), is(Optional.of("image/png")));
    assertThat(ImageFileTypes.isSupported(Path.of("sticker.webp")), is(true));
  }

  @Test
  public void otherFilesAreNotSupported() {
    assertThat(ImageFileTypes.isSupported(Path.of("notes.txt")), is(false));
    assertThat(ImageFileTypes.isSupported(Path.of("replay.tad")), is(false));
    assertThat(ImageFileTypes.isSupported(Path.of("no-extension")), is(false));
    assertThat(ImageFileTypes.isSupported(Path.of("trailing-dot.")), is(false));
  }
}
