# 1.16.4 Fabric port notes

Patch release between 1.16.3 and 1.16.5. Ported from the client-verified **1.16.5** platform.

| Piece | Version |
|---|---|
| Minecraft | 1.16.4 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Gradle | 8.10.2, JDK 17 (compiling with `--release 8`) |
| Fabric Loader | 0.14.24 |
| Fabric API | 0.42.0+1.16 |
| Mixin compatibility level | `JAVA_8` |
| Output class files | major version **52** (Java 8) |

Build:
```
cd versions/1.16.4-fabric
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build
```
Jar: `build/libs/5zig-renewed-1.16.4-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained).

## Delta vs 1.16.5 — no source changes

`platform/1.16.4` is a **byte-identical copy** of `platform/1.16.5`; the build compiled green on the
first attempt. The measured API delta confirms why:

* The two named jars have the same 5,445 classes; only **4** class files differ by CRC:
  `DetectedVersion` (version string/world version), `Direction$Plane`, `PoiSection` (the POI
  “already registered” fix), `BaseFireBlock` (portal-shape check). None is referenced by the mod.
* Decompiled trees (`mc-src/1.16.4/out`, 12,740 files) differ in exactly **5**
  `.java` files, and only two of them are code:
  - `DetectedVersion` — `"1.16.5"`/`worldVersion 2586` → `"1.16.4"`/`2584`;
  - `EffectRenderingInventoryScreen` — `doRenderEffects` is `public` in 1.16.4 but `protected`
    in 1.16.5. The access widener entry carried over from the 1.16.5 port is therefore redundant
    here (harmless; Loom's `validateAccessWidener` accepts it and it still remaps to
    `class_485 field_2900 Z` in the shipped jar).
  - the other three are decompiler-only shapes of the same bytecode (`Direction$Plane.getRandomAxis`
    exists in 1.16.5 only, `PoiSection`/`BaseFireBlock` control-flow rewrites).

So the whole 1.16.4 “port” is a version bump: `minecraft 1.16.4`, `~1.16.4` dependency,
`Minecraft-Version: 1.16.4` manifest, `archivesName`, and the `platform/1.16.4` +
`loader/fabric-1.16.4` source/resource dirs.

### Fabric API compatibility

`fabric-api:0.42.0+1.16` declares `minecraft: ~1.16.2-alpha.20.28.a` (read from the jar's own
`fabric.mod.json`), a range that admits 1.16.3 and 1.16.4 as well as 1.16.5 — the same artifact is
used for all three ports. The mod id is **`fabric`** on this branch (`"fabric": "*"` in
`fabric.mod.json`); declaring `fabric-api` makes the client refuse to load.

## Verification performed

- `gradle --no-daemon build` green (Loom 1.7.4, Gradle 8.10.2, JDK 17 → `--release 8`).
- Jar: 18.79 MB, class major version **52**, manifest `5zig-Version` / `Minecraft-Version: 1.16.4` /
  `5zig-Build: alpha`, 8 nested runtime jars (DiscordIPC, jacob, json, json-simple,
  junixsocket-common, junixsocket-native-common, sqlite-jdbc, netty-codec-http).
- `fabric.mod.json` inside the jar: id `the5zigmod`, `depends.minecraft ~1.16.4`, `depends.fabric *`,
  8 `jars` entries.
- Access widener remapped to intermediary (`class_485 field_2900 Z`).
- `tools/check_mixins.py platform/1.16.4/eu/the5zig/mod/mixin <1.16.4 named jar>`:
  **37/37 injection targets present**; refmap lists 15 classes / 34 entries.
- `tools/LinkCheck.java` (ASM): every class/method/field reference in the compiled mod resolved
  against the 1.16.4 named jar + Fabric API + libraries — **1505 classes, 70,794 references,
  0 unresolved**.
- No 1.17+ symbols (`GuiGraphics`, `setShader`, `BuiltInRegistries`) in the remapped jar.

## Known gaps

- **No client run** (task rule), so nothing is pixel-verified. HUD, second chat, widget themes,
  capes and the mod menu are verified only by compile + link + mixin resolution.
- Same behavioural approximations as 1.16.5 (see `versions/1.16.5-fabric/PORT-NOTES.md`): FPS parsed
  from `fpsString`, reflective `setSession`, Windows battery returns `null`, item icons ignore the
  `PoseStack`, overlay drawn under the vanilla hotbar.
