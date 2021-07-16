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
import javafx.scene.text.Text;

import java.util.Objects;

public class ScatterXChart<X, Y> extends ScatterChart<X, Y> {

  // data defining horizontal markers, xValues are ignored
  private ObservableList<Data<X, Y>> horizontalMarkers;
  private ObservableList<Data<X, Y>> annotationMarkers;

  public ScatterXChart(@NamedArg("xAxis") Axis<X> xAxis, @NamedArg("yAxis") Axis<Y> yAxis) {
    super(xAxis, yAxis);
    // a list that notifies on change of the yValue property
    horizontalMarkers = FXCollections.observableArrayList(d -> new Observable[] {d.YValueProperty()});
    annotationMarkers = FXCollections.observableArrayList(d -> new Observable[] {d.YValueProperty()});
    // listen to list changes and re-plot
    horizontalMarkers.addListener((InvalidationListener) observable -> layoutPlotChildren());
    annotationMarkers.addListener((InvalidationListener) observable -> layoutPlotChildren());
  }

  /**
   * Add horizontal value marker. The marker's Y value is used to plot a
   * horizontal line across the plot area, its X value is ignored.
   *
   * @param marker must not be null.
   */
  public void addHorizontalValueMarker(Data<X, Y> marker, int strokeWidth, String cssStyle) {
    Objects.requireNonNull(marker, "the marker must not be null");
    if (horizontalMarkers.contains(marker)) return;
    Line line = new Line();
    line.setStrokeWidth(strokeWidth);
    line.setStyle(cssStyle);
    marker.setNode(line);
    getPlotChildren().add(line);
    horizontalMarkers.add(marker);
  }

  public void addAnnotationValueMarker(Data<X, Y> marker, String annotation, String cssStyle) {
    Objects.requireNonNull(marker, "the marker must not be null");
    if (annotationMarkers.contains(marker)) return;
    Text text = new Text(annotation);
    text.setStyle(cssStyle);
    marker.setNode(text);
    getPlotChildren().add(text);
    annotationMarkers.add(marker);
  }

  public void clearMarkers() {
    horizontalMarkers.stream().forEach(marker -> {
          getPlotChildren().remove(marker.getNode());
          marker.setNode(null);
        });
    horizontalMarkers.clear();

    annotationMarkers.stream().forEach(marker -> {
      getPlotChildren().remove(marker.getNode());
      marker.setNode(null);
    });
    annotationMarkers.clear();
  }

  /**
   * Overridden to layout the value markers.
   */
  @Override
  protected void layoutPlotChildren() {
    super.layoutPlotChildren();
    for (Data<X, Y> marker : horizontalMarkers) {
      double lower = ((ValueAxis) getXAxis()).getLowerBound();
      X lowerX = getXAxis().toRealValue(lower);
      double upper = ((ValueAxis) getXAxis()).getUpperBound();
      X upperX = getXAxis().toRealValue(upper);
      Line line = (Line) marker.getNode();
      double sx = getXAxis().getDisplayPosition(lowerX);
      double ex = getXAxis().getDisplayPosition(upperX);
      double sy = getYAxis().getDisplayPosition(marker.getYValue());
      line.setStartX(sx);
      line.setEndX(ex);
      line.setStartY(sy);
      line.setEndY(line.getStartY());
    }

    for (Data<X, Y> marker : annotationMarkers) {
      Text text = (Text) marker.getNode();
      double x = getXAxis().getDisplayPosition(marker.getXValue());
      text.setX(x+10.);

      double y = getYAxis().getDisplayPosition(marker.getYValue());
      double yUpper = ((ValueAxis) getYAxis()).getUpperBound();
      double yLower = ((ValueAxis) getYAxis()).getLowerBound();
      if (yUpper-y > y-yLower) {
        text.setY(y+15.);
      }
      else {
        text.setY(y-10.);
      }
    }
  }
}
