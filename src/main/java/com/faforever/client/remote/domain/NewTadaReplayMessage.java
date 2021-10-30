package com.faforever.client.remote.domain;

import com.faforever.client.util.TimeUtil;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class NewTadaReplayMessage extends FafServerMessage {

  private Integer tafReplayId;
  private String tadaReplayId;
  private String mapName;
  private List<String> players;
  private Double timestamp;

  public NewTadaReplayMessage() {
    super(FafServerMessageType.NEW_TADA_REPLAY);
  }

  public Instant getTimestamp() {
    return TimeUtil.fromPythonTime(timestamp.longValue()).toInstant();
  }
}
