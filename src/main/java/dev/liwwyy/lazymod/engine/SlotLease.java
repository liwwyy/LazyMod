// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.engine;

import java.util.OptionalInt;

/** Restore a hotbar selection only while our temporary selection is still current. */
public final class SlotLease {
  private int previous = -1, temporary = -1;

  public void begin(int previous, int temporary) {
    this.previous = previous;
    this.temporary = temporary;
  }

  public boolean manualChange(int current) {
    return temporary >= 0 && temporary < 9 && current != temporary;
  }

  public void abandon() {
    previous = -1;
    temporary = -1;
  }

  public OptionalInt stop(int current) {
    OptionalInt result =
        previous >= 0 && temporary >= 0 && temporary < 9 && current == temporary
            ? OptionalInt.of(previous)
            : OptionalInt.empty();
    abandon();
    return result;
  }
}
