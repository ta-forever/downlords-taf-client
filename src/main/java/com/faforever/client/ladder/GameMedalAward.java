package com.faforever.client.ladder;

import lombok.Value;

import javax.annotation.Nullable;

/** A medal earned in a game. {@code code} is the stable key (display name/icon via i18n). */
@Value
public class GameMedalAward {
  int playerId;
  @Nullable String playerLogin;
  String code;
  @Nullable Float value;
}
