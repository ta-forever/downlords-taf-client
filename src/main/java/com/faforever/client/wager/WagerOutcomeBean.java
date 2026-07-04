package com.faforever.client.wager;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** One tradeable outcome of a market, with a live-updating price (implied probability in
 * [0,1]). The {@code price} is mutated in place by WagerService as WS ticks arrive so a
 * bound TableView refreshes without rebuilding rows. */
public class WagerOutcomeBean {

  private final long outcomeId;
  private final StringProperty outcomeKey = new SimpleStringProperty();
  private final StringProperty label = new SimpleStringProperty();
  private final DoubleProperty price = new SimpleDoubleProperty();
  private final ObjectProperty<Boolean> winner = new SimpleObjectProperty<>();

  public WagerOutcomeBean(long outcomeId, String outcomeKey, String label, double price, Boolean winner) {
    this.outcomeId = outcomeId;
    this.outcomeKey.set(outcomeKey);
    this.label.set(label);
    this.price.set(price);
    this.winner.set(winner);
  }

  public long getOutcomeId() {
    return outcomeId;
  }

  public String getOutcomeKey() {
    return outcomeKey.get();
  }

  public StringProperty outcomeKeyProperty() {
    return outcomeKey;
  }

  public String getLabel() {
    return label.get();
  }

  public StringProperty labelProperty() {
    return label;
  }

  public double getPrice() {
    return price.get();
  }

  public void setPrice(double value) {
    price.set(value);
  }

  public DoubleProperty priceProperty() {
    return price;
  }

  public Boolean getWinner() {
    return winner.get();
  }

  public void setWinner(Boolean value) {
    winner.set(value);
  }

  public ObjectProperty<Boolean> winnerProperty() {
    return winner;
  }
}
