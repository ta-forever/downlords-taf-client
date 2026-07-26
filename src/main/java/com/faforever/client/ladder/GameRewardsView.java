package com.faforever.client.ladder;

import com.faforever.client.i18n.I18n;
import com.faforever.client.theme.UiService;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders the per-game Ladder Points reward bundle (Combat Score → LP chain + medals, §13.7) as a
 * structured scoreboard rather than a wall of text. The Combat Score → LP arithmetic is laid out in
 * aligned columns so the dominant currency reads as <i>earned</i> — Combat Score on the left, the
 * +win / +upset terms in the middle, the banked LP as the glowing right-hand anchor. Shared by the
 * replay detail panel (CL-5) and the post-game Battle Report (CL-6).
 */
public final class GameRewardsView {
  private GameRewardsView() {
  }

  /** Flat, LP-sorted scoreboard. Use where a team lineup is already shown nearby (replay detail) so
   * the combat rows don't introduce a second, conflicting team numbering. */
  public static VBox render(I18n i18n, UiService uiService, GameLadderResult result) {
    return render(i18n, uiService, result, false);
  }

  /**
   * @param groupByTeam when true, the Combat Score → LP rows are grouped into team sections
   *     (strongest team first) under a winner-first ordinal banner. Only suitable for the
   *     standalone Battle Report, where no other team lineup is on screen to contradict the
   *     ordinal numbering (the metric's raw {@code team} field is 0-based and ordered
   *     independently of the lineup cards).
   */
  public static VBox render(I18n i18n, UiService uiService, GameLadderResult result, boolean groupByTeam) {
    VBox box = new VBox();
    box.getStyleClass().add("combat-rewards");
    if (result == null || result.isEmpty()) {
      return box;
    }

    // top Combat Score in the game = the denominator that turns Combat Score into combat LP (§13.7)
    int topInGame = result.getBreakdowns().stream()
        .map(LpGameBreakdown::getDestroyedValue)
        .filter(java.util.Objects::nonNull)
        .mapToInt(Integer::intValue).max().orElse(0);

    Label eyebrow = new Label(i18n.get("scorescreen.section.combat"));
    eyebrow.getStyleClass().add("rewards-eyebrow");
    HBox header = new HBox(6, eyebrow, buildHelpIcon(i18n));
    header.setAlignment(Pos.CENTER_LEFT);
    box.getChildren().add(header);
    if (topInGame > 0) {
      Label denom = new Label(i18n.get("lp.combatScore.topInGame", i18n.number(topInGame)));
      denom.getStyleClass().add("rewards-caption");
      box.getChildren().add(denom);
    }
    box.getChildren().add(buildScoreboard(i18n, result.getBreakdowns(), teamByPlayer(result), groupByTeam));

    appendMedals(i18n, uiService, box, result.getMedals());
    return box;
  }

  /** The "?" badge next to the section title; click opens a popup glossary of the scoreboard terms. */
  private static Label buildHelpIcon(I18n i18n) {
    Label help = new Label("?");
    help.getStyleClass().add("rewards-help-icon");
    Tooltip.install(help, new Tooltip(i18n.get("scorescreen.help.tooltip")));
    help.setOnMouseClicked(event -> {
      if (help.getScene() == null || help.getScene().getWindow() == null) {
        return;
      }
      Popup popup = new Popup();
      popup.setAutoHide(true);
      popup.getContent().add(buildHelpContent(i18n));
      javafx.geometry.Bounds bounds = help.localToScreen(help.getBoundsInLocal());
      popup.show(help.getScene().getWindow(), bounds.getMinX(), bounds.getMaxY() + 4);
      event.consume();
    });
    return help;
  }

  /** Glossary card explaining each scoreboard column; terms reuse the same labels as the headers. */
  private static VBox buildHelpContent(I18n i18n) {
    VBox content = new VBox();
    content.getStyleClass().add("rewards-help");
    content.setMaxWidth(360);
    Label title = new Label(i18n.get("scorescreen.help.title"));
    title.getStyleClass().add("rewards-help-title");
    content.getChildren().add(title);
    content.getChildren().addAll(
        helpRow(i18n, "lp.combatScore.label", "scorescreen.help.combatScore"),
        helpRow(i18n, "scorescreen.col.base", "scorescreen.help.base"),
        helpRow(i18n, "scorescreen.col.win", "scorescreen.help.victory"),
        helpRow(i18n, "scorescreen.col.valiance", "scorescreen.help.valor"),
        helpRow(i18n, "scorescreen.col.upset", "scorescreen.help.upset"),
        helpRow(i18n, "scorescreen.col.lp", "scorescreen.help.lp"));
    return content;
  }

  private static VBox helpRow(I18n i18n, String termKey, String descKey) {
    VBox row = new VBox(1);
    row.getStyleClass().add("rewards-help-row");
    Label term = new Label(i18n.get(termKey));
    term.getStyleClass().add("rewards-help-term");
    Label desc = new Label(i18n.get(descKey));
    desc.getStyleClass().add("rewards-help-desc");
    desc.setWrapText(true);
    desc.setMaxWidth(340);
    row.getChildren().addAll(term, desc);
    return row;
  }

