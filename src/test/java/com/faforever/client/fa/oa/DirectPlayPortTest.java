package com.faforever.client.fa.oa;

import org.junit.Test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

public class DirectPlayPortTest {

  @Test
  public void aFreePortNeedsNothingStopped() throws IOException {
    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    AtomicBoolean stopped = new AtomicBoolean();

    DirectPlayPort.ensureFree(port, () -> stopped.set(true));

    assertThat(stopped.get(), is(false));
  }

  @Test
  public void aTakenPortGetsTheStaleServiceStoppedAndIsReportedIfItStaysTaken() throws IOException {
    try (ServerSocket holder = new ServerSocket(0)) {
      int port = holder.getLocalPort();
      AtomicBoolean stopped = new AtomicBoolean();

      IOException e = assertThrows(IOException.class, () -> DirectPlayPort.ensureFree(port, () -> stopped.set(true)));

      assertThat(stopped.get(), is(true));
      assertThat(e.getMessage().contains(String.valueOf(port)), is(true));
    }
  }

  @Test
  public void aPortFreedByStoppingTheServiceIsUsable() throws IOException {
    ServerSocket holder = new ServerSocket(0);
    int port = holder.getLocalPort();

    DirectPlayPort.ensureFree(port, () -> {
      try {
        holder.close();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    });

    assertThat(DirectPlayPort.isFree(port), is(true));
  }
}
