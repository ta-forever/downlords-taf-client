package com.faforever.client.fa;

import lombok.Value;

@Value
public class DemoFileInfo {
  private String filePath;
  private String mapName;
  private String mapHash;
  private String modHash;
  private Integer taVersionMajor;
  private Integer taVersionMinor;
}
