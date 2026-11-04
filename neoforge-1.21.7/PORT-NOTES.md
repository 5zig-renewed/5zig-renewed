# 1.21.7 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.7.25-beta** (FancyModLoader 9.0.14, Mixin 0.8.7) |
| Minecraft | 1.21.7, Mojang official mappings |

Build:

```bash
cd neoforge-1.21.7
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.7/build/libs/5zig-renewed-1.21.7-neoforge-4.0.0-alpha.1.jar`
(20.1 MB, self-contained: twelve libraries nested under `META-INF/jarjar/`).

## What is different from the verified 1.21.8 project

| Item | 1.21.8 | 1.21.7 |
|---|---|---|
| NeoForge | 21.8.57 (FML 9.0.18) | 21.7.25-beta (FML 9.0.14) |
| `loaderVersion` | `[9,)` | `[9,)` (unchanged) |
| neoForge dependency range | `[21.8,22)` | `[21.7,22)` |
| Minecraft dependency range | `[1.21.8,1.21.9)` | `[1.21.7,1.21.8)` |
| Platform sources | `platform/1.21.8/eu` | `platform/1.21.7/eu` |
| Loader glue | `loader/neoforge-1.21.8/java` | `loader/neoforge-1.21.7/java` |
| Manifest `Minecraft-Version` | 1.21.8 | 1.21.7 |

### Code changes forced by the version bump

* **Core overlay unchanged.** `platform/1.21.7` carries no `AccountManager`/`PacketEncryption`, so
  the same two files are filtered out of the copied core and replaced by
  `versions/1.21.7-fabric/compat-src`, the tree the verified 1.21.7 Fabric build compiles.
* **No payload bridge.** `platform/1.21.7`'s `Variables#sendCustomPayload` already drops legacy
  plugin-channel messages with a one-off warning; no platform code references `PluginMessages`.
  Unlike 1.21.8, this project ships no loader-local bridge (dead code there), so the loader glue is
  key mappings + the client tick driver, exactly like 1.21.10.
* **`additionalRuntimeClasspath` still exists** on FML 9: all twelve libraries are declared twice
  (`implementation` + `additionalRuntimeClasspath`) and jarJar'd, same as 1.21.8.

## Payload registrar API in this line (javap on `neoforge-21.7.25-beta-universal.jar`)

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

21.7 is the line where the handler split happened: `PayloadRegistration` no longer stores a
handler, the clientbound side is a separate registration, and the three-argument
`playBidirectional` compiles to `playBidirectional(type, codec, handler, null)`. Registering a
bidirectional payload with it leaves the clientbound handler unset, which is the
`Some clientbound payloads are missing client-side handlers` abort in `ClientNetworkRegistry#setup`.
A bridge on this line must use the four-argument form (or `playToClient(type, codec)` plus a
`RegisterClientPayloadHandlersEvent` listener).

The client-bound sender also moved: `net.neoforged.neoforge.network.PacketDistributor` has **no**
`sendToServer` in 21.7; it is `net.neoforged.neoforge.client.network.ClientPacketDistributor#sendToServer`.

This build registers no payloads (the platform drops legacy channels), so the above is
documentation for a future bridge, not live code.

## Mixin targets

No loader copy of any mixin was needed. All 30 `@Inject`/`@ModifyVariable`/`@Shadow` targets named by
`platform/1.21.7` resolve with the exact JVM descriptor against
`build/moddev/artifacts/neoforge-21.7.25-beta.jar`, including the HUD bracket
`Gui#render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V`,
`Gui#renderItemHotbar`, `Gui#tick(Z)V` and `Gui#setOverlayMessage(Component, Z)`.

The checker is not vacuous: `platform/1.21.9`'s chat-screen targets run against this jar report
`keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z` and
`mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z` as missing (the 1.21.9 input rework).

## Verification performed

No client was launched (task rule). Static checks:

* `gradle clean build` → `BUILD SUCCESSFUL` against NeoForge 21.7.25-beta's patched jar.
* 30/30 mixin targets resolve by name + descriptor.
* `META-INF/neoforge.mods.toml` parses, `${version}` expands to `4.0.0-alpha.1`, `[[mixins]]` names
  `mixins.the5zigmod.json`, ranges are `[21.7,22)` / `[1.21.7,1.21.8)`.
* `mixins.the5zigmod.json`: `JAVA_21`, 13 classes, every class file present in the jar.
* 12 nested jars, each a valid zip; `META-INF/jarjar/metadata.json` lists exactly those 12 paths;
  no duplicate entries; class file version 65 throughout; zero `net/fabricmc` references.

## Not verified without a client

* HUD bracket / action-bar suppression behaviour (static resolution only).
* Keybinding registration against `Options`' freeze point.
* FML 9 accepting `loaderVersion="[9,)"` with 21.7.25-beta at runtime.
* Discord RPC (DiscordIPC + junixsocket) and sqlite initialisation from the nested jars.
