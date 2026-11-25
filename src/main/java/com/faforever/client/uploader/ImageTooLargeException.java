package com.faforever.client.uploader;

import lombok.Getter;

/** Thrown when an image to upload exceeds the configured maximum upload size. */
@Getter
public class ImageTooLargeException extends RuntimeException {

  private final long size;
  private final long maxSize;

  public ImageTooLargeException(long size, long maxSize) {
    super("Image of " + size + " bytes exceeds max upload size of " + maxSize + " bytes");
    this.size = size;
    this.maxSize = maxSize;
  }
}
