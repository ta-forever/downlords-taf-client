package com.faforever.client.wager;

import com.faforever.client.FafClientApplication;
import com.faforever.client.api.FafApiAccessor;
import com.faforever.client.api.dto.WagerMarket;
import com.faforever.client.api.dto.WagerOutcome;
import com.faforever.client.api.dto.WagerPosition;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.player.PlayerService;
import com.faforever.client.remote.FafServerAccessor;
import com.faforever.client.remote.domain.WagerMarketsMessage;
import com.faforever.client.remote.domain.WagerPriceMessage;
import com.faforever.client.remote.domain.WagerSettledMessage;
import com.faforever.client.remote.domain.WagerTradeAckMessage;
import com.faforever.client.remote.domain.WagerTradeRejectMessage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Lazy
@Service
@Slf4j
@Profile("!" + FafClientApplication.PROFILE_OFFLINE)
public class WagerServiceImpl implements WagerService {

  private final FafApiAccessor fafApiAccessor;
  private final FafServerAccessor fafServerAccessor;
  private final PlayerService playerService;
  private final ExecutorService executorService;

  private final ObservableList<WagerMarketBean> currentMarkets = FXCollections.observableArrayList();
  private final ConcurrentHashMap<String, CompletableFuture<WagerTradeResult>> pendingTrades = new ConcurrentHashMap<>();
  private final AtomicLong clientRefSeq = new AtomicLong();
  private volatile int currentGameId = -1;
  private volatile java.util.function.Consumer<Settlement> settlementListener;

  public WagerServiceImpl(FafApiAccessor fafApiAccessor, FafServerAccessor fafServerAccessor,
                          PlayerService playerService, ExecutorService executorService) {
    this.fafApiAccessor = fafApiAccessor;
    this.fafServerAccessor = fafServerAccessor;
    this.playerService = playerService;
    this.executorService = executorService;

    fafServerAccessor.addOnMessageListener(WagerMarketsMessage.class, this::onMarkets);
    fafServerAccessor.addOnMessageListener(WagerPriceMessage.class, this::onPrice);
    fafServerAccessor.addOnMessageListener(WagerTradeAckMessage.class, this::onTradeAck);
    fafServerAccessor.addOnMessageListener(WagerTradeRejectMessage.class, this::onTradeReject);
    fafServerAccessor.addOnMessageListener(WagerSettledMessage.class, this::onSettled);
  }

  @Override
  public void setSettlementListener(java.util.function.Consumer<Settlement> listener) {
    this.settlementListener = listener;
  }

  @Override
  public CompletableFuture<List<WagerMarket>> getWatchlist() {
    return CompletableFuture.supplyAsync(fafApiAccessor::getOpenWagerMarkets, executorService);
  }

  @Override
  public CompletableFuture<List<com.faforever.client.api.dto.WagerBotPnl>> getBotPnl() {
    return CompletableFuture.supplyAsync(fafApiAccessor::getBotPnl, executorService);
  }

  @Override
  public ObservableList<WagerMarketBean> subscribeToGame(int gameId) {
    if (currentGameId != -1 && currentGameId != gameId) {
      fafServerAccessor.unsubscribeWager(currentGameId);
    }
    currentGameId = gameId;
    JavaFxUtil.runLater(currentMarkets::clear);
    fafServerAccessor.subscribeWager(gameId);   // snapshot arrives async via onMarkets
    return currentMarkets;
  }

  @Override
  public void unsubscribe() {
    int gameId = currentGameId;
    if (gameId != -1) {
      fafServerAccessor.unsubscribeWager(gameId);
      currentGameId = -1;
      JavaFxUtil.runLater(currentMarkets::clear);
    }
  }

  @Override
  public CompletableFuture<WagerTradeResult> trade(int gameId, long marketId, String outcomeKey, double deltaShares) {
    String clientRef = Long.toString(clientRefSeq.incrementAndGet());
    CompletableFuture<WagerTradeResult> future = new CompletableFuture<>();
    pendingTrades.put(clientRef, future);
    fafServerAccessor.sendWagerTrade(gameId, marketId, outcomeKey, deltaShares, clientRef);
    // Don't leak a pending future if the server never answers.
    return future.orTimeout(15, TimeUnit.SECONDS)
        .whenComplete((result, error) -> pendingTrades.remove(clientRef));
  }

