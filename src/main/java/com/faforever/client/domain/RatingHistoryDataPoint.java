package com.faforever.client.domain;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class RatingHistoryDataPoint {
  private final byte score;
  private final OffsetDateTime instant;
  private final double mean;
  private final double deviation;
}
