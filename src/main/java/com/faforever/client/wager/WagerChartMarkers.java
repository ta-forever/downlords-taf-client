package com.faforever.client.wager;

import com.faforever.client.wager.WagerService.TradeMarker;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
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

  /** The two colours used by the anonymised (live-tab) legend: my trades vs everyone else's. */
  private static final Color MINE_COLOR = PALETTE[0];       // blue
  private static final Color OTHERS_COLOR = PALETTE[1];     // orange

  private static final double SIZE = 10;

  /** Settlement stamp: bigger than a trade tick, in the tab's won/lost green and red. */
  private static final double VERDICT_SIZE = 15;
  private static final Color WON_COLOR = Color.web("#26a65b");
  private static final Color LOST_COLOR = Color.web("#cb4b16");

  private WagerChartMarkers() {
  }

  /** Colour for the anonymised live chart, where traders are only ever "me" or "someone else". */
  public static Color anonymousColor(boolean mine) {
    return mine ? MINE_COLOR : OTHERS_COLOR;
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
    Polygon triangle = triangle(marker.up());
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

  /**
   * The settlement stamp for the end of a charted outcome's price line: a green tick if that
   * outcome won, a red X if it lost. Sized above the trade ticks so the verdict reads at a glance,
   * and coloured to match the won/lost colours used elsewhere on the wager tab.
   */
  public static Node verdictNode(boolean won, String tooltipText) {
    Path mark = new Path();
    if (won) {
      mark.getElements().addAll(new MoveTo(0, VERDICT_SIZE * 0.55),
          new LineTo(VERDICT_SIZE * 0.38, VERDICT_SIZE * 0.92), new LineTo(VERDICT_SIZE, 0));
    } else {
      mark.getElements().addAll(new MoveTo(0, 0), new LineTo(VERDICT_SIZE, VERDICT_SIZE),
          new MoveTo(VERDICT_SIZE, 0), new LineTo(0, VERDICT_SIZE));
    }
    mark.setStroke(won ? WON_COLOR : LOST_COLOR);
    mark.setStrokeWidth(3);
    mark.setStrokeLineCap(StrokeLineCap.ROUND);
    mark.setStrokeLineJoin(StrokeLineJoin.ROUND);
    mark.setFill(null);
    mark.setPickOnBounds(true);
    Tooltip tooltip = new Tooltip(tooltipText);
    tooltip.setShowDelay(Duration.millis(150));
    Tooltip.install(mark, tooltip);
    return mark;
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
      legend.getChildren().add(legendEntry(colorFor(colors, marker.userId()), displayName(marker)));
    }
    showIfAny(legend);
  }

  /**
   * Rebuild the legend in anonymised form — two categories only, "my trades" and "other players'
   * trades" — so a live market never reveals who is on the other side of a price move. Only the
   * categories actually present are listed; the pane hides itself when there are no markers.
   */
  public static void populateAnonymousLegend(Pane legend, List<TradeMarker> markers, int currentUserId,
                                             String mineText, String othersText) {
    legend.getChildren().clear();
    boolean anyMine = markers.stream().anyMatch(marker -> marker.userId() == currentUserId);
    boolean anyOthers = markers.stream().anyMatch(marker -> marker.userId() != currentUserId);
    if (anyMine) {
      legend.getChildren().add(legendEntry(MINE_COLOR, mineText));
    }
    if (anyOthers) {
      legend.getChildren().add(legendEntry(OTHERS_COLOR, othersText));
    }
    showIfAny(legend);
  }

  /** ▲ for a trade that pushed the displayed price up, ▼ for down. */
  private static Polygon triangle(boolean up) {
    return up
        ? new Polygon(0, SIZE, SIZE / 2, 0, SIZE, SIZE)
        : new Polygon(0, 0, SIZE, 0, SIZE / 2, SIZE);
  }

  /** One legend row: BOTH tick directions in the entry's colour, then its name — a trader (or
   * category) owns both the up- and down-ticks in that colour, so showing only ▲ read as if the
   * colour meant "bought". */
  private static Node legendEntry(Color color, String text) {
    Polygon up = triangle(true);
    Polygon down = triangle(false);
    up.setFill(color);
    down.setFill(color);
    HBox ticks = new HBox(2, up, down);
    ticks.setAlignment(Pos.CENTER_LEFT);
    Label name = new Label(text);
    name.getStyleClass().add("text-secondary");
    HBox entry = new HBox(4, ticks, name);
    entry.setAlignment(Pos.CENTER_LEFT);
    return entry;
  }

  private static void showIfAny(Pane legend) {
    boolean any = !legend.getChildren().isEmpty();
    legend.setVisible(any);
    legend.setManaged(any);
  }
}
