package com.faforever.client.remote.gson;

import com.faforever.client.game.Faction;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.net.HttpCookie;

public class HttpCookieTypeAdapter extends TypeAdapter<HttpCookie> {

  public static final HttpCookieTypeAdapter INSTANCE = new HttpCookieTypeAdapter();

  private HttpCookieTypeAdapter() {
    // private
  }

  @Override
  public void write(JsonWriter out, HttpCookie value) throws IOException {
    out.value(value.getName());
    out.value(value.getValue());
  }

  @Override
  public HttpCookie read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }

    return new HttpCookie(in.nextString(), in.nextString());
   }
}
