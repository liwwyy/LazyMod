// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod;

import static org.junit.jupiter.api.Assertions.*;

import dev.liwwyy.lazymod.config.*;
import dev.liwwyy.lazymod.config.Settings.*;
import dev.liwwyy.lazymod.engine.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AutomationRulesTest {
  Rules.Item sword(int damage) {
    return new Rules.Item(
        false, "minecraft:diamond_sword", Set.of("minecraft:swords"), true, damage, 1000);
  }

  Rules.Item bottle() {
    return new Rules.Item(false, "minecraft:ominous_bottle", Set.of(), false, 0, 0);
  }

  Rules.Item empty() {
    return new Rules.Item(true, "minecraft:air", Set.of(), false, 0, 0);
  }

  @Test
  void raidRequiresBottleSpecificallyOffhand() {
    var c = ProfileCatalog.preset(ProfileCatalog.RAID).left.conditions;
    assertEquals(
        "",
        Rules.blocked(
            c,
            new Rules.View(sword(0), bottle(), Target.ARMOR_STAND, "minecraft:armor_stand", true)));
    assertTrue(
        Rules.blocked(
                c,
                new Rules.View(
                    sword(0), empty(), Target.ARMOR_STAND, "minecraft:armor_stand", true))
            .contains("Offhand"));
    assertFalse(Rules.itemMatches(c.offItems, sword(0)));
  }

  @Test
  void durabilityUsesStrictExactBoundary() {
    assertTrue(Rules.durability(sword(989), 1));
    assertFalse(Rules.durability(sword(990), 1));
    assertFalse(Rules.durability(sword(999), 1));
    assertFalse(Rules.durability(empty(), 1));
    assertTrue(Rules.durability(bottle(), 1));
  }

  @Test
  void invalidTargetAndWrongTypesBlock() {
    var c = ProfileCatalog.preset(ProfileCatalog.SPAWNER).left.conditions;
    assertFalse(
        Rules.blocked(
                c, new Rules.View(sword(0), empty(), Target.LIVING, "minecraft:zombie", false))
            .isEmpty());
    assertFalse(
        Rules.blocked(
                c, new Rules.View(sword(0), empty(), Target.ANY_ENTITY, "minecraft:player", true))
            .isEmpty());
    c.entityTypes = "minecraft:skeleton";
    assertFalse(
        Rules.blocked(c, new Rules.View(sword(0), empty(), Target.LIVING, "minecraft:zombie", true))
            .isEmpty());
  }

  @Test
  void vanillaNamespaceIsCanonicalized() {
    assertTrue(Rules.itemMatches("#swords", sword(0)));
    assertTrue(Rules.itemMatches("ominous_bottle", bottle()));
    assertEquals(java.util.List.of("minecraft:zombie"), Rules.tokens(" zombie "));
  }

  @Test
  void rightRulesAreIndependent() {
    Profile p = ProfileCatalog.preset(ProfileCatalog.RAID);
    p.right.conditions.checkDurability = false;
    p.right.conditions.target = Target.NONE;
    assertTrue(p.left.conditions.checkDurability);
    assertEquals(Target.ARMOR_STAND, p.left.conditions.target);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 49, 50, 625, 5000, 5001, Integer.MAX_VALUE})
  void exactIntervalsAccepted(int ms) {
    assertEquals(ms, ActionTimer.parseInterval(Integer.toString(ms)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "2147483648", "x", "1.5", ""})
  void invalidIntervalsRejected(String value) {
    assertThrows(IllegalArgumentException.class, () -> ActionTimer.parseInterval(value));
  }

  @Test
  void timerNoDebtNoSameFrameAndManualRebase() {
    ActionTimer t = new ActionTimer();
    t.rearm(0, 625);
    assertFalse(t.ready(624_999_999L, 1));
    assertTrue(t.ready(625_000_000L, 1));
    t.fired(625_000_000L, 1, 625);
    assertFalse(t.ready(99_000_000_000L, 1));
    assertTrue(t.ready(99_000_000_000L, 2));
    t.fired(99_000_000_000L, 2, 625);
    assertFalse(t.ready(99_000_000_001L, 3));
    t.rearm(100_000_000_000L, 625);
    assertFalse(t.ready(100_624_999_999L, 4));
  }

  @Test
  void monotonicTimerWorksWithNegativeOriginAndWraparound() {
    ActionTimer t = new ActionTimer();
    assertTrue(t.ready(-900_000_000L, 1));
    t.rearm(-900_000_000L, 625);
    assertFalse(t.ready(-276_000_000L, 1));
    assertTrue(t.ready(-275_000_000L, 1));
    long origin = Long.MAX_VALUE - 100_000_000L;
    t.rearm(origin, 625);
    assertFalse(t.ready(origin + 624_999_999L, 2));
    assertTrue(t.ready(origin + 625_000_000L, 2));
  }

  @Test
  void standaloneSurvivesProfileAndClickerTransitions() {
    Activation a = new Activation();
    a.autoClicker = true;
    assertTrue(a.effectiveEat(true));
    assertFalse(a.effectiveEat(false));
    a.standaloneEat = true;
    a.autoClicker = false;
    assertTrue(a.effectiveEat(false));
    a.autoClicker = true;
    a.standaloneEat = false;
    assertTrue(a.effectiveEat(true));
    assertFalse(a.effectiveEat(false));
    a.clear();
    assertFalse(a.effectiveEat(true));
  }

  @Test
  void triggerModesAndThresholdBoundary() {
    Eating e = new Eating();
    assertTrue(Rules.hungry(e, 16, 20));
    assertFalse(Rules.hungry(e, 17, 5));
    e.trigger = Trigger.EITHER;
    assertTrue(Rules.hungry(e, 20, 10));
    e.trigger = Trigger.BOTH;
    assertFalse(Rules.hungry(e, 20, 10));
    assertTrue(Rules.hungry(e, 16, 10));
    e.trigger = Trigger.HEALTH;
    assertTrue(Rules.hungry(e, 20, 10));
  }

  @Test
  void slotRestorationHonorsManualSelectionAndOffhand() {
    SlotLease l = new SlotLease();
    l.begin(2, 5);
    assertEquals(2, l.stop(5).orElseThrow());
    assertTrue(l.stop(5).isEmpty());
    l.begin(2, 5);
    assertTrue(l.manualChange(7));
    assertTrue(l.stop(7).isEmpty());
    l.begin(2, 40);
    assertFalse(l.manualChange(2));
    assertTrue(l.stop(2).isEmpty());
    l.begin(2, 5);
    l.abandon();
    assertTrue(l.stop(5).isEmpty());
  }
}
