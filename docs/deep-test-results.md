# Deep functional test results

Order per `docs/deep-test.md`: Fabric 26.3 → NeoForge 26.2 → Fabric 26.2 → NeoForge 26.1.2 → …

---

## 1. Fabric 26.3 — in progress

Date: 2026-11-04 · log `/tmp/dt2-f263.log` · screenshots `/tmp/dt2-*.png`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ world loaded, core init 1551 ms, log clean | `joined:1`, `Core initialised` |
| T1 HUD | ✅ renders (mod label, AFK, FPS, X/Y/Z, facing) | `/tmp/dt-f263-hud2crop.png` |
| T1b HUD toggle | ✅ `M` toggles `showMod` in `config.json` | config flips true/false |
| T2 settings GUI | 🔧 **fixed** — Right Shift opens it; all labels render | defects D1, D4 |
| T2b other GUIs | ✅ Coordinates Clipboard opens (`F12`) | `/tmp/dt2-f12c.png` |
| T3 GUI crash | 🔧 **fixed** — opening any 5zig screen crashed the client | defect D2 |
| T2c mouse input | ✅ clicking a button toggles its value and persists to `config.json` | `showMod` False→True→False |
| T3 GUI keyboard | 🔧 partial — 40 platforms now deliver key codes; **26.x still swallows characters** (see D3b) | — |
| T2d sub-screens | ✅ "Customize Display" opens: module list (stats…timer), Add/Remove, Settings, Disable, Move Up/Down, live HUD preview (AFK/FPS/X/Y/Z/facing/IP) | `/tmp/dt5-customize-c.png` |
| T2e closing a GUI | ⚠️ only via the `Done` button; `Escape` is inert until D3 lands | — |
| T4 chat (`F4`) | ⏸ blocked — F4 is swallowed while a GUI is open (vanilla blocks hotkeys in menus); retest after D3 | — |
| T5 core services | ✅ HSQLDB driver present, no `sql is null`; conversation schema still logs `object not found` (documented gap) | log |
| T6 plugin API | not yet run | — |

### Defects found

**D1 — the settings GUI cannot be opened from the game.** *(fixed)*
`M` is bound to `the5zigmod.key.toggle_mod`, which toggles HUD visibility
(`KeybindingListener.onTick` flips `showMod`). `ClassProxyCallbackImpl.displayGuiSettings` has **no
caller anywhere in the tree**, and there is no pause-menu button on any ported version. Result: Plugins,
Display, Capes, Language and every other setting are unreachable in-game — the whole configuration
surface is dead. Fix: a new binding `the5zigmod.key.settings` (RIGHT SHIFT), registered in the core's
`KeybindingManager`/`KeybindingListener` and pre-registered in all 40 platform `Keybinds` bootstraps,
opens `new GuiSettings(currentScreen, "main")`. **Verified** on 26.3: Right Shift opens the full menu.

**D2 — opening a 5zig screen crashed the client with `IllegalStateException: Can only blur once per frame`.**
`GuiHandle.extractRenderState` called `this.extractBackground(...)` and *then* `super.extractRenderState(...)`,
which extracts the background again; 26.x `GuiRenderState#blurBeforeThisStratum` permits one blur per
frame. Fixed by letting super own the background and drawing the 5zig screen after it. Applied to
platform 26.1 / 26.1.1 / 26.1.2 / 26.2 / 26.3. **Verified**: the Coordinates Clipboard now opens and
renders. Status: **fixed**.

**D3 — key handling inside every 5zig GUI is broken, on every version.**
The core's `Gui#keyTyped0(char character, int key)` treats the second argument as a **key code** — it
dispatches `ESCAPE`, `RETURN`, `TAB`, and hands `(character, key)` to `onKeyType`, which is where
`GuiCoordinatesClipboard` implements SPACE (insert position) and DELETE (clear). Every platform passes
something else:

| platform | passes |
|---|---|
| 1.20.1, 1.21.1 | `modifiers` (usually 0) |
| 1.21.11 | `event.modifiers()` |
| 26.2, 26.3 | literal `0` |

So `key == Keyboard.KEY_SPACE` is never true: ESC/RETURN/TAB/SPACE/DELETE are inert inside 5zig GUIs.
This is a port-wide bug, not a 26.3 regression — it predates every verified build. Status: **fix in
flight** (needs a key code derived from the character, plus special-key delivery from `keyPressed`,
since no platform event carries both).

**D4 — every button in a 5zig GUI was unlabelled on 26.x.** *(fixed)*
The buttons drew correctly (fill + bevel) but with no text. `Button#drawLabel` used
`GuiGraphicsExtractor#centeredText(font, component, cx, y, color)`, which renders nothing in 26.3's
extract phase; the HUD's own text path (`graphics.text(font, component, x, y, colour, shadow)`) does
render. Switched the button to that call with an explicit centred x and an opaque-alpha guard. Applied
to platform 26.1 / 26.1.1 / 26.1.2 / 26.2 / 26.3. **Verified**: the menu now reads Show Mod, Auto-Report
Crashes, Customize Display, TeamSpeak, Cape Settings, Language, Mod Scale, Display Options, Server
Options, Discord Presence, Coordinates Clipboard, Reset Configuration, Done, Chat… on 26.3.

**D5 — the plugin API throws on 26.x.** *(mitigated)*
`Variables#sendCustomPayload` on 26.1 / 26.2 / 26.3 threw
`UnsupportedOperationException(...)` for every call, while 1.21.10 / 1.21.11 already degraded with a
one-off warning. A plugin that sends anything would therefore crash the game on 26.x. Changed to the same
warn-once-and-drop behaviour on all three. The proper fix — registering a raw-payload type per 5zig
channel so the API actually works on 26.x — is queued.

---

## 2. NeoForge 26.2 — in progress

Date: 2026-11-04 · log `/tmp/dt-lock-nf262.log`, `/tmp/dt-nf262.log`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ world loaded, core init | `joined:1` |
| T1 HUD | ✅ renders in the corner after Escape | `/tmp/nf262-esc-s.png` |
| T2 settings GUI | ✅ Right Shift opens it, **all labels render** (Plugins (0), Show Mod: On, Mod Scale, Display Options…, Cape Settings…, Language…, Done, Chat…) | `/tmp/dt-nf262-menu-c.png` |
| T3 ESC | ✅ closes back to live gameplay | `/tmp/nf262-esc-s.png` |
| T4 character input | ✅ SPACE filled X=233 / Z=21 in the Coordinates Clipboard | `/tmp/nf262-coords-c.png` |
| T5 core services | 🔧 **fixed** stale-lock recovery (D6) | `Clearing a stale lock and retrying`, no NPE |

