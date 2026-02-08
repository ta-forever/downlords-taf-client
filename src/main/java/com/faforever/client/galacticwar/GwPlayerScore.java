package com.faforever.client.galacticwar;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

@Value
public class GwPlayerScore {
  Integer wins;

  @SerializedName("cum_winning_scores")
  Float cumWinningScores;  // total +ve score contributed to control of planet

  Integer losses;

  @SerializedName("cum_losing_scores")
  Float cumLosingScores;  // total -ve score contributed to control of planet

  public static final GwPlayerScore EMPTY_SCORE =
      new GwPlayerScore(0, 0.0f, 0, 0.0f);
}

