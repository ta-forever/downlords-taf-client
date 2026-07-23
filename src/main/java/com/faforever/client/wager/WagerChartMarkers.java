package com.faforever.client.wager;

import com.faforever.client.wager.WagerService.TradeMarker;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared rendering for human-trade markers on the wager price charts (live tab + replay detail):
 * an up/down triangle per trade at its point on the price line, colour-coded per trader, plus a
 * swatch-and-name legend. Bot trades never reach here ({@link WagerService.PriceHistory} already
 * excludes them), so every marker is a real user.
 */
public final class WagerChartMarkers {

  /** Per-trader categorical palette, stepped for the client's dark chart surface; assigned in
   * fixed first-seen order and cycled past eight (identity is never colour-alone — the legend
   * name, tooltip and tick direction all carry it too). */
  private static final Color[] PALETTE = {
      Color.web("#3987e5"),   // blue
      Color.web("#d95926"),   // orange
      Color.web("#199e70"),   // aqua
      Color.web("#c98500"),   // yellow
      Color.web("#d55181"),   // magenta
      Color.web("#008300"),   // green
      Color.web("#9085e9"),   // violet
      Color.web("#e66767"),   // red
  };

  private static final double SIZE = 10;

  private WagerChartMarkers() {
  }

  /** The trader's colour, assigning the next palette slot on first sight (stable thereafter
   * for as long as the caller keeps the map). */
  public static Color colorFor(Map<Integer, Color> assigned, int userId) {
    return assigned.computeIfAbsent(userId, id -> PALETTE[assigned.size() % PALETTE.length]);
  }

  /** The trader's display name; a failed lookup falls back to "#id". */
  public static String displayName(TradeMarker marker) {
    return marker.userName() != null ? marker.userName() : "#" + marker.userId();
  }

  /** The triangle symbol for one trade, to be set as the {@code XYChart.Data} node: ▲ for a
   * trade that pushed the displayed price up, ▼ for down, filled with the trader's colour. */
  public static Node markerNode(TradeMarker marker, Color color, String tooltipText) {
    Polygon triangle = marker.up()
        ? new Polygon(0, SIZE, SIZE / 2, 0, SIZE, SIZE)
        : new Polygon(0, 0, SIZE, 0, SIZE / 2, SIZE);
    triangle.setFill(color);
    // thin dark outline so the symbol separates from the series line it sits on
    triangle.setStroke(Color.color(0, 0, 0, 0.6));
    triangle.setStrokeWidth(1);
    triangle.setPickOnBounds(true);
    Tooltip tooltip = new Tooltip(tooltipText);
    tooltip.setShowDelay(Duration.millis(150));
    Tooltip.install(triangle, tooltip);
    return triangle;
  }

  /** Rebuild the per-trader legend (swatch + name, traders in first-trade order); the pane
   * hides itself when there are no markers. */
  public static void populateLegend(Pane legend, List<TradeMarker> markers, Map<Integer, Color> colors) {
    legend.getChildren().clear();
    Set<Integer> seen = new LinkedHashSet<>();
    for (TradeMarker marker : markers) {
      if (!seen.add(marker.userId())) {
        continue;
      }
      Polygon swatch = new Polygon(0, SIZE, SIZE / 2, 0, SIZE, SIZE);
      swatch.setFill(colorFor(colors, marker.userId()));
      Label name = new Label(displayName(marker));
      name.getStyleClass().add("text-secondary");
      HBox entry = new HBox(4, swatch, name);
      entry.setAlignment(Pos.CENTER_LEFT);
      legend.getChildren().add(entry);
    }
    boolean any = !legend.getChildren().isEmpty();
    legend.setVisible(any);
    legend.setManaged(any);
  }
}
