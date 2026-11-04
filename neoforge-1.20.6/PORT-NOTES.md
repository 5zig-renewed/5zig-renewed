# 1.20.6 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (game and toolchain; class files are 65) |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **20.6.144** (FancyModLoader 3.0.45, Mixin 0.8.5) |
| Minecraft | 1.20.6, Mojang official mappings, no reobfuscation |

Build:

```bash
cd neoforge-1.20.6
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.20.6/build/libs/5zig-renewed-1.20.6-neoforge-4.0.0-alpha.1.jar`
(18.8 MB, eight libraries nested under `META-INF/jarjar/`).

## Does NeoForge publish a 1.20.6 build? Yes

`net.neoforged:neoforge:20.6.144` is the final 20.6 release and publishes a `moddev-config.json`,
so MDG 2.0.148 consumes it natively.

## Glue written

| File | Role |
|---|---|
| `loader/neoforge-1.20.6/java/eu/the5zig/mod/neoforge/FiveZigNeoForge.java` | `@Mod(value = "the5zigmod", dist = Dist.CLIENT)` entrypoint, keybinds, core init on `ClientTickEvent.Post` |
| `loader/neoforge-1.20.6/java/eu/the5zig/mod/platform/PluginMessages.java` | NeoForge payload bridge (same package + API as the platform's Fabric version) |
| `neoforge-1.20.6/src/main/resources/META-INF/neoforge.mods.toml` | FML 3 metadata, `loaderVersion="[3,)"`, `[[mixins]]`, `[20.6,20.7)` / `[1.20.6,1.20.7)` |
| `neoforge-1.20.6/src/main/resources/mixins.the5zigmod.json` | Platform mixin config (`JAVA_21`), the same 14 mixins as the Fabric 1.20.6 build |

## Differences vs 1.20.4 (both NeoForge, one MC step apart)

1. **TOML file name.** 1.20.6 / FML 3 reads `META-INF/neoforge.mods.toml`; `mods.toml` is no longer
   picked up. `loaderVersion` moves to `[3,)`.
2. **`@Mod` regains `dist`** (`dist = Dist.CLIENT`), matching 1.21.1.
3. **Tick event split.** `net.neoforged.neoforge.client.event.ClientTickEvent.Post` — the
   `TickEvent`/`Phase` pair is gone.
4. **Payload registration is the modern one.** `RegisterPayloadHandlersEvent` (plural) →
   `PayloadRegistrar.playBidirectional(CustomPacketPayload.Type, StreamCodec<RegistryFriendlyByteBuf, T>, IPayloadHandler)`
   with `context.enqueueWork(...)`; handlers are `IPayloadHandler`, not `IPlayPayloadHandler`.
   On this line `registrar(String)` takes the **version** (`PayloadRegistrar(String version)`,
   verified with `javap` on `neoforge-20.6.144-universal.jar`), and the namespace comes from each
   payload's `Type` id. `registrar("1")` is therefore correct here, unlike the 20.4 build where the
   same-looking call means namespace.
5. **Sending must be registered.** From 1.20.5 the codec drops the body of any payload type it does
   not know, so `PluginMessages.send` goes through `PacketDistributor.sendToServer` and guards with
   `NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, id)` (the `canSend` equivalent).
   `platform/1.20.6/Variables` deliberately has no raw-packet fallback.

## Verification (no client run, per task rules)

- `gradle build` green; jar is 18.8 MB.
- Eight nested jars + `META-INF/jarjar/metadata.json` with exact ranges (only `DiscordIPC`'s SNAPSHOT
  keeps an open range).
- `META-INF/neoforge.mods.toml` expanded and parsed, `[[mixins]]` present, dependency ranges
  `[20.6,20.7)` / `[1.20.6,1.20.7)`.
- Manifest carries `Minecraft-Version: 1.20.6`; class files are version 65; zero `net.fabricmc`
  references.
- 36/36 named mixin method targets in the 14 platform mixins resolve against MDG's patched
  NeoForge 20.6.144 jar.
- Payload registration replayed against the real `NetworkRegistry` from that jar
  (`RegisterPayloadHandlersEvent` → `PayloadRegistrar` → `NetworkRegistry.register`): all three
  `the5zigmod:*` ids land in the `PLAY` registry. So 20.6 never had the 20.4 namespace bug — its
  registrar argument is a version, not a namespace.
