package com.faforever.client.wager;

import com.faforever.client.api.dto.WagerBotPnl;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.ladder.LadderPointsService;
import com.faforever.client.ladder.SeasonStanding;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
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
  private final PlayerService playerService;
  private final LadderPointsService ladderPointsService;
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
   * two loads are independent so a standings hiccup never blanks the model table. */
  public void refresh() {
    wagerService.getBotPnl()
        .thenAccept(rows -> JavaFxUtil.runLater(() -> populate(rows)))
        .exceptionally(error -> {
          log.warn("Could not load model-maker P&L", error);
          return null;
        });
    int playerId = playerService.getCurrentPlayer().map(Player::getId).orElse(0);
    if (playerId > 0) {
      ladderPointsService.getStandingsForPlayerCached(playerId)
          .thenAccept(standings -> JavaFxUtil.runLater(() -> populateYouVsModel(standings)))
          .exceptionally(error -> {
            log.warn("Could not load own wager P&L", error);
            return null;
          });
    }
  }

  /** "You vs the model": the player's own net wager P&L (the ledger-authoritative wager_net,
   * summed across their boards). Net-positive = you're ahead of the informed house player. */
  private void populateYouVsModel(List<SeasonStanding> standings) {
    long net = standings.stream().mapToLong(SeasonStanding::getWagerNet).sum();
    if (net > 0) {
      youLabel.setText(i18n.get("wager.bot.youAhead", net));
    } else if (net < 0) {
      youLabel.setText(i18n.get("wager.bot.youBehind", -net));
    } else {
      youLabel.setText(i18n.get("wager.bot.youEven"));
    }
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
    for (Map.Entry<String, long[]> e : byBoard.entrySet()) {
      long[] a = e.getValue();
      items.add(new BoardRow(e.getKey(), (int) a[0], (int) a[1], a[2], a[3]));
      totalPnl += a[3];
    }
    botPnlTable.setItems(items);
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
