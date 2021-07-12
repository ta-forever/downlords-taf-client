package com.faforever.client.fx;

import javafx.beans.InvalidationListener;
import javafx.beans.NamedArg;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.Axis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.ValueAxis;
import javafx.scene.shape.Line;

import java.util.Objects;

public class ScatterXChart<X, Y> extends ScatterChart<X, Y> {

  // data defining horizontal markers, xValues are ignored
  private ObservableList<Data<X, Y>> horizontalMarkers;

  public ScatterXChart(@NamedArg("xAxis") Axis<X> xAxis, @NamedArg("yAxis") Axis<Y> yAxis) {
    super(xAxis, yAxis);
    // a list that notifies on change of the yValue property
    horizontalMarkers = FXCollections.observableArrayList(d -> new Observable[] {d.YValueProperty()});
    // listen to list changes and re-plot
    horizontalMarkers.addListener((InvalidationListener) observable -> layoutPlotChildren());
  }

  /**
   * Add horizontal value marker. The marker's Y value is used to plot a
   * horizontal line across the plot area, its X value is ignored.
   *
   * @param marker must not be null.
   */
  public void addHorizontalValueMarker(Data<X, Y> marker, int strokeWidth) {
    Objects.requireNonNull(marker, "the marker must not be null");
    if (horizontalMarkers.contains(marker)) return;
    Line line = new Line();
    line.setStrokeWidth(strokeWidth);
    marker.setNode(line );
    getPlotChildren().add(line);
    horizontalMarkers.add(marker);
  }

  /**
   * Remove horizontal value marker.
   *
   * @param horizontalMarker must not be null
   */
  public void removeHorizontalValueMarker(Data<X, Y> marker) {
    Objects.requireNonNull(marker, "the marker must not be null");
    if (marker.getNode() != null) {
      getPlotChildren().remove(marker.getNode());
      marker.setNode(null);
    }
    horizontalMarkers.remove(marker);
  }

  public void clearHorizontalValueMarkers() {
    horizontalMarkers.stream().forEach(marker -> {
          getPlotChildren().remove(marker.getNode());
          marker.setNode(null);
        });
    horizontalMarkers.clear();
  }

  /**
   * Overridden to layout the value markers.
   */
  @Override
  protected void layoutPlotChildren() {
    super.layoutPlotChildren();
    for (Data<X, Y> horizontalMarker : horizontalMarkers) {
      double lower = ((ValueAxis) getXAxis()).getLowerBound();
      X lowerX = getXAxis().toRealValue(lower);
      double upper = ((ValueAxis) getXAxis()).getUpperBound();
      X upperX = getXAxis().toRealValue(upper);
      Line line = (Line) horizontalMarker.getNode();
      line.setStartX(getXAxis().getDisplayPosition(lowerX));
      line.setEndX(getXAxis().getDisplayPosition(upperX));
      line.setStartY(getYAxis().getDisplayPosition(horizontalMarker.getYValue()));
      line.setEndY(line.getStartY());

    }
  }
}
