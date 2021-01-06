package com.faforever.client.remote.gson;

import com.faforever.client.remote.domain.PlayerStatus;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public final class PlayerStateTypeAdapter extends TypeAdapter<PlayerStatus> {

  public static final PlayerStateTypeAdapter INSTANCE = new PlayerStateTypeAdapter();

  private PlayerStateTypeAdapter() {
    // private
  }

  @Override
  public void write(JsonWriter out, PlayerStatus value) throws IOException {
    if (value == null) {
      out.value(PlayerStatus.IDLE.getString());
    } else {
      out.value(value.getString());
    }
  }

  @Override
  public PlayerStatus read(JsonReader in) throws IOException {
    return PlayerStatus.fromString(in.nextString());
  }
}
