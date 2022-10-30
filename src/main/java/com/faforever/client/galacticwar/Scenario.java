package com.faforever.client.galacticwar;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Value
class Scenario {
  String label;

  @SerializedName("node")
  List<Planet> planets;

  @SerializedName("edge")
  List<JumpGate> jumpGates;

  static public Scenario fromFile(Path path) throws IOException {
    return new Gson().fromJson(Files.newBufferedReader(path), Scenario.class);
  }
}
