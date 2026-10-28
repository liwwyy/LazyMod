// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.config;

import static dev.liwwyy.lazymod.config.Settings.*;

import java.util.*;

/** Presets are freshly constructed values, never shared mutable user configuration. */
public final class ProfileCatalog {
  public static final String RAID = "builtin:raidfarm", SPAWNER = "builtin:spawner";

  private ProfileCatalog() {}

  public static boolean builtin(String id) {
    return RAID.equals(id) || SPAWNER.equals(id);
  }

  public static Profile preset(String id) {
    if (!builtin(id)) throw new IllegalArgumentException("Unknown preset: " + id);
    Profile p = new Profile();
    p.id = id;
    p.sourcePreset = id;
    p.name = RAID.equals(id) ? "Raidfarm" : "Spawner";
    p.integrateAutoEat = true;
    p.left.vanillaCooldown = true;
    if (RAID.equals(id)) {
      p.left.conditions.mainItems = "#minecraft:swords";
      p.left.conditions.offItems = "minecraft:ominous_bottle";
      p.left.conditions.target = Target.ARMOR_STAND;
      p.right.conditions = ConfigStore.copy(p.left.conditions, Conditions.class);
      p.right.mode = Mode.HOLD;
      p.right.use = Use.ITEM_ONLY;
      p.right.useHand = Hand.OFF;
    }
    return p;
  }

  public static List<Profile> all(Settings s) {
    List<Profile> list = new ArrayList<>(List.of(preset(RAID), preset(SPAWNER)));
    list.addAll(s.profiles);
    return list;
  }

  public static Profile selected(Settings s) {
    return all(s).stream()
        .filter(p -> p.id.equals(s.selectedProfile))
        .findFirst()
        .orElseGet(() -> preset(RAID));
  }

  public static Profile named(Settings s, String name) {
    return all(s).stream()
        .filter(p -> p.name.equalsIgnoreCase(name.strip()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown profile: " + name));
  }

  public static String validateName(Settings s, String name, String exceptId) {
    String value = name.strip();
    if (value.isEmpty() || value.length() > 64 || value.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException(
          "Use a name of 1–64 characters without control characters.");
    if (all(s).stream()
        .anyMatch(p -> !Objects.equals(p.id, exceptId) && p.name.equalsIgnoreCase(value)))
      throw new IllegalArgumentException("A profile already uses that name.");
    return value;
  }

  public static Profile create(Settings s, String name) {
    Profile p = new Profile();
    p.name = validateName(s, name, null);
    p.id = UUID.randomUUID().toString();
    s.profiles.add(p);
    s.selectedProfile = p.id;
    return p;
  }

  public static Profile duplicate(Settings s, Profile source, String name) {
    String valid = validateName(s, name, null);
    Profile p = ConfigStore.copy(source, Profile.class);
    p.id = UUID.randomUUID().toString();
    p.name = valid;
    s.profiles.add(p);
    s.selectedProfile = p.id;
    return p;
  }

  public static Profile editable(Settings s) {
    Profile p = selected(s);
    if (!builtin(p.id)) return p;
    String stem = p.name + " (edited)", name = stem;
    int i = 2;
    while (hasName(s, name)) name = stem + " " + i++;
    return duplicate(s, p, name);
  }

  private static boolean hasName(Settings s, String name) {
    return all(s).stream().anyMatch(p -> p.name.equalsIgnoreCase(name));
  }

  public static void rename(Settings s, Profile p, String name) {
    if (builtin(p.id))
      throw new IllegalArgumentException("Duplicate a built-in profile to rename it.");
    p.name = validateName(s, name, p.id);
  }

  public static void reset(Profile p) {
    if (builtin(p.id)) return;
    Profile defaults = builtin(p.sourcePreset) ? preset(p.sourcePreset) : new Profile();
    p.left = defaults.left;
    p.right = defaults.right;
    p.integrateAutoEat = defaults.integrateAutoEat;
  }

  public static void delete(Settings s, String id) {
    if (builtin(id)) throw new IllegalArgumentException("Built-in profiles cannot be deleted.");
    Profile p = s.profiles.stream().filter(v -> v.id.equals(id)).findFirst().orElseThrow();
    s.profiles.remove(p);
    if (s.selectedProfile.equals(id))
      s.selectedProfile = builtin(p.sourcePreset) ? p.sourcePreset : RAID;
  }
}
