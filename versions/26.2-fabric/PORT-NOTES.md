# 5zig Renewed — Minecraft 26.2 / Fabric

Status: **tree compiles, jar builds** (`build/libs/5zig-renewed-26.2-fabric-4.0.0-alpha.1.jar`,
1 664 entries). This is the 26.1.2 port re-targeted at 26.2; the platform layer is a copy of
`platform/26.1.2` with the deltas listed below.

## Toolchain (verified)

| Piece | Value |
|---|---|
| Minecraft | 26.2 (unobfuscated jar — **no `mappings` line at all**) |
| Fabric Loader | 0.20.1 (`meta.fabricmc.net/v2/versions/loader/26.2`) |
| Fabric API | 0.161.0+26.2 |
| Loom | `net.fabricmc.fabric-loom` **1.19.3** (requires Gradle ≥ 9.8) |
| Gradle | **9.8.2** (`~/.tools/gradle/gradle-9.8.2`) |
| JDK | **25** (`/usr/lib/jvm/java-25-openjdk`); `options.release = 25` |
| authlib (game) | 9.0.75 |
| Mixin | sponge-mixin 0.17.5+mixin.0.8.7 → `compatibilityLevel: JAVA_25` |

Build:

```bash
cd versions/26.2-fabric
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  ~/.tools/gradle/gradle-9.8.2/bin/gradle --no-daemon build
```

## What 26.2 actually changed (the port delta vs 26.1.2)

Seven files changed, one added. Everything else in `platform/26.2` is byte-identical to
`platform/26.1.2`.

### `Gui` was split: the HUD now lives in `net.minecraft.client.gui.Hud`

| 26.1.2 | 26.2 |
|---|---|
| `Gui#extractRenderState(GuiGraphicsExtractor, DeltaTracker)` | `Gui#extractRenderState(DeltaTracker, boolean, boolean)` — builds the extractor itself and calls `hud.extractRenderState(graphics, deltaTracker)` |
| `Gui#tick(Z)` | `Hud#tick(Z)` |
| `Gui#setOverlayMessage(Component, Z)` | `Hud#setOverlayMessage(Component, Z)` |
| `Gui#extractHotbarAndDecorations(GuiGraphicsExtractor, DeltaTracker)` | `Hud#…` (same descriptor) |
| `Gui#getChat()`, `#getGuiTicks()`, `#getMobEffectSprite(Holder)` | `Hud#…` |
| `Gui(Minecraft)` | `Gui(Minecraft, Hud, GuiRenderState)`; `Minecraft.gui.hud` is a public final field |

Consequences:

* **New `MixinHud`** (`@Mixin(Hud.class)`) owns the frame bracket (`HudFrame.begin/end`), the
  hotbar pass and the `tick`/`setOverlayMessage` hooks. It is listed in
  `loader/fabric-26.2/resources/mixins.the5zigmod.json`.
* **`MixinGui` was reduced to the screen hook** — it no longer has any HUD injection.

### Screens moved off `Minecraft`

* `Minecraft#setScreen(Screen)` → **`Gui#setScreen(Screen)`** (same body: `removed()`,
  `setLastInputType(NONE)`, title/death/chat fallback, mouse grab, `updateTitle()`).
  `Minecraft#setScreenAndShow` = `gui.setScreen` + a forced `renderFrame(false)`.
* `Minecraft.screen` field → **`Gui#screen()`** accessor.
* The text-field flush hook (`The5zigMod.getKeybindingManager().flushTextfields()`) therefore moved
  from `MixinMinecraft#setScreen` to `MixinGui#setScreen`.

### Smaller removals that the platform used

| 26.1.2 | 26.2 replacement |
|---|---|
| `Options#hideGui` (F1) | `Minecraft.gui.hud.isHidden()` (`Hud#toggle()` flips it) |
| `Minecraft#getToastManager()` | `Gui#toastManager()` |
| `Gui#getChat()` | `Gui#hud.getChat()` |
| `Gui#getGuiTicks()` | `Gui#hud.getGuiTicks()` |
| `Gui#getMobEffectSprite(Holder<MobEffect>)` | `Hud.getMobEffectSprite(Holder<MobEffect>)` (static) |
| `LevelRenderer#getEntityStatistics()` | `Minecraft.levelExtractor.entityStatistics()` — **@Nullable**, same `"E: a/b, SD: n"` format |
| `ChatFormatting#isColor/#isFormat/#getChar/#getColor` | gone: the enum now only carries the code (`toString()` = `"§c"`, `getByCode(char)`) and the palette moved to `TextColor` (`fromLegacyFormat`, named constants) |
| `GuiGraphicsExtractor#entity(… Vector3f …)` / `#skin(PlayerModel, …)` / `#sign(…)` | `entity(… Vector3fc …)` / `skin(Model.Simple, …)` / `sign` removed — none of them are used by this platform |

