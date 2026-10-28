// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import dev.liwwyy.lazymod.LazyModClient;
import dev.liwwyy.lazymod.config.*;
import dev.liwwyy.lazymod.config.Settings.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A whole-screen draft: presentation never owns persistence or preset definitions. */
public final class ConfigScreen {
  private ConfigScreen() {}

  public static Screen create(LazyModClient mod, Screen parent) {
    return new Editor(mod, parent).screen();
  }

  private static Component text(String s) {
    return Component.literal(s);
  }

  private static OptionDescription desc(String s) {
    return OptionDescription.of(text(s));
  }

  private static final class Editor {
    final LazyModClient mod;
    final Screen parent;
    final Settings draft;
    final List<Option<?>> form = new ArrayList<>();
    Profile working;
    InputConstants.Key acKey, aeKey;
    boolean building;

    Editor(LazyModClient mod, Screen parent) {
      this.mod = mod;
      this.parent = parent;
      draft = ConfigStore.copy(mod.settings, Settings.class);
      acKey = InputConstants.getKey(mod.acKey.saveString());
      aeKey = InputConstants.getKey(mod.aeKey.saveString());
    }

    private <T> Option<T> track(Option<T> option) {
      form.add(option);
      return option;
    }

    private Option<Boolean> bool(
        String name,
        String detail,
        boolean defaults,
        Supplier<Boolean> get,
        Consumer<Boolean> set) {
      return track(
          Option.<Boolean>createBuilder()
              .name(text(name))
              .description(desc(detail))
              .binding(defaults, get, set)
              .controller(TickBoxControllerBuilder::create)
              .build());
    }

    private Option<String> string(
        String name, String detail, String defaults, Supplier<String> get, Consumer<String> set) {
      return track(
          Option.<String>createBuilder()
              .name(text(name))
              .description(desc(detail))
              .binding(defaults, get, set)
              .controller(StringControllerBuilder::create)
              .build());
    }

    private Option<Integer> integer(
        String name,
        String detail,
        int defaults,
        int min,
        int max,
        Supplier<Integer> get,
        Consumer<Integer> set) {
      return track(
          Option.<Integer>createBuilder()
              .name(text(name))
              .description(desc(detail))
              .binding(defaults, get, set)
              .controller(o -> IntegerSliderControllerBuilder.create(o).range(min, max).step(1))
              .build());
    }

    private <E extends Enum<E>> Option<E> enumeration(
        String name, String detail, E defaults, Class<E> type, Supplier<E> get, Consumer<E> set) {
      return track(
          Option.<E>createBuilder(type)
              .name(text(name))
              .description(desc(detail))
              .binding(defaults, get, set)
              .controller(o -> EnumControllerBuilder.create(o).enumClass(type))
              .build());
    }

    private ButtonOption button(String name, String label, String detail, Runnable action) {
      return ButtonOption.createBuilder()
          .name(text(name))
          .text(text(label))
          .description(desc(detail))
          .action(screen -> action.run())
          .build();
    }

    private void show() {
      Minecraft.getInstance().gui.setScreen(screen());
    }

    private void capture() {
      form.forEach(Option::applyValue);
      Profile selected = ProfileCatalog.selected(draft);
      if (!ConfigStore.same(selected, working)) {
        Profile target = ProfileCatalog.editable(draft);
        target.left = ConfigStore.copy(working.left, Button.class);
        target.right = ConfigStore.copy(working.right, Button.class);
        target.integrateAutoEat = working.integrateAutoEat;
      }
    }

