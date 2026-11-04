# 1.20.4 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 to launch Gradle, **17** toolchain (the game is a Java 17 title; class files are 61) |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **20.4.253** (FancyModLoader 2.0.17, Mixin 0.8.5) |
| Minecraft | 1.20.4, Mojang official mappings, **no reobfuscation** |

Build:

```bash
cd neoforge-1.20.4
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.20.4/build/libs/5zig-renewed-1.20.4-neoforge-4.0.0-alpha.1.jar`
(18.7 MB, self-contained: eight libraries nested under `META-INF/jarjar/`).

## Does NeoForge publish a 1.20.4 build? Yes

`net.neoforged:neoforge:20.4.253` (final 20.4 release) is a real `net.neoforged.*` loader and —
unlike 1.20.1 — publishes `neoforge-20.4.253-moddev-config.json`, which is the artefact MDG 2.x
needs. 20.4.251 is the last 20.4 build *before* 253; 253 itself sits slightly out of order in the
maven metadata because it was published later.

## Mappings: no reobf needed on this line

The 1.20.1 Forge/NeoForge jars had to be reobfuscated to SRG for production. NeoForge 20.2 onward
is **officially mapped at runtime**: the 20.4 universal jar references `net/minecraft/client/Options.keyMappings`
directly, with no `f_…_`/`m_…_` SRG names anywhere in its bytecode. So the platform sources (written
against Mojang official names, the same set the Fabric 1.20.4 build uses) compile and ship
unchanged, and neither `reobfJar` nor a mixin refmap is needed. Mixin injection points are resolved
by name at runtime exactly like on 1.21.1.

## Glue written

| File | Role |
|---|---|
| `loader/neoforge-1.20.4/java/eu/the5zig/mod/neoforge/FiveZigNeoForge.java` | `@Mod("the5zigmod")` entrypoint (FML 2 has no `dist` attribute), keybind collection, core init on the first `TickEvent.ClientTickEvent` END phase |
| `loader/neoforge-1.20.4/java/eu/the5zig/mod/platform/PluginMessages.java` | NeoForge replacement for the Fabric-API payload bridge (same package + public API: `bootstrap()`, `send()`, `MAX_PAYLOAD_SIZE`) |
| `neoforge-1.20.4/src/main/resources/META-INF/mods.toml` | FML 2 metadata — note the **`mods.toml`** name (the `neoforge.mods.toml` rename is FML 3/1.20.5+), `loaderVersion="[2,)"`, `[[mixins]]`, `type="required"` dependencies |
| `neoforge-1.20.4/src/main/resources/mixins.the5zigmod.json` | Platform mixin config (`JAVA_17`), same 14 mixins as the Fabric 1.20.4 build |

## What 1.20.4 forced to change vs the 1.21.1 glue

1. **`@Mod` has no `dist`.** FML 2.0.17's annotation only carries `value()`; the client-only nature
   is expressed by the TOML `side="CLIENT"` dependencies and by the entrypoint only being loaded on
   the client. `FiveZigNeoForge` is therefore `@Mod("the5zigmod")`.
2. **Tick event.** `net.neoforged.neoforge.event.TickEvent.ClientTickEvent` with
   `phase == TickEvent.Phase.END`; the split `ClientTickEvent.Pre/Post` only exists from 1.20.5.
3. **Payloads are keyed by `ResourceLocation`, not by a payload `Type`.** 1.20.4's
   `CustomPacketPayload` has `id()` + `write(FriendlyByteBuf)` and no `Type` record, so
   `RegisterPayloadHandlerEvent` (singular) → `IPayloadRegistrar.play(ResourceLocation, FriendlyByteBuf.Reader, IPlayPayloadHandler)`.
   Handlers run on the network thread and hop to the client thread with
   `PlayPayloadContext.workHandler().execute(...)` (there is no `enqueueWork` yet).
4. **Sending is vanilla.** `PacketDistributor` on 20.4 is the builder-style API with no static
   `sendToServer`, but it is not needed: `ServerboundCustomPayloadPacket` still writes an arbitrary
   id + body, so `PluginMessages.send` sends that packet directly and `Variables#sendCustomPayload`
   keeps its existing raw-payload fallback.
5. **`[[mixins]]` in the TOML, not the manifest.** FML 2.0.17 reads mixin configs from
   `META-INF/mods.toml` (`ModFileParser#getMixinConfigs`); the manifest intentionally does not list
   `MixinConfigs` so the config is not registered twice.

## Verification (no client run, per task rules)

- `gradle build` green; jar is 18.7 MB.
- Eight nested jars in `META-INF/jarjar/` (`DiscordIPC`, `jacob`, `json`, `json-simple`,
  `junixsocket-common`, `junixsocket-native-common`, `netty-codec-http`, `sqlite-jdbc`) and
  `META-INF/jarjar/metadata.json` with exact ranges (`[3.45.3.0]`, …; only the `DiscordIPC`
  SNAPSHOT keeps an open range).
- `META-INF/mods.toml` parsed from the jar: version expanded, `[[mixins]]` present,
  `[20.4,20.5)` / `[1.20.4,1.20.5)` dependency ranges.
- Manifest carries `5zig-Version` / `Minecraft-Version: 1.20.4` / `5zig-Build`.
- Class files are version 61 (Java 17); zero `net.fabricmc` references in the artifact.
- 36/36 named mixin method targets in the 14 platform mixins resolve against MDG's patched
  NeoForge 20.4.253 jar (`javap`-based check, private members included).
