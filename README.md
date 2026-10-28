# LazyMod

LazyMod is a **client-only Fabric mod for Minecraft 26.2**, by **liwwyy**. It provides
profile-based AutoClicker and independent AutoEat with a YACL configuration screen.

Requires Java 25, Fabric Loader 0.19.5 or later, Fabric API 0.161.0+26.2 or later for
26.2, and **YACL 3.9.7+26.2-fabric**. Fabric API and YACL are separate required mods.
Install the single `lazymod-<version>+26.2.jar` in your client's mods folder.
No server installation is needed. Use automation only where the server permits it.

## Getting started

Open a world and run `/lazy`. AutoClicker and AutoEat start disabled after launch;
death/disconnection clears their runtime activation. Both activation keys start
unbound. Assign or clear them under **General & Keys**, or Minecraft Controls.
Keys are saved through vanilla options and work only outside screens. Commands
work without bindings.

- **AutoClicker:** profile selector and all create/duplicate/rename/delete/reset
  controls, profile AutoEat integration, left/right actions and conditions.
- **AutoEat:** standalone activation, hunger/health triggers, food preference and blacklist.
- **General & Keys:** in-screen world automation and activation bindings.

Settings, selection, profiles and key changes are drafts until **Save**. Cancel/Esc
discards the draft. Start/Stop buttons are explicitly immediate runtime actions
and operate on the currently applied profile; save a new selection first.

## Commands

All commands are handled locally, including malformed `/lazy` input; they are
never sent to the server.

```text
/lazy
/lazy config
/lazy help
/lazy ac start <profile name>
/lazy ac stop
/lazy ac toggle
/lazy ac status
/lazy ac profiles
/lazy ac profile <profile name>
/lazy ae start
/lazy ae stop
/lazy ae toggle
/lazy ae status
```

Names are case-insensitive. Use spaces directly or quote a complete name:
`/lazy ac start "My Farm"`. Suggestions quote names safely. `ac profile` preserves
whether AutoClicker was active; `ac start` selects and enables atomically after
saving. Unknown profiles leave the current selection/state unchanged.

`ae start/stop/toggle` controls the **standalone request**. If the active profile
still requests AutoEat, `ae stop` leaves integrated AutoEat effective and status
reports that source. Disable that profile's integration or stop AutoClicker to
remove the profile request. Stopping AutoClicker never disables standalone AutoEat.

## Profiles and conditions

Built-in Raidfarm and Spawner definitions are immutable. Editing a built-in's
settings creates a selected local copy when the edit is committed to the draft.
The original remains available. Custom profiles use the same engine.

- A preset-derived copy resets to its original preset, retaining its name/ID.
- Other custom profiles reset to common new-profile defaults.
- Duplicates retain preset provenance, if any.
- Deleting the active custom profile and saving stops AutoClicker and selects
  its source preset (or Raidfarm); it never silently starts another profile.
- Names are unique ignoring case; built-in names cannot be renamed or reused.

| Default | Raidfarm | Spawner |
| --- | --- | --- |
| Left | Repeat, fully charged vanilla attack | Repeat, fully charged vanilla attack |
| Stored custom interval | 625 ms | 625 ms |
| Target | Armor stand under the crosshair | Living entity, excluding players/armor stands |
| Main hand | Sword (`#minecraft:swords`) | Nonempty item |
| Offhand | Ominous bottle, any level | Unrestricted |
| Durability | Main-hand remaining durability >1% | Main-hand remaining durability >1% |
| Right | Hold, explicit offhand item-only use; same initial conditions as left | Disabled, independently editable |
| Integrated AutoEat | On | On |

**Raidfarm is intentionally strict:** attacks wait while the player drinks.
After the last offhand ominous bottle is consumed, the offhand condition fails
and attacks stop until another bottle is supplied. There is no automatic refill
or hidden omen-effect condition. Item-only use avoids interacting with/equipping
the armor stand instead of drinking.

New custom profiles start with left repeat at 625 ms, Vanilla Cooldown off,
a living crosshair target and >1% durability, right disabled and integration off.

Every button has independent item, target and durability settings. All enabled
condition rows must pass. Item filters accept comma-separated namespaced item IDs
or `#tag` IDs as alternatives, e.g. `minecraft:diamond_sword,#minecraft:swords`.
Empty filters impose no item requirement. An entity type filter is an optional
list such as `minecraft:zombie,minecraft:skeleton` and always requires an entity.
Unresolved IDs/tags are retained in configuration and block the relevant action;
status reports the reason. No scanning, aiming or reach extension is performed.
Left automation requires a valid entity even if its target setting is NONE or BLOCK;
it does not swing into air or break blocks.

Durability uses a strict comparison without rounding: exactly 1% fails a >1%
guard. Empty stacks fail; non-damageable/unbreakable stacks pass. The protected
hand is independently selectable.

## Timing, physical input and screens

Custom intervals accept positive integers up to 2,147,483,647 ms. The interval
dialog provides a 50–5000 ms slider **and** exact integer entry; out-of-slider
values remain intact, with an explanatory message. Invalid values are rejected.
Moving the slider explicitly chooses a value in its range.

