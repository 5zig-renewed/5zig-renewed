# 1.21.2 Fabric port notes

Era E8 (1.21.2–1.21.4) — the **start** of the era. Built with `gradle-8.10.2` / JDK 21, `--no-daemon`,
target Java 21.

| Piece | Version |
|---|---|
| Minecraft | 1.21.2 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.16.10 |
| Fabric API | 0.106.1+1.21.2 |
| Mixin compatibility level | `JAVA_21` |

Build: `cd versions/1.21.2-fabric && JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.21.2-fabric-4.0.0-alpha.1.jar` (1570 entries, 1483 classes, ~18.8 MB)

Entrypoint: `eu.the5zig.mod.fabric.FiveZigFabric1212` (registers the 5zig payload types before delegating
to the shared `FiveZigFabric`).

## Decompiled reference

`mc-src/1.21.2/out` (vineflower 1.12.0, `-dgs=1 --only=net/minecraft`, 5439 files), from
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.2-loom.mappings.1_21_2.layered+hash.2198-v2/`.

## Base

`platform/1.21.2` is a copy of `platform/1.21.4`. 1.21.2 opens E8, so the era's rewrite is present and the
1.21.4 adapters carry over: `GuiGraphics#blit`/`blitSprite` take a `Function<ResourceLocation, RenderType>`
plus an ARGB tint and `GuiGraphics#setColor` is gone, `PositionMoveRotation` is inside
`ClientboundPlayerPositionPacket`, toasts are `getWantedVisibility()` + `update(ToastManager, long)` +
`render(GuiGraphics, Font, long)` with `Minecraft#getToastManager()`, the inventory effect panel is
`EffectsInInventory` behind `Screen#showsActiveEffects()`, and `GlStateManager#_clear(int)` /
`NativeImage#setPixel` / `Registry` holder lookups are all already in their 1.21.4 shapes. Verified
individually against the decompiled sources, not assumed.

## API delta 1.21.2 vs 1.21.4 that affects this platform

Only the texture stack differs; two source changes, both in `platform.SimpleTexture`:

- **`AbstractTexture#load(ResourceManager)` is still abstract** in 1.21.2 (it disappeared in 1.21.4). This
  is not cosmetic: `TextureManager#register(ResourceLocation, AbstractTexture)` calls `load(...)`
  synchronously and only then puts the texture into `byPath`, and `AbstractTexture#reset` re-enters
  `register` on every resource reload. Without an implementation the class does not compile; a no-op is
  also the right behaviour, because these textures are fed from a `BufferedImage` and upload lazily from
  `getId()`. `ReloadableTexture`/`TextureContents` do not exist in 1.21.2 at all.
- **`NativeImage#upload` has no eight-argument overload.** 1.21.2 has `(int,int,int,boolean)`,
  `(int,int,int,int,int,int,int,boolean,boolean)` and the eleven-argument form. The nine-argument call is
  used with the last two flags `(mipmap=false, close=true)`; the forwarding order was read out of the
  overload's bytecode (`blur`/`clamp` are hard-coded `false`, argument 8 becomes `mipmap`, argument 9
  becomes `close`), and filtering stays with `AbstractTexture#setFilter(false, false)`.

1.21.4 members the platform deliberately does **not** use, so nothing had to be backported:
`Gui#clearTitles` (1.21.2: `Gui#clear`), `GuiGraphics#drawWordWrap`, `Style#withShadowColor` /
`getShadowColor`, `TitleScreen#registerTextures` (1.21.2: `preloadResources`),
`Minecraft#getItemModelResolver`, `PlayerInfo#showHat/setShowHat`,
`TextureManager#registerAndLoad` / `registerForNextReload`, `AbstractTexture#setClamp` /
`setFilter(TriState, boolean)`, `KeyMapping.get(String)` and the `public` widening of
`Player#getPermissionLevel`.

Conversely, 1.21.2-only members that still exist but are intentionally unused by the shared source:
`TextureManager#getTexture(ResourceLocation, AbstractTexture)` (the "peek, do not load" overload — the
platform keeps its own cape cache because 1.21.4 dropped it), `TextureManager#preload`,
`TextureManager#register(String, DynamicTexture)`, `DynamicTexture#load(ResourceManager)` and
`AbstractWidget#clicked(double, double)` (its body is identical to the `isMouseOver` call the platform
already uses).

## Fixed along the way

Nothing beyond the two texture-stack items above — no other compile error and no other source edit was
needed, which is the expected shape for a port that only steps one patch back inside the same era. Comments
that describe E8 behaviour were relabelled from "1.21.4" to "1.21.2+" so they do not claim a version the
file is not built for.

## Known gaps (inherited)

Same as 1.21.4: typed custom payloads only for the three registered 5zig channels, capes resolved but not
spliced into the immutable `PlayerSkin`, enchantment damage terms omitted, animated capes driven by the
registry tick rather than `Tickable`.

## Verification performed

- `gradle --no-daemon build` succeeds (compile + `validateAccessWidener` + `remapJar`); the only warnings
  are the pre-existing `MixinCommonMinecraft` "unable to determine descriptor" note and the
  `AccessController` removal note in core.
- Jar entry set (1570 files) is byte-for-byte the same file set as the 1.21.4 jar apart from the refmap
  name and the version-specific entrypoint class; manifest attributes match the 1.21.4 jar except
  `Fabric-Minecraft-Version`.
- All eight `include(...)` payloads are nested: DiscordIPC, jacob, json, json-simple, junixsocket-common,
  junixsocket-native-common, sqlite-jdbc, netty-codec-http. `fabric.mod.json` in the jar declares
  `minecraft: ~1.21.2`, entrypoint `FiveZigFabric1212`, the access widener and the eight nested jars.
- All 32 `method = "..."` mixin targets (13 mixin classes) were resolved against the 1.21.2 named jar with
  `javap -p -s`: 32/32 present with matching descriptors, and the generated refmap maps all 32 to
  intermediary names (`class_742;method_3118(ZF)F`, …).
- No client was launched (per task rules), so this is compile + static-target verification only.
