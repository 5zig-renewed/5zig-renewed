# 1.21.10 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.10.64** (FancyModLoader 10.x, Mixin 0.8.7) |
| Minecraft | 1.21.10, Mojang official mappings |

Build:

```bash
cd neoforge-1.21.10
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.10/build/libs/5zig-renewed-1.21.10-neoforge-4.0.0-alpha.1.jar`
(18.8 MB, self-contained: eight libraries nested under `META-INF/jarjar/`).

## What is different from the verified 1.21.4 project

| Item | 1.21.4 | 1.21.10 |
|---|---|---|
| NeoForge | 21.4.161 | 21.10.64 |
| neoForge dependency range | `[21.4,22)` | `[21.10,22)` |
| Minecraft dependency range | `[1.21.4,1.21.5)` | `[1.21.10,1.21.11)` |
| Platform sources | `platform/1.21.4/eu` | `platform/1.21.10/eu` |
| Loader glue | `loader/neoforge-1.21.4/java` | `loader/neoforge-1.21.10/java` |
| Manifest `Minecraft-Version` | 1.21.4 | 1.21.10 |

Three structural changes were required:

1. **`additionalRuntimeClasspath` is gone.** MDG 2.0.148 refuses the configuration on 1.21.10:
   *"there is no additional classpath anymore for Minecraft 1.21.10"* — FML's mod-locator rework
   makes the mod class loader see the game classpath, so `implementation` alone is correct (the
   same change the verified 26.1.2 build needed). The eight libraries are still nested with
   `jarJar`; only the duplicate declaration was dropped.
2. **Core overlay.** MC 1.21.10 ships authlib 7 (GameProfile is a record with `id()`/`name()`, the
   legacy Yggdrasil client is gone) and commons-lang3 3.19 adds `org.apache.commons.lang3.Strings`,
   which collides with Guava's `Strings`. `platform/1.21.10` does not carry replacements, so the
   build copies `core/` into `build/core-compat` with the twelve affected files filtered out and
   compiles the replacements from `versions/1.21.10-fabric/compat-src` — the exact tree the
   verified 1.21.10 Fabric build compiles. Nothing under `versions/` or `core/` is modified.
3. **No payload bridge.** `platform/1.21.10` has no `PluginMessages` at all: 1.21.6+ replaced
   free-form plugin channels with registered `CustomPacketPayload` types and
   `Variables#sendCustomPayload` drops such messages with a one-time warning. The entrypoint
   therefore registers nothing on the mod bus for networking and `loader/neoforge-1.21.10` contains
   only `FiveZigNeoForge` (no `PluginMessages` class is compiled or packaged, unlike 1.21/1.21.5).

The glue is the 1.21.4 entrypoint with the payload listener and its imports removed; everything
else (mod-bus key mappings, `ClientTickEvent.Post` tick driver, core bootstrap on the first tick)
is unchanged and compiles against NeoForge 21.10.

## HUD hook and renames

`platform/1.21.10`'s `@Mixin(Gui)` targets `render(GuiGraphics, DeltaTracker)`,
`renderItemHotbar(GuiGraphics, DeltaTracker)`, `tick()` and `setOverlayMessage(Component, Z)`.
NeoForge 21.10 keeps `Gui#render` as the frame entry point (it delegates to its `GuiLayerManager`,
as 21.0/21.5/21.4 already did) and keeps `renderItemHotbar` — 1.21.9 moved the hotbar *items* into
it, which is exactly where the platform's TAIL injection draws the 5zig numbers. No method was
renamed on this line, so no loader-local mixin was needed (unlike NeoForge 26.1.2, which renamed
`extractHotbarAndDecorations` to `extractHotbar`).

## Verification performed

No client was launched (task rule). Static checks:

* `gradle clean build` → `BUILD SUCCESSFUL`; compile against the real NeoForge 21.10.64 patched jar.
* Mixin targets: 31/31 `@Inject`/`@ModifyVariable`/`@Shadow` member targets named by
  `platform/1.21.10` resolve with the exact JVM descriptor in
  `build/moddev/artifacts/neoforge-21.10.64.jar` (30 injection targets + one `@Shadow`; two fewer
  than 1.21/1.21.5 because this platform's `MixinAbstractClientPlayer` and `MixinScreen` carry one
  fewer injection each).
* `META-INF/neoforge.mods.toml` TOML-parsed: version expanded, `[[mixins]] config` present,
  `neoforge` `[21.10,22)` / `minecraft` `[1.21.10,1.21.11)` ranges.
* `mixins.the5zigmod.json`: `JAVA_21`, 13 classes, all present in the jar.
* Manifest carries `5zig-Version` / `Minecraft-Version: 1.21.10` / `5zig-Build`.
* Eight nested jars, each a valid zip; `META-INF/jarjar/metadata.json` lists exactly those eight
  paths; no duplicate entries; single manifest.
* 1479 classes, all class file 65; zero `net/fabricmc` references.
* `eu/the5zig/mod/platform/PluginMessages.class` absent, as intended.

## Unverified / known cosmetics

* No live client run. In particular the HUD bracket is still the platform mixin on `Gui#render`;
  on NeoForge 21.10 the actual layer drawing happens inside `GuiLayerManager`, so the mod HUD is
  drawn at the head of the frame (before the vanilla layers), exactly like the verified 1.21.4
  build whose `Gui#render` is also a `GuiLayerManager` delegate. Visual order is untested.
* `Variables#sendCustomPayload`'s warning text says "1.21.11": `platform/1.21.10` is shared with
  the 1.21.11 Fabric build. Cosmetic only, and the file is not owned by this build.