Left Vanilla Cooldown uses actual `getAttackStrengthScale(0.0F)` readiness and
retains the custom interval without using it as an additional readiness condition.
Right repeat makes separate presses; Hold sustains item use and uses vanilla's
held-use retry cadence. Item cooldowns and consumption durations still apply.
Actual actions run on client ticks (normally about 50 ms resolution), at most once
per button per render frame. There is no missed-click catch-up debt after lag.

Physical mapped attack/use input is preserved and takes priority. Manual actions
rebase automation timing. AutoEat temporarily suspends conflicting AutoClicker
actions while leaving its activation intact, and restores the hotbar only if the
user has not selected a different slot. LazyMod never forces shared mouse-key states.
Other mods' automation is outside LazyMod's ownership; disable conflicting
modules manually if they issue competing inputs.

Automation while screens are open defaults **off**. Enabling it allows in-world
actions behind inventory, chat and configuration screens, including a multiplayer
pause menu. It never synthesizes GUI clicks or inventory slot operations. Actual
single-player pausing, unavailable worlds, death and spectator state still suspend
actions. It cannot advance a genuinely paused world. Opening a screen with the
setting off releases owned use and rearms custom timing.

## AutoEat

AutoEat searches eligible offhand food first, then the hotbar. It does not move
items from the main inventory. Defaults: hunger at/below 16, saturation preference;
optional health threshold defaults to 10 health points. Hunger, Health, Either
and Both modes are available. Consumption finishes one item before deciding
whether another is needed; health-triggered food remains subject to vanilla
hunger/always-edible rules, and is not an instant heal.

The editable default blacklist excludes enchanted/golden apples, chorus fruit,
poisonous potato, pufferfish, raw chicken, rotten flesh, spider eye and suspicious
stew. Food must have both food and consumable components. Ominous bottles are not
food. No suitable food means AutoEat waits without blocking ordinary AutoClicker
work. Manual use has priority; AutoEat may interrupt LazyMod-owned bottle use.

## Configuration and recovery

`config/lazymod.json` stores a versioned settings/profile document and stable
profile IDs. Activation is not persisted. Saving uses a temporary file and atomic
replacement when available, with a last-known-good `.bak` copy. Invalid or migrated
input is preserved under a uniquely named recovery backup. A corrupt primary can
recover from the good backup. A newer unsupported schema protects the file from
writes and disables automation; use a compatible LazyMod version or explicitly
move the file aside before starting fresh. Failures are reported in chat/logs.

## Development and verification

See [AGENTS.md](AGENTS.md) for the mandatory log inspection and installation workflow.
Pinned build: Java 25, Gradle 9.8.2, non-remapping Fabric Loom 1.19.3, official names.
YACL resolves from Modrinth Maven; no Maven credentials are needed.

```sh
./scripts/build-local.sh --no-daemon
```

Every successful local `build` runs compilation, JUnit, installer rollback tests,
static mixin-target checks and artifact checks before installing the verified JAR
to the configured PrismLauncher instance. `LAZYMOD_LOCAL_MODS` can select another
existing local mods directory. Previously installed JARs with metadata ID `lazymod`
are archived in `.local/install-backups/`; unrelated files are untouched. A build
receipt prevents installing an old JAR after source changes or a failed build.

`./gradlew test compileClientJava` runs unit checks/compilation without packaging
or installing. `CI=true ./gradlew build` performs the portable CI build without a
local install. No default task starts Minecraft, GameTests or a headless instance.

Automated checks cover profile/reset semantics, config recovery, timing boundaries,
conditions, activation requests, slot ownership, command names and installer safety.
Mixin target checks validate pinned member signatures; they **do not prove runtime
mixin application**, nor do unit tests prove actual game input behavior.

Focused in-game acceptance remains pending:

- Open YACL; verify Save/Cancel, profile switching/copies/resets and key clearing.
- Test manual clicks alongside interval and full-cooldown attacks.
- Verify offhand bottle use while targeting an armor stand and stopping after consumption.
- Verify AutoEat priority, depleted food, manual slot changes and independent activation.
- Test inventory/chat/config screens and genuine single-player pause transitions.
- Confirm malformed commands remain local and inspect the next session's log.
- Check compatibility with the user's other input/automation mods.

Minecraft has not been launched for routine development verification.

## GitHub Actions and releases

Pushes and pull requests build/test on Java 25 and upload the verified primary JAR.
CI never installs into PrismLauncher. Actions are pinned to immutable commits.

For a release, update `mod_version` and the changelog, commit/push, then push an
annotated tag such as `v1.0.0`. The release workflow validates an exact three-part
numeric tag against the committed version, rebuilds/tests that commit and publishes
its JAR to a GitHub Release. It does not publish to Modrinth/CurseForge. No release
tag is automatically created by ordinary development builds.

## License

Original LazyMod code: [MIT](LICENSE). Meteor was inspected as a behavioral reference;
no Meteor source was copied/adapted. YACL remains an external LGPL dependency.
See [third-party notices](THIRD_PARTY_NOTICES.md).
