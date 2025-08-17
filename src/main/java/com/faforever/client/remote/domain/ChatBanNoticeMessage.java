package com.faforever.client.remote.domain;

public class ChatBanNoticeMessage extends FafServerMessage {

  private Boolean isBanned;
  private String expiry;
  private String reason;

  public ChatBanNoticeMessage() {
    super(FafServerMessageType.CHAT_BAN_NOTICE);
  }

  public Boolean getIsBanned() { return isBanned; }
  public void setIsBanned(Boolean isBanned) { this.isBanned = isBanned; }
  public String getExpiry() {
    return expiry;
  }
  public void setExpiry(String reason) {
    this.expiry = expiry;
  }
  public String getReason() {
    return reason;
  }
  public void setReason(String reason) {
    this.reason = reason;
  }

}