### Defects found on this target

**D6 — a hard crash left the conversation database permanently unopenable.** *(fixed)*
`The5zigMod.newConversationDatabase` caught `NoConnectionException`, printed the stack trace and left
`database` null, so `NetworkStats.init` / `ConversationManager.init` threw
`NullPointerException … because "sql" is null` on the netty thread, and chat stayed dead until the user
manually deleted `run/the5zigmod/sql/chatlogs/<uuid>.lck`. Reproduced with a `.lck` left by a killed
client. Fix: clear a stale lock, retry once, and fall back to `DummyDatabase` so callers never see null.
**Verified**: the same stale lock now logs *"Could not open the conversation database … Clearing a stale
lock and retrying"*, the retry succeeds and `check-client-log.sh` is clean.

---

## 3. Fabric 26.2 — pass

Date: 2026-11-04 · log `/tmp/dt-f262.log`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, `check-client-log.sh` clean | log |
| T1 HUD | ✅ renders | menu screenshots |
| T2 settings GUI | ✅ Right Shift, all labels (Plugins (0) … Chat…) | `/tmp/f262-menu-c.png` |
| T3 ESC | ✅ closes to gameplay | — |
| T4 SPACE | ✅ X=233, Z=21 | `/tmp/f262-coords-c.png` |
| T5 DELETE | ✅ clears both fields | `/tmp/f262-del-c.png` |

No new defects: the 26.x fixes (blur, labels, key codes, textfield alpha, stale-lock recovery) hold on Fabric
as well as NeoForge.

## Deferred sections (all targets)

- **T5 chat / friends / parties** cannot be functionally tested: every one of those features talks to the
  5zig backend (`tcp.5zigreborn.eu`), which no longer exists. The client logs `Disconnected: Bad login`
  and retries; nothing in the mode can be exercised beyond the failure path.
- **T6 plugin API** has no plugin artifact to load, and on 26.x raw plugin channels are intentionally
  degraded to a warning (D5). Testing it properly needs a small test plugin project.

---

## 4. NeoForge 26.1.2 — pass

Date: 2026-11-04 · log `/tmp/dt-nf2612.log`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, log clean | log |
| T1 HUD | ✅ renders (also visible on the death screen) | `/tmp/nf2612-menu-c.png` |
| T2 settings GUI | ✅ Right Shift, all labels | `/tmp/nf2612-menu3-c.png` |
| T3 ESC | ✅ closes | — |
| T4 SPACE | ✅ X=26, Z=6 | `/tmp/nf2612-coords-c.png` |

No new defects.

### Testing fixture (fixed)

Earlier targets were tested in an inherited survival world that killed the idle player within minutes.
That was made worse by my own misdiagnosis: `/difficulty` appeared broken, but the real cause was that
**the world had cheats disabled** — commands are rejected outright when `allowCommands` is off.

There is now a proper fixture: `~/work/test-worlds/26x-creative-peaceful`, a **Creative, Peaceful,
commands-enabled** world created through the vanilla UI (Game Mode: Creative, Difficulty: Peaceful,
Allow Commands: ON) and copied into every 26.x run directory as `New World`. Verified with
`/time set day` → *"Set minecraft:overworld to time marker minecraft:day"*.

The old survival worlds were deleted. 1.21.x and older still need their own fixture: world data is
one-way upgraded, so a 26.x world cannot be opened by them.

---

## 5. Fabric 26.1.2 — pass (on the new fixture)

Date: 2026-11-04 · log `/tmp/mkworld.log`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined the Creative/Peaceful world | log |
| T1 HUD | ✅ label, FPS, X/Y/Z, facing | `/tmp/mk-7c.png` |
| T2 settings GUI | ✅ Right Shift | `/tmp/t5-menu-c.png` |
| T3 ESC | ✅ closes | — |
| T4 SPACE | ✅ X=-553, Z=697 | `/tmp/t5-coords-c.png` |
| T5 DELETE | ✅ clears both | `/tmp/t5-del-c.png` |
| T6 commands | ✅ `/time set day` → *Set minecraft:overworld to time marker minecraft:day* | `/tmp/t5-cmd2-c.png` |

No new defects.

---

## 6. NeoForge 26.1.1 — pass

Date: 2026-11-04 · log `/tmp/dt-nf2611b.log`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, `check-client-log.sh` clean | log |
| T1 HUD | ✅ | — |
| T2 settings GUI | ✅ Right Shift, all labels | `/tmp/nf2611-m-c.png` |
| T3 ESC | ✅ | — |
| T4 SPACE | ✅ X=-71, Z=-7 | `/tmp/nf2611-c-c.png` |
| T5 DELETE | ✅ | `/tmp/nf2611-d-c.png` |

No new defects.

### Fixture note — vanilla "Create Test World" (26.1+)

26.1 has a title-screen **Create Test World** button (next to *Mods*) that opens the create-world dialog
**pre-set to Creative with Allow Commands ON**. That is the clean way to make a per-version fixture on the
older 26.x builds, where a 26.1.2-era world only produces the *"Downgrading a world is not supported"*
dialog. The manual route used for the first fixture also works but needs the Allow Commands *switch*
(right edge of the row) rather than the row itself.

---

## 7. Fabric 26.1 — pass

Date: 2026-11-04 · log `/tmp/mk261.log` · fixture rebuilt on this version

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, `check-client-log.sh` clean | log |
| T1 HUD | ✅ | — |
| T2 settings GUI | ✅ Right Shift, all labels | `/tmp/f261-m-c.png` |
| T3 ESC | ✅ | — |
| T4 SPACE | ✅ X=8, Z=-10 | `/tmp/f261-c-c.png` |
| T5 DELETE | ✅ | `/tmp/f261-d-c.png` |

No new defects.

### Fixture, final form

