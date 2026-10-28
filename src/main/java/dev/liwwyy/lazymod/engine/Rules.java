// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.engine;

import static dev.liwwyy.lazymod.config.Settings.*;

import java.util.*;

public final class Rules {
  private Rules() {}

  public record Item(
      boolean empty, String id, Set<String> tags, boolean damageable, int damage, int maximum) {}

  public record View(Item main, Item off, Target target, String entityType, boolean validTarget) {}

  public static List<String> tokens(String text) {
    return Arrays.stream(text.split(","))
        .map(String::strip)
        .filter(v -> !v.isEmpty())
        .map(
            v ->
                v.contains(":")
                    ? v
                    : v.startsWith("#") ? "#minecraft:" + v.substring(1) : "minecraft:" + v)
        .toList();
  }

  public static boolean itemMatches(String filter, Item item) {
    if (filter.isBlank()) return true;
    if (item.empty()) return false;
    return tokens(filter).stream()
        .anyMatch(
            v -> v.startsWith("#") ? item.tags().contains(v.substring(1)) : item.id().equals(v));
  }

  public static boolean durability(Item item, int percent) {
    if (item.empty()) return false;
    return !item.damageable()
        || (item.maximum() > 0
            && (long) (item.maximum() - item.damage()) * 100 > (long) item.maximum() * percent);
  }

  public static String blocked(Conditions c, View v) {
    if (!itemMatches(c.mainItems, v.main())) return "Main-hand item requirement";
    if (!itemMatches(c.offItems, v.off())) return "Offhand item requirement";
    if (c.target != Target.NONE) {
      if (!v.validTarget()) return "No valid crosshair target in range";
      boolean match =
          c.target == v.target()
              || c.target == Target.ANY_ENTITY
                  && v.target() != Target.BLOCK
                  && v.target() != Target.NONE;
      if (!match) return "Crosshair target requirement: " + c.target;
    }
    if (!c.entityTypes.isBlank()
        && (!v.validTarget() || !tokens(c.entityTypes).contains(v.entityType())))
      return "Entity type requirement";
    if (c.checkDurability
        && !durability(
            c.durabilityHand == Hand.MAIN ? v.main() : v.off(), c.minimumDurabilityPercent))
      return "Held-item durability must be > " + c.minimumDurabilityPercent + "%";
    return "";
  }

  public static boolean hungry(Eating e, int hunger, float health) {
    boolean h = hunger <= e.hunger, hp = health <= e.health;
    return switch (e.trigger) {
      case HUNGER -> h;
      case HEALTH -> hp;
      case EITHER -> h || hp;
      case BOTH -> h && hp;
    };
  }
}