    Screen screen() {
      building = true;
      form.clear();
      working = ConfigStore.copy(ProfileCatalog.selected(draft), Profile.class);
      Profile defaults =
          ProfileCatalog.builtin(working.sourcePreset)
              ? ProfileCatalog.preset(working.sourcePreset)
              : new Profile();
      var ac = ConfigCategory.createBuilder().name(text("AutoClicker"));
      ac.option(
          LabelOption.create(
              text(
                  "Edits and profile management remain drafts until Save; Cancel discards them.")));
      ac.option(LabelOption.create(text(mod.automation.clickerStatus())));
      ac.option(
          button(
              "AutoClicker (applied profile: " + ProfileCatalog.selected(mod.settings).name + ")",
              mod.automation.activation.autoClicker ? "Stop" : "Start",
              "Runtime action, applied immediately to the saved selection. Save draft profile"
                  + " changes first.",
              () -> {
                capture();
                mod.automation.setClicker(!mod.automation.activation.autoClicker);
                show();
              }));
      var selection =
          Option.<String>createBuilder()
              .name(text("Selected profile"))
              .description(
                  desc(
                      "Selection is applied with Save. Built-in settings edits create a custom"
                          + " copy."))
              .binding(working.name, () -> working.name, value -> {})
              .controller(
                  o ->
                      DropdownStringControllerBuilder.create(o)
                          .values(ProfileCatalog.all(draft).stream().map(p -> p.name).toList())
                          .allowAnyValue(false))
              .listener(
                  (option, name) -> {
                    if (building || name.equals(working.name)) return;
                    capture();
                    draft.selectedProfile = ProfileCatalog.named(draft, name).id;
                    show();
                  })
              .build();
      ac.option(selection);
      ac.option(
          button(
              "New profile",
              "Create",
              "Common safe defaults; independent settings.",
              () -> {
                capture();
                prompt(
                    "New profile",
                    "",
                    name -> {
                      ProfileCatalog.create(draft, name);
                      show();
                    });
              }));
      ac.option(
          button(
              "Duplicate selected profile",
              "Duplicate",
              "Copy current settings into an editable custom profile.",
              () -> {
                capture();
                var source = ProfileCatalog.selected(draft);
                prompt(
                    "Duplicate profile",
                    source.name + " copy",
                    name -> {
                      ProfileCatalog.duplicate(draft, source, name);
                      show();
                    });
              }));
      ac.option(
          ButtonOption.createBuilder()
              .name(text("Rename custom profile"))
              .text(text("Rename"))
              .available(!ProfileCatalog.builtin(working.id))
              .action(
                  screen -> {
                    capture();
                    var source = ProfileCatalog.selected(draft);
                    prompt(
                        "Rename profile",
                        source.name,
                        name -> {
                          ProfileCatalog.rename(draft, source, name);
                          show();
                        });
                  })
              .build());
      ac.option(
          ButtonOption.createBuilder()
              .name(text("Delete custom profile"))
              .text(text("Delete"))
              .description(
                  desc(
                      "Save stops AutoClicker if its active profile was deleted. Standalone AutoEat"
                          + " remains independent."))
              .available(!ProfileCatalog.builtin(working.id))
              .action(
                  screen -> {
                    capture();
                    String id = draft.selectedProfile;
                    Minecraft.getInstance()
                        .gui
                        .setScreen(
                            new net.minecraft.client.gui.screens.ConfirmScreen(
                                yes -> {
                                  if (yes) ProfileCatalog.delete(draft, id);
                                  show();
                                },
                                text("Delete profile?"),
                                text("This only affects the draft until Save.")));
                  })
              .build());
      ac.option(
          ButtonOption.createBuilder()
              .name(text("Reset profile settings"))
              .text(text("Reset"))
              .description(
                  desc(
                      "Preset-derived copies reset to the original preset; other custom profiles"
                          + " reset to new-profile defaults. Names are retained."))
              .available(!ProfileCatalog.builtin(working.id))
              .action(
                  screen -> {
                    capture();
                    Minecraft.getInstance()
                        .gui
                        .setScreen(
                            new net.minecraft.client.gui.screens.ConfirmScreen(
                                yes -> {
                                  if (yes) ProfileCatalog.reset(ProfileCatalog.selected(draft));
                                  show();
                                },
                                text("Reset profile settings?"),
                                text("The reset remains a draft until Save.")));
                  })
              .build());
      ac.option(
          bool(
              "Integrate AutoEat",
              "Request AutoEat while this profile's AutoClicker is enabled. Does not change"
                  + " standalone activation.",
              defaults.integrateAutoEat,
              () -> working.integrateAutoEat,
              v -> working.integrateAutoEat = v));
      ac.option(
          LabelOption.create(
              text(
                  "Raidfarm uses offhand item-only Hold. Drinking suspends attacks; consuming the"
                      + " last offhand bottle stops attacks until replaced.")));
      ac.group(buttonGroup(true, working.left, defaults.left));
      ac.group(buttonGroup(false, working.right, defaults.right));
      var ae = ConfigCategory.createBuilder().name(text("AutoEat"));
      ae.option(LabelOption.create(text(mod.automation.eatStatus())));
      ae.option(
          button(
              "Standalone activation",
              mod.automation.activation.standaloneEat ? "Stop" : "Start",
              "Runtime action, applied immediately. Profile integration remains independent.",
              () -> {
                capture();
                mod.automation.setStandaloneEat(!mod.automation.activation.standaloneEat);
                show();
              }));
      Eating e = draft.autoEat, ed = new Eating();
      ae.option(
          enumeration(
              "Trigger",
              "Health is not an instant heal; food must remain edible under vanilla rules.",
              ed.trigger,
              Trigger.class,
              () -> e.trigger,
              v -> e.trigger = v));
      ae.option(
          integer(
              "Hunger threshold",
              "Start at or below this hunger level; finish the current food item before checking"
                  + " again.",
              16,
              0,
              19,
              () -> e.hunger,
              v -> e.hunger = v));
      ae.option(
          integer(
              "Health threshold",
              "Health points, not hearts. Full hunger may prevent ordinary food use.",
              10,
              1,
              20,
              () -> e.health,
              v -> e.health = v));
      ae.option(
          enumeration(
              "Food priority",
              "Eligible offhand food first, then best hotbar food with stable slot order. No"
                  + " inventory-moving automation.",
              ed.priority,
              FoodPriority.class,
              () -> e.priority,
              v -> e.priority = v));
      ae.option(
          string(
              "Food blacklist",
              "Comma-separated item IDs. Only edible food is considered. Ominous"
                  + " bottles are not food.",
              ed.blacklist,
              () -> e.blacklist,
              v -> e.blacklist = v));
      var general = ConfigCategory.createBuilder().name(text("General & Keys"));
      general.option(
          bool(
              "Allow automation while screens are open",
              "In-world actions only, including inventory/chat/configuration. Never clicks GUI"
                  + " controls. A genuinely paused world still suspends automation.",
              false,
              () -> draft.allowInScreens,
              v -> draft.allowInScreens = v));
      general.option(
          button(
              "AutoClicker activation key",
              acKey.getDisplayName().getString(),
              "Unbound by default. Capture a key/mouse button or Clear. Saved with this screen.",
              () -> captureKey(true)));
      general.option(
          button(
              "Standalone AutoEat activation key",
              aeKey.getDisplayName().getString(),
              "Changes only the standalone request. Saved with this screen.",
              () -> captureKey(false)));
      general.option(
          LabelOption.create(
              text(
                  "Commands work with unbound keys. Activation keys are ignored while screens are"
                      + " open.")));
      if (mod.store.readOnly())
        general.option(
            LabelOption.create(
                text(
                    "Configuration is protected/read-only; saving and automation are disabled. See"
                        + " the log.")));
      // Management actions rebuild YACL: retain a visible pending marker so Save stays available.
      var dirty =
          Option.<Boolean>createBuilder()
              .name(text("Unsaved profile/key changes"))
              .binding(false, () -> false, v -> {})
              .controller(TickBoxControllerBuilder::create)
              .available(false)
              .build();
      if (!ConfigStore.same(draft, mod.settings)
          || !acKey.getName().equals(mod.acKey.saveString())
          || !aeKey.getName().equals(mod.aeKey.saveString())) dirty.requestSet(true);
      general.option(dirty);
      var config =
          YetAnotherConfigLib.createBuilder()
              .title(text("LazyMod"))
              .category(ac.build())
              .category(ae.build())
              .category(general.build())
              .save(() -> {})
              .build();
      var screen =
          new dev.isxander.yacl3.gui.YACLScreen(config, parent) {
            @Override
            public void finishOrSave() {
              capture();
              boolean changed =
                  !ConfigStore.same(draft, mod.settings)
                      || !acKey.getName().equals(mod.acKey.saveString())
                      || !aeKey.getName().equals(mod.aeKey.saveString());
              if (!changed) {
                onClose();
                return;
              }
              boolean activeDeleted =
                  !ProfileCatalog.builtin(mod.settings.selectedProfile)
                      && draft.profiles.stream()
                          .noneMatch(p -> p.id.equals(mod.settings.selectedProfile));
              if (mod.apply(draft)) {
                if (activeDeleted) mod.automation.setClicker(false);
                mod.acKey.setKey(acKey);
                mod.aeKey.setKey(aeKey);
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
                show();
              } else {
                dirty.requestSet(true);
                setSaveButtonMessage(
                    text("Save failed — retry"),
                    text("See LazyMod's chat/log message. Your draft is still available."));
              }
            }
          };
      building = false;
      return screen;
    }

