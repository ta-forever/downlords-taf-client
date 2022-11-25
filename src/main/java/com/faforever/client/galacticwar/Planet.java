package com.faforever.client.galacticwar;

import com.faforever.client.game.Faction;
import com.google.gson.annotations.SerializedName;
import com.sun.javafx.charts.Legend;
import com.sun.javafx.charts.Legend.LegendItem;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.layout.Region;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Value
public class Planet {
  Integer id;

  @SerializedName("label")
  String name;

  @SerializedName("map")
  String mapName;

  @SerializedName("mod")
  String modTechnical;
  Double size;
  Map<Faction, Double> score;
  Map<Integer, Map<Faction, Double>> belligerents;

  @SerializedName("capital_of")
  Faction capitalOf;

  @SerializedName("controlled_by")
  Faction controlledBy;

  PlanetGraphics graphics;

  public String toString() {
    return name;
  }
}
