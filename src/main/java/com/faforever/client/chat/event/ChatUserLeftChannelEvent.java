package com.faforever.client.chat.event;

import lombok.Value;

/** Another user left a channel we're in, by parting it or quitting IRC. Not posted for renames or for ourselves. */
@Value
public class ChatUserLeftChannelEvent {
  String channelName;
  String username;
}
