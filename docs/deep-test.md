# Deep functional test plan

Per-target checklist used to prove the mod *works*, not just that it renders a HUD. Order is by
recency, alternating loaders as instructed:

1. Fabric 26.3 → 2. NeoForge 26.2 → 3. Fabric 26.2 → 4. NeoForge 26.1.2 → 5. Fabric 26.1.2 →
6. NeoForge 26.1.1 → 7. Fabric 26.1.1 → 8. NeoForge 26.1 → 9. Fabric 26.1 → 10. NeoForge 1.21.11 → …

Each target is signed off only when every item below passes, or the failure is written up as a known
defect with the exact symptom.

## T0 — boot

- [ ] client reaches a world (`joined the game`)
- [ ] `scripts/check-client-log.sh <log>` clean
- [ ] core init time logged, no exceptions on any worker thread

## T1 — HUD

- [ ] every module in `modules.json` renders (stats label, AFK, FPS, coordinates, biome, direction…)
- [ ] HUD text is visible (alpha, not just "draws called") on a screenshot
- [ ] HUD position/hotkey toggle works

## T2 — settings GUI (keybind `M`)

- [ ] `M` opens the 5zig menu (screenshot)
- [ ] every tab reachable: Plugins / Show Mod / Display / Customize / Capes / Language / social icons
- [ ] a setting can be changed and persists to `run/the5zigmod/config.json`
- [ ] `Escape` returns to the game with the HUD intact

## T3 — keybinds (each must fire, not merely register)

| key | binding |
|---|---|
| `M` | toggle mod menu |
| `F4` | toggle 2nd chat |
| `F12` | copy coordinates |
| `Z` | zoom |
| `Pause` | raid calculator / username history |
| `ScrollLock` | hypixel stats / chat filter |
| `Insert` | change account |
| `KP0` / `KP.` | timer start / reset |

Grep `key_the5zigmod.key.*` in `run/options.txt` first — if the mapping exists but nothing happens, the
bug is in dispatch (see the skill's keybind pitfall), not registration.

## T4 — chat

- [ ] 2nd chat toggles (`F4`) and renders server chat
- [ ] chat filter toggles
- [ ] `T` still opens vanilla chat (no input capture fight)

## T5 — core services

- [ ] conversation DB opens: no `because "sql" is null`, no `ClassNotFoundException: org.hsqldb`
- [ ] Discord RPC path does not throw `NoClassDefFoundError`
- [ ] no `InvalidInjectionException` / `MixinApplyError` anywhere in the log

## T6 — plugin API

- [ ] plugin channels register without `RegistrationFailedException` / "missing client-side handlers"
- [ ] a plugin loaded from `run/the5zigmod/plugins/` is listed in the Plugins tab

## Recording

Results per target go in `docs/deep-test-results.md`: target, date, each section T0–T6 with pass/fail and
the screenshot/log evidence path. Defects found get fixed and the target re-tested from T0.

## Test fixtures

World data upgrades **one way**, so each family's fixture is created on the *oldest* version in the family
and copied forward — never the other way round (that only yields "Downgrading a world is not supported").

| fixture | created on | copied to |
|---|---|---|
| `~/work/test-worlds/26x-creative` | Fabric 26.1 | all 26.x Fabric + NeoForge run dirs |
| `~/work/test-worlds/121x-creative` | Fabric 1.21 | all 1.21.x Fabric + NeoForge run dirs (24) |

Both are **Creative**, because a survival fixture kills the player mid-battery and the death screen then
swallows every key press — which reads exactly like "the hotkey is dead".

### The game dir is not always `run/`

NeoGradle-based 1.20.x projects put the client's working directory in **`runs/client`** (the `runs { }`
block in `build.gradle`), not `run/`. Hardcoding `run/` produced three separate false signals before it was
noticed:

| symptom | real cause |
|---|---|
| "no world within 420s", client sat on the title screen | `run/options.txt` never existed, so `onboardAccessibility:false` was never applied and the **Welcome to Minecraft** dialog blocked quickplay |
| the fixture "disappeared" (Singleplayer opened *Create New World*) | `run/saves` did not exist; the world was in `runs/client/saves` |
| crash count always 0 for those targets | `run/crash-reports` did not exist — **a zero from a missing directory is not evidence of no crash** |

Both scripts now resolve `GDIR` (`run/` if present, else `runs/client/`) and create it before seeding.
Any crash count taken before this fix on a 1.20.x target is unverified and must be re-run.
