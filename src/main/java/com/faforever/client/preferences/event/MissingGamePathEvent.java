package com.faforever.client.preferences.event;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class MissingGamePathEvent {
  private boolean immediateUserActionRequired;
  String modTechnicalName;

  public MissingGamePathEvent(String modTechnicalName) {
    this.immediateUserActionRequired = false;
    this.modTechnicalName = modTechnicalName;
  }
}
