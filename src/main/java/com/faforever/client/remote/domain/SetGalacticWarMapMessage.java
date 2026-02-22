package com.faforever.client.remote.domain;

public class SetGalacticWarMapMessage extends ClientMessage {

  private String galaxyTechnicalName;
  private String planetName;
  private String mapName;

  public SetGalacticWarMapMessage(String galaxyTechnicalName, String planetName, String mapName) {
    super(ClientMessageType.GALACTIC_WAR_SET_MAP);
    this.galaxyTechnicalName = galaxyTechnicalName;
    this.planetName = planetName;
    this.mapName = mapName;
  }

  public String getGalaxyTechnicalName() {
    return galaxyTechnicalName;
  }
  public String getPlanetName() {
    return planetName;
  }
  public String getMapName() {
    return mapName;
  }
}
