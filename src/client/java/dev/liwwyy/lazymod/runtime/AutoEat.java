// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.runtime;

import dev.liwwyy.lazymod.config.Settings.*;
import dev.liwwyy.lazymod.engine.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

final class AutoEat {
  private final Automation a;
  private final Minecraft mc;
  private final ActionTimer foodRetry = new ActionTimer();
  private final SlotLease lease = new SlotLease();
  private boolean session;
  private String eatReason = "Disabled";

  AutoEat(Automation a) {
    this.a = a;
    mc = a.mc;
  }

  String status() {
    return eatReason;
  }

  void stop() {
    if (!session) return;
    if (a.owner() == Automation.Owner.EAT) a.releaseUse();
    if (mc.player != null)
      lease
          .stop(mc.player.getInventory().getSelectedSlot())
          .ifPresent(mc.player.getInventory()::setSelectedSlot);
    else lease.abandon();
    session = false;
    a.clicker.reset();
  }

  private boolean edible(ItemStack stack) {
    FoodProperties food = stack.get(DataComponents.FOOD);
    return !stack.isEmpty()
        && food != null
        && stack.has(DataComponents.CONSUMABLE)
        && !Rules.tokens(a.mod.settings.autoEat.blacklist)
            .contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
        && !mc.player.getCooldowns().isOnCooldown(stack)
        && (mc.player.getFoodData().needsFood() || food.canAlwaysEat());
  }

  private ItemStack foodStack(int slot) {
    return slot == 40 ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(slot);
  }

  private int findFood() {
    if (edible(mc.player.getOffhandItem())) return 40;
    int best = -1;
    float score = -1;
    for (int i = 0; i < 9; i++) {
      ItemStack stack = foodStack(i);
      if (!edible(stack)) continue;
      FoodProperties food = stack.get(DataComponents.FOOD);
      float value =
          a.mod.settings.autoEat.priority == FoodPriority.SATURATION
              ? food.saturation()
              : food.nutrition();
      if (value > score) {
        score = value;
        best = i;
      }
    }
    return best;
  }

  boolean tick(long now) {
    if (!a.activation.effectiveEat(a.profile().integrateAutoEat)) {
      stop();
      eatReason = "Disabled";
      return false;
    }
    if (session) {
      if (lease.manualChange(mc.player.getInventory().getSelectedSlot())) {
        // User owns the new selection: never restore over it.
        lease.abandon();
        stop();
        a.yieldUntil(now + 500_000_000L);
        eatReason = "Manual slot change";
        return true;
      }
      if (a.owner() == Automation.Owner.EAT && mc.player.isUsingItem()) {
        eatReason = "Eating";
        return true;
      }
      stop();
    }
    Eating e = a.mod.settings.autoEat;
    if (!Rules.hungry(e, mc.player.getFoodData().getFoodLevel(), mc.player.getHealth())) {
      eatReason = "Waiting for threshold";
      return false;
    }
    if (mc.player.isUsingItem() && a.owner() != Automation.Owner.CLICKER) {
      eatReason = "Waiting for manual item use";
      return false;
    }
    int slot = findFood();
    if (slot < 0) {
      eatReason = "No eligible food in offhand/hotbar";
      return false;
    }
    if (!foodRetry.ready(now, a.currentFrame())) {
      eatReason = "Waiting for use retry";
      return false;
    }
    if (a.owner() == Automation.Owner.CLICKER) a.releaseUse();
    lease.begin(mc.player.getInventory().getSelectedSlot(), slot);
    session = true;
    if (slot < 9) mc.player.getInventory().setSelectedSlot(slot);
    InteractionHand hand = slot == 40 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    a.automatic(
        () -> {
          MinecraftBridge.useItem(mc, hand);
          if (mc.player.isUsingItem()) {
            a.claim(Automation.Owner.EAT, hand);
          }
        });
    foodRetry.fired(now, a.currentFrame(), 200);
    if (a.owner() != Automation.Owner.EAT) {
      stop();
      eatReason = "Item use rejected";
      return false;
    }
    eatReason = "Eating";
    return true;
  }
}
