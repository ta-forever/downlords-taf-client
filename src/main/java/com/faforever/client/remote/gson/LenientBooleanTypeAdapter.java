package com.faforever.client.remote.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Reads booleans that the server may send as numbers (MySQL {@code TINYINT(1)} columns serialise as
 * {@code 0}/{@code 1}) or as strings, in addition to real JSON booleans.
 */
public class LenientBooleanTypeAdapter extends TypeAdapter<Boolean> {

  @Override
  public void write(JsonWriter out, Boolean value) throws IOException {
    out.value(value);
  }

  @Override
  public Boolean read(JsonReader in) throws IOException {
    JsonToken token = in.peek();
    switch (token) {
      case NULL:
        in.nextNull();
        return null;
      case NUMBER:
        return in.nextInt() != 0;
      case STRING:
        String value = in.nextString();
        return "1".equals(value) || Boolean.parseBoolean(value);
      default:
        return in.nextBoolean();
    }
  }
}
