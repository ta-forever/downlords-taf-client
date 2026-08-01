package com.faforever.client.wager;

import com.faforever.client.api.dto.WagerBotPnl;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.LeaderboardService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The "House model-maker" scoreboard card (LIVE_PRICING_DESIGN.md §6). Shows how the crowd is
 * doing against the automated informed trader, per board. Data is the aggregate
 * {@code wager_bot_pnl} (realised P&amp;L over settled markets) — the bot's live per-game position
 * is never exposed, so nothing here helps bait it. Per-board rows are summed across seasons for
 * an all-time view. Positive model P&amp;L = the crowd is collectively losing to the model.
 *
 * <p>Embedded via {@code fx:include}; it loads its own data on {@link #initialize()}.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class WagerBotPnlController implements Controller<Node> {

  private final WagerService wagerService;
  private final I18n i18n;
  private final LeaderboardService leaderboardService;

  /** Leaderboard technical name → human-readable display name; the board column never shows a raw
   * technical name. Falls back to the technical name until loaded / for an unknown board. */
  private final Map<String, String> boardDisplayNames = new HashMap<>();

  public VBox botPnlRoot;
  public TableView<BoardRow> botPnlTable;
  public TableColumn<BoardRow, String> boardColumn;
  public TableColumn<BoardRow, Number> gamesColumn;
  public TableColumn<BoardRow, String> hitColumn;
  public TableColumn<BoardRow, Number> stakedColumn;
  public TableColumn<BoardRow, Number> pnlColumn;
  public TableColumn<BoardRow, String> roiColumn;
  public Label summaryLabel;
  public Label youLabel;

  // "You vs the model" needs both sides, which load independently — whichever lands last renders.
  // Null = not loaded yet (distinct from a real zero).
  private Long youStakedLp;
  private long youPnlLp;
  private Long modelStakedLp;
  private long modelPnlLp;

  @Override
  public Node getRoot() {
    return botPnlRoot;
  }

  @Override
  public void initialize() {
    boardColumn.setCellValueFactory(param -> new SimpleStringProperty(boardDisplayName(param.getValue().board())));
    gamesColumn.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().markets()));
    hitColumn.setCellValueFactory(param -> new SimpleStringProperty(
        String.format("%.0f%%", 100.0 * param.getValue().hitRate())));
    stakedColumn.setCellValueFactory(param -> new SimpleDoubleProperty(param.getValue().staked()));
    pnlColumn.setCellValueFactory(param -> new SimpleDoubleProperty(param.getValue().pnl()));
    roiColumn.setCellValueFactory(param -> new SimpleStringProperty(
        String.format("%+.1f%%", param.getValue().roiPct())));
    botPnlTable.setPlaceholder(new Label(i18n.get("wager.bot.empty")));
    loadBoardDisplayNames();
    refresh();
  }

  /** Fetch the leaderboards once and cache technical name → display name, then re-render the table
   * so the board column shows display names rather than raw technical names. */
  private void loadBoardDisplayNames() {
    leaderboardService.getLeaderboards()
        .thenAccept(leaderboards -> JavaFxUtil.runLater(() -> {
          leaderboards.forEach(lb -> boardDisplayNames.put(lb.getTechnicalName(), i18n.get(lb.getNameKey())));
          botPnlTable.refresh();
        }))
        .exceptionally(throwable -> {
          log.warn("Could not load leaderboard display names for model-maker table", throwable);
          return null;
        });
  }

  /** Human-readable board name for a technical name, never the raw technical name if we can help it. */
  private String boardDisplayName(String technicalName) {
    if (technicalName == null || "?".equals(technicalName)) {
      return technicalName;
    }
    return boardDisplayNames.getOrDefault(technicalName, technicalName);
  }

  /** Fetch the scoreboard + the player's own wager P&L and repopulate (safe to call again). The
   * two loads are independent so a positions hiccup never blanks the model table. */
  public void refresh() {
    wagerService.getBotPnl()
        .thenAccept(rows -> JavaFxUtil.runLater(() -> populate(rows)))
        .exceptionally(error -> {
          log.warn("Could not load model-maker P&L", error);
          return null;
        });
    wagerService.getMyPositions()
        .thenAccept(positions -> JavaFxUtil.runLater(() -> populateYouVsModel(positions)))
        .exceptionally(error -> {
          log.warn("Could not load own wager P&L", error);
          return null;
        });
  }

  /**
   * The player's own realised wagering record, measured EXACTLY the way the model's is so the two
   * are comparable: settled markets only, staked = cost basis, P&L = payout − cost. Voided markets
   * are skipped (refunded at cost — they'd pad the denominator with a wager that never resolved),
   * and open positions are skipped because their stake is spent but unresolved, which would drag
   * the number down purely for having bets in flight.
   */
  private void populateYouVsModel(List<WagerPositionBean> positions) {
    long staked = 0;
    double pnl = 0;
    for (WagerPositionBean position : positions) {
      if (!"SETTLED".equals(position.getMarketStatus())) {
        continue;
      }
      staked += position.getCostLp();
      pnl += position.pnl(WagerService.SHARE_PAYOUT_LP);
    }
    youStakedLp = staked;
    youPnlLp = Math.round(pnl);
    renderYouVsModel();
  }

  /**
   * "You vs the model", with the context Walker asked for: his own ROI next to the model's, so
   * "you're ahead" has a magnitude. Needs both loads, so it's driven from whichever lands last.
   * Coloured on the ROI comparison — that's the apples-to-apples "am I beating the house" read,
   * whereas raw LP just says who has wagered more.
   */
  private void renderYouVsModel() {
    if (youStakedLp == null) {
      return;                       // own positions not loaded yet
    }
    if (youStakedLp == 0) {
      youLabel.setText(i18n.get("wager.bot.youNoTrades"));
      youLabel.setStyle("");
      return;
    }
    double yourRoi = 100.0 * youPnlLp / youStakedLp;
    String lp = String.format("%+d", youPnlLp);
    String yourRoiText = String.format("%+.1f%%", yourRoi);
    if (modelStakedLp == null || modelStakedLp == 0) {
      youLabel.setText(i18n.get("wager.bot.youOnly", lp, youStakedLp, yourRoiText));
      youLabel.setStyle("");
      return;
    }
    double modelRoi = 100.0 * modelPnlLp / modelStakedLp;
    youLabel.setText(i18n.get("wager.bot.youVsModel", lp, youStakedLp, yourRoiText,
        String.format("%+.1f%%", modelRoi)));
    youLabel.setStyle(yourRoi >= modelRoi
        ? "-fx-text-fill: #26a65b; -fx-font-weight: bold;"    // green — beating the house
        : "-fx-text-fill: #cb4b16;");                          // red
  }

  private void populate(List<WagerBotPnl> rows) {
    // Sum per board across seasons (all-time view); keep boards alphabetical for a stable list.
    Map<String, long[]> byBoard = new TreeMap<>();   // board -> [markets, wins, staked, pnl]
    for (WagerBotPnl r : rows) {
      String board = r.getRatingType() == null ? "?" : r.getRatingType();
      long[] a = byBoard.computeIfAbsent(board, k -> new long[4]);
      a[0] += r.getMarkets();
      a[1] += r.getWins();
      a[2] += r.getStakedLp();
      a[3] += r.getPnlLp();
    }
    ObservableList<BoardRow> items = FXCollections.observableArrayList();
    long totalPnl = 0;
    long totalStaked = 0;
    for (Map.Entry<String, long[]> e : byBoard.entrySet()) {
      long[] a = e.getValue();
      items.add(new BoardRow(e.getKey(), (int) a[0], (int) a[1], a[2], a[3]));
      totalStaked += a[2];
      totalPnl += a[3];
    }
    botPnlTable.setItems(items);
    modelStakedLp = totalStaked;
    modelPnlLp = totalPnl;
    renderYouVsModel();      // the model's ROI is the yardstick for the "you" line
    // Framing: positive model P&L means the crowd is behind. Encourage "beat the model".
    if (items.isEmpty()) {
      summaryLabel.setText("");
    } else if (totalPnl > 0) {
      summaryLabel.setText(i18n.get("wager.bot.modelAhead", totalPnl));
    } else {
      summaryLabel.setText(i18n.get("wager.bot.crowdAhead", -totalPnl));
    }
  }

  /** One aggregated per-board scoreboard row. */
  public record BoardRow(String board, int markets, int wins, long staked, long pnl) {
    public double hitRate() {
      return markets == 0 ? 0.0 : (double) wins / markets;
    }

    public double roiPct() {
      return staked == 0 ? 0.0 : 100.0 * pnl / staked;
    }
  }
}
