package com.faforever.client.remote.domain;

/**
 * Wire-protocol constants for the reserved-slots feature. Values MUST match
 * the strings the server emits; do NOT change a constant's value without a
 * coordinated server change AND a backward-compatibility audit of older
 * clients still in the wild.
 *
 * Grouped here (rather than next to the DTOs they describe) so client code
 * and server-side {@code lobbyconnection.py} / {@code game.py} can be diffed
 * against one shared list when adding new flows.
 */
public final class ReservedSlotsProtocol {
  private ReservedSlotsProtocol() {}

  /** Generic notice style for any "you can't join this game" denial.
   *  Pre-existing protocol value; reused by the reserved-slots denial flow
   *  with {@link #NOTICE_REASON_CODE_RESERVED_SLOTS} as a discriminator. */
  public static final String NOTICE_STYLE_GAME_JOIN_FAIL = "game_join_fail";

  /** Sent to a joiner whose pending request has just been approved by the
   *  host (i.e. the host added them to the reserved list while their
   *  request was outstanding). Triggers the persistent invite-to-join
   *  notification on the client. */
  public static final String NOTICE_STYLE_GAME_JOIN_INVITE = "game_join_invite";

  /** Sent to a leaver whose slot the server has just auto-reserved for 30
   *  seconds at disconnect time. Triggers the "Reserve my spot?" prompt on
   *  the client. */
  public static final String NOTICE_STYLE_RESERVED_SLOT_AUTO_RESERVED = "reserved_slot_auto_reserved";

  /** Discriminator carried alongside {@link #NOTICE_STYLE_GAME_JOIN_FAIL}
   *  when the denial is due to reserved-slots capacity. Lets the client
   *  show a "Request access" action instead of the plain English modal. */
  public static final String NOTICE_REASON_CODE_RESERVED_SLOTS = "reserved_slots";
}
