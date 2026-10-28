// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.runtime;

import dev.liwwyy.lazymod.config.Settings.*;
import dev.liwwyy.lazymod.engine.Rules;
import dev.liwwyy.lazymod.mixin.MinecraftAccess;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;

public final class MinecraftBridge {
  private MinecraftBridge() {}

  public static InteractionHand hand(Hand h) {
    return h == Hand.MAIN ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
  }

  public static String unresolved(Conditions c) {
    for (String filter : List.of(c.mainItems, c.offItems))
      for (String token : Rules.tokens(filter)) {
        boolean tag = token.startsWith("#");
        Identifier id = Identifier.tryParse(tag ? token.substring(1) : token);
        if (id == null
            || (tag
                ? BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id)).isEmpty()
                : !BuiltInRegistries.ITEM.containsKey(id))) return "Unknown item/tag: " + token;
      }
    for (String token : Rules.tokens(c.entityTypes)) {
      Identifier id = Identifier.tryParse(token);
      if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id))
        return "Unknown entity type: " + token;
    }
    return "";
  }

  private static Rules.Item item(ItemStack stack) {
    return new Rules.Item(
        stack.isEmpty(),
        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
        stack.typeHolder().tags().map(t -> t.location().toString()).collect(Collectors.toSet()),
        stack.isDamageableItem(),
        stack.getDamageValue(),
        stack.getMaxDamage());
  }

  public static Rules.View view(Minecraft mc, boolean attack) {
    Target type = Target.NONE;
    String entityType = "";
    boolean valid = false;
    if (mc.hitResult instanceof EntityHitResult hit) {
      Entity entity = hit.getEntity();
      entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
      type =
          entity instanceof ArmorStand
              ? Target.ARMOR_STAND
              : entity instanceof LivingEntity && !(entity instanceof Player)
                  ? Target.LIVING
                  : Target.ANY_ENTITY;
      valid =
          !entity.isRemoved()
              && entity.isAttackable()
              && (!(entity instanceof LivingEntity living) || living.isAlive())
              && mc.level.getWorldBorder().isWithinBounds(entity.blockPosition())
              && (attack
                  ? mc.player.isWithinAttackRange(
                      mc.player.getMainHandItem(), entity.getBoundingBox(), 0.0)
                  : mc.player.isWithinEntityInteractionRange(entity, 0.0));
    } else if (mc.hitResult instanceof BlockHitResult hit
        && hit.getType() == HitResult.Type.BLOCK) {
      type = Target.BLOCK;
      valid = mc.player.isWithinBlockInteractionRange(hit.getBlockPos(), 0.0);
    }
    return new Rules.View(
        item(mc.player.getMainHandItem()),
        item(mc.player.getOffhandItem()),
        type,
        entityType,
        valid);
  }

  public static boolean useItem(Minecraft mc, InteractionHand hand) {
    if (mc.player.getCooldowns().isOnCooldown(mc.player.getItemInHand(hand))
        || mc.player.isHandsBusy()
        || mc.gameMode.isDestroying()) return false;
    ((MinecraftAccess) mc).lazymod$useDelay(4);
    InteractionResult result = mc.gameMode.useItem(mc.player, hand);
    if (result instanceof InteractionResult.Success success) {
      if (success.swingSource() == InteractionResult.SwingSource.CLIENT) mc.player.swing(hand);
      mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
      return true;
    }
    return false;
  }

  public static void refreshTarget(Minecraft mc) {
    ((MinecraftAccess) mc).lazymod$pick(1.0F);
  }
}