  private static Map<Integer, Byte> teamByPlayer(GameLadderResult result) {
    return result.getMetrics().stream()
        .filter(m -> m.getTeam() != null)
        .collect(Collectors.toMap(PlayerCombatMetrics::getPlayerId, PlayerCombatMetrics::getTeam, (a, b) -> a));
  }

  private static GridPane buildScoreboard(I18n i18n, List<LpGameBreakdown> breakdowns,
                                          Map<Integer, Byte> teamByPlayer, boolean groupByTeam) {
    GridPane grid = new GridPane();
    grid.getStyleClass().add("scoreboard");
    // player (grow) | combat score | base | win | valiance | upset | = LP
    ColumnConstraints name = new ColumnConstraints();
    name.setHgrow(Priority.ALWAYS);
    name.setFillWidth(true);
    grid.getColumnConstraints().add(name);
    for (int i = 0; i < 6; i++) {
      ColumnConstraints c = new ColumnConstraints();
      c.setHalignment(HPos.RIGHT);
      grid.getColumnConstraints().add(c);
    }

    // The outcome term (winBonus) is a flat win reward for the winners but the losing-team combat
    // consolation for everyone else (server lp.py §3.8). Winners therefore hold the strictly-higher
    // outcome value, so the global max identifies the winning side without needing a win/loss flag.
    Set<Integer> wonPlayerIds = wonPlayerIds(breakdowns);

    int[] row = {0};
    addColumnHeaders(i18n, grid, row);

    long distinctTeams = breakdowns.stream()
        .map(b -> teamByPlayer.get(b.getPlayerId()))
        .filter(java.util.Objects::nonNull).distinct().count();

    if (!groupByTeam || distinctTeams < 2) {
      breakdowns.stream()
          .sorted(Comparator.comparingInt(LpGameBreakdown::getLpAwarded).reversed())
          .forEach(b -> addPlayerRow(i18n, grid, row, b, wonPlayerIds.contains(b.getPlayerId())));
      return grid;
    }

    // -1 bucket = players with no team metric; teams ordered by total LP (strongest first).
    Map<Integer, List<LpGameBreakdown>> byTeam = breakdowns.stream()
        .collect(Collectors.groupingBy(b -> {
          Byte t = teamByPlayer.get(b.getPlayerId());
          return t == null ? -1 : (int) t;
        }));
    List<Map.Entry<Integer, List<LpGameBreakdown>>> ordered = byTeam.entrySet().stream()
        .sorted(Comparator.comparingInt((Map.Entry<Integer, List<LpGameBreakdown>> e) ->
            e.getValue().stream().mapToInt(LpGameBreakdown::getLpAwarded).sum()).reversed())
        .collect(Collectors.toList());
    int ordinal = 1;  // winner-first display number, independent of the raw 0-based team index
    for (Map.Entry<Integer, List<LpGameBreakdown>> e : ordered) {
      int teamTotal = e.getValue().stream().mapToInt(LpGameBreakdown::getLpAwarded).sum();
      String title = e.getKey() < 0
          ? i18n.get("game.tooltip.teamTitleNoTeam")
          : i18n.get("scorescreen.teamHeader", ordinal++, teamTotal);
      addTeamBanner(grid, row, title);
      e.getValue().stream()
          .sorted(Comparator.comparingInt(LpGameBreakdown::getLpAwarded).reversed())
          .forEach(b -> addPlayerRow(i18n, grid, row, b, wonPlayerIds.contains(b.getPlayerId())));
    }
    return grid;
  }

  /** Winners share the flat outcome term (the highest in the game); losers get a smaller, individual
   * combat consolation. So the players whose winBonus equals the game's max outcome are the winners. */
  private static Set<Integer> wonPlayerIds(List<LpGameBreakdown> breakdowns) {
    int maxOutcome = breakdowns.stream().mapToInt(LpGameBreakdown::getWinBonus).max().orElse(0);
    if (maxOutcome <= 0) {
      return Set.of();   // no win recorded (e.g. a draw) — show everyone's term as valiance
    }
    return breakdowns.stream()
        .filter(b -> b.getWinBonus() == maxOutcome)
        .map(LpGameBreakdown::getPlayerId)
        .collect(Collectors.toSet());
  }

  private static void addColumnHeaders(I18n i18n, GridPane grid, int[] row) {
    int r = row[0]++;
    grid.add(colHeader(i18n.get("scorescreen.col.player"), HPos.LEFT), 0, r);
    grid.add(colHeader(i18n.get("lp.combatScore.label"), HPos.RIGHT), 1, r);
    grid.add(colHeader(i18n.get("scorescreen.col.base"), HPos.RIGHT), 2, r);
    grid.add(colHeader(i18n.get("scorescreen.col.win"), HPos.RIGHT), 3, r);
    Label valiance = colHeader(i18n.get("scorescreen.col.valiance"), HPos.RIGHT);
    Tooltip.install(valiance, new Tooltip(i18n.get("scorescreen.col.valiance.tooltip")));
    grid.add(valiance, 4, r);
    grid.add(colHeader(i18n.get("scorescreen.col.upset"), HPos.RIGHT), 5, r);
    grid.add(colHeader(i18n.get("scorescreen.col.lp"), HPos.RIGHT), 6, r);
  }