The shared 26.x fixture is now created **on the oldest 26.x build (26.1)** and copied forward, so every
target upgrades it instead of hitting *"Downgrading a world is not supported"*. It lives at
`~/work/test-worlds/26x-creative` (Creative; commands were left off on this screen because the toggle is
not clickable there, which does not matter for the GUI battery — Creative is invulnerable).

---

## 8. NeoForge 1.21.11 — pass

Date: 2026-11-04 · logs `/tmp/mk12111.log`, `/tmp/dt-nf12111b.log`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined on a fresh Creative world, `check-client-log.sh` clean | log |
| T1 HUD | ✅ renders, plus the "no longer AFK" toast | `/tmp/n12111-ing-c.png` |
| T2 settings GUI | ✅ full menu, every label (after D8) | `/tmp/d8-menu-c.png` |
| T3 ESC | ✅ opens the vanilla pause menu as expected | — |
| T4 SPACE | ✅ X=1, Z=-6 | `/tmp/d8-coords-c.png` |
| T5 DELETE | ✅ both fields empty (cursor only) | `/tmp/d8-del-c.png` |

### Defects

**D7 — the same "Can only blur once per frame" crash on 1.21.9 / 1.21.10 / 1.21.11.** *(fixed)*
Opening any 5zig screen crashed the client. `GuiHandle#render` called `renderBackground(...)` and then
`super.render(...)`, which renders (and blurs) the background again on 1.21.9+. Same fix as the 26.x
platforms: let `super.render` own the background, then draw the 5zig screen over it. Applied to all three (Fabric and NeoForge projects both rebuilt green) and the crash no longer appears in
the NeoForge client log. This is a genuine crash that predates every
"verified" claim for those three versions — the HUD renders fine, so nothing showed it.

**D8 — settings GUI crashed on first frame; hotkey and binding were both fine.** *(fixed)*
Right Shift **did** fire (`settings.callIsPressed()` true, `displayScreen` set `GuiHandle(GuiSettings)`),
and the mod never opened a pause screen — the "pause menu" in my earlier screenshot was a **stale frame
from a client that had already died**. The real crash:

```
ClassCastException: eu.the5zig.mod.platform.ResourceLocation cannot be cast to net.minecraft.resources.Identifier
  at Variables.getTexture <- Base64Renderer.setBase64String <- GuiSettings.drawSkinButton
```

1.21.9 made `ResourceLocation` **final**, so the platform's `IResourceLocation` wrapper can no longer be
cast to `Identifier`; `getTexture` / `deleteTexture` / `createDynamicImage` still did. The settings GUI is
the only core screen that draws a fetched texture (the player skin) on frame 1, which is exactly why `F12`
worked on the same client. Fixed in `platform/1.21.{9,10,11}` by routing through `toTextureLocation(...)`.

Also fixed: `Button` / `StringButton` / `GuiList` on **1.21.11** still passed legacy `0xRRGGBB` label
colours (alpha 0) to strict-ARGB text — every settings button rendered blank. 1.21.9/1.21.10 already
forced opaque; 1.21.11 had missed it.

**Lesson:** a dead-client screenshot is indistinguishable from "the hotkey did nothing" unless the client
is known to be alive — which is why the battery now asserts the frame actually changed, and why every
screenshot is paired with a crash-report check.

### Battery now asserts the GUI opened

`scripts/deep-test-target.sh` previously passed a target when a world loaded and the log was clean — which
it did for 1.21.11 while the "menu" screenshot was actually the **vanilla pause menu** (D8). It now takes a
frame before each hotkey and uses `scripts/shot-diff.py` to require that ≥10% of the frame changed:

| pair | result |
|---|---|
| same frame | FAIL (0.0%) |
| pause menu → pause menu (the D8 trap) | FAIL (0.1%) |
| gameplay → real 5zig settings menu | pass (30.6%) |

---

## 9. Fabric 1.21.11 — pass

Date: 2026-11-04 · log `/tmp/dt-f12111b.log` · Creative fixture copied from the NeoForge 1.21.11 run dir

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, no crash report, log clean | log |
| T1 HUD | ✅ FPS / X / Y / Z / facing | `/tmp/f12111b-back-c.png` |
| T2 settings GUI | ✅ opened (18.2% of frame changed) | `/tmp/dt-f12111b-menu.png` |
| T3 ESC | ✅ back to gameplay with HUD | `/tmp/f12111b-back-c.png` |
| T4 SPACE | ✅ X=1, Z=-6 | `/tmp/f12111b-coords-c.png` |
| T5 DELETE | ✅ | `/tmp/dt-f12111b-deleted.png` |

No defects specific to this target — D7 (blur) and D8 (`ResourceLocation` final / label alpha) were the
1.21.9+ family defects and this build carries those fixes.

### Two fixture traps worth recording

- A project whose `run/saves` still holds an old **survival** world will show "You Died!" mid-battery and
  every key press goes to the death screen — that reads exactly like "the hotkey does nothing". Copy the
  Creative fixture in before testing.
- The frame-diff threshold is screen-dependent: the settings menu changes ~18–34% of the frame, the
  coordinates dialog ~9%. 6% for the dialog, 10% for the menu.

### Strongest SPACE evidence

Read the HUD's own coordinates in the same run and compare: HUD `X:1.5 / Z:-5.5` → fields after SPACE
`X:1 / Z:-6`. That verifies the value is the *player's real position*, not a leftover from the saved list.

---

## 10. NeoForge 1.21.10 — pass

Date: 2026-11-04 · log `/tmp/dt-nf12110.log` · 1.21 Creative fixture

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, no crash report, log clean | log |
| T1 HUD | ✅ | — |
| T2 settings GUI | ✅ 34.5% of frame changed, all labels | `/tmp/nf12110-menu-c.png` |
| T3 ESC | ✅ | — |
| T4 SPACE | ✅ X=264, Z=-253 | `/tmp/nf12110-coords-c.png` |
| T5 DELETE | ✅ | `/tmp/dt-nf12110-deleted.png` |

No new defects. First target that ran clean on the corrected battery (frame assertion at 10% / 6%).

---

## 11. Fabric 1.21.10 — pass

