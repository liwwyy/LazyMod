# LazyMod development

- At the start of EVERY new development task inspect `/mnt/a400/Games/PrismLauncher/instances/OneClient 26.2/minecraft/logs/latest.log` for LazyMod errors and surrounding stack traces. Report missing logs, timestamps older than 24 hours, or logs predating the installed build. Do not attribute unrelated mod/network/audio errors to LazyMod.
- Target exactly Minecraft 26.2, Java 25, Fabric's non-remapping Loom plugin and official names. Keep pinned dependencies; do not introduce Yarn or remapJar.
- Use `./scripts/build-local.sh` for local builds. EVERY successful local build must be verified and installed in `/mnt/a400/Games/PrismLauncher/instances/OneClient 26.2/minecraft/mods/`. If invoking `./gradlew build` directly, immediately run `python3 scripts/install-local.py`. Never install failed builds, sources JARs, or leave duplicate LazyMod JARs. Identify old mods using fabric.mod.json; preserve unrelated files.
- Routine verification: compilation, JUnit, artifact/static checks. Do not launch Minecraft, headless instances, or screenshot/control loops. State which runtime behaviors remain untested.
- Pure config/profile/timing/activation rules live in main sources; Fabric, Minecraft, mixins and YACL live in client sources. Config must not import YACL. Presets are immutable definitions; all profiles run through the same engine.
- Preserve physical inputs. Automation owns only its own item-use session; AutoEat priority suspends actions without toggling AutoClicker off.
- Meteor is a behavioral reference only. Do not copy/adapt GPL code into MIT sources. Keep dependency notices and external YACL replaceability.
- Keep README/changelog current. Before Git operations inspect status and remote, preserve user work, and never force-push. Verify pushes, CI and releases before claiming success. CI must not reference local Prism paths.
