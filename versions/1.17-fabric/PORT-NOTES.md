# 1.17 Fabric port notes

Base 1.17 release (not 1.17.1). Ported from `platform/1.17.1` after that build was green; the
platform source is **byte-identical** apart from the `1.17.1` → `1.17` comment sweep (0 code
differences after normalising the version string), because 1.17 → 1.17.1 was a bugfix release that
did not move any API used by the platform. Every spot that had to change against 1.18.2
(`Level#getBiome` returning `Biome`, `doRenderEffects`, the 4-argument `User`, `startAttack()V`,
`scrollChat(D)V`, `isHovered()`) is identical in 1.17 and 1.17.1 and was re-checked in the 1.17
decompiled sources.

Built with `gradle-8.10.2` / JDK 17, `--no-daemon`, **`--release 16`** (class file 60).

| Piece | Version |
|---|---|
| Minecraft | 1.17 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.36.0+1.17 |
| Mixin compatibility level | `JAVA_16` |

Build: `cd versions/1.17-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.17-fabric-4.0.0-alpha.1.jar` (18 819 544 bytes, self-contained,
1690 entries, sha256 `2f1fa19942f96d8193a8833aaa43df9a068ef9e1dbf4f0cab349fa51f4b41bfd`).

## Fabric API's mod id

Fabric API declares `"id": "fabric"`, so `fabric.mod.json` depends on `fabric`, **not**
`fabric-api` (the latter makes the loader reject the mod at startup with *"requires any version of
fabric-api, which is missing"*).

The API version is pinned to **0.36.0+1.17** because it is the last build whose own
`minecraft` constraint still admits 1.17.0 (`~1.17-alpha.21.19.a`). Every later 1.17 build,
including 0.46.1+1.17 used for the 1.17.1 jar, declares `minecraft: ~1.17.1-rc`, which semver-wise
excludes 1.17.0 — installing one of those on a 1.17.0 client makes the loader reject the API itself.
The compiled mod is byte-identical against either API (the jar does not embed it), so this is purely
a build/target declaration.

## Decompiled reference

`mc-src/1.17/out` (4141 `.java` files), Loom `genSourcesWithVineflower`
(vineflower 1.12.0) from
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.17-loom.mappings.1_17.layered+hash.2198-v2/…`.

## Delta against `platform/1.17.1`

None in code. `fabric.mod.json` differs in one field (`minecraft: ~1.17` instead of `~1.17.1`), and
`build.gradle` in the Minecraft/Fabric coordinates (`1.17`, Fabric API 0.36.0+1.17 instead of
0.46.1+1.17 — see above) and `archivesName`. The 1.17.1 jar does not load
on 1.17.0 anyway (its `~1.17.1` constraint rejects it), so this artifact is the one to ship for
1.17.0 clients.

## IVariables coverage

`Variables` implements **all 162 methods** of `eu.the5zig.mod.util.IVariables` (162 `@Override`s).
Fully real: text/width/split, widget factories, chat (2nd chat, chat box text, chat field), sounds,
server data/pinger, FOV/smooth camera, screen switching, joining servers, client accessors (user,
profile, session, proxy, FPS source, window, scaled resolution), player position/rotation/chunk,
target block/entity ray trace, biome/light/entity count, food/health/armor/air/resistance, potion
effects, items in hand/armor/hotbar + counts, HUD helpers, plugin payloads, resource/texture handling,
scoreboard, battery (OSHI 5.7.4), key dispatch, reflective session replacement, chat components with
click/hover.

Degraded / approximated — identical to 1.17.1 (see `versions/1.17.1-fabric/PORT-NOTES.md` for the
full table): `getFPS()` parses `Minecraft#fpsString`; `setFOV`/`getFOV` write the unclamped
`Options#fov` double; `setSession` replaces the private final `Minecraft#user` field by reflection
(and cannot express the `MSA` type — 1.17 has only `LEGACY`/`MOJANG`); `getPopulationInfo()` returns
the sampled player-list size because 1.17's `ServerData` has no online/max counters;
`getDummyPotionEffects()` is synthetic; `resetServer()`/`updateScaledResolution()` are no-ops by
design; item icons ignore `PoseStack` transforms; the scissor facade converts GUI units to
framebuffer pixels and can be off by one at non-integral GUI scales; `getMinecraftIcon()` falls back
to `/pack.png`.

The single `UnsupportedOperationException` is the unreachable tail of `setSession`.

## Verification performed

- `gradle --no-daemon build` green (JDK 17, Loom 1.7.4, `--release 16`); access widener validates.
- `tools/check_mixins.py`: **37/37** injector targets present in the 1.17 named jar (15 mixin
  classes).
- `tools/LinkCheck.java` (ASM): **1495 classes, 65 315 references, 0 unresolved** against the 1.17
  named jar + Fabric API + libraries.
- Remapped jar: 15 mixin classes / 34 injector descriptors in the refmap, **0 empty targets**;
  Java 16 bytecode (class-file major 60); 8 nested runtime jars; `5zig-*` manifest attributes;
  `fabric.mod.json` declares `minecraft: ~1.17` and depends on `fabric`.
- No reference to 1.18+-only symbols (`Holder`, `GuiGraphics`, `BuiltInRegistries`, `ItemRenderer`).

## Known gaps / not verified

- **No client run** (task rule). Everything is API/link-level verification only.
- Same non-blocking gaps as 1.17.1 (item-icon pose, scissor rounding, unregistered
  `MixinCommonMinecraft`, no secure chat).
