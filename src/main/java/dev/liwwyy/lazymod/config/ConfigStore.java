// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.config;

import static dev.liwwyy.lazymod.config.Settings.*;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ConfigStore {
  private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
  private final Path file;
  private boolean readOnly;
  private final List<String> notices = new ArrayList<>();

  public ConfigStore(Path file) {
    this.file = file;
  }

  public static <T> T copy(T value, Class<T> type) {
    return JSON.fromJson(JSON.toJson(value), type);
  }

  public static boolean same(Object a, Object b) {
    return JSON.toJson(a).equals(JSON.toJson(b));
  }

  public boolean readOnly() {
    return readOnly;
  }

  public List<String> notices() {
    return List.copyOf(notices);
  }

  public Settings load() {
    if (!Files.exists(file)) return new Settings();
    try {
      JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
      int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
      if (schema > SCHEMA) {
        readOnly = true;
        notices.add("Configuration is from a newer LazyMod; automation and saving are disabled.");
        return new Settings();
      }
      Settings s = JSON.fromJson(root, Settings.class);
      List<String> repairs = sanitize(s);
      if (schema < SCHEMA || !repairs.isEmpty()) {
        Files.copy(file, backup("recovery"));
        notices.add(
            "Preserved original configuration before repairing: " + String.join("; ", repairs));
      }
      return s;
    } catch (Exception e) {
      notices.add(
          "Invalid configuration: " + e.getClass().getSimpleName() + "; original preserved.");
      try {
        Files.copy(file, backup("invalid"));
      } catch (IOException failure) {
        readOnly = true;
        notices.add("Cannot back up configuration; saving disabled.");
      }
      Path lastGood = file.resolveSibling(file.getFileName() + ".bak");
      if (!readOnly && Files.exists(lastGood)) {
        try {
          Settings recovered = JSON.fromJson(Files.readString(lastGood), Settings.class);
          if (recovered != null && recovered.schemaVersion <= SCHEMA) {
            sanitize(recovered);
            notices.add("Recovered settings from last-known-good backup.");
            return recovered;
          }
        } catch (Exception ignored) {
          notices.add("Backup is also invalid; using safe defaults.");
        }
      }
      return new Settings();
    }
  }

  private Path backup(String kind) {
    return file.resolveSibling(file.getFileName() + "." + kind + "-" + UUID.randomUUID() + ".bak");
  }

  public void save(Settings s) throws IOException {
    if (readOnly)
      throw new IOException("Saving disabled to protect unsupported or unbacked-up configuration.");
    // Callers must validate before committing; do not silently repair UI input here.
    Settings check = copy(s, Settings.class);
    if (!sanitize(check).isEmpty()) throw new IOException("Configuration failed validation.");
    Files.createDirectories(file.getParent());
    Path tmp = Files.createTempFile(file.getParent(), "lazymod-", ".tmp");
    try {
      Files.writeString(tmp, JSON.toJson(s));
      if (Files.exists(file)) {
        try {
          Settings old = JSON.fromJson(Files.readString(file), Settings.class);
          if (old != null && old.schemaVersion <= SCHEMA && sanitize(old).isEmpty())
            Files.copy(
                file,
                file.resolveSibling(file.getFileName() + ".bak"),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (JsonParseException | IllegalStateException ignored) {
          /* Preserve the existing good backup. */
        }
      }
      try {
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  public static List<String> sanitize(Settings s) {
    List<String> issues = new ArrayList<>();
    s.schemaVersion = SCHEMA;
    if (s.profiles == null) {
      s.profiles = new ArrayList<>();
      issues.add("missing profiles");
    }
    Set<String> ids = new HashSet<>(List.of(ProfileCatalog.RAID, ProfileCatalog.SPAWNER));
    Set<String> names = new HashSet<>(List.of("raidfarm", "spawner"));
    Iterator<Profile> it = s.profiles.iterator();
    while (it.hasNext()) {
      Profile p = it.next();
      if (p == null
          || p.id == null
          || p.id.isBlank()
          || p.name == null
          || p.name.isBlank()
          || p.name.length() > 64
          || p.name.chars().anyMatch(Character::isISOControl)
          || !ids.add(p.id)
          || !names.add(p.name.strip().toLowerCase(Locale.ROOT))) {
        it.remove();
        issues.add("invalid/duplicate profile removed");
        continue;
      }
      p.name = p.name.strip();
      if (p.left == null) {
        p.left = new Button();
        issues.add("missing left settings");
      }
      if (p.right == null) {
        p.right = new Button();
        p.right.mode = Mode.DISABLED;
        issues.add("missing right settings");
      }
      if (p.targetGraceMs < 0 || p.targetGraceMs > 10000) {
        p.targetGraceMs = 0;
        issues.add("invalid target grace");
      }
      repair(p.left, true, issues);
      repair(p.right, false, issues);
    }
    if (s.selectedProfile == null
        || ProfileCatalog.all(s).stream().noneMatch(p -> p.id.equals(s.selectedProfile))) {
      s.selectedProfile = ProfileCatalog.RAID;
      issues.add("invalid selection");
    }
    if (s.autoEat == null) {
      s.autoEat = new Eating();
      issues.add("missing AutoEat settings");
    }
    Eating e = s.autoEat;
    if (e.trigger == null) {
      e.trigger = Trigger.HUNGER;
      issues.add("invalid trigger");
    }
    if (e.priority == null) {
      e.priority = FoodPriority.SATURATION;
      issues.add("invalid food priority");
    }
    if (e.blacklist == null) {
      e.blacklist = new Eating().blacklist;
      issues.add("invalid blacklist");
    }
    if (e.hunger < 0 || e.hunger > 19) {
      e.hunger = 16;
      issues.add("invalid hunger");
    }
    if (e.health < 1 || e.health > 20) {
      e.health = 10;
      issues.add("invalid health");
    }
    return issues;
  }

  private static void repair(Button b, boolean left, List<String> issues) {
    if (b.mode == null || (left && b.mode == Mode.HOLD)) {
      b.mode = Mode.DISABLED;
      issues.add("invalid action mode");
    }
    if (b.intervalMs < 1) {
      b.intervalMs = 625;
      issues.add("invalid interval");
    }
    if (b.use == null) {
      b.use = Use.VANILLA;
      issues.add("invalid use mode");
    }
    if (b.useHand == null) {
      b.useHand = Hand.MAIN;
      issues.add("invalid hand");
    }
    if (b.conditions == null) {
      b.conditions = new Conditions();
      issues.add("missing conditions");
    }
    Conditions c = b.conditions;
    if (c.target == null) {
      c.target = Target.LIVING;
      issues.add("invalid target");
    }
    if (c.mainItems == null) {
      c.mainItems = "";
      b.mode = Mode.DISABLED;
      issues.add("invalid main items");
    }
    if (c.offItems == null) {
      c.offItems = "";
      b.mode = Mode.DISABLED;
      issues.add("invalid offhand items");
    }
    if (c.entityTypes == null) {
      c.entityTypes = "";
      b.mode = Mode.DISABLED;
      issues.add("invalid entity types");
    }
    if (c.durabilityHand == null) {
      c.durabilityHand = Hand.MAIN;
      issues.add("invalid durability hand");
    }
    if (c.minimumDurabilityPercent < 0 || c.minimumDurabilityPercent > 100) {
      c.minimumDurabilityPercent = 1;
      issues.add("invalid durability threshold");
    }
  }
}
