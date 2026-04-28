package com.faforever.client;

import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;

import java.util.Comparator;

/**
 * Lightweight startup profiler. Records coarse wall-clock milestones from
 * the moment FafClientApplication.init() begins through the first chat /
 * game-info message arriving, so we can see where startup time goes at
 * a glance without setting up a full profiler.
 *
 * <p>Pairs with a {@link BufferingApplicationStartup} attached to the
 * SpringApplicationBuilder; once Spring boot finishes, {@link #dumpSpring}
 * prints the slowest 30 startup steps it recorded (bean instantiation,
 * autoconfig, etc.).
 *
 * <p>Each {@link #mark} prints a delta-from-previous and delta-from-start
 * line at INFO so the timing shows up in {@code client.log} without any
 * special log level. The class is intentionally a static singleton — the
 * profiler has no state worth instance-managing and the milestones come
 * from many places (entry-point, FXML load, lobby connect handlers, etc.).
 */
public final class StartupProfiler {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StartupProfiler.class);

  private static final long START_NS = System.nanoTime();
  private static volatile long lastNs = START_NS;
  private static volatile boolean disabled = false;

  private StartupProfiler() {}

  public static void mark(String label) {
    if (disabled) return;
    long now = System.nanoTime();
    long sincePrev = (now - lastNs) / 1_000_000;
    long sinceStart = (now - START_NS) / 1_000_000;
    lastNs = now;
    log.info("STARTUP {} +{}ms (total {}ms)", label, sincePrev, sinceStart);
  }

  /**
   * Mark this milestone only if it hasn't already fired in this VM
   * lifetime — useful for "first chat message received" / "first game
   * list snapshot" kinds of events that the rest of the runtime keeps
   * generating long after startup.
   */
  public static void markOnce(String label) {
    synchronized (StartupProfiler.class) {
      if (firedLabels.add(label)) {
        mark(label);
      }
    }
  }

  private static final java.util.Set<String> firedLabels = new java.util.HashSet<>();

  /**
   * Dump the slowest {@code limit} steps recorded by the buffering startup
   * recorder. Sorted by duration descending so the bottlenecks float to
   * the top. After dumping we mark the profiler {@code disabled} so the
   * post-startup runtime doesn't spam the log with stale milestones.
   */
  public static void dumpSpring(BufferingApplicationStartup startup, int limit) {
    if (startup == null) return;
    StartupTimeline drained = startup.drainBufferedTimeline();
    log.info("STARTUP --- top {} Spring init steps by duration ---", limit);
    drained.getEvents().stream()
        .filter(e -> e.getDuration() != null)
        .sorted(Comparator.comparing(e -> e.getDuration(), Comparator.reverseOrder()))
        .limit(limit)
        .forEach(e -> {
          // StartupStep.getTags() returns Iterable<Tag>; the default toString
          // is a lambda hashCode (not useful), so format key=value pairs by
          // hand. Most steps have one or two tags (beanName, beanType, etc.)
          // and that's the main bit telling us WHICH bean was slow.
          StringBuilder sb = new StringBuilder();
          for (org.springframework.core.metrics.StartupStep.Tag tag : e.getStartupStep().getTags()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(tag.getKey()).append('=').append(tag.getValue());
          }
          log.info("STARTUP   {}ms  {}  [{}]",
              e.getDuration().toMillis(),
              e.getStartupStep().getName(),
              sb);
        });
    log.info("STARTUP --- end ---");
  }

  public static void disable() {
    disabled = true;
  }
}
