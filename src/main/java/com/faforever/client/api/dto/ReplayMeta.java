package com.faforever.client.api.dto;

import lombok.Value;

@Value
public class ReplayMeta {
  Integer gameId;
  String unitsHash;
  Integer taVersionMajor;
  Integer taVersionMinor;
  Boolean cheatsEnabled;
  Integer permLosByte;
  String mapName;
  String taMapHash;
}
