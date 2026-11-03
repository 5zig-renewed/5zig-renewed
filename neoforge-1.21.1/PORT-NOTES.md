# 1.21.1 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.1.265** (FancyModLoader 4.0.45, Mixin 0.8.7) |
| Minecraft | 1.21.1, Mojang official mappings |

Build:

```bash
cd neoforge-1.21.1
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.1/build/libs/5zig-renewed-1.21.1-neoforge-4.0.0-alpha.1.jar`
(18.8 MB, self-contained: eight libraries nested under `META-INF/jarjar/`).

## Why MDG and not ForgeGradle / NeoGradle

The 1.20.1 NeoForge build works because `net.neoforged:forge:1.20.1-47.1.106` is a Forge-format
userdev artifact. NeoForge 21.1.x is a real `net.neoforged.*` loader, so ForgeGradle cannot consume it.
MDG is the NeoForge-official plugin for that line and needs no reobfuscation: NeoForge 1.20.5+ runs on
Mojang official mappings, the same names `platform/1.21.1` is written against. Consequences:

- no `reobfJar`/`reobfJarJar` step, and no mixin refmap — the 13 platform mixins resolve directly
  (verified against MDG's patched NeoForge jar: 35/35 injection targets found);
- `neoForge { enable { disableRecompilation = true } }` skips the NeoForm decompile/recompile pass
  (only the compiled game jar is needed for this build and it keeps the build inside the box's
  memory budget). Remove it if IDE sources are wanted.

## Glue written

| File | Role |
|---|---|
| `loader/neoforge-1.21.1/java/eu/the5zig/mod/neoforge/FiveZigNeoForge.java` | `@Mod(value = "the5zigmod", dist = Dist.CLIENT)` entrypoint; keybind collection, payload registration, core init on the first client tick |
| `loader/neoforge-1.21.1/java/eu/the5zig/mod/platform/PluginMessages.java` | NeoForge replacement for the Fabric-API payload bridge in `platform/1.21.1` (same package and public API: `bootstrap()`, `send()`, `MAX_PAYLOAD_SIZE`) |
| `neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml` | FML 4 metadata, `[[mixins]]` declaration, `[21.1,22)` / `[1.21.1,1.21.2)` dependency ranges |
| `neoforge-1.21.1/src/main/resources/mixins.the5zigmod.json` | Platform mixin config (`JAVA_21`), same 13 mixins as `loader/fabric-1.21.1` |

No new loader mixin was needed. NeoForge 1.21.1 renders the **vanilla** `Gui` (there is no
`ForgeGui`), so the platform's own `@Mixin(Gui)` hook on
`render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V` is the HUD
bracket; it delegates to `HudFrame` exactly like the Fabric build. The 1.20.1
`MixinForgeGui` is therefore not carried over.

## What 1.21.1 NeoForge forced to change vs the 1.20.1 glue

1. **Packages.** `net.neoforged.fml.common.Mod`, `net.neoforged.bus.api.IEventBus`,
   `net.neoforged.neoforge.client.event.*`, `net.neoforged.neoforge.common.NeoForge`.
2. **No `@EventBusSubscriber` scanning.** Listeners are attached explicitly in the mod constructor
   (`modEventBus.addListener(...)`, `NeoForge.EVENT_BUS.addListener(...)`), which removes any
   mod-id/bus ambiguity. FML 4 injects `(IEventBus, ModContainer)` into the constructor.
3. **Tick event split.** `TickEvent.ClientTickEvent` with phases is gone; the driver listens to
   `ClientTickEvent.Post`.
4. **`RegisterKeyMappingsEvent` is still a mod-bus event** (`implements IModBusEvent`, fired from
   `Options` construction), not a game-bus one. `Keybinds.bootstrap()` still runs in the constructor
   — before `Options` exists — and the event handler registers the collected mappings and closes the
   registration window.
5. **Mixin configs moved.** FML 4 reads `[[mixins]] config="..."` from `META-INF/neoforge.mods.toml`
   (`ModFileParser#getMixinConfigs`); it no longer reads the legacy `MixinConfigs` manifest
   attribute. Declaring only the manifest attribute would silently disable the HUD hook, so the
   manifest no longer carries it.
6. **`mods.toml` → `META-INF/neoforge.mods.toml`** with `loaderVersion="[4,)"` and
   `type="required"` dependency entries (`mandatory` no longer exists in FML 4).
7. **Jar-in-jar** is MDG's `jarJar` configuration (nested jars + `META-INF/jarjar/metadata.json`),
   not ForgeGradle's `jarJar` extension. Ranges are exact (`[3.45.3.0]`, …) so another mod bundling
   the same library cannot be selected at a different version. `DiscordIPC:master-SNAPSHOT` is the
   only entry with an open range; its metadata was verified to parse with FML's own
   `JarJarMetadata 0.4.1`.
   `json-simple` and the two `junixsocket` artifacts are nested explicitly even though nothing in
   `core` imports them: `DiscordIPC`'s POM declares them and its bytecode needs
   `org.json.simple` (7 classes) plus `org.newsclub.net.unix` (`UnixPipe`). They are only reachable
   transitively in a dev workspace, so without nesting them the shipped jar dies with
   `NoClassDefFoundError` inside `The5zigMod.init()` and the mod silently disables itself.
8. **Typed payloads** (`PluginMessages`): Fabric's `PayloadTypeRegistry` +
   `ClientPlayNetworking` became `RegisterPayloadHandlersEvent` → `PayloadRegistrar.optional()`
   `.playBidirectional(...)`, sending through `PacketDistributor.sendToServer`, and the
   "can the server receive this?" check became
   `NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, id)` (the Fabric build's
   `ClientPlayNetworking.canSend` equivalent).
9. **Build wiring.** `sourceSets` still point at the repo root, but `platform/1.21.1` needs the same
   two authlib-6 exclusions as `versions/1.21.1-fabric`, and additionally excludes the platform's
   Fabric-API `PluginMessages.java` in favour of the NeoForge copy.

## Behaviour that is stubbed or degraded

- **Plugin channels** only cover the three registered 5zig channels
  (`the5zigmod:5zig`, `…:5zig_reg`, `…:5zig_set`), declared `optional()` so vanilla servers are
  unaffected. `send()` returns `false` (once, with a warning) for any other channel, and also when
  the server has not announced the channel — 1.21.1 cannot send unregistered payload types. Same
  degradation as the 1.21.1 Fabric build.
- **ReplayMod detection is off.** `ModCompatibility` looks up `net.minecraftforge.fml.common.Loader`
  when `Transformer.FORGE` is true; that class does not exist on NeoForge 21.1, so the lookup is
  swallowed and `hasReplayMod` stays false. Harmless.
- **No config screen.** No `IConfigScreenFactory` extension point is registered; the mod's own
  `M` menu is the settings UI, as on every other target.
- **Decompiled sources are not attached** (`disableRecompilation`), so IDE navigation into Minecraft
  shows signatures only. Drop that flag if sources are needed.
- **Not client-verified.** Per the task rules no Minecraft client was launched. Static checks done:
  compile against the real NeoForge 21.1.265 artifact, 35/35 mixin injection targets resolved
  against MDG's patched jar, TOML parsed, all five nested jars valid, one manifest, no duplicate
  entries, zero `net.fabricmc` references left in the artifact.
