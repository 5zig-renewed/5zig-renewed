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
| T3 GUI keyboard | ⏳ waiting on the key-code fix (D3) | defect D3 |
| T4 chat (`F4`) | not yet run | — |
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
