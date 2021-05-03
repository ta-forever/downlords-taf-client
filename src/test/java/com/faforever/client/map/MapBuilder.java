package com.faforever.client.map;

import com.faforever.client.map.MapBean.Type;

public class MapBuilder {

  private final MapBean mapBean;

  private MapBuilder() {
    mapBean = new MapBean();
  }

  public static MapBuilder create() {
    return new MapBuilder();
  }

  public MapBuilder defaultValues() {
    return mapName("Map name")
        .hpiArchiveName("map_name.v001")
        .type(Type.SKIRMISH)
        .mapSize(MapSize.valueOf(512, 512));
  }

  public MapBuilder mapSize(MapSize mapSize) {
    mapBean.setSize(mapSize);
    return this;
  }

  public MapBuilder hpiArchiveName(String hpi) {
    mapBean.setHpiArchiveName(hpi);
    return this;
  }

  public MapBuilder mapName(String name) {
    mapBean.setMapName(name);
    return this;
  }

  public MapBuilder type(Type type) {
    mapBean.setType(type);
    return this;
  }

  public MapBean get() {
    return mapBean;
  }
}
