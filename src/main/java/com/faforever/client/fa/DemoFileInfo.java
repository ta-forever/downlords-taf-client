package com.faforever.client.fa;

import lombok.Value;

@Value
public class DemoFileInfo {
  String filePath;
  String mapName;
  String mapHash;
  String modHash;
  Integer taVersionMajor;
  Integer taVersionMinor;
}
