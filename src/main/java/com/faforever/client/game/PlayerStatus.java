package com.faforever.client.game;

import com.faforever.client.player.NameRecord;
import com.faforever.client.player.Player;
import lombok.Getter;

import java.util.stream.Collectors;

public enum PlayerStatus {

  IDLE("player.playerStatus.idle"),
  HOSTING("player.playerStatus.hosting"),
  JOINING("player.playerStatus.joining"),
  HOSTED("player.playerStatus.hosted"),
  JOINED("player.playerStatus.joined"),
  PLAYING("player.playerStatus.playing");

  @Getter
  private final String i18nKey;

  PlayerStatus(String i18nKey) {
    this.i18nKey = i18nKey;
  }

  public static PlayerStatus fromDto(com.faforever.client.remote.domain.PlayerStatus dto) {
    return switch(dto) {
      case IDLE -> IDLE;
      case HOSTING -> HOSTING;
      case JOINING -> JOINING;
      case HOSTED -> HOSTED;
      case JOINED -> JOINED;
      case PLAYING -> PLAYING;
    };
  }
}
