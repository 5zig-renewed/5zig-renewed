# 1.21.3 Fabric port notes

Era E8 (1.21.2–1.21.4). Built with `gradle-8.10.2` / JDK 21, `--no-daemon`, target Java 21.

| Piece | Version |
|---|---|
| Minecraft | 1.21.3 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.16.10 |
| Fabric API | 0.114.1+1.21.3 |
| Mixin compatibility level | `JAVA_21` |

Build: `cd versions/1.21.3-fabric && JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.21.3-fabric-4.0.0-alpha.1.jar`

Entrypoint: `eu.the5zig.mod.fabric.FiveZigFabric1213` (registers the 5zig payload types before delegating
to the shared `FiveZigFabric`).

## Decompiled reference

`mc-src/1.21.3/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`), from
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.3-loom.mappings.1_21_3.layered+hash.2198-v2/`.

## Base

`platform/1.21.3` is a copy of `platform/1.21.4`. Both sit in E8, so the 1.21.2+ GUI rewrite
(`GuiGraphics#blit` taking a `RenderType` factory, `setColor` gone, reshaped toasts, the `EffectsInInventory`
panel, the `PositionMoveRotation` teleport packet, the `ReloadableTexture` stack) is present in both and the
1.21.4 adapters are kept verbatim. 1.21.3 is _older_ than 1.21.4, so every 1.21.4-era API the platform uses
was re-checked against the 1.21.3 jar; the only source change needed was in `SimpleTexture`.

## API delta 1.21.3 → 1.21.4 that affects this platform

- **`AbstractTexture#load(ResourceManager)` still exists** (abstract) in 1.21.3; 1.21.4 dropped it when
  `SimpleTexture` became a `ReloadableTexture`. `platform.SimpleTexture` now implements it as a no-op.
- **`NativeImage#upload` still takes the per-flag shape** in 1.21.3:
  `upload(level, x, y, skipRows, skipPixels, width, height, blur, close)`. 1.21.4 collapsed the trailing
  blur/clamp/mipmap flags into a single `close` boolean. The upload call uses the 1.21.3 nine-argument
  form; filtering is still applied afterwards through `AbstractTexture#setFilter`.
- **`AbstractWidget#clicked(double,double)` still exists** in 1.21.3 (`protected`), unlike 1.21.4. Its body
  is identical to `isMouseOver`, which the platform already uses, so no change was needed.

Nothing else in the ~107 `net.minecraft` types the platform imports differs in a way that matters:
`Gui`, `GuiGraphics`, `Screen`, `ChatComponent`, `Toast`/`ToastManager`, `RenderType`, `TextureManager`
and `Minecraft` differ only by additive 1.21.4 members (`getItemModelResolver`, `clearTitles`,
`registerTextures`, `Style#withShadowColor`, resin items, …) or by 1.21.3-only legacy helpers
(`TextureManager#preload`, `getTexture(loc, missing)`) the platform does not call.

## Fixed along the way

The `SimpleTexture` upload/`load` pair above — both were hard compile errors against 1.21.3 and are the
only port edits. 1.21.4's `textures/gui/icons.png` → `hud/food_*` sprite remap, the toast reshape and the
`blit` adapters were already in the base and carry over unchanged.

## Known gaps (inherited)

Same as 1.21.4: typed custom payloads only for the three registered 5zig channels, capes resolved but not
spliced into the immutable `PlayerSkin`, enchantment damage terms omitted, animated capes driven by the
registry tick rather than `Tickable`.

## Verification performed

- `gradle --no-daemon build` succeeds; jar contains remapped classes, `fabric.mod.json` (minecraft
  `~1.21.3`, entrypoint `FiveZigFabric1213`, five nested jars), the access widener and
  `5zig-renewed-1.21.3-fabric-refmap.json`.
- Jar entry list is byte-for-byte the same file set as the 1.21.4 jar apart from the refmap name and the
  entrypoint class.
- All 32 `method = "..."` mixin targets (13 mixin classes) were resolved against the 1.21.3 named jar with
  `javap -p -s`: 32/32 present, and the generated refmap maps all 32 to intermediary names
  (`class_742;method_3118(ZF)F`, …) — i.e. the mixin annotation processor resolved every target.
- Decompiled 1.21.3 sources were used for the `AbstractTexture`/`NativeImage` shapes; nothing was guessed.
- No client was launched (per task rules), so this is compile + static-target verification only.
