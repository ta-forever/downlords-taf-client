package com.faforever.client.main.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OpenTadaPageEvent extends NavigateEvent {
  private final String url;

  public OpenTadaPageEvent(String url) {
    super(NavigationItem.TADA);
    this.url = url;
  }
}