  @Override
  public CompletableFuture<List<WagerPositionBean>> getMyPositions() {
    int playerId = playerService.getCurrentPlayer().map(p -> p.getId()).orElse(0);
    if (playerId == 0) {
      return CompletableFuture.completedFuture(List.of());
    }
    return CompletableFuture.supplyAsync(() -> fafApiAccessor.getWagerPositionsForPlayer(playerId).stream()
        .map(this::toPositionBean)
        .collect(Collectors.toList()), executorService);
  }

  @Override
  public CompletableFuture<List<PricePoint>> getPriceHistory(long marketId, long outcomeId, double b,
                                                             boolean twoOutcome, int limit) {
    return CompletableFuture.supplyAsync(() -> priceHistorySync(marketId, outcomeId, b, twoOutcome, limit),
        executorService);
  }

  /** Synchronous core of {@link #getPriceHistory}: one outcome's price path from the market's
   * trade log. Shared with {@link #getWinningOutcomeHistory} so it can chain off one async hop. */
  private List<PricePoint> priceHistorySync(long marketId, long outcomeId, double b,
                                            boolean twoOutcome, int limit) {
    List<PricePoint> points = new ArrayList<>();
    for (com.faforever.client.api.dto.WagerTrade t : fafApiAccessor.getWagerTradesForMarket(marketId, limit)) {
      if (t.getCreatedAt() == null) {
        continue;
      }
      double epoch = t.getCreatedAt().toEpochSecond();
      // The trade log stores only price_AFTER, and only for the outcome that was traded.
      // Reconstruct that outcome's pre-trade price from delta + b, then map both to the
      // SELECTED outcome (same outcome -> as is; the other 2-team outcome -> 1 − p).
      double postTraded = t.getPriceAfter();
      double preTraded = WagerMath.priceBefore(postTraded, b, t.getDeltaShares());
      double pre;
      double post;
      if (t.getOutcomeId() == outcomeId) {
        pre = preTraded;
        post = postTraded;
      } else if (twoOutcome) {
        pre = 1 - preTraded;
        post = 1 - postTraded;
      } else {
        continue;                 // >2 outcomes: can't derive from another outcome's trade
      }
      points.add(new PricePoint(epoch - 1, pre));   // just before the trade
      points.add(new PricePoint(epoch, post));       // after the trade
    }
    return points;
  }

  @Override
  public CompletableFuture<Optional<WagerService.ReplayPriceChart>> getWinningOutcomeHistory(int gameId) {
    return CompletableFuture.supplyAsync(() -> {
      for (com.faforever.client.api.dto.WagerMarket market : fafApiAccessor.getWagerMarketsForGame(gameId)) {
        if (!"TEAM_WIN".equals(market.getMarketType()) || market.getOutcomes() == null) {
          continue;
        }
        com.faforever.client.api.dto.WagerOutcome winner = market.getOutcomes().stream()
            .filter(o -> Boolean.TRUE.equals(o.getIsWinner()))
            .findFirst()
            .orElse(null);
        if (winner == null) {
          continue;                 // no decided winner (unsettled / voided) -> no line to draw
        }
        boolean twoOutcome = market.getOutcomes().size() == 2;
        List<PricePoint> points = priceHistorySync(Long.parseLong(market.getId()),
            Long.parseLong(winner.getId()), market.getLiquidity(), twoOutcome, 1000);
        if (points.isEmpty()) {
          continue;                 // market never traded -> nothing to chart
        }
        String label = WagerLabels.outcomeLabel(market.getMarketType(), winner.getOutcomeKey(), winner.getLabel());
        return Optional.of(new WagerService.ReplayPriceChart(label, points));
      }
      return Optional.<WagerService.ReplayPriceChart>empty();
    }, executorService);
  }

