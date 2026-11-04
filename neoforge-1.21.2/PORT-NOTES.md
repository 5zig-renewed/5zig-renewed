# 1.21.2 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.2.1-beta** (FancyModLoader 4.0.29, Mixin 0.8.7) — beta only, see below |
| Minecraft | 1.21.2 (`neoform:1.21.2-20241022.151510`), Mojang official mappings |

Build:

```bash
cd neoforge-1.21.2
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.2/build/libs/5zig-renewed-1.21.2-neoforge-4.0.0-alpha.1.jar`
(18.8 MB, self-contained: eight libraries nested under `META-INF/jarjar/`).

## Release situation — this line never left beta

`maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml` contains **exactly two**
artefacts for Minecraft 1.21.2:

```
21.2.0-beta
21.2.1-beta
```

There is no stable `21.2.x`. The reason is the calendar: 1.21.2 shipped 2024-10-22 and 1.21.3
superseded it the next day, so NeoForge shipped two betas for 1.21.2 and moved the line to 21.3.x.
`21.2.1-beta` is the last and only usable build; it is what this project targets.

Consequence for shipping: a 1.21.2 jar would have to declare a dependency on a beta loader. It builds
and packages cleanly, but it is not a release-grade target — 1.21.3 (stable, `21.3.100`) is the
recommended NeoForge artefact for this era.

## What is different from the 1.21.4 project

| Item | 1.21.4 | 1.21.2 |
|---|---|---|
| NeoForge | 21.4.161 | 21.2.1-beta |
| FancyModLoader | 6.0.18 | 4.0.29 |
| `loaderVersion` | `[4,)` | `[4,)` |
| neoForge dependency range | `[21.4,22)` | `[21.2,22)` |
| Minecraft dependency range | `[1.21.4,1.21.5)` | `[1.21.2,1.21.3)` |
| Platform sources | `platform/1.21.4/eu` | `platform/1.21.2/eu` |
| Loader glue | `loader/neoforge-1.21.4/java` | `loader/neoforge-1.21.2/java` |
| Manifest `Minecraft-Version` | 1.21.4 | 1.21.2 |

## Payload registrar (checked with javap, not assumed)

`javap` on `net.neoforged:neoforge:21.2.1-beta:universal`:

```
PayloadRegistrar registrar(String version)                     // RegisterPayloadHandlersEvent
PayloadRegistrar playBidirectional(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>, IPayloadHandler<T>)
```

Identical to 21.3.100: the legacy three-argument form with a single handler for both directions,
`registrar(String)` being the payload **version**. The 1.21.8+ split (`playBidirectional` without an
implicit clientbound handler, which produces "Some clientbound payloads are missing client-side
handlers") does not apply here.

## Mixin targets verified against the patched jar

`build/moddev/artifacts/neoforge-21.2.1-beta.jar` carries every injection point named by the platform
mixins, with matching descriptors — `Gui#render(GuiGraphics, DeltaTracker)` /
`renderItemHotbar(GuiGraphics, DeltaTracker)` / `tick()` / `setOverlayMessage(Component, boolean)`,
`Screen#render/renderBackground/hasControlDown`, `ChatComponent#render/addMessage/clearMessages/scrollChat`,
`ChatScreen#render/keyPressed/mouseClicked`, `TitleScreen#render`, `DeathScreen#init`,
`DisconnectedScreen#init`, `ClientPacketListener#sendChat/handleCustomPayload/handleTabListCustomisation/
handleContainerSetSlot/handleMovePlayer/handleChangeDifficulty`, `Connection#send/channelRead0`,
`Minecraft#setScreen/startAttack/startUseItem`, `PlayerTabOverlay#renderPingIcon`,
`AbstractClientPlayer#getFieldOfViewModifier/getSkin`, `GameRenderer#pick(F)`.

No loader-local mixin was needed; the HUD bracket stays the platform's `@Mixin(Gui)` delegating to
`HudFrame`.

## Verification performed

No client was launched (task rule).

* `gradle build` → `BUILD SUCCESSFUL` against the real NeoForge 21.2.1-beta patched jar.
* Mixin config packaged and declared via `[[mixins]] config=`; 13 declared client mixins present,
  `MixinCommonMinecraft` comes from `core/resources/mixins.json`.
* `META-INF/jarjar/` holds all eight libraries + `metadata.json` with `strictly` ranges
  (`DiscordIPC:master-SNAPSHOT` keeps `[master-SNAPSHOT,)`).
* `PluginMessages.class` in the jar is the NeoForge implementation.
* Manifest carries `Minecraft-Version: 1.21.2`.
