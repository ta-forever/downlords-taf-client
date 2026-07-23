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
import java.util.Map;
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
  private final com.faforever.client.config.ClientProperties clientProperties;

  private final ObservableList<WagerMarketBean> currentMarkets = FXCollections.observableArrayList();
  private final ConcurrentHashMap<String, CompletableFuture<WagerTradeResult>> pendingTrades = new ConcurrentHashMap<>();
  private final AtomicLong clientRefSeq = new AtomicLong();
  /** userId → login for trade markers; trades are append-only and names change rarely, so a
   * session-lifetime cache spares one API roundtrip per chart (re)load. */
  private final ConcurrentHashMap<Integer, String> traderNames = new ConcurrentHashMap<>();
  private volatile int currentGameId = -1;
  private volatile java.util.function.Consumer<Settlement> settlementListener;
  private volatile java.util.function.Consumer<SubscribeReject> subscribeRejectListener;

  public WagerServiceImpl(FafApiAccessor fafApiAccessor, FafServerAccessor fafServerAccessor,
                          PlayerService playerService, ExecutorService executorService,
                          com.faforever.client.config.ClientProperties clientProperties) {
    this.fafApiAccessor = fafApiAccessor;
    this.fafServerAccessor = fafServerAccessor;
    this.playerService = playerService;
    this.executorService = executorService;
    this.clientProperties = clientProperties;

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
  public void setSubscribeRejectListener(java.util.function.Consumer<SubscribeReject> listener) {
    this.subscribeRejectListener = listener;
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
  public CompletableFuture<PriceHistory> getPriceHistory(long marketId, long outcomeId, double b,
                                                         boolean twoOutcome, int limit) {
    return CompletableFuture.supplyAsync(() -> priceHistorySync(marketId, outcomeId, b, twoOutcome, limit),
        executorService);
  }

  /** Synchronous core of {@link #getPriceHistory}: one outcome's price path from the market's
   * trade log, plus a {@link TradeMarker} per human trade (the model-maker bot's trades move the
   * line but are anonymous by design — no marker). Shared with {@link #getReplayWagerSummary}
   * so it can chain off one async hop. */
  private PriceHistory priceHistorySync(long marketId, long outcomeId, double b,
                                        boolean twoOutcome, int limit) {
    int botUserId = clientProperties.getWager().getBotUserId();
    List<PricePoint> points = new ArrayList<>();
    List<TradeMarker> markers = new ArrayList<>();
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
      if (botUserId == 0 || t.getUserId() != botUserId) {
        // Tick direction relative to the DISPLAYED outcome: buying the other 2-team outcome is
        // a down-tick on this line. post == pre (deadband-rounded no-op) keeps the buy/sell sense
        // of the trade itself.
        boolean up = post != pre ? post > pre : (t.getDeltaShares() > 0) == (t.getOutcomeId() == outcomeId);
        markers.add(new TradeMarker(epoch, post, t.getUserId(), null, up, Math.abs(t.getDeltaShares())));
      }
    }
    return new PriceHistory(points, resolveTraderNames(markers));
  }

  /** Fill in {@link TradeMarker#userName()} for all markers in one batched player lookup
   * (session-cached). A user whose lookup fails keeps a null name (UI falls back to "#id"). */
  private List<TradeMarker> resolveTraderNames(List<TradeMarker> markers) {
    Map<Integer, String> names = resolveNames(markers.stream().map(TradeMarker::userId).toList());
    return markers.stream()
        .map(m -> new TradeMarker(m.epochSeconds(), m.price(), m.userId(),
            names.get(m.userId()), m.up(), m.shares()))
        .collect(Collectors.toList());
  }

  /** userId → login for the given ids, via the session cache with one batched API lookup for
   * the misses. Ids whose lookup fails are simply absent from the returned map. */
  private Map<Integer, String> resolveNames(java.util.Collection<Integer> userIds) {
    List<Integer> unknown = userIds.stream()
        .distinct()
        .filter(id -> !traderNames.containsKey(id))
        .toList();
    if (!unknown.isEmpty()) {
      try {
        for (com.faforever.client.api.dto.Player player : fafApiAccessor.getPlayersByIds(unknown)) {
          if (player.getId() != null && player.getLogin() != null) {
            traderNames.put(Integer.parseInt(player.getId()), player.getLogin());
          }
        }
      } catch (Exception e) {
        log.warn("Could not resolve wager trader names", e);
      }
    }
    return traderNames;
  }

  @Override
  public CompletableFuture<ReplayWagerSummary> getReplayWagerSummary(int gameId) {
    return CompletableFuture.supplyAsync(() -> {
      List<com.faforever.client.api.dto.WagerMarket> markets = fafApiAccessor.getWagerMarketsForGame(gameId);
      return new ReplayWagerSummary(winningOutcomeChart(markets), traderPnls(markets));
    }, executorService);
  }

  /** The eventual winner's price path over the game's TEAM_WIN market (replay-detail chart),
   * or empty when there's no settled winner or the market never traded. */
  private Optional<WagerService.ReplayPriceChart> winningOutcomeChart(
      List<com.faforever.client.api.dto.WagerMarket> markets) {
    for (com.faforever.client.api.dto.WagerMarket market : markets) {
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
      PriceHistory history = priceHistorySync(Long.parseLong(market.getId()),
          Long.parseLong(winner.getId()), market.getLiquidity(), twoOutcome, 1000);
      if (history.points().isEmpty()) {
        continue;                 // market never traded -> nothing to chart
      }
      String label = WagerLabels.outcomeLabel(market.getMarketType(), winner.getOutcomeKey(), winner.getLabel());
      return Optional.of(new WagerService.ReplayPriceChart(label, history.points(), history.markers()));
    }
    return Optional.empty();
  }

  /** Every human trader's realised LP P&amp;L over a game's RESOLVED markets, best-first,
   * replayed from the trade log with the server's settlement math (markets.py): a settled
   * market pays {@code round(finalShares × payout)} per winning outcome (positive payouts
   * only) against everything paid in (cost + fees); a voided market refunds the cost basis,
   * leaving −fees. Open/closed (unresolved) markets and the bot are skipped. */
  private List<TraderPnl> traderPnls(List<com.faforever.client.api.dto.WagerMarket> markets) {
    int botUserId = clientProperties.getWager().getBotUserId();
    Map<Integer, Long> pnlByUser = new java.util.LinkedHashMap<>();
    for (com.faforever.client.api.dto.WagerMarket market : markets) {
      boolean settled = "SETTLED".equals(market.getStatus());
      boolean voided = "VOIDED".equals(market.getStatus());
      if (!settled && !voided) {
        continue;
      }
      java.util.Set<Long> winningOutcomeIds = !settled || market.getOutcomes() == null ? java.util.Set.of()
          : market.getOutcomes().stream()
              .filter(o -> Boolean.TRUE.equals(o.getIsWinner()))
              .map(o -> Long.parseLong(o.getId()))
              .collect(Collectors.toSet());
      // Per user: LP paid in (cost + fees), cost basis (cost only), final shares per outcome.
      Map<Integer, long[]> paidAndCost = new java.util.LinkedHashMap<>();          // [paid, cost]
      Map<Integer, Map<Long, Double>> sharesByOutcome = new java.util.HashMap<>();
      for (com.faforever.client.api.dto.WagerTrade t
          : fafApiAccessor.getWagerTradesForMarket(Long.parseLong(market.getId()), 1000)) {
        if (botUserId != 0 && t.getUserId() == botUserId) {
          continue;
        }
        long[] acc = paidAndCost.computeIfAbsent(t.getUserId(), id -> new long[2]);
        acc[0] += t.getCostLp() + t.getFeeLp();
        acc[1] += t.getCostLp();
        sharesByOutcome.computeIfAbsent(t.getUserId(), id -> new java.util.HashMap<>())
            .merge(t.getOutcomeId(), t.getDeltaShares(), Double::sum);
      }
      paidAndCost.forEach((userId, acc) -> {
        long pnl;
        if (voided) {
          pnl = acc[1] - acc[0];    // refund-at-cost: all that's lost is the fees
        } else {
          long payout = sharesByOutcome.getOrDefault(userId, Map.of()).entrySet().stream()
              .filter(e -> winningOutcomeIds.contains(e.getKey()))
              .mapToLong(e -> Math.max(0, Math.round(e.getValue() * SHARE_PAYOUT_LP)))
              .sum();
          pnl = payout - acc[0];
        }
        pnlByUser.merge(userId, pnl, Long::sum);
      });
    }
    Map<Integer, String> names = resolveNames(pnlByUser.keySet());
    return pnlByUser.entrySet().stream()
        .map(e -> new TraderPnl(e.getKey(), names.get(e.getKey()), e.getValue()))
        .sorted(java.util.Comparator.comparingLong(TraderPnl::pnlLp).reversed())
        .collect(Collectors.toList());
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
      } else {
        // A trade reject whose pending future is already gone (e.g. it timed out) — nothing to
        // fail, and not a subscribe reject, so don't surface it as one.
        log.debug("Wager trade rejected with no pending future: {}", reason);
      }
      return;
    }
    // A reject with no client_ref is a subscribe-level reject (e.g. participant_blocked on
    // subscribe, §8) — no pending trade to fail. Surface it against the currently-subscribed
    // game so the UI can explain the empty outcomes instead of silently swallowing it.
    log.debug("Wager subscribe rejected: {}", reason);
    int gameId = currentGameId;
    java.util.function.Consumer<SubscribeReject> listener = subscribeRejectListener;
    if (gameId != -1 && listener != null) {
      JavaFxUtil.runLater(() -> listener.accept(new SubscribeReject(gameId, reason)));
    }
  }
}
