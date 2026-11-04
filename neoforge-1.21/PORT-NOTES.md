# 1.21 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.0.167** (FancyModLoader 4.x, Mixin 0.8.7) |
| Minecraft | 1.21, Mojang official mappings |

Build:

```bash
cd neoforge-1.21
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21/build/libs/5zig-renewed-1.21-neoforge-4.0.0-alpha.1.jar`
(18.8 MB, self-contained: eight libraries nested under `META-INF/jarjar/`).

## What is different from the verified 1.21.4 project

Only the version constants and the source paths:

| Item | 1.21.4 | 1.21 |
|---|---|---|
| NeoForge | 21.4.161 | 21.0.167 |
| neoForge dependency range | `[21.4,22)` | `[21.0,22)` |
| Minecraft dependency range | `[1.21.4,1.21.5)` | `[1.21,1.21.1)` |
| Platform sources | `platform/1.21.4/eu` | `platform/1.21/eu` |
| Loader glue | `loader/neoforge-1.21.4/java` | `loader/neoforge-1.21/java` |
| Manifest `Minecraft-Version` | 1.21.4 | 1.21 |

The glue files (`FiveZigNeoForge`, `PluginMessages`) are the 1.21.4 ones with the version strings
changed. `platform/1.21` carries its own `AccountManager` / `PacketEncryption` copies (like
`platform/1.21.4`), so the two core files are excluded and the platform copies compile instead; no
core overlay tree is needed. `PluginMessages` still registers the three 5zig channels as optional
bidirectional typed payloads through `RegisterPayloadHandlersEvent`, and `send()` still gates on
`NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, id)` — all of those APIs exist
unchanged in NeoForge 21.0.167 and the glue compiles as-is.

Runtime libraries are declared twice (`implementation` + `additionalRuntimeClasspath`, which MDG
still provides on 1.21.0) and nested with `jarJar`: eight artifacts, `version { strictly '[x]' }`
for every one except `DiscordIPC:master-SNAPSHOT`.

## HUD hook

`platform/1.21`'s `@Mixin(Gui)` targets `render(GuiGraphics, DeltaTracker)`,
`renderItemHotbar(GuiGraphics, DeltaTracker)`, `tick()` and `setOverlayMessage(Component, Z)`.
NeoForge 21.0 already installs a `GuiLayerManager`: `Gui#render` is a thin shell that delegates the
layers, but the method itself is unchanged and still the frame entry point, and `renderItemHotbar`
is still live (called from the Gui layer lambdas). All targets resolve against the patched jar, so
no loader-local mixin was needed.

## Verification performed

No client was launched (task rule). Static checks:

* `gradle clean build` → `BUILD SUCCESSFUL`; compile against the real NeoForge 21.0.167 patched jar.
* Mixin targets: 33/33 `@Inject`/`@ModifyVariable`/`@Shadow` member targets named by
  `platform/1.21` resolve with the exact JVM descriptor in
  `build/moddev/artifacts/neoforge-21.0.167.jar`.
* `META-INF/neoforge.mods.toml` TOML-parsed: version expanded, `[[mixins]] config` present,
  `neoforge` `[21.0,22)` / `minecraft` `[1.21,1.21.1)` ranges.
* `mixins.the5zigmod.json`: `JAVA_21`, 13 classes, all present in the jar.
* Manifest carries `5zig-Version` / `Minecraft-Version: 1.21` / `5zig-Build`.
* Eight nested jars, each a valid zip; `META-INF/jarjar/metadata.json` lists exactly those eight
  paths; no duplicate entries; single manifest.
* 1482 classes, all class file 65; zero `net/fabricmc` references.
