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