  private WagerPositionBean toPositionBean(WagerPosition p) {
    WagerOutcome outcome = p.getOutcome();
    com.faforever.client.api.dto.WagerMarket market = outcome != null ? outcome.getMarket() : null;
    int gameId = market != null ? market.getGameId() : 0;
    String marketType = market != null ? market.getMarketType() : null;
    double liquidity = market != null ? market.getLiquidity() : 0d;
    int feeBps = market != null ? market.getFeeBps() : 0;
    String label = WagerLabels.outcomeLabel(marketType,
        outcome != null ? outcome.getOutcomeKey() : null,
        outcome != null ? outcome.getLabel() : null);
    double lastPrice = p.getLastPrice() != null ? p.getLastPrice() : 0d;
    String outcomeKey = outcome != null ? outcome.getOutcomeKey() : null;
    return new WagerPositionBean(p.getMarketId(), gameId, outcomeKey, label, p.getShares(), p.getCostLp(),
        lastPrice, p.getMarketStatus(), p.getIsWinner(), liquidity, feeBps);
  }

  // --- live WS handlers (run on a non-FX thread; hop to the FX thread for UI state) ---

  private void onMarkets(WagerMarketsMessage message) {
    if (message.getGameId() != currentGameId || message.getMarkets() == null) {
      return;
    }
    JavaFxUtil.runLater(() -> {
      currentMarkets.clear();
      for (WagerMarketsMessage.MarketInfo mi : message.getMarkets()) {
        WagerMarketBean market = new WagerMarketBean(
            mi.getMarketId(), message.getGameId(), mi.getMarketType(), mi.getMedalCode(),
            mi.getStatus(), mi.getB(), mi.getFeeBps());
        if (mi.getOutcomes() != null) {
          for (WagerMarketsMessage.OutcomeInfo oi : mi.getOutcomes()) {
            market.getOutcomes().add(new WagerOutcomeBean(
                oi.getOutcomeId(), oi.getOutcomeKey(), oi.getLabel(), oi.getPrice(), oi.getIsWinner()));
          }
        }
        currentMarkets.add(market);
      }
    });
  }

  private void onPrice(WagerPriceMessage message) {
    if (message.getOutcomes() == null) {
      return;
    }
    JavaFxUtil.runLater(() -> currentMarkets.stream()
        .filter(m -> m.getMarketId() == message.getMarketId())
        .findFirst()
        .ifPresent(market -> {
          for (WagerPriceMessage.PriceInfo pi : message.getOutcomes()) {
            market.getOutcomes().stream()
                .filter(o -> Objects.equals(o.getOutcomeKey(), pi.getOutcomeKey()))
                .findFirst()
                .ifPresent(o -> o.setPrice(pi.getPrice()));
          }
        }));
  }

  private void onTradeAck(WagerTradeAckMessage message) {
    CompletableFuture<WagerTradeResult> future = pendingTrades.remove(message.getClientRef());
    if (future != null) {
      future.complete(new WagerTradeResult(
          message.getMarketId(), message.getOutcomeKey(), message.getDeltaShares(),
          message.getLpCost(), message.getFeeLp(), message.getPriceAfter(),
          message.getPositionShares(), message.getNewScore()));
    }
  }

  private void onSettled(WagerSettledMessage message) {
    Settlement settlement = new Settlement(
        message.getGameId(), message.getMarketId(), message.getMarketType(), message.getStatus(),
        message.getWinningOutcomeKeys() != null ? message.getWinningOutcomeKeys() : List.of(),
        message.getPayoutLp());
    JavaFxUtil.runLater(() -> {
      // If it's a market in the game we're viewing, resolve it live (status + winners).
      if (message.getGameId() == currentGameId) {
        currentMarkets.stream()
            .filter(m -> m.getMarketId() == message.getMarketId())
            .findFirst()
            .ifPresent(market -> {
              market.setStatus(message.getStatus());
              for (WagerOutcomeBean outcome : market.getOutcomes()) {
                outcome.setWinner(settlement.winningOutcomeKeys().contains(outcome.getOutcomeKey()));
              }
            });
      }
      java.util.function.Consumer<Settlement> listener = settlementListener;
      if (listener != null) {
        listener.accept(settlement);
      }
    });
  }

  private void onTradeReject(WagerTradeRejectMessage message) {
    String reason = message.getReason() != null ? message.getReason() : "rejected";
    if (message.getClientRef() != null) {
      CompletableFuture<WagerTradeResult> future = pendingTrades.remove(message.getClientRef());
      if (future != null) {
        future.completeExceptionally(new WagerTradeException(reason));
        return;
      }
    }
    // A reject with no client_ref (e.g. participant_blocked on subscribe) — nothing to fail.
    log.debug("Wager request rejected: {}", reason);
  }
}