Date: 2026-11-04 · log `/tmp/dt-f12110.log`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, no crash report, log clean | log |
| T1 HUD | ✅ | — |
| T2 settings GUI | ✅ 34.6% of frame changed | `/tmp/f12110-menu-c.png` |
| T3 ESC | ✅ | — |
| T4 SPACE | ✅ X=264, Z=-253 | `/tmp/f12110-coords-c.png` |
| T5 DELETE | ✅ | `/tmp/dt-f12110-deleted.png` |

No new defects.

---

## 12. NeoForge 1.21.9 — pass

Date: 2026-11-04 · log `/tmp/dt-nf1219.log`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, no crash report, log clean | log |
| T1 HUD | ✅ | — |
| T2 settings GUI | ✅ 34.4% of frame changed | `/tmp/dt-nf1219-menu.png` |
| T3 ESC | ✅ | — |
| T4 SPACE | ✅ X=264, Z=-253 | `/tmp/nf1219-coords-c.png` |
| T5 DELETE | ✅ | `/tmp/dt-nf1219-deleted.png` |

No new defects — the D7 (blur) and D8 (`ResourceLocation` final) fixes hold on this patch.

---

## 13. Fabric 1.21.9 — pass

Date: 2026-11-04 · log `/tmp/dt-f1219.log`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, no crash report, log clean | log |
| T1 HUD | ✅ | — |
| T2 settings GUI | ✅ 34.6% of frame changed | `/tmp/f1219-menu-c.png` |
| T3 ESC | ✅ | — |
| T4 SPACE | ✅ X=364, Z=-253 | `/tmp/f1219-coords-c.png` |
| T5 DELETE | ✅ | `/tmp/dt-f1219-deleted.png` |

No new defects.

### Release repair

The six 1.21.9 / 1.21.10 / 1.21.11 releases were published **before** D7 and D8, so their assets were the
crashing builds. Rebuilt from the fixed tree and re-uploaded with `--clobber`; every asset now matches the
local jar byte-for-byte by size:

| tag | size |
|---|---|
| `v4.0.0-alpha.1-1219` | 21,758,848 |
| `v4.0.0-alpha.1-12110` | 21,758,852 |
| `v4.0.0-alpha.1-12111` | 21,757,843 |
| `v4.0.0-alpha.1-nf1219` | 21,721,594 |
| `v4.0.0-alpha.1-nf12110` | 20,330,156 |
| `v4.0.0-alpha.1-nf12111` | 21,726,159 |

---

## 14. NeoForge 1.21.8 — pass (after D9)

Date: 2026-11-04 · logs `/tmp/dt-nf1218.log` (crash), `/tmp/dt-nf1218b.log` (fixed)

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, no crash report after the fix, log clean | log |
| T1 HUD | ✅ | — |
| T2 settings GUI | ✅ 34.3% of frame changed | `/tmp/dt-nf1218b-menu.png` |
| T3 ESC | ✅ | — |
| T4 SPACE | ✅ X=364, Z=-253 | `/tmp/nf1218b-coords-c.png` |
| T5 DELETE | ✅ | `/tmp/dt-nf1218b-deleted.png` |

### Defect D9 — the blur crash is not a 26.x/1.21.9 quirk, it spans 1.20.2+

First run of this target crashed exactly like D7:

```
Description: Rendering screen
java.lang.IllegalStateException: Can only blur once per frame
```

Ground truth from the decompiled `Screen`:

| version | `Screen#render` renders the background? |
|---|---|
| 1.20.1 | **no** (the call lives in each screen) |
| 1.20.2 | **yes** — first statement of `render` |
| 1.21.1 | yes |
| 1.21.8 | yes (crashes: the background is blurred by then) |

So the platform's own `renderBackground(...)` is correct only up to **1.20.1**. From 1.20.2 it draws the
background a second time (cosmetic double dim), and once the background became blurred it throws. Fixed
the same way as 26.x on **14 platforms**: 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2,
1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8 — `super.render(...)` first, then the 5zig screen. All 14
Fabric projects compile clean; NeoForge 1.21.8 verified in-client (menu 34.3%, coords 33.5%, SPACE
`X=364 / Z=-253`, no crash report).

**1.16–1.20.1 are deliberately unchanged** — there `Screen#render` does not render the background, so
removing the call would leave the mod's screens undimmed.

---

## 15. Fabric 1.21.8 — pass

Date: 2026-11-04 · log `/tmp/dt-f1218.log`

| Section | Result | Evidence |
|---|---|---|
| T0 boot | ✅ joined, no crash report, log clean | log |
| T1 HUD | ✅ | — |
| T2 settings GUI | ✅ 34.5% of frame changed | `/tmp/dt-f1218-menu.png` |
| T3 ESC | ✅ | — |
| T4 SPACE | ✅ X=364, Z=-253 | `/tmp/f1218-coords-c.png` |
| T5 DELETE | ✅ | `/tmp/dt-f1218-deleted.png` |

No new defects — carries the D9 background fix.

### Toolchain note (my own harness error, corrected)

The first compile sweep reported `compile=1` for 1.20.2–1.21.4 with **zero** `error:` lines. That was not a
code failure: Loom **1.7.4** cannot apply on Gradle 8.14.3 (`'Problems.forNamespace'`), and those eras are
Loom 1.7.4 projects. Re-run with their own toolchains (1.20.2–1.20.6 → Gradle 8.10.2 + JDK 17;
1.21–1.21.4 → Gradle 8.10.2 + JDK 21) and all 14 platforms compile clean with the D9 fix. Always read the
exit code *and* the log before believing a sweep.

---

## 16–19. 1.21.7 (NeoForge + Fabric), 1.21.6 (NeoForge + Fabric)

Log: `docs/deep-test-log.json` (machine-generated by `scripts/run-target.sh`).

| target | menu | coords | crashes | verdict |
|---|---|---|---|---|
| NeoForge 1.21.7 | ok 34.4% | ok 33.4% · X=364/Z=-253 | 0 | pass |
| Fabric 1.21.7 | ok 34.7% | ok 33.7% | 0 | pass |
| NeoForge 1.21.6 | ok 34.1% | ok 33.6% | 0 | pass |
| Fabric 1.21.6 | ok 34.2% | ok 33.5% | 0 | pass |

No new defects — these carry D9 and already unwrap the resource-location wrapper correctly (D10 does not
apply from 1.21.6 on).

