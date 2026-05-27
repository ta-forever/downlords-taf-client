package com.faforever.client.remote.domain;

/**
 * Sent by a player who is leaving a reserved-slots game and wants to hold
 * their slot for the 5-minute return window. The server identifies the game
 * from the connection state; no payload required.
 */
public class LeaveAndReserveMessage extends ClientMessage {

  public LeaveAndReserveMessage() {
    super(ClientMessageType.LEAVE_AND_RESERVE);
  }
}