  private static Label colHeader(String text, HPos align) {
    Label label = new Label(text);
    label.getStyleClass().add("scoreboard-col-header");
    GridPane.setHalignment(label, align);
    return label;
  }

  private static void addTeamBanner(GridPane grid, int[] row, String title) {
    Label banner = new Label(title);
    banner.getStyleClass().add("scoreboard-team-banner");
    banner.setMaxWidth(Double.MAX_VALUE);
    GridPane.setColumnSpan(banner, 7);
    grid.add(banner, 0, row[0]++);
  }

  /** @param won whether this player was on the winning side; their outcome term reads as a flat
   *     win reward (Win column) when they won, or a combat consolation (Valiance column) when not. */
  private static void addPlayerRow(I18n i18n, GridPane grid, int[] row, LpGameBreakdown b, boolean won) {
    int r = row[0]++;
    Label player = new Label(b.getPlayerLogin() != null ? b.getPlayerLogin() : "?");
    player.getStyleClass().add("scoreboard-name");
    grid.add(player, 0, r);

    grid.add(cell(b.getDestroyedValue() != null ? i18n.number(b.getDestroyedValue()) : "—", "scoreboard-score"), 1, r);
    grid.add(cell(i18n.number(b.getCombatBase()), "scoreboard-term"), 2, r);
    // The win reward only belongs to winners; the same outcome term is the valiant-loss consolation
    // for everyone else, surfaced in its own column so a good fight in defeat reads as recognition.
    grid.add(cell(won ? signed(i18n, b.getWinBonus()) : "·", "scoreboard-term"), 3, r);
    grid.add(cell(won ? "·" : signed(i18n, b.getWinBonus()), "scoreboard-term"), 4, r);
    grid.add(cell(signed(i18n, b.getUpsetBonus()), "scoreboard-term"), 5, r);

    Label lp = new Label(i18n.get("lp.perGame.change", b.getLpAwarded()));
    lp.getStyleClass().add("scoreboard-lp");
    GridPane.setHalignment(lp, HPos.RIGHT);
    grid.add(lp, 6, r);
  }

  /** "+25" / "−5" / "·" (muted) for a zero term, so the chain stays legible without noise. */
  private static String signed(I18n i18n, int value) {
    if (value == 0) {
      return "·";
    }
    return (value > 0 ? "+" : "") + i18n.number(value);
  }

  private static Label cell(String text, String styleClass) {
    Label label = new Label(text);
    label.getStyleClass().add(styleClass);
    GridPane.setHalignment(label, HPos.RIGHT);
    return label;
  }

  private static void appendMedals(I18n i18n, UiService uiService, VBox box, List<GameMedalAward> medals) {
    if (medals.isEmpty()) {
      return;
    }
    Label eyebrow = new Label(i18n.get("scorescreen.medalsEarned"));
    eyebrow.getStyleClass().add("rewards-eyebrow");
    box.getChildren().add(eyebrow);

    // grouped by player, preserving lobby order of first appearance
    Map<String, List<GameMedalAward>> byPlayer = medals.stream()
        .collect(Collectors.groupingBy(m -> m.getPlayerLogin() == null ? "?" : m.getPlayerLogin(),
            LinkedHashMap::new, Collectors.toList()));
    byPlayer.forEach((login, awards) -> {
      HBox rowBox = new HBox();
      rowBox.getStyleClass().add("medal-award-row");
      rowBox.setAlignment(Pos.CENTER_LEFT);
      Label who = new Label(login);
      who.getStyleClass().add("medal-award-name");
      HBox.setHgrow(who, Priority.NEVER);
      Region spacer = new Region();
      spacer.setMinWidth(8);
      FlowPane chips = new FlowPane();
      chips.getStyleClass().add("medal-chips");
      HBox.setHgrow(chips, Priority.ALWAYS);
      awards.forEach(a -> chips.getChildren().add(medalChip(i18n, uiService, a.getCode())));
      rowBox.getChildren().addAll(who, spacer, chips);
      box.getChildren().add(rowBox);
    });
  }

  private static HBox medalChip(I18n i18n, UiService uiService, String code) {
    HBox chip = new HBox();
    chip.getStyleClass().add("medal-chip");
    chip.setAlignment(Pos.CENTER_LEFT);
    ImageView icon = new ImageView(uiService.getThemeImageOrDefault(
        LadderUiUtil.medalIconPath(code), UiService.DEFAULT_MEDAL_IMAGE));
    icon.setFitWidth(18);
    icon.setFitHeight(18);
    icon.setPreserveRatio(true);
    Label name = new Label(LadderUiUtil.medalDisplayName(i18n, code));
    name.getStyleClass().add("medal-chip-label");
    chip.getChildren().addAll(icon, name);
    String desc = LadderUiUtil.medalDescription(i18n, code);
    Tooltip.install(chip, new Tooltip(
        desc.isEmpty() ? LadderUiUtil.medalDisplayName(i18n, code) : desc));
    return chip;
  }
}