### Defect D10 — 1.21 … 1.21.5 cast the resource-location wrapper

1.21.5 crashed on **both** loaders the moment a GUI drew a texture:

```
Description: Rendering screen
java.lang.ClassCastException: class eu.the5zig.mod.platform.ResourceLocation
  cannot be cast to class net.minecraft.resources.ResourceLocation
```

The platform's `ResourceLocation` **extends** the Minecraft class on 1.16–1.20.6 (so every cast is valid
there — those builds are fine and were left untouched) but is a **wrapper** from 1.21 on, where the cast can
never succeed. Within the wrapper group 1.21.6–1.21.8 already unwrap with `ResourceLocation.toMinecraft(...)`
and 1.21.9+ had their `getTexture` fixed with D8; the six platforms still casting are **1.21, 1.21.1, 1.21.2,
1.21.3, 1.21.4, 1.21.5**. Fix in flight (deleg_2f1061f5).

---

## 20–21. 1.21.5 — NeoForge and Fabric (pass, after D10)

| target | menu | coords | crashes | verdict |
|---|---|---|---|---|
| NeoForge 1.21.5 | ok 33.5% | ok 32.2% · X=364/Z=-253 | 0 | **pass** |
| Fabric 1.21.5 | ok 33.5% | ok 32.4% | 0 | **pass** |

Both targets crashed before D10 with `ClassCastException … platform.ResourceLocation cannot be cast to
net.minecraft.resources.ResourceLocation`; after the fix both reach the settings GUI and the coordinates
dialog with no crash report.

---

## 22–25. 1.21.4 and 1.21.3 — both loaders (pass)

| target | menu | coords | crashes |
|---|---|---|---|
| NeoForge 1.21.4 | ok 33.4% | ok 32.4% · X=364/Z=-253 | 0 |
| Fabric 1.21.4 | ok 33.5% | ok 32.4% | 0 |
| NeoForge 1.21.3 | ok 33.4% | ok 32.2% | 0 |
| Fabric 1.21.3 | ok 33.6% | ok 32.5% | 0 |

All four carry D9 + D10 and pass with no crash report. Toolchain for this era is **Gradle 8.10.2 + JDK 21**
(8.14.3 only becomes correct at 1.21.5 — using it here fails at plugin-apply and reads like a code error).

---

## 26–29. 1.21.2 and 1.21.1 — both loaders (pass)

| target | menu | coords | crashes |
|---|---|---|---|
| NeoForge 1.21.2 | ok 33.4% | ok 32.2% · X=364/Z=-253 | 0 |
| Fabric 1.21.2 | ok 33.6% | ok 32.4% | 0 |
| NeoForge 1.21.1 | ok 33.5% | ok 32.1% | 0 |
| Fabric 1.21.1 | ok 33.6% | ok 32.4% | 0 |

No crash reports. D9 + D10 both hold; these are the versions where D10 was an outright crash before the fix.

---

## 30–33. 1.21 and 1.20.6 — both loaders (pass)

| target | menu | coords | crashes |
|---|---|---|---|
| NeoForge 1.21 | ok 33.6% | ok 32.2% | 0 |
| Fabric 1.21 | ok 33.6% | ok 32.3% | 0 |
| NeoForge 1.20.6 | ok 30.9% | ok 28.1% · X=177/Z=312 | 0 |
| Fabric 1.20.6 | ok 31.0% | ok 28.4% | 0 |

1.20.6's frame diffs are lower than the 1.21 numbers (its dialog covers less of the frame), which is exactly
why the dialog threshold is 6% and not 10% — a threshold calibrated on 1.21 would have false-failed this.

**Fixture gap:** the 1.21 Creative fixture was copied to 1.21.x and 26.x run dirs only, so these 1.20.x runs
used their older **survival** worlds and only passed because the player happened not to die. A death screen
silently swallows every key press. Copy the fixture down before the rest of the 1.20.x sweep.

---

## D11 — NeoForge 1.20.2 crashed on the first HUD frame (missing runtime classpath)

First real client run of this target (earlier it was only "build green") died immediately in-world:

```
Description: Unexpected error
java.lang.NoClassDefFoundError: com/jagrosh/discordipc/exceptions/NoDiscordClientException
  at eu.the5zig.mod.platform.HudFrame.begin(HudFrame.java:51)
  at net.minecraftforge.client.gui.overlay.ExtendedGui.render(...)
```

Two separate problems had to be fixed:

1. **The dev runtime never had the libraries.** `build.gradle` declared DiscordIPC et al with
   `implementation` plus `jarJar`, which is right for the *shipped* jar but invisible to NeoGradle 7's dev
   runtime. NeoGradle 7 has **no** `additionalRuntimeClasspath` (that is a ModDevGradle configuration); it
   exposes **`localRuntime`** / `localRunRuntime` instead. Adding the same library set to `localRuntime`
   puts them where the mod's classloader can see them.
2. **A missing optional library must not kill the game.** `HudFrame.begin` caught `Throwable` only around
   `renderGameOverlay()`, but the failure was thrown while *initialising* the core class (before the draw),
   and the catch handler's own `The5zigMod.logger` reference re-triggered the same class init — so the error
   escaped. The guard now wraps the entire method body.

This target had been listed as "build green" in earlier sweeps. It was not runnable — the same lesson as
D7/D9/D10: a build proves nothing about whether the mod works once the world loads.

**Current state: blocked in dev runs.** `localRuntime` *does* resolve the artifacts (verified with
`gradle dependencies --configuration localRuntime`), but FML's `TransformingClassLoader` still does not see
them, and NeoGradle 7 exposes no `minecraftLibrary`-style hook (probed every configuration: only
`localRunRuntime`, `localRuntime`, `runtimeClasspath`, `runtimeOnly` exist). The HUD guard cannot help either:
the error is raised while resolving `The5zigMod` itself, so it is thrown before the `try` body executes and
re-thrown by the handler's own `The5zigMod.logger` access.

The **shipped** jar nests these libraries via `jarJar`, so a real install should not hit this — but that is
untested here, and "should" is not "verified". This target needs a packaged-jar install test rather than a dev
run to be called working.

---

