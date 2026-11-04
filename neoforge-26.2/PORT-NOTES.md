# 26.2 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | **9.8.2** (`~/.tools/gradle/gradle-9.8.2`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | **25** (`/usr/lib/jvm/java-25-openjdk`), target class file 69 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **26.2.0.93** (FancyModLoader 11.0.16, neoform `26.2-2`) |
| Minecraft | **26.2 — unobfuscated**, no mappings anywhere |
| Mixin | `compatibilityLevel: JAVA_25` |

Build:

```bash
cd neoforge-26.2
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  ~/.tools/gradle/gradle-9.8.2/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-26.2/build/libs/5zig-renewed-26.2-neoforge-4.0.0-alpha.1.jar`
(21.4 MB, eleven libraries nested under `META-INF/jarjar/`).

## The Gui/Hud split

26.2 moved the in-game overlay out of `Gui` into a new `net.minecraft.client.gui.Hud`. The platform
layer already ships both halves (`platform/26.2/.../MixinGui.java` for the `setScreen` hook and
`platform/26.2/.../MixinHud.java` for the HUD brackets), and the mixin config lists both. What
changes on NeoForge is the hotbar method **name**:

* vanilla 26.2 `Hud`: `extractHotbarAndDecorations(GuiGraphicsExtractor, DeltaTracker)`
* NeoForge 26.2 patched `Hud`: **`extractHotbar(GuiGraphicsExtractor, DeltaTracker)`**
  (`patches/net/minecraft/client/gui/Hud.java.patch`)

`loader/neoforge-26.2/java/eu/the5zig/mod/mixin/MixinHud.java` is the platform class with that one
target corrected, and `the5zig/mod/mixin/MixinHud.java` is excluded from the platform source root.
Everything else in it — `extractRenderState` HEAD/RETURN, `tick(Z)V`, `setOverlayMessage` — matches
the patched jar unchanged.

`Gui` is *not* replaced: NeoForge 26.2 keeps `Gui#setScreen(Screen)`, so the platform's one-method
`MixinGui` compiles and resolves as-is. (Note `Gui#extractRenderState` is
`(DeltaTracker, boolean, boolean)` on 26.2, not `(GuiGraphicsExtractor, DeltaTracker)` — the
platform does not touch it.)

## Source layout

* `build/core-compat` — `core/` with the twelve authlib-7 / commons-lang3-3.19 files filtered out.
* `versions/26.2-fabric/compat-src` — the 26.x replacements (read-only reuse of the verified 26.2
  Fabric overlay).
* `platform/26.2/eu`, `loader/neoforge-26.2/java`.
* `eu/the5zig/mod/installer/**` and `eu/the5zig/mod/mixin/MixinCommonMinecraft.java` excluded.

## Glue deltas vs 26.1.2 NeoForge

1. **`MixinHud` instead of `MixinGui`** for the HUD: the platform's Hud mixin is replaced by the
   loader copy with `extractHotbar`; the platform's `Gui` mixin is kept untouched.
2. **Mixin config lists 14 classes** — `MixinHud` added next to `MixinGui`.
3. **`loaderVersion="[11,)"`**, `neoforge` `[26.2,27)`, `minecraft` `[26.2,26.3)`.
4. **No `additionalRuntimeClasspath`** on 26.x (FML 11 mod-locator rework).
5. **HttpClient + httpcore nested**; eleven nested jars total.

## Verification performed

* `gradle build` → `BUILD SUCCESSFUL` (59 s) against `minecraft-patched-26.2.0.93.jar`.
* `scripts/verify-neoforge-26x.py`: 29/29 mixin targets resolve —
  `MixinHud` → `Hud` with 4 targets including `extractHotbar`, `MixinGui` → `Gui` with `setScreen`.
* The compiled `MixinHud.class` in the jar contains **no** `extractHotbarAndDecorations` reference.
* TOML parses, `${version}` → `4.0.0-alpha.1`, `[[mixins]]` present, ranges as above.
* Eleven nested jars, valid zips, `metadata.json` matches; 1478 classes at major 69; no `net/fabricmc`
  references; `PluginMessages` absent.

## Not verified

* **No client run** (task rule) — the parent verifies launch behaviour.
* 26.2 is a stable NeoForge line (26.2.0.57 … 26.2.0.93), but the HUD hook has not been seen
  drawing on screen.
