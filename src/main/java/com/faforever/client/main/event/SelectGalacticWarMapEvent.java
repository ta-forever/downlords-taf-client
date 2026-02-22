package com.faforever.client.main.event;

import com.faforever.client.galacticwar.GwMapSelectStrategy;
import com.faforever.client.galacticwar.Planet;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SelectGalacticWarMapEvent extends OpenCustomGamesEvent {
  final private String galaxyTechnicalName;
  final private Planet planet;
  final private GwMapSelectStrategy mapSelectStrategy;
  final private List<String> mapSelectRegexes;
  final private Integer mapSelectMapPoolId;
}
