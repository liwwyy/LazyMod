// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.liwwyy.lazymod.config.*;
import dev.liwwyy.lazymod.engine.ProfileNames;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;

public final class LazyCommands {
  private static CommandDispatcher<FabricClientCommandSource> dispatcher;
  private static LazyModClient mod;

  private LazyCommands() {}

  public static void register(LazyModClient instance) {
    mod = instance;
    ClientCommandRegistrationCallback.EVENT.register(
        (d, context) -> {
          dispatcher = d;
          var ac =
              literal("ac")
                  .executes(c -> help())
                  .then(
                      literal("start")
                          .executes(c -> usage("/lazy ac start <profile name>"))
                          .then(
                              profileArgument()
                                  .executes(
                                      c -> select(StringArgumentType.getString(c, "name"), true))))
                  .then(
                      literal("stop")
                          .executes(
                              c -> {
                                mod.automation.setClicker(false);
                                return 1;
                              }))
                  .then(
                      literal("toggle")
                          .executes(
                              c -> {
                                mod.automation.setClicker(!mod.automation.activation.autoClicker);
                                return 1;
                              }))
                  .then(
                      literal("status")
                          .executes(
                              c -> {
                                mod.message(mod.automation.clickerStatus());
                                return 1;
                              }))
                  .then(
                      literal("profiles")
                          .executes(
                              c -> {
                                ProfileCatalog.all(mod.settings)
                                    .forEach(
                                        p ->
                                            mod.message(
                                                (p.id.equals(mod.settings.selectedProfile)
                                                        ? "* "
                                                        : "  ")
                                                    + p.name
                                                    + (ProfileCatalog.builtin(p.id)
                                                        ? " (built-in)"
                                                        : "")));
                                return 1;
                              }))
                  .then(
                      literal("profile")
                          .executes(c -> usage("/lazy ac profile <profile name>"))
                          .then(
                              profileArgument()
                                  .executes(
                                      c ->
                                          select(StringArgumentType.getString(c, "name"), false))));
          var ae =
              literal("ae")
                  .executes(c -> help())
                  .then(
                      literal("start")
                          .executes(
                              c -> {
                                mod.automation.setStandaloneEat(true);
                                return 1;
                              }))
                  .then(
                      literal("stop")
                          .executes(
                              c -> {
                                mod.automation.setStandaloneEat(false);
                                return 1;
                              }))
                  .then(
                      literal("toggle")
                          .executes(
                              c -> {
                                mod.automation.setStandaloneEat(
                                    !mod.automation.activation.standaloneEat);
                                return 1;
                              }))
                  .then(
                      literal("status")
                          .executes(
                              c -> {
                                mod.message(mod.automation.eatStatus());
                                return 1;
                              }));
          d.register(
              literal("lazy")
                  .executes(c -> open())
                  .then(literal("config").executes(c -> open()))
                  .then(literal("help").executes(c -> help()))
                  .then(ac)
                  .then(ae));
        });
    // Consume the exact root before Fabric's permissive parse-error/server fallback.
    ClientSendMessageEvents.ALLOW_COMMAND.register(
        command -> {
          if (!ProfileNames.isLazyCommand(command)) return true;
          executeLocal(command);
          return false;
        });
  }

  private static com.mojang.brigadier.builder.RequiredArgumentBuilder<
          FabricClientCommandSource, String>
      profileArgument() {
    return argument("name", StringArgumentType.greedyString())
        .suggests(
            (context, builder) -> {
              String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
              for (var p : ProfileCatalog.all(mod.settings)) {
                String quoted = ProfileNames.quote(p.name);
                if (p.name.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)
                    || quoted.toLowerCase(java.util.Locale.ROOT).startsWith(remaining))
                  builder.suggest(quoted);
              }
              return builder.buildFuture();
            });
  }

  public static void executeLocal(String command) {
    try {
      var connection = Minecraft.getInstance().getConnection();
      if (dispatcher == null || connection == null) {
        mod.message("Commands require an active world.");
        return;
      }
      dispatcher.execute(
          command.stripLeading(), (FabricClientCommandSource) connection.getSuggestionsProvider());
    } catch (Exception e) {
      mod.message("Command error: " + e.getMessage() + ". Use /lazy help.");
    }
  }

  private static int select(String input, boolean start) {
    try {
      var p = ProfileCatalog.named(mod.settings, ProfileNames.parse(input));
      Settings draft = ConfigStore.copy(mod.settings, Settings.class);
      draft.selectedProfile = p.id;
      if (!mod.apply(draft)) return 0;
      if (start) mod.automation.setClicker(true);
      else
        mod.message(
            "Selected "
                + p.name
                + "; AutoClicker "
                + (mod.automation.activation.autoClicker ? "enabled" : "disabled"));
      return 1;
    } catch (IllegalArgumentException e) {
      mod.message(e.getMessage());
      return 0;
    }
  }

  private static int usage(String usage) {
    mod.message("Usage: " + usage);
    return 0;
  }

  private static int open() {
    mod.openConfig();
    return 1;
  }

  private static int help() {
    mod.message("/lazy [config|help] — configuration or help");
    mod.message("/lazy ac start <name> | stop | toggle | status | profiles | profile <name>");
    mod.message(
        "/lazy ae start | stop | toggle | status — standalone request; profile integration is"
            + " independent");
    mod.message("Names with spaces may be quoted, e.g. /lazy ac start \"My Farm\".");
    return 1;
  }
}
