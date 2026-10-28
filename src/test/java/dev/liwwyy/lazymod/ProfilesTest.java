// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod;

import static org.junit.jupiter.api.Assertions.*;

import dev.liwwyy.lazymod.config.*;
import dev.liwwyy.lazymod.config.Settings.*;
import org.junit.jupiter.api.Test;

class ProfilesTest {
  @Test
  void presetEditingCreatesIndependentCopy() {
    Settings s = new Settings();
    Profile p = ProfileCatalog.editable(s);
    p.left.intervalMs = 9000;
    assertFalse(ProfileCatalog.builtin(p.id));
    assertEquals(p.id, s.selectedProfile);
    assertEquals(625, ProfileCatalog.preset(ProfileCatalog.RAID).left.intervalMs);
    assertEquals(ProfileCatalog.RAID, p.sourcePreset);
    assertSame(p, ProfileCatalog.editable(s));
    assertEquals(1, s.profiles.size());
  }

  @Test
  void presetsAreFreshAndHandsAreIndependent() {
    Profile p = ProfileCatalog.preset(ProfileCatalog.RAID);
    assertEquals("minecraft:ominous_bottle", p.left.conditions.offItems);
    assertEquals(Hand.OFF, p.right.useHand);
    assertEquals(Use.ITEM_ONLY, p.right.use);
    p.right.conditions.offItems = "";
    assertFalse(p.left.conditions.offItems.isEmpty());
    assertFalse(ProfileCatalog.preset(ProfileCatalog.RAID).right.conditions.offItems.isEmpty());
  }

  @Test
  void bothPresetsIntegrateEat() {
    for (String id : new String[] {ProfileCatalog.RAID, ProfileCatalog.SPAWNER})
      assertTrue(ProfileCatalog.preset(id).integrateAutoEat);
  }

  @Test
  void newCustomUsesCommonDefaults() {
    Profile p = ProfileCatalog.create(new Settings(), "My Farm");
    assertFalse(p.integrateAutoEat);
    assertEquals(Mode.DISABLED, p.right.mode);
    assertEquals(625, p.left.intervalMs);
  }

  @Test
  void resetDerivedRestoresPresetAndRetainsIdentity() {
    Settings s = new Settings();
    Profile p = ProfileCatalog.editable(s);
    String id = p.id, name = p.name;
    p.left.intervalMs = 7;
    p.integrateAutoEat = false;
    ProfileCatalog.reset(p);
    assertEquals(id, p.id);
    assertEquals(name, p.name);
    assertEquals(625, p.left.intervalMs);
    assertTrue(p.integrateAutoEat);
  }

  @Test
  void resetOrdinaryDuplicateUsesCommonDefaults() {
    Settings s = new Settings();
    Profile p = ProfileCatalog.create(s, "A");
    p.left.intervalMs = 9000;
    Profile q = ProfileCatalog.duplicate(s, p, "B");
    assertEquals(9000, q.left.intervalMs);
    ProfileCatalog.reset(q);
    assertEquals(625, q.left.intervalMs);
    assertFalse(q.integrateAutoEat);
    assertEquals(9000, p.left.intervalMs);
  }

  @Test
  void duplicateKeepsPresetResetSource() {
    Settings s = new Settings();
    Profile p = ProfileCatalog.duplicate(s, ProfileCatalog.preset(ProfileCatalog.SPAWNER), "Copy");
    p.left.intervalMs = 8;
    Profile q = ProfileCatalog.duplicate(s, p, "Copy again");
    ProfileCatalog.reset(q);
    assertEquals(ProfileCatalog.SPAWNER, q.sourcePreset);
    assertTrue(q.left.vanillaCooldown);
  }

  @Test
  void builtinProtectionAndCaseInsensitiveNames() {
    Settings s = new Settings();
    Profile builtin = ProfileCatalog.preset(ProfileCatalog.RAID);
    assertThrows(IllegalArgumentException.class, () -> ProfileCatalog.rename(s, builtin, "X"));
    assertThrows(IllegalArgumentException.class, () -> ProfileCatalog.delete(s, builtin.id));
    assertThrows(IllegalArgumentException.class, () -> ProfileCatalog.create(s, "raidFARM"));
    Profile p = ProfileCatalog.create(s, "My Farm");
    assertSame(p, ProfileCatalog.named(s, "my farm"));
    assertThrows(IllegalArgumentException.class, () -> ProfileCatalog.create(s, " MY FARM "));
  }

  @Test
  void invalidNamesRejected() {
    for (String name : new String[] {" ", "x\ny", "x".repeat(65)})
      assertThrows(
          IllegalArgumentException.class, () -> ProfileCatalog.create(new Settings(), name));
  }

  @Test
  void deleteSelectionFallsBackPredictably() {
    Settings s = new Settings();
    s.selectedProfile = ProfileCatalog.SPAWNER;
    Profile p = ProfileCatalog.editable(s);
    ProfileCatalog.delete(s, p.id);
    assertEquals(ProfileCatalog.SPAWNER, s.selectedProfile);
    Profile custom = ProfileCatalog.create(s, "Independent");
    ProfileCatalog.delete(s, custom.id);
    assertEquals(ProfileCatalog.RAID, s.selectedProfile);
  }
}
