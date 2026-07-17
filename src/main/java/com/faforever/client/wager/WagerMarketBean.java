package com.faforever.client.wager;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** A wager market for the currently-subscribed game, with its outcomes. Prices on the
 * outcomes update live; the market {@code status} flips to CLOSED/SETTLED/VOIDED. */
public class WagerMarketBean {

  private final long marketId;
  private final int gameId;
  private final String marketType;
  private final String medalCode;
  private final double liquidity;      // LMSR b (shares)
  private final int feeBps;            // trade fee (basis points)
  private final StringProperty status = new SimpleStringProperty();
  private final ObservableList<WagerOutcomeBean> outcomes = FXCollections.observableArrayList();

  public WagerMarketBean(long marketId, int gameId, String marketType, String medalCode, String status,
                         double liquidity, int feeBps) {
    this.marketId = marketId;
    this.gameId = gameId;
    this.marketType = marketType;
    this.medalCode = medalCode;
    this.status.set(status);
    this.liquidity = liquidity;
    this.feeBps = feeBps;
  }

  public double getLiquidity() {
    return liquidity;
  }

  public int getFeeBps() {
    return feeBps;
  }

  public long getMarketId() {
    return marketId;
  }

  public int getGameId() {
    return gameId;
  }

  public String getMarketType() {
    return marketType;
  }

  public String getMedalCode() {
    return medalCode;
  }

  public String getStatus() {
    return status.get();
  }

  public void setStatus(String value) {
    status.set(value);
  }

  public StringProperty statusProperty() {
    return status;
  }

  public ObservableList<WagerOutcomeBean> getOutcomes() {
    return outcomes;
  }
}
