package com.faforever.client.fa.oa;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.Locale;

/**
 * A hosting Open Annihilation binds DirectPlay's enumeration port (47624, TCP and UDP) and refuses to host if it
 * cannot. On Linux the Wine DirectPlay service ({@code dplaysvr.exe}) that a TotalA.exe game leaves behind holds that
 * port after the game has gone, so the next OA host would fail with "The game could not be created". Before OA hosts,
 * a stale Wine service is stopped; anything else on the port is reported by name of the port, never killed.
 */
@Slf4j
final class DirectPlayPort {

  static final int PORT = 47624;
  private static final int WAIT_MS = 3000;

  private DirectPlayPort() {
  }

  /** @throws IOException when the port is still taken after a stale Wine DirectPlay service was stopped */
  static void ensureFreeForHosting() throws IOException {
    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
      return; // Windows DirectPlay shares the port; the problem is Wine's
    }
    ensureFree(PORT, DirectPlayPort::stopWineDirectPlayService);
  }

  static void ensureFree(int port, Runnable stopStaleService) throws IOException {
    if (isFree(port)) {
      return;
    }
    log.info("[OaLaunchServer] port {} is taken; stopping a Wine DirectPlay service if that is what holds it", port);
    stopStaleService.run();
    for (int waited = 0; waited < WAIT_MS; waited += 200) {
      if (isFree(port)) {
        return;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    throw new IOException("DirectPlay port " + port + " is in use by another program, so Open Annihilation cannot host");
  }

  static boolean isFree(int port) {
    try (ServerSocket tcp = new ServerSocket(port); DatagramSocket udp = new DatagramSocket(port)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static void stopWineDirectPlayService() {
    try {
      new ProcessBuilder("pkill", "-f", "dplaysvr.exe").redirectErrorStream(true).start().waitFor();
    } catch (IOException e) {
      log.debug("[OaLaunchServer] could not run pkill: {}", e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
