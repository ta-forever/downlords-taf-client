package com.faforever.client.fa.oa;

/** Waits; replaceable so a test does not sleep. */
@FunctionalInterface
interface OaPause {
  void sleep(long millis) throws InterruptedException;
}
