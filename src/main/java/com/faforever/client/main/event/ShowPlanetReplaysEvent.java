package com.faforever.client.main.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ShowPlanetReplaysEvent extends OpenOnlineReplayVaultEvent {
  private final String gwPlanetHash;
  private final List<String> legacyPlanetNames;
}