    private OptionGroup buttonGroup(boolean left, Button b, Button defaults) {
      var group = OptionGroup.createBuilder().name(text(left ? "Left attack" : "Right use"));
      if (left) {
        group.option(
            bool(
                "Enable left repeat",
                "Entity attacks only; no air swings or block breaking.",
                defaults.mode != Mode.DISABLED,
                () -> b.mode != Mode.DISABLED,
                v -> b.mode = v ? Mode.REPEAT : Mode.DISABLED));
        group.option(
            bool(
                "Vanilla Cooldown",
                "Wait for actual full attack strength. The custom interval is retained but is not"
                    + " an additional readiness rule.",
                defaults.vanillaCooldown,
                () -> b.vanillaCooldown,
                v -> b.vanillaCooldown = v));
      } else {
        group.option(
            enumeration(
                "Right action",
                "HOLD sustains use and retries at vanilla's four-tick cadence; REPEAT makes"
                    + " separate presses.",
                defaults.mode,
                Mode.class,
                () -> b.mode,
                v -> b.mode = v));
        group.option(
            enumeration(
                "Right interaction",
                "ITEM_ONLY bypasses entity/block interactions and uses the explicit hand. VANILLA"
                    + " tries the target and hands normally.",
                defaults.use,
                Use.class,
                () -> b.use,
                v -> b.use = v));
        group.option(
            enumeration(
                "Item-only use hand",
                "This selects the action hand; item requirements below independently select the"
                    + " required hand.",
                defaults.useHand,
                Hand.class,
                () -> b.useHand,
                v -> b.useHand = v));
      }
      group.option(
          button(
              "Custom interval: " + b.intervalMs + " ms",
              "Edit",
              "50–5000 ms slider plus exact positive integer input. Values outside slider range are"
                  + " retained. Actual dispatch follows client ticks.",
              () -> {
                capture();
                boolean isLeft = left;
                int current =
                    isLeft
                        ? ProfileCatalog.selected(draft).left.intervalMs
                        : ProfileCatalog.selected(draft).right.intervalMs;
                Minecraft.getInstance()
                    .gui
                    .setScreen(
                        new IntervalScreen(
                            current,
                            value -> {
                              if (value != current) {
                                Profile p = ProfileCatalog.editable(draft);
                                if (isLeft) p.left.intervalMs = value;
                                else p.right.intervalMs = value;
                              }
                              show();
                            },
                            this::show));
              }));
      Conditions c = b.conditions, d = defaults.conditions;
      group.option(
          string(
              "Main-hand requirement",
              "Empty = any. Comma-separated item IDs or #tag IDs (OR), e.g. #minecraft:swords. All"
                  + " condition rows must pass.",
              d.mainItems,
              () -> c.mainItems,
              v -> c.mainItems = v));
      group.option(
          string(
              "Offhand requirement",
              "Matches OFFHAND only. minecraft:ominous_bottle accepts every ominous level. Unknown"
                  + " IDs block automation.",
              d.offItems,
              () -> c.offItems,
              v -> c.offItems = v));
      group.option(
          enumeration(
              "Crosshair target",
              "LIVING excludes players and armor stands. ANY_ENTITY includes them. Left attacks"
                  + " always require an entity. NONE is useful for item-only right use.",
              d.target,
              Target.class,
              () -> c.target,
              v -> c.target = v));
      group.option(
          string(
              "Entity type filter",
              "Optional comma-separated entity IDs, e.g. minecraft:zombie,minecraft:skeleton. This"
                  + " requires a matching entity even with target NONE.",
              d.entityTypes,
              () -> c.entityTypes,
              v -> c.entityTypes = v));
      group.option(
          bool(
              "Check remaining durability",
              "Empty hand fails. Non-damageable/unbreakable items pass. The comparison is strictly"
                  + " greater than the threshold.",
              d.checkDurability,
              () -> c.checkDurability,
              v -> c.checkDurability = v));
      group.option(
          enumeration(
              "Durability hand",
              "Which held stack is protected; independent of the item-use hand.",
              d.durabilityHand,
              Hand.class,
              () -> c.durabilityHand,
              v -> c.durabilityHand = v));
      group.option(
          integer(
              "Remaining durability must exceed (%)",
              "Exactly the threshold fails; values are compared without rounding.",
              1,
              0,
              100,
              () -> c.minimumDurabilityPercent,
              v -> c.minimumDurabilityPercent = v));
      return group.build();
    }

    private void prompt(String title, String initial, Consumer<String> done) {
      Minecraft.getInstance().gui.setScreen(new TextPrompt(title, initial, done, this::show));
    }

    private void captureKey(boolean ac) {
      capture();
      Minecraft.getInstance()
          .gui
          .setScreen(
              new KeyCaptureScreen(
                  key -> {
                    InputConstants.Key other = ac ? aeKey : acKey;
                    if (!key.equals(InputConstants.UNKNOWN) && key.equals(other))
                      throw new IllegalArgumentException(
                          "That input already activates the other module.");
                    if (ac) acKey = key;
                    else aeKey = key;
                    show();
                  },
                  this::show));
    }
  }
}