## 34. Fabric 1.20.1 — pass (project root, not `versions/1.20.1-fabric`)

`versions/1.20.1-fabric/` holds only a stale `runs/` directory — the actual 1.20.1 Fabric project is the
**repository root** (`gradle.properties: minecraft_version=1.20.1`, Loom 1.7.4). Running the battery against
`versions/1.20.1-fabric` fails instantly with *"Project directory … is not part of the build defined by
settings file"*, which reads like a broken build but is a wrong working directory.

| section | result |
|---|---|
| boot | ✅ joined, no crash report, log clean |
| settings GUI | ✅ 23.7% of the frame changed |
| ESC | ✅ |
| coords GUI | ✅ 16.0% |
| SPACE / DELETE | ✅ |

Frame diffs are lower than the 26.x builds (23.7% / 16.0%) because the 1.20.1 GUI is smaller and
less blurred — another reminder that the threshold has to be per screen, not global.

## 35. Forge 1.20.1 — fixture, not a defect

First run showed the **death screen** (`You Died!`, X:-7.8 Y:68.9 Z:15.6) because `forge/run/saves` held an
old survival world; every key press went to the death dialog. Re-run with the 1.20.1 Creative world copied in
from the root project (same Minecraft version, so no conversion).

---

## D12 — 1.20.1 shipped without the HSQLDB driver (both loaders)

The log gate flagged `ClassNotFoundException: org.hsqldb.jdbc.JDBCDriver` on **both** 1.20.1 targets. Cause:
the earlier HSQLDB sweep added the driver to the `versions/*-fabric` projects and the NeoForge builds, but
**not** to the 1.20.1 pair — the repo-root Fabric project and `forge/`:

```
build/libs/5zig-renewed-1.20.1-fabric-4.0.0-alpha.1.jar   hsqldb=0
forge/build/libs/…-1.20.1-forge-4.0.0-alpha.1.jar         hsqldb=0
```

The core's conversation store is HSQLDB, so `Class.forName('org.hsqldb.jdbc.JDBCDriver')` threw and the chat
stack ran against a null connection — a real functional failure that a HUD/GUI screenshot cannot show. Fixed:

- root `build.gradle`: `implementation 'org.hsqldb:hsqldb:2.7.4'` + `include(…)` (Fabric nesting)
- `forge/build.gradle`: `minecraftLibrary 'org.hsqldb:hsqldb:2.7.4'` + a `['org.hsqldb','hsqldb','2.7.4','[2.7.4]']`
  entry in the `jarJarModules` list

Both jars now report `hsqldb=1`. Re-verified in-client: `f201c` and `f1201c` both PASS with an **empty**
log-gate report (no missing class, no silent-failure signature), menu 23.6% / 23.3%.

**This is why the log gate is part of the battery and a green GUI is not the finish line:** D12 breaks a whole
core subsystem while every screenshot looks perfect.

---

## 36. 1.20.1 — Fabric and Forge both pass (after D12)

| target | menu | coords | crashes | log gate |
|---|---|---|---|---|
| Fabric 1.20.1 (`f1201c`) | ok 23.3% | ok | 0 | clean (was `ClassNotFoundException: hsqldb`) |
| Forge 1.20.1 (`f201c`) | ok 23.6% | ok | 0 | clean (was `ClassNotFoundException: hsqldb`) |

Both loaders reach the settings GUI and the coordinates dialog, no crash reports, and the log gate is
silent. 1.20.1 is the first version line in this sweep that is **fully** verified rather than "GUI works but
the driver is missing".

---

## 37. 1.19.x — passes once the client is driven in

`--quickPlaySingleplayer` **does not exist before 1.20**. On 1.19.x the client logs
`Completely ignored arguments: [--quickPlaySingleplayer, New World]` and parks on the title screen, so the
battery reported "no world within N seconds" for 1.19.4 / 1.19.3 / 1.19.2 — three failures that had nothing
to do with the mod.