`GuiGraphicsExtractor` is otherwise **unchanged** in 26.2: `text(Font, String|Component|FormattedCharSequence, x, y, colour, dropShadow)`, `fill`, `fillGradient`, `blit` (normalised UVs), `blitSprite`, `enableScissor`/`disableScissor`, `pose()` (`Matrix3x2fStack`) all keep their 26.1.2 signatures.

## Colour trap (unchanged in 26.2 — still strict ARGB)

26.x colours are strict ARGB: a legacy `0xRRGGBB` constant has alpha 0 and draws **nothing**, with
no error. `forceOpaque(int)` (`(c & 0xFF000000) == 0 ? c | 0xFF000000 : c`) is applied on every
colour-carrying draw that takes a caller-supplied colour:

* `Variables#drawString` / `#drawCenteredString` → `graphics.text(..., forceOpaque(color), ...)`.

Every other draw call in `platform/26.2` passes an explicit `0xFF……` constant or an
alpha-composed value, so it is opaque by construction. No `forceOpaque` call was dropped in this
port.

## Core overlay (unchanged)

`core/` is still compiled through `coreCompatTree` (core minus 12 authlib-4/commons-lang3 files,
plus `compat-src/` versions). 26.2 ships authlib 9; the overlay files compile against it unchanged
(`GameProfile` is still a record with `id()`/`name()`; there is no password/Yggdrasil client).

## Verification done here

* `gradle clean build` → `BUILD SUCCESSFUL`; `build/libs/5zig-renewed-26.2-fabric-4.0.0-alpha.1.jar`,
  1 664 entries; every `platform/26.2` source has a matching `.class` in the jar.
* `check-mixins.py` — every `@Inject`/`@ModifyVariable` method descriptor exists in the **26.2**
  jar (`all mixin targets resolve`).
* `check-handlers.py` — every injector handler's parameter list matches its target descriptor
  (`all mixin handlers match their targets`).
* Both scripts were re-pointed from 26.1.2 to 26.2 (jar path, `platform/…` path).
* Mixin AP emitted `5zig-renewed-26.2-fabric-refmap.json` (identity named→intermediary map,
  12 injector classes incl. `MixinHud`/`MixinGui`); `processResources` put the `refmap` key into
  `mixins.the5zigmod.json`. All 14 mixin classes listed there are in the jar.
* `fabric.mod.json`: `minecraft ~26.2`, `java >=25`, `fabricloader >=0.20.1`.
* No Minecraft client was started from this build.

## Real vs. stubbed (carried over unchanged from 26.1.2)

The same twelve `compat-src` files and the same stubs as 26.1.2; 26.2 did not remove or add
anything in those paths, so none of them regressed:

| Item | State |
|---|---|
| `Variables.sendCustomPayload(channel, bytes)` | `UnsupportedOperationException` — 26.x has typed `CustomPacketPayload`s, no raw plugin channels |
| `SimpleTexture#checkTextureUploaded` / `#getId` | `UnsupportedOperationException` — no dynamic GPU upload / GL texture ids |
| `SimpleTickingTexture` | frame bookkeeping only |
| `GLUtilHandle` colour/blend/depth | no-ops (26.x pipelines own that state) |
| `WrappedItemStack` "+damage/+armor" overlay | not drawn (attribute modifiers are a data component) |
| `MixinAbstractClientPlayer` (capes), `MixinTitleScreen` (version stamp), `MixinDisconnectedScreen` (countdown draw) | inert mixins — those hooks no longer exist |
| `GuiList` / `FileSelector` custom backgrounds | flat fill / texture blit |

## Remaining gaps

* **No runtime test.** The jar is compile-verified and mixin-verified only; the HUD has not been
  rendered on 26.2 (no client runs in this environment). The 26.1.2 build *was* client-verified and
  the draw path is unchanged, but `Hud` is a new injection site and needs one visual pass.
* `Hud#extractRenderState` only runs when the level is being rendered (`GameRenderer#extract`:
  `resourcesLoaded && advanceGameTime && level != null`), so the overlay no longer draws on the
  title screen or over a paused single-player game. 26.1.2 hooked `Gui#extractRenderState`, which
  ran unconditionally; on 26.2 the HUD belongs to `Hud` and is world-only. This matches how the
  vanilla HUD behaves. If the overlay must cover the title screen too, the bracket has to move to
  `Gui#extractRenderState(DeltaTracker, boolean, boolean)` (and take the extractor from a
  `@Local`), not to `Hud`.
* Network-facing features (custom payloads, Hypixel stats) stay stubbed as above.
