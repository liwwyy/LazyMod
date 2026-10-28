// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod;

import com.mojang.blaze3d.platform.InputConstants;
import dev.liwwyy.lazymod.config.*;
import dev.liwwyy.lazymod.runtime.Automation;
import dev.liwwyy.lazymod.ui.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LazyModClient implements ClientModInitializer {
  public static LazyModClient INSTANCE;
  public static final Logger LOG = LoggerFactory.getLogger("lazymod");
  public ConfigStore store;
  public Settings settings;
  public Automation automation;
  public KeyMapping acKey, aeKey;
  private boolean openRequested, noticesShown, conflictShown;
  private boolean acHeld, aeHeld;

  @Override
  public void onInitializeClient() {
    INSTANCE = this;
    store = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("lazymod.json"));
    settings = store.load();
    store.notices().forEach(LOG::warn);
    automation = new Automation(this);
    KeyMapping.Category category =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lazymod", "modules"));
    acKey =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.lazymod.autoclicker", InputConstants.Type.KEYSYM, -1, category));
    aeKey =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.lazymod.autoeat", InputConstants.Type.KEYSYM, -1, category));
    LazyCommands.register(this);
    ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, mc) -> {
          automation.disconnect();
          noticesShown = false;
        });
    LOG.info("LazyMod initialized for Minecraft 26.2 (client only)");
  }

  private void tick(Minecraft mc) {
    if (mc.player != null && !noticesShown) {
      noticesShown = true;
      store.notices().forEach(this::message);
    }
    boolean acPress = false, aePress = false;
    while (acKey.consumeClick()) acPress = true;
    while (aeKey.consumeClick()) aePress = true;
    boolean acDown = acKey.isDown(), aeDown = aeKey.isDown();
    if (mc.gui.screen() == null && mc.player != null) {
      boolean conflict = !acKey.isUnbound() && acKey.saveString().equals(aeKey.saveString());
      if (conflict) {
        if (!conflictShown)
          message(
              "Both LazyMod keybinds use the same input. Change one; ambiguous toggles are"
                  + " ignored.");
        conflictShown = true;
      } else {
        conflictShown = false;
        if (acPress && !acHeld) automation.setClicker(!automation.activation.autoClicker);
        if (aePress && !aeHeld) automation.setStandaloneEat(!automation.activation.standaloneEat);
      }
    }
    acHeld = acDown;
    aeHeld = aeDown;
    if (openRequested) {
      openRequested = false;
      mc.gui.setScreen(ConfigScreen.create(this, mc.gui.screen()));
    }
    automation.tick();
  }

  public void openConfig() {
    openRequested = true;
  }

  public void message(String text) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null)
      mc.player.sendSystemMessage(
          Component.literal("❯ ")
              .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
              .append(
                  Component.literal("LazyMod")
                      .withStyle(
                          net.minecraft.ChatFormatting.LIGHT_PURPLE,
                          net.minecraft.ChatFormatting.BOLD))
              .append(Component.literal(" │ ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
              .append(
                  Component.literal(text)
                      .withStyle(
                          text.toLowerCase(java.util.Locale.ROOT).contains("error")
                                  || text.contains("Could not")
                              ? net.minecraft.ChatFormatting.RED
                              : net.minecraft.ChatFormatting.AQUA)));
    else LOG.info(text);
  }

  public boolean apply(Settings draft) {
    try {
      store.save(draft);
      automation.configurationChanged();
      settings = ConfigStore.copy(draft, Settings.class);
      automation.configurationChanged();
      return true;
    } catch (Exception e) {
      message("Could not save configuration: " + e.getMessage());
      LOG.error("Could not save LazyMod configuration", e);
      return false;
    }
  }
}
