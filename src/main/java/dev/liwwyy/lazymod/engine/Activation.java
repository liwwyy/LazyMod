// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.engine;

public final class Activation {
  public boolean autoClicker;
  public boolean standaloneEat;

  public boolean integratedEat(boolean profileIntegration) {
    return autoClicker && profileIntegration;
  }

  public boolean effectiveEat(boolean profileIntegration) {
    return standaloneEat || integratedEat(profileIntegration);
  }

  public void clear() {
    autoClicker = false;
    standaloneEat = false;
  }
}
