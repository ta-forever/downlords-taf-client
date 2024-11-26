package com.faforever.client.chat;

import com.faforever.client.preferences.ToxicityAction;
import lombok.Data;

import java.time.Instant;
import java.util.Objects;

@Data
public class ChatMessage {

  private final String source;
  // TODO change to LocalTime?
  private final Instant time;
  private final String username;
  private final String message;
  private final double toxicityScore;
  private boolean action;
  private String subject;

  /**
   * @param source the name of the message source/target - either a channel or a username.
   * @param username the user who sent the message
   */
  public ChatMessage(String source, Instant time, String username, String message, double toxicityScore) {
    this(source, time, username, message, toxicityScore, false);
  }

  public ChatMessage(String source, Instant time, String username, String message, double toxicityScore, boolean isAction) {
    this.source = source;
    this.time = time;
    this.username = username;
    this.message = message;
    this.toxicityScore = toxicityScore;
    this.action = isAction;
  }

  public boolean isPrivate() {
    return !Objects.toString(source, "").startsWith("#");
  }

  /**
   * @param subject who/what is the message about. Might be used to suppress notifications about oneself
   * @return
   */
  public ChatMessage setSubject(String subject) {
    this.subject = subject;
    return this;
  }
}