`deep-test-target.sh` now detects that log line and walks the client in by itself. The first attempt (Enter,
Tab, Enter) did nothing: **nothing is focused on the title screen, so Enter is a no-op.** The working sequence
is: read the window geometry, click the first menu button just above centre, wait, then `Tab` (off the world
list's search box) and `Enter`.

| target | menu | coords | crashes |
|---|---|---|---|
| Fabric 1.19.4 (`f1194c`) | ok 37.0% | ok · X=5/Z=10 | 0 |
| Fabric 1.19.3 (`f1193d`) | ok 35.8% | ok · X=5/Z=10 | 0 |
| Fabric 1.19.2 (`f1192e`) | ok 37.1% | ok 37.4% | 0 |
| Fabric 1.19.1 (`f1191d`) | ok 37.1% | ok | 0 |
| Fabric 1.19 (`f119d`) | ok 37.0% | ok | 0 |

`Tab`+`Enter` in the world list is **not** reliable: on 1.19.2 it leaves focus in the search box and never
starts the world (four attempts, no join). The reliable step is a **click on "Play Selected World"** — the
bottom row, ~31% across and ~82% down the window. The failover now clicks that as well as sending the keys.

The first failover attempt was a **race**: `Completely ignored arguments` is logged while parsing argv, long
before the title screen exists, so a single click 25 s later was a no-op on the slower builds (1.19.3 needed
two attempts; its battery log shows `drive attempt 1 sent` / `drive attempt 2 sent`). The failover now retries
up to four times, re-reading the window geometry each round and re-checking for a joined world.

Fixture: Creative world created on **1.19** (the oldest of the family) and copied to all five 1.19.x projects —
a 1.21 world cannot be used here, world data only upgrades.

---

## D13 — FALSE ALARM: 1.18.x keybinds work; the battery never got in-world

Fabric 1.18.2 joins a world and draws the full HUD (`The 5zig Mod vDEV`, `FPS: 54`, `X/Y/Z`, facing), yet
**Right Shift opens nothing and F12 opens nothing** — frame diff before/after the key press is **0.01%**,
against ~37% on 1.19.4 and ~23% on 1.20.1 where both screens open.

Not the fixture, not the harness: the room is in-world, the HUD is live, and the same battery drives
1.19.x correctly. So the keybind path is broken on 1.18.x specifically.

What was ruled out:

| hypothesis | evidence |
|---|---|
| world/fixture problem | HUD renders; the screen diff never changes at all |
| options.txt shows no `the5zigmod.key.*` | the client is SIGTERM'd and never saves options — **1.19.4/1.19.2 also show zero and their bindings work** |
| core never initialised | the HUD is drawn by the core, so it did |

1.18.x is the **fabric-keybindings-v0** era (`fabric-keybindings-v0 0.2.10` on the classpath), which is the
prime suspect: `Keybinds.bootstrap()` runs from the shared Fabric entrypoint on every target, so the
difference must be in how 1.18.x's mappings reach the live `Options`.

Also seen in these logs (separate, known-vestigial): `SQLSyntaxErrorException: … object not found: VERSION /
NETWORK_STATS` — the HSQLDB schema was never created; the friends/party backend is dead upstream.

Investigation delegated with client access (deleg_e97650e1).

**Verdict: not a defect.** With a hand-driven debug session on 1.18.2 the binding works exactly as designed.
The instrumentation (temporary mixin logging, since reverted) printed:

```
[D13] key=344 action=1 screen=null arrayLen=47 settingsInArray=true
      settings=the5zigmod.key.settings clicks=1 down=true save=key.keyboard.right.shift
      mapHit=the5zigmod.key.settings mapSize=41
[D13] key=344 action=0 screen=eu.the5zig.mod.platform.GuiHandle@17307576 clicks=0 down=false
```

The mapping is in `Options#keyMappings`, the GLFW→`KeyMapping` map hits it, `clickCount` increments, and the
screen that opens is the 5zig `GuiHandle`. A screenshot confirms the **full settings menu renders** — Plugins,
Show Mod, Mod Scale, Display Options, Coordinates Clipboard, Done, Chat.

**The real cause of the 0.0% frame diff:** the world list does **not auto-select** a world on 1.18.2, so
"Play Selected World" is **greyed out and silently does nothing** — the battery clicked it and the client
stayed on the world list. The run that recorded `joined=1` did reach a world, so its keys were pressed
somewhere that swallowed them; the likeliest explanation is that the press landed during world load, but
that specific run was not instrumented and **that part is not proven**.

The drive sequence now clicks the list, presses **Down** to select the first world, then clicks Play.

**Lessons worth keeping:** (a) `joined the game` must be counted only in the current run's log — truncate it
first; (b) a disabled Minecraft button does nothing at all and looks identical to a broken hotkey; (c) two
identical screenshots do not mean "frozen" — the Select World screen is static by design, and the thread dump
showed a normal GLFW wait.

---

## 38. 1.18.2 — both GUIs proven working; the automated drive is flaky

No single battery run passed both screens on this target, but the evidence across runs is unambiguous:

| run | settings GUI | coords GUI | what it shows |
|---|---|---|---|
| `f1182e` | **ok 15.96%** | fail (pause menu open) | the settings screen genuinely opens |
| `f1182f` | fail (first key lost) | **ok 36.39%** | the clipboard screen genuinely opens |
| manual | full menu screenshot | — | Plugins, Show Mod, Mod Scale, Display Options, Coordinates Clipboard, Done, Chat |

Plus the instrumented log proving the keybind path end-to-end:
`clicks=1 down=true mapHit=the5zigmod.key.settings` → `screen=eu.the5zig.mod.platform.GuiHandle`.

So **1.18.2 is functionally fine** — the mod's GUIs open and render. What fails is the harness driving it,
for three distinct reasons, all now fixed in the script:

1. **A dying window was being addressed.** `pkill` + `sleep 3` is not enough on 1.18.x; the old window is
   still mapped, so `xdotool search` returned it and every click/key went to a dead client. The script now
   waits until no minecraft window exists (up to 20 s).
2. **The world list does not auto-select**, so "Play Selected World" is greyed out and silent. The drive now
   clicks the list and presses **Down** before Play.
3. **The first key press after joining is swallowed** while chunks stream in, and on 1.18.x a single Escape
   from a 5zig screen leaves the vanilla pause menu open. Both steps now retry once with a longer settle.

Residual flakiness: the drive still occasionally fails to reach a world at all (`f1182g`, four attempts).
That is a harness reliability issue on 1.18.x, not a mod defect, and it is recorded as such rather than
counted as a pass or blamed on the port.

### 1.18.1 / 1.18 — coordinates GUI works, settings menu does not render

| target | menu | coords | joined | crashes |
|---|---|---|---|---|
| Fabric 1.18.1 (`f1181g`) | FAIL 0.8% | **ok 29.7%** | yes | 0 |
| Fabric 1.18 (`f118g`) | FAIL 0.9% | **ok 29.7%** | yes | 0 |

Same signature on both: the clipboard GUI opens every time, the settings GUI never changes the frame.

**Correction — this is not a 1.18.0/1.18.1 rendering gap.** I first suspected one, then compared the three
platform trees properly: `platform/1.18`, `1.18.1` and `1.18.2` are **identical apart from javadoc comments**
(checked with a per-file md5 pass, then a `difflib` diff of the files that differed — the only changed lines
are the version number in prose). Identical code cannot produce a version-specific rendering bug on its own,
and 1.18.2 itself *failed the same assertion on another run* (`f1182g`, 0.0001).

So the honest reading is: **the settings-menu step is flaky on all three 1.18 targets**, while the
coordinates step is reliable (15.96% / 29.7% / 29.7% across runs). Most likely the first hotkey after joining
is still being dropped — the retry helps on 1.18.2 but not always on the others. That is a harness
limitation, recorded as such; it is not evidence of a port defect, and these three targets should be
re-tested before anything is concluded either way.

Not investigated further in this pass: it is 3 targets of 40, the cause is bounded, and the 1.17/1.16 lines
had not been tested at all. Recorded here rather than marked passing.

---

## 39. 1.17.x and the 1.18 line — where the harness stops being trustworthy

| target | menu | coords | joined |
|---|---|---|---|
| Fabric 1.17.1 (`f1171h`) | FAIL 0.39% | **ok 23.8%** | yes |
| Fabric 1.17 (`f117`) | FAIL 0.39% | **ok 23.8%** | yes |
| Fabric 1.18.2 | ok once (15.96%), fail otherwise | ok 36.4% | yes |
| Fabric 1.18.1 | FAIL 0.8% | **ok 29.7%** | yes |
| Fabric 1.18 | FAIL 0.85% | **ok 29.7%** | yes |

**What is proven:** the coordinates clipboard opens reliably on every one of these targets, and a *manual*
screenshot on 1.17.1 shows the full **settings menu rendered** ("The 5zig Mod bDEV", Plugins (4), Show Mod,
Mod Scale, Display Options, Coordinates Clipboard, Done, Chat). So the settings GUI is not broken on 1.17/1.18.

**What is not proven:** that the automated battery can catch it. Widening the wait from 5 s to 12 s did not
change the result (identical 0.39% on 1.17.1), while the manual session showed the menu up after a longer,
human-paced sequence. The honest description is: *the battery's settings step is unreliable on 1.17–1.18 under
software rendering*, and its FAIL there is not evidence of a mod defect.

**Why I stopped:** five targets out of forty, with one screen already proven by hand on the earliest of them,
versus 16/17/1.16 still completely untested. Spending more of the session on the harness for these five would
be polishing the ruler instead of measuring — so they are recorded as **inconclusive (harness)**, not pass and
not fail, and the reason is written down for whoever picks them up.

---

## D14 — pre-1.19: bindings that OPEN A GUI do not fire, bindings that TOGGLE state do (open)

Reproduced by hand on **1.16.2** (live client, Creative world, HUD rendering at 28 FPS):

| key | binding | effect | frame change |
|---|---|---|---|
| **M** | `toggle_mod` (no GUI, flips a boolean) | **works** — HUD hides/shows | **17.0%** |
| **Shift_R** | `settings` (opens `GuiSettings`) | nothing | 1.3% (HUD only) |
| **F12** | `coordinates_clipboard` (opens a GUI) | nothing | 0.0% |

Same asymmetry on **1.17, 1.17.1, 1.18, 1.18.1** (settings never fires; the clipboard fires *sometimes*), and
1.18.2 opened the settings menu **once** in a manual session. **1.19.x and newer are unaffected** — both screens
open at ~37%.

What this rules out:

| ruled out | evidence |
|---|---|
| keybind registration | `M` fires, so the bindings and the poll path work |
| a crash while opening | no exception and no crash report; the Netty/HSQLDB noise is unrelated (dead chat backend) |
| `displayScreen` differing by version | the `displayScreen(Gui)`/`displayScreen(Object)` bodies are **identical** in 1.16.2, 1.18.2 and 1.19.4 |

So the failure is inside the GUI-open path only — most likely `GuiSettings`' constructor or the screen
wrapper failing silently on 1.16–1.18 (the listener catches and swallows), or the screen being set and then
overwritten in the same tick. Next step is a targeted instrumentation run on 1.16.2 of
`KeybindingListener` → `displayScreen` → `GuiHandle.<init>`, exactly as was done for D13 on 1.18.2.

Affects: 1.16.2–1.16.5, 1.17, 1.17.1, 1.18, 1.18.1 (1.18.2 partial). Not counted as passing.

### D14 — corrected root-cause direction (my first instrumentation read the wrong object)

I first added a probe to `platform/1.16.2/.../platform/Keybinding.java` and, seeing zero calls, wrote that the
core never polls these bindings. **That was wrong.** `Variables.createKeybinding` delegates to
`Keybinds.wrap(...)`, which returns a private `FiveZigKeybinding` (a different class), so the probe was
attached to an object the core never touches. The "never invoked" claim is withdrawn.

Reading the real implementation:

```java
private static final class FiveZigKeybinding implements IKeybinding {
    private final KeyMapping mapping;
    @Override public boolean callIsPressed() { return mapping.consumeClick(); }
}
```

`consumeClick()` is **one-shot and edge-triggered**: it returns true only if vanilla has queued a click for
that `KeyMapping`. Vanilla queues clicks in `KeyboardHandler` for mappings it knows about — i.e. those in
`Options.keyMappings`. That fits every observation:

| observation | explained by |
|---|---|
| settings/coords never open on 1.16.2–1.18.1 | their mappings were never added to `Options.keyMappings`, so no click is ever queued |
| `M` still toggles the HUD | that path runs through the raw key-dispatch hook, not `consumeClick()` |
| 1.19.x works (~37%) | those platforms register correctly (or use a level-triggered poll) |
| `f1181g`/`f118g` opened the clipboard at 29.7% | timing-dependent: a click can be queued and consumed within one tick on faster frames |

**CONFIRMED by client run (1.16.2, vanilla Options → Controls, scrolled to the very bottom):** there is
**no "The 5zig Mod" category and no 5zig entries anywhere in the key list** — only vanilla Movement, Gameplay,
Inventory and Miscellaneous. So the mod's `KeyMapping`s are **never registered with vanilla**, which makes
`consumeClick()` false forever and kills every poll-based binding. This also explains why `run/options.txt`
never contains a `key_the5zigmod.*` line on these versions.

The failure is therefore in `Keybinds` registration timing on 1.16–1.18 (the Fabric `KeyBindingHelper` path),
**not** in `displayScreen`, not in the key press, and not in the GUI code. `M` works because that toggle is
served by the raw key-dispatch hook, which does not need a registered mapping.

**Sharper cause, from the 1.16.2 startup log:**

```
[5zig Renewed] Registered 13 key bindings          <- the registrar WAS called 13 times
Reloading ResourceManager: ... (Fabric Key Binding API (v1), ...)
   - fabric-keybindings-v0 0.2.2+3cc0f0907d         <- and v0 is ALSO on the classpath
```

So `Keybinds.bootstrap()` runs, `setRegistrar(KeyBindingHelper::registerKeyBinding)` is called, and the mod
reports 13 registrations — but none of them reach `Options.keyMappings`. The likely reason is that **both
keybindings modules are present**: v0 (`fabric-keybindings-v0`) and v1 (`Fabric Key Binding API (v1)`), so
the helper the mod calls and the mixin that feeds `Options` belong to different modules, and the registrations
land in a registry nothing reads.

Fix direction: pin the era to **one** module — for 1.16–1.18 that is v0 — and call that module's
`KeyBindingHelper` explicitly, rather than relying on whatever `KeyBindingHelper` resolves to when both are
present.
