package com.faforever.client.remote.domain;

import java.util.HashMap;
import java.util.Map;

public enum ClientMessageType {
  HOST_GAME("game_host"),
  LIST_REPLAYS("list"),
  JOIN_GAME("game_join"),
  ASK_SESSION("ask_session"),
  SOCIAL_ADD("social_add"),
  SOCIAL_REMOVE("social_remove"),
  STATISTICS("stats"),
  LOGIN("hello"),
  GAME_MATCH_MAKING("game_matchmaking"),
  AVATAR("avatar"),
  ICE_SERVERS("ice_servers"),
  RESTORE_GAME_SESSION("restore_game_session"),
  PING("ping"),
  PONG("pong"),
  ADMIN("admin"),
  INVITE_TO_PARTY("invite_to_party"),
  ACCEPT_PARTY_INVITE("accept_party_invite"),
  KICK_PLAYER_FROM_PARTY("kick_player_from_party"),
  READY_PARTY("ready_party"),
  UNREADY_PARTY("unready_party"),
  LEAVE_PARTY("leave_party"),
  SET_PARTY_FACTIONS("set_party_factions"),
  SET_PLAYER_ALIAS("set_player_alias"),
  SET_GAME_MAP_DETAILS("set_game_map_details"),
  SET_GAME_PASSWORD("set_game_password"),
  SET_RESERVED_SLOTS_ENABLED("set_reserved_slots_enabled"),
  SET_RESERVED_PLAYERS("set_reserved_players"),
  LEAVE_AND_RESERVE("leave_and_reserve"),
  CANCEL_RESERVATION("cancel_reservation"),
  REQUEST_GAME_ACCESS("request_game_access"),
  DISMISS_JOIN_REQUEST("dismiss_join_request"),
  MATCHMAKER_INFO("matchmaker_info"),
  GAME_MATCHMAKING("game_matchmaking"),
  UPLOAD_REPLAY_TO_TADA("upload_replay_to_tada"),
  GALACTIC_WAR_SET_MAP("galactic_war_set_map"),
  TOURNAMENT_SIGNUP("tournament_signup"),
  TOURNAMENT_WITHDRAW("tournament_withdraw"),
  TOURNAMENT_CHECK_IN("tournament_check_in"),
  TOURNAMENT_CREATE("tournament_create"),
  // Team tournaments — see TournamentTeamService
  TOURNAMENT_TEAM_CREATE("tournament_team_create"),
  TOURNAMENT_TEAM_INVITE("tournament_team_invite"),
  TOURNAMENT_TEAM_ACCEPT_INVITE("tournament_team_accept_invite"),
  TOURNAMENT_TEAM_DECLINE_INVITE("tournament_team_decline_invite"),
  TOURNAMENT_TEAM_LEAVE("tournament_team_leave"),
  TOURNAMENT_TEAM_REMOVE_MEMBER("tournament_team_remove_member"),
  TOURNAMENT_TEAM_DISBAND("tournament_team_disband"),
  TOURNAMENT_START("tournament_start"),
  TOURNAMENT_CANCEL("tournament_cancel"),
  TOURNAMENT_EDIT("tournament_edit");

  private static Map<String, ClientMessageType> fromString;

  static {
    fromString = new HashMap<>();
    for (ClientMessageType clientMessageType : values()) {
      fromString.put(clientMessageType.string, clientMessageType);
    }
  }

  private String string;

  ClientMessageType(String string) {
    this.string = string;
  }

  public static ClientMessageType fromString(String string) {
    return fromString.get(string);
  }

  public String getString() {
    return string;
  }
}
