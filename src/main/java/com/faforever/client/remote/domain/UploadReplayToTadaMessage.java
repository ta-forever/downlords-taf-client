package com.faforever.client.remote.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadReplayToTadaMessage extends ClientMessage {

  private Integer replayId;

  public UploadReplayToTadaMessage(Integer replayId) {
    super(ClientMessageType.UPLOAD_REPLAY_TO_TADA);
    this.replayId = replayId;
  }

}
