# 1.18 Fabric port notes

Base release of the 1.18 era. Built by copying the finished `platform/1.18.1` and reconciling it
against the decompiled 1.18 sources: **no platform code changes were required**. 1.18 and 1.18.1
share the same client code; everything that differs is build metadata and the Fabric API version.

Built with `gradle-8.10.2` / JDK 17, `--no-daemon`, Java 17 (class file 61).

| Piece | Version |
|---|---|
| Minecraft | 1.18 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.46.3+1.18 |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.18-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.18-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained, 8 nested
runtime jars, sha256 `bd42d4aab6788aec8e79c05001279d43ff8863f9938356a561887a8912b393e4`).

## Decompiled reference

`mc-src/1.18/out` (vineflower 1.12.0, `-dgs=1 --only=net/minecraft`, 28 s),
decompiled from
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.18-loom.mappings.1_18.layered+hash.2198-v2/…`
(`version.json` → `world_version 2860`).

## Fabric API version choice

`0.46.3+1.18` is the **last** Fabric API build whose `fabric.mod.json` still accepts plain 1.18
(`"minecraft": "~1.18-alpha.21.37.a"`). From `0.46.4+1.18` the metadata pins `"minecraft": "1.18.1"`
exactly, so on a 1.18 client Loader would refuse to load the API. Like every other 1.18.x API build,
this jar registers as mod id **`fabric`**, so `loader/fabric-1.18/resources/fabric.mod.json` declares
`"fabric": "*"` — declaring `fabric-api` would fail with *"requires any version of fabric-api, which
is missing"* although the API is on the classpath.

## Scope of the delta against `platform/1.18.1`

**Zero code changes.** All 54 platform classes are code-identical to `platform/1.18.1` after a
comment-only sweep replacing "1.18.1" with "1.18"; `5zig.accesswidener` and
`mixins.the5zigmod.json` are byte-identical.

Evidence gathered before declaring the port done:

| Check | Result |
|---|---|
| `javap -p -s` API diff of all **106** `net.minecraft` classes imported by the platform, 1.18.1 named jar vs 1.18 named jar | 1 class differs: `Util` only, and only in `logAndPauseIfInIde` / `preInitLog4j` / a private log4j helper — none referenced by the mod |
| Normalised source diff of the 17 hot client classes (`Minecraft`, `Gui`, `ChatComponent`, `Screen`, `ClientPacketListener`, `Connection`, `GameRenderer`, `KeyboardHandler`, `LocalPlayer`, `ChatScreen`, `TitleScreen`, `DeathScreen`, `DisconnectedScreen`, `PlayerTabOverlay`, `AbstractClientPlayer`, `LevelReader`, `Biome`) | 16 identical; `GameRenderer` differs only by a 4-line `RenderSystem.setInverseViewRotationMatrix` block that 1.18.1 added inside `renderLevel` after the camera rotation is set up — `pick(F)`, which the mod hooks, is byte-identical |
| Classes present in 1.18 but absent in 1.18.1 (or vice versa) among the imported set | none |

So 1.18 inherits the 1.18.1 code as-is, including the three 1.18.2→1.18.1 changes: `Biome` (not
`Holder<Biome>`) from `getBiome`, `Minecraft#startAttack()V`, and `ChatComponent#scrollChat(D)V`.

## IVariables coverage

`Variables` implements all 162 methods of `eu.the5zig.mod.util.IVariables` (enforced by `@Override`).
The only `UnsupportedOperationException` is the defensive `setSession` fallback; no `TODO`/`FIXME`
markers. Degraded/approximated behaviour is the same as documented for `1.18.1` and `1.18.2`
(FPS parsed from `Minecraft#fpsString`, `Options#fov` written directly, `ServerData` population
counters unavailable, synthetic potion effects for the HUD preview, `resetServer` /
`updateScaledResolution` no-ops, item icons ignore `PoseStack`).

## Verification performed

- `gradle --no-daemon clean build` green, first attempt after the metadata change; only the
  pre-existing `common/MixinCommonMinecraft` "clickMouse" annotation-processor warning.
- `tools/check_mixins.py`: **37/37** `@Inject`/`@ModifyVariable` targets resolve against the 1.18
  named jar across 15 mixin classes.
- `tools/LinkCheck.java` (ASM): **1495 classes, 65 310 references, 0 unresolved** against the 1.18
  named jar + Fabric API + libraries.
- Refmap: 15 mixin classes; `MixinChatComponent.scrollChat` → `class_338;method_1802(D)V`,
  `MixinMinecraft.startAttack` → `class_310;method_1536()V`.
- Jar: 8 nested runtime jars, `5zig-Version` / `Minecraft-Version: 1.18` / `5zig-Build` manifest
  attributes, `fabric.mod.json` declaring `minecraft: ~1.18` and `fabric: *`.

## Known gaps / not verified

- **No client run** (task rule): HUD, 2nd chat and menus are verified only by API/link compatibility.
- Same inherited approximations as `1.18.1` (see its PORT-NOTES for the per-method table).
