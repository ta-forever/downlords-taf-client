package com.faforever.client.remote.domain;

import com.faforever.client.notification.Severity;


public class NoticeMessage extends FafServerMessage {

  private String text;
  private String style;
  private String i18nKey;
  /**
   * How this notice should be displayed. One of "immediate" (modal dialog),
   * "persistent" (bell-icon list), "transient" (toast), "refresh" (no display,
   * just signals the player client to reload tournament data). Null/unknown
   * values fall back to immediate so legacy server messages don't get silently
   * downgraded.
   */
  private String kind;
  /**
   * Tournament id this notice is about, if any. When set, the player client
   * triggers a tournament refresh in addition to whatever display the kind
   * dictates.
   */
  @com.fasterxml.jackson.annotation.JsonProperty("tournament_id")
  private Integer tournamentId;
  /**
   * Machine-readable denial reason. Used to branch client UX without parsing
   * the english `text`. Currently only "reserved_slots" is defined (sent on
   * a `game_join_fail` style notice when the join was blocked by the
   * reserved-slots feature). Legacy clients ignore this field.
   */
  private String reasonCode;
  /**
   * Game uid this notice relates to, when applicable. Set on reserved-slot
   * denials so the client can later issue a RequestGameAccess. Set on
   * `game_join_invite` style notices so the client can show a one-click join
   * prompt. Null on notices unrelated to a specific game.
   */
  private Integer gameUid;

  public NoticeMessage() {
    super(FafServerMessageType.NOTICE);
  }

  public Severity getSeverity() {
    if (style == null) {
      return Severity.INFO;
    }
    switch (style) {
      case "error":
        return Severity.ERROR;
      case "warning":
        return Severity.WARN;
      case "info":
        return Severity.INFO;
      default:
        return Severity.INFO;
    }
  }

  public String getText() { return text; }

  public void setText(String text) {
    this.text = text;
  }

  public String getI18nKey() { return this.i18nKey; }

  public void setI18nKey(String i18nKey) {
    this.i18nKey = i18nKey;
  }

  public void setStyle(String style) {
    this.style = style;
  }

  public String getStyle() {
    return style;
  }

  public String getKind() { return kind; }

  public void setKind(String kind) { this.kind = kind; }

  public Integer getTournamentId() { return tournamentId; }

  public void setTournamentId(Integer tournamentId) { this.tournamentId = tournamentId; }

  public String getReasonCode() { return reasonCode; }

  public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

  public Integer getGameUid() { return gameUid; }

  public void setGameUid(Integer gameUid) { this.gameUid = gameUid; }
}
