package com.faforever.client.remote.domain;

/**
 * Player declines the "Hold your spot?" prompt after exiting a reserved-slots
 * game from battleroom. Removes the auto-reserve TTL entry that the server
 * added on disconnect. The server identifies the relevant game from the
 * player's outstanding TTL reservations; no payload required.
 */
public class CancelReservationMessage extends ClientMessage {

  public CancelReservationMessage() {
    super(ClientMessageType.CANCEL_RESERVATION);
  }
}
