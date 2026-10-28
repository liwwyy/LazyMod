// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.runtime;

import dev.liwwyy.lazymod.LazyModClient;
import dev.liwwyy.lazymod.config.*;
import dev.liwwyy.lazymod.config.Settings.Profile;
import dev.liwwyy.lazymod.engine.Activation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Coordinates two independent modules; activation is never used as input ownership. */
public final class Automation {
  public final Activation activation = new Activation();
  final LazyModClient mod;
  final Minecraft mc = Minecraft.getInstance();
  final AutoClicker clicker;
  final AutoEat eater;

  enum Owner {
    NONE,
    CLICKER,
    EAT
  }

  private Owner owner = Owner.NONE;
  private InteractionHand ownedHand;
  private boolean automatic;
  private long frame;
  private long yieldUntil = System.nanoTime();
  private Object level;
  private String suspended = "";

  public Automation(LazyModClient mod) {
    this.mod = mod;
    clicker = new AutoClicker(this);
    eater = new AutoEat(this);
  }

  Profile profile() {
    return ProfileCatalog.selected(mod.settings);
  }

  public boolean automatic() {
    return automatic;
  }

  public void frame() {
    frame++;
  }

  long currentFrame() {
    return frame;
  }

  Owner owner() {
    return owner;
  }

  void claim(Owner owner, InteractionHand hand) {
    this.owner = owner;
    this.ownedHand = hand;
  }

  void automatic(Runnable action) {
    automatic = true;
    try {
      action.run();
    } finally {
      automatic = false;
    }
  }

  public boolean keepUsing() {
    return owner != Owner.NONE
        && allowed()
        && mc.player.isUsingItem()
        && mc.player.getUsedItemHand() == ownedHand;
  }

  private boolean allowed() {
    return !mod.store.readOnly()
        && mc.level != null
        && mc.player != null
        && mc.gameMode != null
        && mc.player.isAlive()
        && !mc.player.isSpectator()
        && !mc.isPaused()
        && mc.gui.overlay() == null
        && (mod.settings.allowInScreens || mc.gui.screen() == null);
  }

  void releaseUse() {
    if (owner != Owner.NONE
        && mc.player != null
        && mc.gameMode != null
        && mc.player.isUsingItem()
        && mc.player.getUsedItemHand() == ownedHand
        && !mc.options.keyUse.isDown()) mc.gameMode.releaseUsingItem(mc.player);
    owner = Owner.NONE;
    ownedHand = null;
  }

  void yieldUntil(long deadline) {
    yieldUntil = deadline;
  }

  public void setClicker(boolean active) {
    if (active && mod.store.readOnly()) {
      mod.message("Automation disabled: unsupported configuration.");
      return;
    }
    eater.stop();
    releaseUse();
    activation.autoClicker = active;
    clicker.reset();
    mod.message(clickerStatus());
  }

  public void setStandaloneEat(boolean active) {
    if (active && mod.store.readOnly()) {
      mod.message("Automation disabled: unsupported configuration.");
      return;
    }
    activation.standaloneEat = active;
    if (!activation.effectiveEat(profile().integrateAutoEat)) eater.stop();
    mod.message(eatStatus());
  }

  public void configurationChanged() {
    eater.stop();
    releaseUse();
    clicker.reset();
  }

  public void disconnect() {
    eater.stop();
    releaseUse();
    activation.clear();
    level = null;
    clicker.reset();
  }

  public void screenChanging(boolean opening) {
    if (opening && !mod.settings.allowInScreens) {
      eater.stop();
      releaseUse();
    }
    clicker.reset();
  }

  public void manualPress(boolean attack) {
    if (automatic || mc.gui.screen() != null) return;
    clicker.manual(attack);
    eater.stop();
    releaseUse();
    yieldUntil = System.nanoTime() + 200_000_000L;
  }

  public void manualAttempt(boolean attack) {
    if (!automatic) clicker.manual(attack);
  }

  public String clickerStatus() {
    return "AutoClicker "
        + (activation.autoClicker ? "enabled" : "disabled")
        + "; profile: "
        + profile().name
        + "; "
        + (suspended.isEmpty() ? clicker.status() : suspended);
  }

  public String eatStatus() {
    return "AutoEat standalone="
        + activation.standaloneEat
        + ", profile="
        + activation.integratedEat(profile().integrateAutoEat)
        + ", effective="
        + activation.effectiveEat(profile().integrateAutoEat)
        + "; "
        + (suspended.isEmpty() ? eater.status() : suspended);
  }

  public void tick() {
    if (mc.level != level) {
      disconnect();
      level = mc.level;
    }
    if (mc.player != null && !mc.player.isAlive()) {
      disconnect();
      suspended = "Dead";
      return;
    }
    if (!allowed()) {
      eater.stop();
      releaseUse();
      clicker.reset();
      suspended = "Suspended (screen, pause or unavailable world)";
      return;
    }
    suspended = "";
    if (owner != Owner.NONE && !mc.player.isUsingItem()) {
      owner = Owner.NONE;
      ownedHand = null;
    }
    long now = System.nanoTime();
    if (now - yieldUntil < 0 || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown()) {
      eater.stop();
      releaseUse();
      suspended = "Waiting for physical input";
      return;
    }
    if (eater.tick(now)) {
      clicker.waitForFood();
      return;
    }
    clicker.tick(now);
  }
}
