# 1.21.9 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.9.16-beta** (FancyModLoader 10.0.14, Mixin 0.8.7) |
| Minecraft | 1.21.9, Mojang official mappings |

Build:

```bash
cd neoforge-1.21.9
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.9/build/libs/5zig-renewed-1.21.9-neoforge-4.0.0-alpha.1.jar`
(20.2 MB, self-contained: twelve libraries nested under `META-INF/jarjar/`).

## What is different from the verified 1.21.10 project

| Item | 1.21.10 | 1.21.9 |
|---|---|---|
| NeoForge | 21.10.64 (FML 10) | 21.9.16-beta (FML 10.0.14) |
| `loaderVersion` | `[10,)` | `[10,)` (unchanged) |
| neoForge dependency range | `[21.10,22)` | `[21.9,22)` |
| Minecraft dependency range | `[1.21.10,1.21.11)` | `[1.21.9,1.21.10)` |
| Platform sources | `platform/1.21.10/eu` | `platform/1.21.9/eu` |
| Loader glue | `loader/neoforge-1.21.10/java` | `loader/neoforge-1.21.9/java` |
| Manifest `Minecraft-Version` | 1.21.10 | 1.21.9 |

### Code changes forced by the version bump

* **Core overlay is the 1.21.10 one.** `versions/1.21.9-fabric/compat-src` carries the same twelve
  files (`AccountManager`, `NetworkManager`, `PacketEncryption`, `GuiHypixelStats`,
  `GuiNameHistory`, `ChatFilterManager`, `DataManager`, `ServerPing`, `OCCListener`,
  `ChatHighlighting`, `ServerTabImpl`, `ImageManager`): authlib 7 (`GameProfile` record) and
  commons-lang3 3.19's `org.apache.commons.lang3.Strings` (collides with Guava's `Strings`).
* **No `additionalRuntimeClasspath`.** Not a choice: MDG 2.0.148 rejects the configuration on this
  Minecraft version at configuration time —

  ```
  Tried to add a dependency to configuration configuration ':additionalRuntimeClasspath',
  but there is no additional classpath anymore for Minecraft 1.21.9.
  Add the dependency to a standard configuration such as implementation or runtimeOnly.
  ```

  `implementation` alone is enough (FML 10's mod-locator rework puts the game classpath in reach of
  the mod class loader), and `jarJar` still nests all twelve libraries. Same as 1.21.10.
* **No payload bridge.** `platform/1.21.9`'s `Variables#sendCustomPayload` drops legacy
  plugin-channel messages with a one-off warning, so the loader glue is only key mappings + the
  client tick driver. (Its log line still says "not supported on 1.21.11"; cosmetic, in the shared
  platform layer, not touched here.)

## Payload registrar API in this line (javap on `neoforge-21.9.16-beta-universal.jar`)

```
RegisterPayloadHandlersEvent.registrar(String)                       // a version string, not a namespace
PayloadRegistrar.playBidirectional(Type, StreamCodec, IPayloadHandler, IPayloadHandler)   <- use this
PayloadRegistrar.playBidirectional(Type, StreamCodec, IPayloadHandler)                    <- delegates null
PayloadRegistrar.playToClient    (Type, StreamCodec, IPayloadHandler)
PayloadRegistrar.playToClient    (Type, StreamCodec)                                      <- codec only
PayloadRegistrar.playToServer    (Type, StreamCodec, IPayloadHandler)
net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent           <- exists
PayloadRegistration: type, codec, protocols, flow, version, optional   (NO handler component)
```

Identical shape to 21.7: the three-argument `playBidirectional` compiles to
`playBidirectional(type, codec, handler, null)`, so a bidirectional registration made with it leaves
the clientbound side unhandled and `ClientNetworkRegistry#setup` aborts with
`Some clientbound payloads are missing client-side handlers`. A bridge here must use the four-argument
form (or `playToClient(type, codec)` plus `RegisterClientPayloadHandlersEvent`).

The client sender is `net.neoforged.neoforge.client.network.ClientPacketDistributor#sendToServer`;
`net.neoforged.neoforge.network.PacketDistributor` has no `sendToServer` in this line.

This build registers no payloads, so the above is documentation for a future bridge.

## Mixin targets

No loader copy of any mixin was needed. All 30 `@Inject`/`@ModifyVariable`/`@Shadow` targets named by
`platform/1.21.9` resolve with the exact JVM descriptor against
`build/moddev/artifacts/neoforge-21.9.16-beta.jar`, including the HUD bracket
`Gui#render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V`,
`Gui#renderItemHotbar`, `Gui#tick()V` (1.21.9 dropped the `boolean` parameter) and
`Gui#setOverlayMessage(Component, Z)`. The 1.21.9 input rework
(`keyPressed(KeyEvent)`, `mouseClicked(MouseButtonEvent, Z)`) is in the platform already and
resolves here too.

The checker is not vacuous: `platform/1.21.6`'s chat-screen targets run against this jar report
`keyPressed(III)Z` and `mouseClicked(DDI)Z` as missing.

## Verification performed

No client was launched (task rule). Static checks:

* `gradle clean build` → `BUILD SUCCESSFUL` against NeoForge 21.9.16-beta's patched jar.
* 30/30 mixin targets resolve by name + descriptor.
* `META-INF/neoforge.mods.toml` parses, `${version}` expands to `4.0.0-alpha.1`, `[[mixins]]` names
  `mixins.the5zigmod.json`, ranges are `[21.9,22)` / `[1.21.9,1.21.10)`.
* `mixins.the5zigmod.json`: `JAVA_21`, 13 classes, every class file present in the jar.
* 12 nested jars, each a valid zip; `META-INF/jarjar/metadata.json` lists exactly those 12 paths;
  no duplicate entries; class file version 65 throughout; zero `net/fabricmc` references.

## Not verified without a client

* HUD bracket / action-bar suppression behaviour (static resolution only).
* Keybinding registration against `Options`' freeze point, including the
  `KeyMapping.Category` instances the 1.21.9 platform registers itself.
* FML 10 accepting `loaderVersion="[10,)"` with 21.9.16-beta at runtime.
* Discord RPC (DiscordIPC + junixsocket) and sqlite initialisation from the nested jars.
