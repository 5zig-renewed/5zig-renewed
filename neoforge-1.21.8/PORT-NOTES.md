# 1.21.4 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.4.161** (FancyModLoader 4.x, Mixin 0.8.7) |
| Minecraft | 1.21.4, Mojang official mappings |

Build:

```bash
cd neoforge-1.21.4
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.4/build/libs/5zig-renewed-1.21.4-neoforge-4.0.0-alpha.1.jar`
(18.8 MB, self-contained: eight libraries nested under `META-INF/jarjar/`).

## What is different from the verified 1.21.1 project

Nothing structural. The project is the 1.21.1 one with the version constants moved:

| Item | 1.21.1 | 1.21.4 |
|---|---|---|
| NeoForge | 21.1.265 | 21.4.161 |
| neoForge dependency range | `[21.1,22)` | `[21.4,22)` |
| Minecraft dependency range | `[1.21.1,1.21.2)` | `[1.21.4,1.21.5)` |
| Platform sources | `platform/1.21.1/eu` | `platform/1.21.4/eu` |
| Loader glue | `loader/neoforge-1.21.1/java` | `loader/neoforge-1.21.4/java` |
| Manifest `Minecraft-Version` | 1.21.1 | 1.21.4 |

The glue files (`FiveZigNeoForge`, `PluginMessages`) are the 1.21.1 ones verbatim; the platform
APIs they touch (`Keybinds`, `CoreState`, `PluginMessages`) are byte-identical between
`platform/1.21.1` and `platform/1.21.4`, so no code change was needed.

* `@Mod(value = "the5zigmod", dist = Dist.CLIENT)`, explicit `modEventBus`/`NeoForge.EVENT_BUS`
  listener registration, `ClientTickEvent.Post` for the per-tick driver, `RegisterKeyMappingsEvent`
  for the bindings — unchanged from 1.21.1.
* `PluginMessages` still registers the three 5zig channels (`the5zigmod:5zig`, `…:5zig_reg`,
  `…:5zig_set`) as `optional()` bidirectional typed payloads through
  `RegisterPayloadHandlersEvent`, and `send()` still gates on
  `NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, id)`.
* The HUD hook is still the platform's own `@Mixin(Gui)` on
  `render(GuiGraphics, DeltaTracker)` / `renderItemHotbar(GuiGraphics, DeltaTracker)`, delegating to
  `HudFrame`; NeoForge 21.4 installs a `GuiLayerManager` but keeps both methods, so no loader mixin
  is needed (unlike 26.x).

Runtime libraries are declared twice (`implementation` + `additionalRuntimeClasspath`) and nested
with `jarJar`, exactly as on 1.21.1: eight artifacts, `version { strictly '[x]' }` for every one
except `DiscordIPC:master-SNAPSHOT`, which has no fixed version and keeps its `[master-SNAPSHOT,)`
metadata (verified to parse with FML's JarJarMetadata).

## Verification performed

No client was launched (task rule). Static checks:

* `gradle build` → `BUILD SUCCESSFUL`; compile against the real NeoForge 21.4.161 patched jar.
* Mixin targets: 36/36 `@Inject`/`@Shadow` targets named by `platform/1.21.4` resolve with the exact
  JVM descriptor in `build/moddev/artifacts/neoforge-21.4.161.jar`.
* Manifest, `META-INF/neoforge.mods.toml` (TOML-parsed, version expanded, ranges checked) and
  `mixins.the5zigmod.json` (`JAVA_21`, 13 classes, all present) inside the jar.
* Eight nested jars, each a valid zip; `META-INF/jarjar/metadata.json` lists exactly those eight
  paths; no duplicate jar entries; single manifest.
* All classes are class file 65; zero `net/fabricmc` references in the artifact.
