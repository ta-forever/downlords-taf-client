package com.faforever.client.map;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@Data
public class MapSize implements Comparable<MapSize> {

  private static final float MAP_SIZE_FACTOR = 1.0f;

  private static Map<String, MapSize> cache = new HashMap<>();
  private final int width;
  private final int height;

  /**
   * @param width in kilometers
   * @param height in kilometers
   */
  private MapSize(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public static MapSize valueOf(int widthInPixels, int heightInPixels) {
    String cacheKey = String.valueOf(widthInPixels) + String.valueOf(heightInPixels);
    if (cache.containsKey(cacheKey)) {
      return cache.get(cacheKey);
    }

    MapSize mapSize = new MapSize(widthInPixels, heightInPixels);
    cache.put(cacheKey, mapSize);
    return mapSize;
  }

  @Override
  public int compareTo(@NotNull MapSize o) {
    int dimension = width * height;
    int otherDimension = o.width * o.height;

    if (dimension == otherDimension) {
      //noinspection SuspiciousNameCombination
      return Integer.compare(width, o.width);
    }

    return Integer.compare(dimension, otherDimension);
  }

  @Override
  public int hashCode() {
    int result = width;
    result = 31 * result + height;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    MapSize mapSize = (MapSize) o;

    return width == mapSize.width && height == mapSize.height;
  }

  public int getWidthInKm() {
    return (int) (width / MAP_SIZE_FACTOR);
  }

  public int getHeightInKm() {
    return (int) (height / MAP_SIZE_FACTOR);
  }

  @Override
  public String toString() {
    return String.format("%dx%d", width, height);
  }
}
