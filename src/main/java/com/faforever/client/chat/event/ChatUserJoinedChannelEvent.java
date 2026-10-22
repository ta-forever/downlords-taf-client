package com.faforever.client.chat.event;

import lombok.Value;

/**
 * Another user joined a channel we're in (an IRC JOIN). Not posted for the user list we receive when we join a channel
 * ourselves, nor for renames.
 */
@Value
public class ChatUserJoinedChannelEvent {
  String channelName;
  String username;
}
