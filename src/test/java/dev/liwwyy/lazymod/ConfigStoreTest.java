// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod;

import static org.junit.jupiter.api.Assertions.*;

import dev.liwwyy.lazymod.config.*;
import dev.liwwyy.lazymod.config.Settings.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {
  @TempDir Path dir;

  Path file() {
    return dir.resolve("config/lazymod.json");
  }

  @Test
  void roundTripKeepsProfileIdentityAndLargeInterval() throws Exception {
    Settings s = new Settings();
    Profile p = ProfileCatalog.create(s, "Farm with spaces");
    p.left.intervalMs = Integer.MAX_VALUE;
    p.right.intervalMs = 5;
    s.allowInScreens = true;
    new ConfigStore(file()).save(s);
    Settings loaded = new ConfigStore(file()).load();
    assertTrue(ConfigStore.same(s, loaded));
    assertEquals(p.id, loaded.selectedProfile);
  }

  @Test
  void corruptFilePreservedAndDefaultsSafe() throws Exception {
    Files.createDirectories(file().getParent());
    Files.writeString(file(), "{broken");
    ConfigStore store = new ConfigStore(file());
    assertEquals(ProfileCatalog.RAID, store.load().selectedProfile);
    assertFalse(store.notices().isEmpty());
    assertEquals("{broken", Files.readString(file()));
    try (var files = Files.list(file().getParent())) {
      assertEquals(1, files.filter(p -> p.toString().contains("invalid-")).count());
    }
  }

  @Test
  void futureVersionProtected() throws Exception {
    Files.createDirectories(file().getParent());
    String content = "{\"schemaVersion\":99}";
    Files.writeString(file(), content);
    ConfigStore store = new ConfigStore(file());
    Settings s = store.load();
    assertTrue(store.readOnly());
    assertThrows(java.io.IOException.class, () -> store.save(s));
    assertEquals(content, Files.readString(file()));
  }

  @Test
  void missingSchemaMigratesWithBackup() throws Exception {
    Files.createDirectories(file().getParent());
    Files.writeString(file(), "{}");
    ConfigStore store = new ConfigStore(file());
    assertEquals(Settings.SCHEMA, store.load().schemaVersion);
    assertFalse(store.notices().isEmpty());
  }

  @Test
  void invalidIndividualSettingsAreRepaired() {
    Settings s = new Settings();
    Profile p = ProfileCatalog.create(s, "A");
    p.left.intervalMs = -1;
    p.left.mode = null;
    p.right.conditions.minimumDurabilityPercent = 101;
    s.autoEat.hunger = 40;
    assertFalse(ConfigStore.sanitize(s).isEmpty());
    assertEquals(625, p.left.intervalMs);
    assertEquals(Mode.DISABLED, p.left.mode);
    assertEquals(1, p.right.conditions.minimumDurabilityPercent);
    assertEquals(16, s.autoEat.hunger);
  }

  @Test
  void unknownItemIdentifiersArePreserved() {
    Settings s = new Settings();
    Profile p = ProfileCatalog.create(s, "A");
    p.left.conditions.mainItems = "missing:tool";
    assertTrue(ConfigStore.sanitize(s).isEmpty());
    assertEquals("missing:tool", p.left.conditions.mainItems);
  }

  @Test
  void invalidSelectedDuplicateFallsBack() {
    Settings s = new Settings();
    Profile p = ProfileCatalog.create(s, "A");
    p.name = "Raidfarm";
    ConfigStore.sanitize(s);
    assertTrue(s.profiles.isEmpty());
    assertEquals(ProfileCatalog.RAID, s.selectedProfile);
  }

  @Test
  void invalidSaveDoesNotOverwriteGoodFile() throws Exception {
    ConfigStore store = new ConfigStore(file());
    Settings s = new Settings();
    store.save(s);
    String old = Files.readString(file());
    ProfileCatalog.create(s, "A").left.intervalMs = 0;
    assertThrows(java.io.IOException.class, () -> store.save(s));
    assertEquals(old, Files.readString(file()));
  }

  @Test
  void corruptPrimaryRecoversLastGoodBackupAndPreservesIt() throws Exception {
    ConfigStore store = new ConfigStore(file());
    Settings s = new Settings();
    ProfileCatalog.create(s, "Keep me");
    store.save(s);
    s.allowInScreens = true;
    store.save(s);
    Path backup = file().resolveSibling("lazymod.json.bak");
    String lastGood = Files.readString(backup);
    Files.writeString(file(), "broken");
    ConfigStore recovery = new ConfigStore(file());
    Settings recovered = recovery.load();
    assertEquals("Keep me", recovered.profiles.getFirst().name);
    assertFalse(recovered.allowInScreens);
    recovery.save(recovered);
    assertEquals(lastGood, Files.readString(backup));
  }
}
