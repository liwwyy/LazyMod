// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.config;

import java.util.ArrayList;
import java.util.List;

/** Persistence model. No game, loader or presentation dependencies. */
public final class Settings {
  public static final int SCHEMA = 1;
  public int schemaVersion = SCHEMA;
  public boolean allowInScreens = false;
  public String selectedProfile = "builtin:raidfarm";
  public List<Profile> profiles = new ArrayList<>();
  public Eating autoEat = new Eating();

  public enum Mode {
    DISABLED,
    REPEAT,
    HOLD
  }

  public enum Target {
    LIVING,
    ANY_ENTITY,
    ARMOR_STAND,
    BLOCK,
    NONE
  }

  public enum Use {
    VANILLA,
    ITEM_ONLY
  }

  public enum Hand {
    MAIN,
    OFF
  }

  public enum Trigger {
    HUNGER,
    HEALTH,
    EITHER,
    BOTH
  }

  public enum FoodPriority {
    SATURATION,
    NUTRITION
  }

  public static final class Profile {
    public String id;
    public String name;
    public String sourcePreset;
    public boolean integrateAutoEat;
    public int targetGraceMs;
    public Button left = new Button();
    public Button right = new Button();

    public Profile() {
      right.mode = Mode.DISABLED;
    }
  }

  public static final class Button {
    public Mode mode = Mode.REPEAT;
    public int intervalMs = 625;
    public boolean vanillaCooldown;
    public Use use = Use.VANILLA;
    public Hand useHand = Hand.MAIN;
    public Conditions conditions = new Conditions();
  }

  public static final class Conditions {
    /** Comma-separated item identifiers or #tag identifiers; empty means unrestricted. */
    public String mainItems = "";

    public String offItems = "";
    public Target target = Target.LIVING;

    /** Optional comma-separated entity type identifiers. */
    public String entityTypes = "";

    public boolean checkDurability = true;
    public Hand durabilityHand = Hand.MAIN;
    public int minimumDurabilityPercent = 1;
  }

  public static final class Eating {
    public Trigger trigger = Trigger.HUNGER;
    public int hunger = 16;
    public int health = 10;
    public FoodPriority priority = FoodPriority.SATURATION;
    public String blacklist =
        "minecraft:enchanted_golden_apple,minecraft:golden_apple,minecraft:chorus_fruit,minecraft:poisonous_potato,minecraft:pufferfish,minecraft:chicken,minecraft:rotten_flesh,minecraft:spider_eye,minecraft:suspicious_stew";
  }
}
