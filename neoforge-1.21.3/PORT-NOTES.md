# 1.21.3 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.3.100** (FancyModLoader 5.0.8, Mixin 0.8.7) — last build of the 21.3.x line |
| Minecraft | 1.21.3 (`neoform:1.21.3-20241023.131943`), Mojang official mappings |

Build:

```bash
cd neoforge-1.21.3
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.3/build/libs/5zig-renewed-1.21.3-neoforge-4.0.0-alpha.1.jar`
(18.8 MB, self-contained: eight libraries nested under `META-INF/jarjar/`).

## Release situation

The 21.3.x line is the Minecraft 1.21.3 line. It started as `21.3.0-beta` … `21.3.55-beta` and went
stable at `21.3.56`; `21.3.100` is the newest artefact in the line. 1.21.3 itself was a one-week
hotfix release (2024-10-23 → 1.21.4 on 2024-12-03), so the line is short but has a real stable build.

## What is different from the 1.21.4 project

Only version constants and source roots; the glue is byte-identical apart from its comments.

| Item | 1.21.4 | 1.21.3 |
|---|---|---|
| NeoForge | 21.4.161 | 21.3.100 |
| FancyModLoader | 6.0.18 | 5.0.8 |
| `loaderVersion` | `[4,)` | `[5,)` |
| neoForge dependency range | `[21.4,22)` | `[21.3,22)` |
| Minecraft dependency range | `[1.21.4,1.21.5)` | `[1.21.3,1.21.4)` |
| Platform sources | `platform/1.21.4/eu` | `platform/1.21.3/eu` |
| Loader glue | `loader/neoforge-1.21.4/java` | `loader/neoforge-1.21.3/java` |
| Manifest `Minecraft-Version` | 1.21.4 | 1.21.3 |

## Payload registrar (checked with javap, not assumed)

`javap` on `net.neoforged:neoforge:21.3.100:universal` shows the **legacy three-argument** contract:

```
PayloadRegistrar registrar(String version)                     // RegisterPayloadHandlersEvent
PayloadRegistrar playBidirectional(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>, IPayloadHandler<T>)
```

i.e. one handler serves both directions, unlike the 1.21.8+ split where `playBidirectional` no longer
supplies a clientbound handler. `event.registrar("1")` is the **version** string here. The glue from
1.21.4 therefore compiles and behaves unchanged.

## Mixin targets verified against the patched jar

`build/moddev/artifacts/neoforge-21.3.100.jar` (the NeoForm + NeoForge patched client) carries every
injection point the platform mixins name, with matching descriptors:

* `Gui#render(GuiGraphics, DeltaTracker)`, `renderItemHotbar(GuiGraphics, DeltaTracker)`, `tick()`,
  `setOverlayMessage(Component, boolean)` — the HUD bracket lives here and delegates to `HudFrame`.
* `Screen#render/renderBackground/hasControlDown`, `ChatComponent#render/addMessage/clearMessages/scrollChat`,
  `ChatScreen#render/keyPressed/mouseClicked`, `TitleScreen#render`, `DeathScreen#init`,
  `DisconnectedScreen#init`.
* `ClientPacketListener#sendChat/handleCustomPayload/handleTabListCustomisation/handleContainerSetSlot/
  handleMovePlayer/handleChangeDifficulty`, `Connection#send/channelRead0`,
  `Minecraft#setScreen/startAttack/startUseItem`, `PlayerTabOverlay#renderPingIcon`,
  `AbstractClientPlayer#getFieldOfViewModifier/getSkin`, `GameRenderer#pick(F)`.

No loader-local mixin was needed: NeoForge 21.3 keeps the vanilla `Gui` render hooks (no `GuiLayerManager`
split like 1.21.5+/26.x).

## Verification performed

No client was launched (task rule). Static checks only:

* `gradle build` → `BUILD SUCCESSFUL`; compiled against the real NeoForge 21.3.100 patched jar.
* `mixins.the5zigmod.json` present at the jar root, declared through `[[mixins]] config=` in
  `META-INF/neoforge.mods.toml` (FML 4+ ignores the legacy `MixinConfigs` manifest key).
* All 13 declared client mixins plus `MixinCommonMinecraft` (declared in `core/resources/mixins.json`)
  are present as classes; `MixinGui` references `HudFrame`.
* `META-INF/jarjar/` holds all eight libraries + `metadata.json`; every entry uses
  `version { strictly '[x]' }` except `DiscordIPC:master-SNAPSHOT` (`[master-SNAPSHOT,)`).
* `PluginMessages.class` in the jar is the NeoForge implementation (references
  `net/neoforged/neoforge/network/**`), not the Fabric one.
* Manifest carries `5zig-Version`, `Minecraft-Version: 1.21.3`, `5zig-Build`, `Specification-Title`.
