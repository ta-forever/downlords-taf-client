package com.faforever.client.wager;

import com.faforever.client.FafClientApplication;
import com.faforever.client.api.dto.WagerMarket;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Profile(FafClientApplication.PROFILE_OFFLINE)
public class MockWagerService implements WagerService {

  @Override
  public CompletableFuture<List<WagerMarket>> getWatchlist() {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public ObservableList<WagerMarketBean> subscribeToGame(int gameId) {
    return FXCollections.observableArrayList();
  }

  @Override
  public void unsubscribe() {
  }

  @Override
  public CompletableFuture<WagerTradeResult> trade(int gameId, long marketId, String outcomeKey, double deltaShares) {
    return CompletableFuture.failedFuture(new WagerTradeException("not_eligible"));
  }

  @Override
  public CompletableFuture<List<WagerPositionBean>> getMyPositions() {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<com.faforever.client.api.dto.WagerBotPnl>> getBotPnl() {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<PricePoint>> getPriceHistory(long marketId, long outcomeId, double b,
                                                             boolean twoOutcome, int limit) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<java.util.Optional<ReplayPriceChart>> getWinningOutcomeHistory(int gameId) {
    return CompletableFuture.completedFuture(java.util.Optional.empty());
  }

  @Override
  public void setSettlementListener(java.util.function.Consumer<Settlement> listener) {
  }

  @Override
  public void setSubscribeRejectListener(java.util.function.Consumer<SubscribeReject> listener) {
  }
}
