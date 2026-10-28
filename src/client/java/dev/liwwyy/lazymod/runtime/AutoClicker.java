// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.runtime;

import dev.liwwyy.lazymod.config.Settings.*;
import dev.liwwyy.lazymod.engine.*;
import dev.liwwyy.lazymod.mixin.MinecraftAccess;
import net.minecraft.client.Minecraft;

final class AutoClicker {
  private net.minecraft.world.phys.HitResult lastTarget;
  private long lastTargetAt, lastRightAt;
  private net.minecraft.world.phys.HitResult lastRightTarget;
  private final Automation a;
  private final Minecraft mc;
  private final ActionTimer left = new ActionTimer(), right = new ActionTimer();
  private String leftReason = "Disabled", rightReason = "Disabled";
  private long lastManualLeftFrame = Long.MIN_VALUE, lastManualRightFrame = Long.MIN_VALUE;

  AutoClicker(Automation a) {
    this.a = a;
    mc = a.mc;
    reset();
  }

  void reset() {
    lastTarget = null;
    lastRightTarget = null;
    long now = System.nanoTime();
    Profile p = a.profile();
    left.rearm(now, p.left.intervalMs);
    right.rearm(now, p.right.mode == Mode.HOLD ? 200 : p.right.intervalMs);
  }

  void manual(boolean attack) {
    if (attack) {
      left.rearm(System.nanoTime(), a.profile().left.intervalMs);
      lastManualLeftFrame = a.currentFrame();
    } else {
      right.rearm(System.nanoTime(), a.profile().right.intervalMs);
      lastManualRightFrame = a.currentFrame();
    }
  }

  String status() {
    return "left: " + leftReason + "; right: " + rightReason;
  }

  void waitForFood() {
    leftReason = rightReason = "Waiting for AutoEat";
  }

  void tick(long now) {
    if (!a.activation.autoClicker) {
      if (a.owner() == Automation.Owner.CLICKER) a.releaseUse();
      leftReason = rightReason = "Disabled";
      return;
    }
    MinecraftBridge.refreshTarget(mc);
    Profile p = a.profile();
    Rules.View view = MinecraftBridge.view(mc, true);
    leftReason = reason(p.left, view);
    if (leftReason.isEmpty() && view.validTarget()) {
      lastTarget = mc.hitResult;
      lastTargetAt = now;
    }
    boolean grace =
        lastTarget != null
            && p.targetGraceMs > 0
            && now - lastTargetAt < p.targetGraceMs * 1_000_000L;
    boolean cachedAttack = false;
    net.minecraft.world.phys.HitResult currentTarget = mc.hitResult;
    if (grace && !leftReason.isEmpty()) {
      mc.hitResult = lastTarget;
      try {
        view = MinecraftBridge.view(mc, true);
        leftReason = reason(p.left, view);
        cachedAttack = leftReason.isEmpty();
      } finally {
        mc.hitResult = currentTarget;
      }
    }
    // Left automation is intentionally entity-only: no unrequested air swings/block mining.
    if (leftReason.isEmpty() && (view.target() == Target.NONE || view.target() == Target.BLOCK))
      leftReason = "An entity target is required for attacks";
    rightReason = reason(p.right, MinecraftBridge.view(mc, false));
    if (rightReason.isEmpty() && MinecraftBridge.view(mc, false).validTarget()) {
      lastRightTarget = mc.hitResult;
      lastRightAt = now;
    }
    boolean rightGrace =
        lastRightTarget != null
            && p.targetGraceMs > 0
            && now - lastRightAt < p.targetGraceMs * 1_000_000L;
    if (rightGrace && !rightReason.isEmpty()) {
      mc.hitResult = lastRightTarget;
      try {
        rightReason = reason(p.right, MinecraftBridge.view(mc, false));
      } finally {
        mc.hitResult = currentTarget;
      }
    }
    if (a.owner() == Automation.Owner.CLICKER
        && (!rightReason.isEmpty() || p.right.mode != Mode.HOLD)) a.releaseUse();
    if (mc.player.isUsingItem()) {
      leftReason = "Waiting for item use";
      return;
    }
    if (leftReason.isEmpty()) {
      boolean ready =
          p.left.vanillaCooldown
              ? mc.player.getAttackStrengthScale(0.0F) >= 1.0F
              : left.ready(now, a.currentFrame());
      if (ready && lastManualLeftFrame != a.currentFrame() && lastLeftFrame != a.currentFrame()) {
        if (cachedAttack) mc.hitResult = lastTarget;
        try {
          a.automatic(() -> ((MinecraftAccess) mc).lazymod$attack());
        } finally {
          mc.hitResult = currentTarget;
        }
        lastLeftFrame = a.currentFrame();
        left.fired(now, a.currentFrame(), p.left.intervalMs);
        // Vanilla also handles attacks before use when both inputs are present.
        leftReason = "Attack requested";
      } else
        leftReason =
            p.left.vanillaCooldown ? "Waiting for full attack strength" : "Waiting for interval";
    }
    if (!rightReason.isEmpty()) return;
    int delay = p.right.mode == Mode.HOLD ? 200 : p.right.intervalMs;
    if (p.right.mode == Mode.HOLD && ((MinecraftAccess) mc).lazymod$useDelay() > 0) {
      rightReason = "Waiting for vanilla held-use delay";
      return;
    }
    if (!right.ready(now, a.currentFrame()) || lastManualRightFrame == a.currentFrame()) {
      rightReason = "Waiting for interval/use delay";
      return;
    }
    if (p.right.use == Use.ITEM_ONLY
        && mc.player
            .getCooldowns()
            .isOnCooldown(mc.player.getItemInHand(MinecraftBridge.hand(p.right.useHand)))) {
      rightReason = "Item cooldown";
      return;
    }
    a.automatic(
        () -> {
          if (p.right.use == Use.ITEM_ONLY)
            MinecraftBridge.useItem(mc, MinecraftBridge.hand(p.right.useHand));
          else ((MinecraftAccess) mc).lazymod$use();
          if (mc.player.isUsingItem()) {
            a.claim(Automation.Owner.CLICKER, mc.player.getUsedItemHand());
          }
        });
    right.fired(now, a.currentFrame(), delay);
    rightReason = "Using";
  }

  private long lastLeftFrame = Long.MIN_VALUE;

  private String reason(Button b, Rules.View view) {
    if (b.mode == Mode.DISABLED) return "Disabled";
    String unresolved = MinecraftBridge.unresolved(b.conditions);
    return unresolved.isEmpty() ? Rules.blocked(b.conditions, view) : unresolved;
  }
}
