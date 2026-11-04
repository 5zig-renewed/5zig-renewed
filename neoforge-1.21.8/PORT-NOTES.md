# 1.21.8 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.8.57** (FancyModLoader 9.0.18, Mixin 0.8.7) |
| Minecraft | 1.21.8, Mojang official mappings |

Build:

```bash
cd neoforge-1.21.8
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.8/build/libs/5zig-renewed-1.21.8-neoforge-4.0.0-alpha.1.jar`
(20.2 MB, self-contained: twelve libraries nested under `META-INF/jarjar/`).

## What is different from the verified 1.21.4 project

The project is the 1.21.4 one with the version constants moved, plus two code-level changes:

| Item | 1.21.4 | 1.21.8 |
|---|---|---|
| NeoForge | 21.4.161 (FML 6) | 21.8.57 (FML 9) |
| `loaderVersion` | `[4,)` | `[9,)` |
| neoForge dependency range | `[21.4,22)` | `[21.8,22)` |
| Minecraft dependency range | `[1.21.4,1.21.5)` | `[1.21.8,1.21.9)` |
| Platform sources | `platform/1.21.4/eu` | `platform/1.21.8/eu` |
| Loader glue | `loader/neoforge-1.21.4/java` | `loader/neoforge-1.21.8/java` |
| Manifest `Minecraft-Version` | 1.21.4 | 1.21.8 |

### Code changes forced by the version bump

* **Core overlay.** `platform/1.21.8` has no `AccountManager`/`PacketEncryption`; 1.21.8 ships
  authlib 6 (`GameProfile` is a record, the legacy Yggdrasil client is gone). The build excludes
  those two files from the copied core and compiles `versions/1.21.8-fabric/compat-src` instead,
  exactly like the verified 1.21.8 Fabric build. That compat tree has no Fabric imports.
* **`PacketDistributor.sendToServer` is gone.** In this line only the server-bound senders remain on
  `net.neoforged.neoforge.network.PacketDistributor`; the client sender lives in
  `net.neoforged.neoforge.client.network.ClientPacketDistributor`. `PluginMessages.send()` now calls
  that one (a one-line change, otherwise the file is the 1.21.4 implementation).
* **Apache HttpClient is nested.** 1.21.5+ no longer ships it (authlib dropped it) and the core's
  Spotify/"open URL" code imports `org.apache.http.*` directly. `httpclient 4.5.14`,
  `httpcore 4.4.16`, `commons-codec 1.17.1` and `commons-logging 1.3.4` are `implementation` +
  `additionalRuntimeClasspath` + `jarJar`'d, taking the nested set from 8 to 12 jars.

`additionalRuntimeClasspath` still exists on FML 9 and is still needed: the mod's
TransformingClassLoader cannot see the game classpath in dev.

### Mixin targets

No loader copy of any mixin was needed. All 33 `@Inject`/`@ModifyVariable`/`@Shadow` targets named
by `platform/1.21.8` resolve with the exact JVM descriptor against
`build/moddev/artifacts/neoforge-21.8.57.jar`. In particular `Gui#render(GuiGraphics, DeltaTracker)`,
`Gui#renderItemHotbar(GuiGraphics, DeltaTracker)` (called from `renderHotbar`), `Gui#tick(Z)V` and
`Gui#setOverlayMessage(Component, Z)` all survive NeoForge 21.8's Gui patches — unlike 26.x, which
renames `extractHotbarAndDecorations` → `extractHotbar`.

## Verification performed

No client was launched (task rule). Static checks:

* `gradle build` → `BUILD SUCCESSFUL` against NeoForge 21.8.57's patched jar.
* 33/33 mixin targets resolve by name + descriptor (checker also validated in reverse: the 1.21.11
  platform against this jar reports the three 1.21.9+ chat/input renames as misses, so it is not
  vacuously green).
* `META-INF/neoforge.mods.toml` TOML-parses, `${version}` is expanded, `[[mixins]]` names
  `mixins.the5zigmod.json`, dependency ranges are `[21.8,22)` / `[1.21.8,1.21.9)`.
* `mixins.the5zigmod.json` in the jar: `JAVA_21`, 13 classes, every class file present.
* 12 nested jars, each a valid zip; `META-INF/jarjar/metadata.json` lists exactly those 12 paths;
  no duplicate zip entries; class file version 65 throughout; zero `net/fabricmc` references.

## Not verified without a client

* That the HUD frame actually brackets the frame (`Gui#render` HEAD/RETURN) and that the action-bar
  hook suppresses the vanilla overlay — no `@At` were dead-code-checked beyond call-site presence.
* Keybinding registration (`RegisterKeyMappingsEvent` + `Options` freeze timing).
* The three payload channels handshaking as `optional()` against a vanilla server, and the
  `NetworkRegistry.hasChannel` gate.
* Discord RPC (DiscordIPC + junixsocket) and sqlite initialisation from the nested jars.
