# 1.21.5 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.5.101** (FancyModLoader 7.x, Mixin 0.8.7) |
| Minecraft | 1.21.5, Mojang official mappings |

Build:

```bash
cd neoforge-1.21.5
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.5/build/libs/5zig-renewed-1.21.5-neoforge-4.0.0-alpha.1.jar`
(18.8 MB, self-contained: eight libraries nested under `META-INF/jarjar/`).

## What is different from the verified 1.21.4 project

| Item | 1.21.4 | 1.21.5 |
|---|---|---|
| NeoForge | 21.4.161 | 21.5.101 |
| neoForge dependency range | `[21.4,22)` | `[21.5,22)` |
| Minecraft dependency range | `[1.21.4,1.21.5)` | `[1.21.5,1.21.6)` |
| Platform sources | `platform/1.21.4/eu` | `platform/1.21.5/eu` |
| Loader glue | `loader/neoforge-1.21.4/java` | `loader/neoforge-1.21.5/java` |
| Manifest `Minecraft-Version` | 1.21.4 | 1.21.5 |

Two structural changes were required:

1. **Core overlay.** MC 1.21.5 ships authlib 6, which dropped the legacy client-side Yggdrasil
   login API the core was written against. `platform/1.21.5` does *not* carry replacements this
   time, so the build copies `core/` into `build/core-compat` with `AccountManager.java` and
   `PacketEncryption.java` filtered out and compiles the two replacements from
   `versions/1.21.5-fabric/compat-src` — the exact tree the verified 1.21.5 Fabric build compiles.
   Nothing under `versions/` or `core/` is modified.
2. **`additionalRuntimeClasspath` is still available** on 21.5 (it disappears on 21.10), so the
   runtime libraries stay declared twice exactly as on 1.21.4: `implementation` for the dev
   classpath plus `additionalRuntimeClasspath` for the TransformingClassLoader, and `jarJar` for
   the packaged jar (eight artifacts, `strictly` ranges except the `DiscordIPC` snapshot).

The glue files (`FiveZigNeoForge`, `PluginMessages`) are the 1.21.4 ones with the version strings
changed; `platform/1.21.5` still has `PluginMessages` and `Variables#sendCustomPayload` calls
`PluginMessages.send`, so the NeoForge payload bridge is required and compiles unchanged against
NeoForge 21.5's network API (`RegisterPayloadHandlersEvent`, `PayloadRegistrar`,
`NetworkRegistry.hasChannel`, `ConnectionProtocol.PLAY`).

## HUD hook

`platform/1.21.5`'s `@Mixin(Gui)` is byte-identical to the 1.21.4 one: `render(GuiGraphics,
DeltaTracker)`, `renderItemHotbar(GuiGraphics, DeltaTracker)`, `tick()`,
`setOverlayMessage(Component, Z)`. NeoForge 21.5 still keeps `Gui#render` (it delegates to its
`GuiLayerManager`) and `renderItemHotbar` is still live, so all targets resolve and no
loader-local mixin was needed.

## Verification performed

No client was launched (task rule). Static checks:

* `gradle clean build` → `BUILD SUCCESSFUL`; compile against the real NeoForge 21.5.101 patched jar.
* Mixin targets: 33/33 `@Inject`/`@ModifyVariable`/`@Shadow` member targets named by
  `platform/1.21.5` resolve with the exact JVM descriptor in
  `build/moddev/artifacts/neoforge-21.5.101.jar`.
* `META-INF/neoforge.mods.toml` TOML-parsed: version expanded, `[[mixins]] config` present,
  `neoforge` `[21.5,22)` / `minecraft` `[1.21.5,1.21.6)` ranges.
* `mixins.the5zigmod.json`: `JAVA_21`, 13 classes, all present in the jar.
* Manifest carries `5zig-Version` / `Minecraft-Version: 1.21.5` / `5zig-Build`.
* Eight nested jars, each a valid zip; `META-INF/jarjar/metadata.json` lists exactly those eight
  paths; no duplicate entries; single manifest.
* 1482 classes, all class file 65; zero `net/fabricmc` references.
* `PluginMessages` present in the jar and reachable from `Variables#sendCustomPayload`.

## Unverified

* No live client/server run, so the three payload channels were never exercised on a real 21.5
  connection; the registration path only proves it compiles and links against the loader API.
