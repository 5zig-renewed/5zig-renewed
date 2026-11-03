# 1.21.4 Fabric port notes

Era E8 (1.21.2–1.21.4). Built with `gradle-8.10.2` / JDK 21, `--no-daemon`, target Java 21.

| Piece | Version |
|---|---|
| Minecraft | 1.21.4 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.16.10 |
| Fabric API | 0.119.4+1.21.4 |
| Mixin compatibility level | `JAVA_21` |

Build: `cd versions/1.21.4-fabric && JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.21.4-fabric-4.0.0-alpha.1.jar`

Entrypoint: `eu.the5zig.mod.fabric.FiveZigFabric1214` (registers the 5zig payload types before delegating
to the shared `FiveZigFabric`).

## Decompiled reference

`mc-src/1.21.4/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`), from
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.4-loom.mappings.1_21_4.layered+hash.2198-v2/`.

## Base

`platform/1.21.4` is a copy of `platform/1.21.1` (E7 is closer to E8 than E9: the item-model rework of
1.21.4 is additive, while 1.21.5 replaced the whole GUI render stack with render states). The authlib-6
replacements (`AccountManager`, `PacketEncryption`) and the `sourceSets` excludes are unchanged.

## API breaks 1.21.1 → 1.21.4

- **`GuiGraphics` draw calls became pipeline-driven.** `setColor` is gone and every `blit`/`blitSprite`
  overload now takes `Function<ResourceLocation, RenderType>` first and an optional trailing ARGB tint.
  `GuiCompat` grew 1.21.1-shaped wrappers (`blit`, `blitScaled`, `blitSprite`, `blitRepeating`) that inject
  `RenderType::guiTextured` and the tint. Call sites touched: `Button`, `Slider`, `GuiList`, `FileSelector`,
  `GuiHandle`, `Variables`, `Overlay`.
  `RenderSystem.setShaderColor` still exists and still feeds `ColorModulator`, so `GLUtilHandle#setColor`
  keeps tinting (the GUI shader `position_tex_color` declares the uniform).
- **Toasts were reshaped.** `Toast#render(GuiGraphics, ToastComponent, long)` became
  `getWantedVisibility()` + `update(ToastManager, long)` + `render(GuiGraphics, Font, long)`;
  `ToastComponent` is `ToastManager` and `Minecraft#getToasts()` is `getToastManager()`.
- **`EffectRenderingInventoryScreen` was removed.** The inventory effect panel is now `EffectsInInventory`,
  and container screens advertise it through `Screen#showsActiveEffects()` (used by
  `getPotionEffectIndicatorHeight`).
- **`AbstractClientPlayer#getFieldOfViewModifier()` became `(boolean scoping, float partialTick)`**
  (mixin descriptor `(ZF)F`).
- **`ClientboundPlayerPositionPacket`** carries a `PositionMoveRotation`: `change().position()/yRot()/xRot()`
  instead of `getX()/getY()/getZ()/getYRot()/getXRot()`.
- **`AbstractWidget#clicked(double,double)` was removed**; `isMouseOver` is the replacement.
- **Texture stack rewrite.** `TextureManager#getTexture(ResourceLocation, AbstractTexture)` and
  `register(String, AbstractTexture)` are gone (`register(ResourceLocation, AbstractTexture)` is void and no
  longer mints a `dynamic/<name>_<n>` path). `SimpleTexture` is now a `ReloadableTexture` that must produce
  `TextureContents` from a pack, so `platform.SimpleTexture` derives from `AbstractTexture` and uploads by
  hand. Cape/frame lookups use platform-side caches instead of `getTexture(location, null)`.
- **`NativeImage`**: `setPixelRGBA` → `setPixel`; the 11-argument `upload` collapsed to 8 with a single
  `close` flag.
- **`GlStateManager._clear(int, boolean)` → `_clear(int)`.**
- **`InteractionResult#shouldSwing()`** is gone; successful results carry `SwingSource` (NONE/CLIENT/SERVER).
- **Registry lookups**: `get(ResourceLocation)` returns `Optional<Holder.Reference<T>>` (`getValue` is the
  value getter) and `getHolder(int)` became `get(int)`.

## Fixed along the way

`textures/gui/icons.png` (the 1.8-era sheet the core blits hunger/saturation icons from) no longer exists in
1.21.x — the 1.21.1 platform still pointed at it. `drawIngameTexturedModalRect` now maps the six 9x9 icons
(u = 16/52/61/88/97/133, v = 27) onto the modern `hud/food_*` GUI sprites, so the food/saturation overlays
render instead of showing the missing-texture checkerboard.

## Known gaps (inherited from the 1.21.1 fork)

- Legacy 5zig plugin channels: only the three registered payload types can be sent/received (1.21.x typed
  custom payloads).
- 5zig capes are resolved and uploaded but still cannot be spliced into the immutable `PlayerSkin`.
- Enchantment damage bonus / damage protection terms are omitted from tooltips and the HUD.
- Animated capes advance from the registry tick rather than the removed `Tickable` contract (the texture
  manager still ticks `SimpleTickingTexture` instances registered with it).

## Verification performed

- `gradle --no-daemon clean build` succeeds; jar contains remapped classes, `fabric.mod.json` (minecraft
  `~1.21.4`), the access widener and `5zig-renewed-1.21.4-fabric-refmap.json`.
- `tools/check_mixins.py` logic re-run against the 1.21.4 named jar: 35/35 `@Inject`/`@ModifyVariable`
  targets present (no residues of the 1.21.1 `getFieldOfViewModifier()F` target).
- Decompiled sources were used for every changed signature; nothing was guessed.
- No client was launched (per task rules), so this is compile + static-target verification only.
