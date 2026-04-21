package com.faforever.client.remote.domain;

import com.faforever.client.rankedmatch.MatchmakerInfoMessage;
import com.faforever.client.remote.UpdatedAchievementsMessage;

import java.util.HashMap;
import java.util.Map;

public enum FafServerMessageType implements ServerMessageType {
  WELCOME("welcome", LoginMessage.class),
  SESSION("session", SessionMessage.class),
  GAME_INFO("game_info", GameInfoMessage.class),
  PLAYER_INFO("player_info", PlayersMessage.class),
  GAME_LAUNCH("game_launch", GameLaunchMessage.class),
  MATCHMAKER_INFO("matchmaker_info", MatchmakerInfoMessage.class),
  MATCH_FOUND("match_found", MatchFoundMessage.class),
  MATCH_CANCELLED("match_cancelled", MatchCancelledMessage.class),
  SOCIAL("social", SocialMessage.class),
  AUTHENTICATION_FAILED("authentication_failed", AuthenticationFailedMessage.class),
  CHAT_BAN_NOTICE("chat_ban_notice", ChatBanNoticeMessage.class),
  UPDATED_ACHIEVEMENTS("updated_achievements", UpdatedAchievementsMessage.class),
  NOTICE("notice", NoticeMessage.class),
  ICE_SERVERS("ice_servers", IceServersServerMessage.class),
  AVATAR("avatar", AvatarMessage.class),
  PARTY_UPDATE("update_party", PartyInfoMessage.class),
  PARTY_INVITE("party_invite", PartyInviteMessage.class),
  PARTY_KICKED("kicked_from_party", PartyKickedMessage.class),
  SEARCH_INFO("search_info", SearchInfoMessage.class),
  NEW_TADA_REPLAY("new_tada_replay", NewTadaReplayMessage.class),
  GALACTIC_WAR_UPDATE("galactic_war_update", GalacticWarUpdateMessage.class),
  PLAYER_LEFT("player_left", PlayerLeftMessage.class),
  // Team tournament messages.
  TOURNAMENT_TEAM_UPDATED("tournament_team_updated", TournamentTeamUpdatedMessage.class),
  TOURNAMENT_TEAM_INVITE_RECEIVED("tournament_team_invite", TournamentTeamInviteReceivedMessage.class),
  TOURNAMENT_TEAM_INVITE_RESOLVED("tournament_team_invite_resolved", TournamentTeamInviteResolvedMessage.class),
  TOURNAMENT_TIMER_STOPPED("tournament_timer_stopped", TournamentTimerStoppedMessage.class),
  TOURNAMENT_TIMER_RESTARTED("tournament_timer_restarted", TournamentTimerRestartedMessage.class);

  private static final Map<String, FafServerMessageType> fromString;

  static {
    fromString = new HashMap<>(values().length, 1);
    for (FafServerMessageType fafServerMessageType : values()) {
      fromString.put(fafServerMessageType.string, fafServerMessageType);
    }
  }

  private final String string;
  private final Class<? extends FafServerMessage> type;

  FafServerMessageType(String string, Class<? extends FafServerMessage> type) {
    this.string = string;
    this.type = type;
  }

  public static FafServerMessageType fromString(String string) {
    return fromString.get(string);
  }

  @Override
  public String getString() {
    return string;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends ServerMessage> Class<T> getType() {
    return (Class<T>) type;
  }

}
