package com.faforever.client.preferences;

import com.faforever.client.player.SocialStatus;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import lombok.Data;

public class ToxicitySetting {
  private final ObjectProperty<SocialStatus> socialStatus;
  private final DoubleProperty toxicityThreshold;
  private final ObjectProperty<ToxicityAction> action;

  public ToxicitySetting(SocialStatus socialStatus, double toxicityThreshold, ToxicityAction action) {
    this.socialStatus = new SimpleObjectProperty<>(socialStatus);
    this.toxicityThreshold = new SimpleDoubleProperty(toxicityThreshold);
    this.action = new SimpleObjectProperty<>(action);
  }

  public ObservableValue<SocialStatus> socialStatusProperty() { return socialStatus; }
  public SocialStatus getSocialStatus() { return socialStatus.get(); }
  public DoubleProperty toxicityThresholdProperty() {
    return toxicityThreshold;
  }
  public Double getToxicityThreshold() { return toxicityThreshold.get(); }
  public ObservableValue<ToxicityAction> actionProperty() {
    return action;
  }
  public ToxicityAction getAction() { return action.get(); }
}

