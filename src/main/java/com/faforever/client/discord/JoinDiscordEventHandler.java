package com.faforever.client.discord;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.fx.PlatformService;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class JoinDiscordEventHandler {

  private static final String COMMAND_KEY = "Discord\\shell\\open\\command";

  private final ClientProperties clientProperties;
  private final PlatformService platformService;

  @EventListener
  public void onJoin(JoinDiscordEvent event) throws IOException {
    if (canJoinViaDiscord()) {
      try {
        joinViaDiscord(event.getUrl());
        return;
      } catch (java.io.IOException e) {
        log.error("unable to launch discord. using browser instead", e.getMessage());
      }
    }
    joinViaBrowser(event.getUrl());
  }

  private boolean canJoinViaDiscord() {
    return Platform.isWindows() && Advapi32Util.registryKeyExists(WinReg.HKEY_CLASSES_ROOT, COMMAND_KEY);
  }

  private void joinViaBrowser(String joinUrl) {
    platformService.showDocument(joinUrl);
  }

  private void joinViaDiscord(String joinUrl) throws IOException {
    String command = Advapi32Util.registryGetStringValue(WinReg.HKEY_CLASSES_ROOT, COMMAND_KEY, null)
        .replace("%1", joinUrl);

    Runtime.getRuntime().exec(String.format("%s > nul 2>&1", command));
  }
}
