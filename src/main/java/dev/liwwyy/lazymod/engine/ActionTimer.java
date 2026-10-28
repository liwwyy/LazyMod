// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.engine;

/** Monotonic deadlines; no accumulated debt and no dispatch twice within a render frame. */
public final class ActionTimer {
  private long next;
  private long frame = Long.MIN_VALUE;

  public void rearm(long now, int milliseconds) {
    next = now + milliseconds * 1_000_000L;
  }

  public boolean ready(long now, long currentFrame) {
    return now >= next && frame != currentFrame;
  }

  public void fired(long now, long currentFrame, int milliseconds) {
    frame = currentFrame;
    rearm(now, milliseconds);
  }

  public static int parseInterval(String value) {
    try {
      int n = Integer.parseInt(value.strip());
      if (n > 0) return n;
    } catch (NumberFormatException ignored) {
    }
    throw new IllegalArgumentException("Enter a whole number from 1 to 2147483647 milliseconds.");
  }
}
