# 1.21.6 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.6.20-beta** (FancyModLoader 9.0.2, Mixin 0.8.7) |
| Minecraft | 1.21.6, Mojang official mappings |

Build:

```bash
cd neoforge-1.21.6
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.6/build/libs/5zig-renewed-1.21.6-neoforge-4.0.0-alpha.1.jar`
(20.1 MB, self-contained: twelve libraries nested under `META-INF/jarjar/`).

## What is different from the verified 1.21.8 project

This project is the 1.21.8 one with the version constants moved:

| Item | 1.21.8 | 1.21.6 |
|---|---|---|
| NeoForge | 21.8.57 (FML 9.0.18) | 21.6.20-beta (FML 9.0.2) |
| `loaderVersion` | `[9,)` | `[9,)` (unchanged) |
| neoForge dependency range | `[21.8,22)` | `[21.6,22)` |
| Minecraft dependency range | `[1.21.8,1.21.9)` | `[1.21.6,1.21.7)` |
| Platform sources | `platform/1.21.8/eu` | `platform/1.21.6/eu` |
| Loader glue | `loader/neoforge-1.21.8/java` | `loader/neoforge-1.21.6/java` |
| Manifest `Minecraft-Version` | 1.21.8 | 1.21.6 |

### Code changes forced by the version bump

* **Core overlay unchanged.** 1.21.6 ships authlib 6 (`GameProfile` is a record, the legacy
  Yggdrasil client is gone), so the same two files (`AccountManager`, `PacketEncryption`) are
  filtered out of the core copy and supplied by `versions/1.21.6-fabric/compat-src` — the exact
  tree the verified 1.21.6 Fabric build compiles.
* **No payload bridge.** `platform/1.21.6`'s `Variables#sendCustomPayload` already drops legacy
  plugin-channel messages with a one-off warning ("free-form payloads were replaced by registered
  CustomPacketPayload types"), and nothing in the platform refers to a `PluginMessages` class.
  The 1.21.8 project still carries a loader-local bridge that no platform code calls; this project
  does not, so the loader glue is only key mappings + the client tick driver, exactly like 1.21.10.
* **`additionalRuntimeClasspath` still exists.** FML 9 keeps the configuration, so all twelve
  libraries are declared twice (`implementation` + `additionalRuntimeClasspath`) **and** jarJar'd,
  same as 1.21.8.

## Payload registrar API in this line (javap on `neoforge-21.6.20-beta-universal.jar`)

```
RegisterPayloadHandlersEvent.registrar(String)                       // a version string, not a namespace
PayloadRegistrar.playBidirectional(Type, StreamCodec, IPayloadHandler)          <- the ONLY overload
PayloadRegistrar.playToClient    (Type, StreamCodec, IPayloadHandler)
PayloadRegistrar.playToServer    (Type, StreamCodec, IPayloadHandler)
PayloadRegistration: type, codec, handler, protocols, flow, version, optional
```

There is no `net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent` in
21.6, and `PayloadRegistration` still has a single `handler` component: one handler serves both
directions, so `playBidirectional(type, codec, handler)` is correct here. The client-bound sender
is still `net.neoforged.neoforge.network.PacketDistributor#sendToServer`; this line has no
`ClientPacketDistributor` class at all.

Since this build registers no payloads, the point is documentation only — but if a bridge is ever
restored here, that is the shape to use.

## Mixin targets

No loader copy of any mixin was needed. All 30 `@Inject`/`@ModifyVariable`/`@Shadow` targets named by
`platform/1.21.6` resolve with the exact JVM descriptor against
`build/moddev/artifacts/neoforge-21.6.20-beta.jar`, including
`Gui#render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V` (the HUD
bracket), `Gui#renderItemHotbar`, `Gui#tick(Z)V` and `Gui#setOverlayMessage(Component, Z)`.

The checker is not vacuous: `platform/1.21.9`'s chat-screen targets run against this same jar report
`keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z` and
`mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z` as missing, which is exactly the
1.21.9 input rework.

## Verification performed

No client was launched (task rule). Static checks:

* `gradle clean build` → `BUILD SUCCESSFUL` against NeoForge 21.6.20-beta's patched jar.
* 30/30 mixin targets resolve by name + descriptor.
* `META-INF/neoforge.mods.toml` parses, `${version}` expands to `4.0.0-alpha.1`, `[[mixins]]` names
  `mixins.the5zigmod.json`, ranges are `[21.6,22)` / `[1.21.6,1.21.7)`.
* `mixins.the5zigmod.json`: `JAVA_21`, 13 classes, every class file present in the jar.
* 12 nested jars, each a valid zip; `META-INF/jarjar/metadata.json` lists exactly those 12 paths;
  no duplicate entries; class file version 65 throughout; zero `net/fabricmc` references.

## Not verified without a client

* That the HUD frame actually brackets the frame and the action-bar hook suppresses the vanilla
  overlay (static resolution only, no `@At` dead-code check).
* Keybinding registration against `Options`' freeze point, and `KeyMapping.Category` behaviour.
* FML 9 accepting `loaderVersion="[9,)"` with 21.6.20-beta at runtime.
* Discord RPC (DiscordIPC + junixsocket) and sqlite initialisation from the nested jars.
