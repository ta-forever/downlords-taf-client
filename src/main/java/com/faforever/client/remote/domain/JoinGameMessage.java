package com.faforever.client.remote.domain;

public class JoinGameMessage extends ClientMessage {

  private Integer uid;
  private String password;
  /** "cavedog" or "oa"; null from clients that predate engine reporting. */
  private String engine;
  private String engineVersion;

  public JoinGameMessage(int uid, String password, String engine, String engineVersion) {
    super(ClientMessageType.JOIN_GAME);
    this.setUid(uid);
    this.setPassword(password);
    this.engine = engine;
    this.engineVersion = engineVersion;
  }

  public String getEngine() {
    return engine;
  }

  public String getEngineVersion() {
    return engineVersion;
  }

  public Integer getUid() {
    return uid;
  }

  public void setUid(Integer uid) {
    this.uid = uid;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
