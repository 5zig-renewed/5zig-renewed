# 1.18.1 Fabric port notes

First **patch-level** port of the 1.18 era: built by copying `platform/1.18.2` (client-verified) and
reconciling it against the decompiled 1.18.1 sources. 1.18.1 is the last release before the
`Holder`-based registry refactor that 1.18.2 brought in, so the delta is small but not zero.

Built with `gradle-8.10.2` / JDK 17, `--no-daemon`, Java 17 (class file 61).

| Piece | Version |
|---|---|
| Minecraft | 1.18.1 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.46.6+1.18 |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.18.1-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.18.1-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained, 8 nested
runtime jars, sha256 `f2efca3da9e22a679204b9318ad816ed873d592d3eab0ade05061a2abb398c96`).

## Decompiled reference

`mc-src/1.18.1/out` (vineflower 1.12.0, `-dgs=1 --only=net/minecraft`, 36 s),
decompiled from
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.18.1-loom.mappings.1_18_1.layered+hash.2198-v2/…`
(`version.json` → `world_version 2865`).

## Fabric API mod id (the one trap that bites at runtime)

Fabric API renamed its mod id from **`fabric`** to **`fabric-api`** at 0.60. Every 1.18.x build of
the API still registers as `fabric` — `fabric-api-0.46.6+1.18.jar`'s own `fabric.mod.json` says
`"id": "fabric"` and does **not** `provide` `fabric-api`. Declaring `"fabric-api": "*"` in
`loader/fabric-1.18.1/resources/fabric.mod.json` therefore makes Fabric Loader abort with
*"requires any version of fabric-api, which is missing"* while the API is sitting on the classpath.
This port declares `"fabric": "*"` (same as the 1.16.5 / 1.19 / 1.19.1 loaders in this repo).

## Scope of the delta against `platform/1.18.2`

Three files carry code changes: **9 insertions / 11 deletions**. The other 51 files are identical
after a comment-only sweep replacing "1.18.2" with "1.18.1".

| Area | 1.18.2 | 1.18.1 | Resolution |
|---|---|---|---|
| Biomes | `LevelReader#getBiome` returns `Holder<Biome>`; `unwrapKey()` yields the `ResourceKey` | `net.minecraft.core.Holder` **does not exist**; `getBiome` returns `Biome` | `Variables#getBiome()` now resolves the key through `level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getKey(biome)` and returns `null` when the biome is unregistered |
| Left-click hook | `Minecraft#startAttack()` returns `boolean` | `startAttack()` returns `void` | `MixinMinecraft`: target `startAttack()V`, handler takes `CallbackInfo` instead of `CallbackInfoReturnable<Boolean>` |
| Chat scroll hook | `ChatComponent#scrollChat(int)` | `scrollChat(double)` | `MixinChatComponent`: target `scrollChat(D)V`, handler takes `double`, casts to `int` for `IGui2ndChat#scroll(int)` |

Both mixin mismatches surfaced only as annotation-processor **warnings**
(`Cannot find target method …`) — the build stays green and the mixin then fails at class-load time,
so they had to be hunted down explicitly rather than by watching for red build output.

Everything else in the platform is byte-identical to the 1.18.2 port, including the `GuiContext`
`PoseStack`/`RenderSystem` rendering facade (1.18.1 has no `GuiGraphics`, no GUI-scale scissor
helpers, and `Options#graphicsMode` is still a plain `GraphicsStatus` field, exactly as in 1.18.2).

## IVariables coverage

`Variables` implements all 162 methods of `eu.the5zig.mod.util.IVariables`; `javac` enforces it via
`@Override`. The only `UnsupportedOperationException` is the defensive fallback at the end of
`setSession`, as in every other port. No `TODO`/`FIXME`/`not implemented` markers.

Degraded/approximated behaviour is inherited unchanged from the 1.18.2 port (FPS parsed from
`Minecraft#fpsString`, `Options#fov` written directly, `ServerData` population counters unavailable,
synthetic potion effects for the HUD preview, `resetServer`/`updateScaledResolution` no-ops).
`getBiome()` is the one behaviour that differs in implementation between the two targets.

## Verification performed

- `gradle --no-daemon build` green (JDK 17, Loom 1.7.4, `--release 17`), no `Cannot find target`
  warnings left; the only remaining warning is the pre-existing `common/MixinCommonMinecraft`
  "clickMouse" one that every Fabric port carries.
- `tools/check_mixins.py` resolves every `@Inject`/`@ModifyVariable` name+descriptor against the
  1.18.1 named jar: **37/37 present** across 15 mixin classes.
- `tools/LinkCheck.java` (ASM) resolves every class/method/field reference in the compiled mod
  against the 1.18.1 named jar + Fabric API + libraries: **1495 classes, 65 310 references, 0
  unresolved** — nothing that could throw `NoSuchMethodError`/`NoSuchFieldError`.
- Refmap on the remapped jar: `MixinMinecraft.startAttack` → `class_310;method_1536()V`,
  `MixinChatComponent.scrollChat` → `class_338;method_1802(D)V`,
  `MixinChatComponent.addMessage` → `class_338;method_1812`.
- Jar: 8 nested runtime jars (DiscordIPC, jacob, json, json-simple, junixsocket-common,
  junixsocket-native-common, sqlite-jdbc, netty-codec-http), `5zig-Version` / `Minecraft-Version:
  1.18.1` / `5zig-Build` manifest attributes, `fabric.mod.json` declaring `minecraft: ~1.18.1` and
  `fabric: *`.
- No `net/minecraft/core/Holder` reference survives in the shipped classes.

## Known gaps / not verified

- **No client run** (task rule): HUD, 2nd chat and menus are verified only by API/link compatibility.
- `getBiome()` now depends on `ClientLevel#registryAccess()`, which delegates to the connection's
  registry access; it is non-null for any live client level but the method returns `null` rather
  than guessing if the biome is not in the registry.
- Item icons still ignore `PoseStack` transforms (inherited from the 1.18.2 renderer).
- `setFOV` does not clamp to the vanilla slider range.
