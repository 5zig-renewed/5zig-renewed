# 1.20.2 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 to launch Gradle **and** as toolchain, `--release 17` for the mod (class files are 61) |
| Plugin | NeoGradle `net.neoforged.gradle.userdev` **7.1.39** (not MDG — see below) |
| NeoForge | **20.2.93** (the final 20.2 release; FML 1.0.16, Mixin 0.8.5) |
| Minecraft | 1.20.2, Mojang official mappings, no reobfuscation |

Build:

```bash
cd neoforge-1.20.2
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.20.2/build/libs/5zig-renewed-1.20.2-neoforge-4.0.0-alpha.1.jar`
(18.8 MB, eight libraries nested under `META-INF/jarjar/`). The thin jar is `…-dev.jar`.

## Does NeoForge publish a 1.20.2 build? Yes

`net.neoforged:neoforge:20.2.93` — the first NeoForge line that is a real `net.neoforged.*`
loader (20.2.86 is the first non-beta; 20.2.93 is the last). It is published **only** as a
Forge-format `-userdev` jar plus `universal`/`installer`; there is no `moddev-config.json`.

## Why NeoGradle and not ModDevGradle

Both MDG major versions reject 20.2.93:

```
> Could not resolve net.neoforged:neoforge:20.2.93.
  > Unable to find a variant with the requested capability:
    coordinates 'net.neoforged:neoforge-moddev-bundle'
```

MDG 1.0.24 and 2.0.148 both resolve NeoForge through the `neoforge-moddev-bundle` capability that
first appears with the `moddev-config.json` artefact (20.4+). NeoGradle `7.1.39` is the toolchain
NeoForge shipped for 20.2/20.4 and consumes the `-userdev` config directly, so this one project uses
it. Consequences:

- the project uses NeoGradle's `runs { client { modSource sourceSets.main } }` DSL instead of
  `neoForge { … }`;
- NeoGradle's own tooling (`net.neoforged.jst.cli.Main`, the source transformer) is a **Java 21**
  artifact: running it from a Java 17 toolchain dies with `UnsupportedClassVersionError` (class file
  65). The toolchain is therefore 21 and the mod is compiled with `--release 17`;
- NeoGradle names the jar-in-jar output `<name>-all.jar`. The build renames classifiers so the
  self-contained jar is the plain `<name>.jar` and the thin one is `<name>-dev.jar`, like every other
  project in the repo;
- NeoGradle's jar-in-jar writer always emits `[<version>,)` ranges regardless of
  `jarJar.pin`/`jarJar.ranged`, so a `doLast` on `jarJar` rewrites `META-INF/jarjar/metadata.json` to
  exact ranges (only the `DiscordIPC` SNAPSHOT keeps its open range).

## Glue written

| File | Role |
|---|---|
| `loader/neoforge-1.20.2/java/eu/the5zig/mod/neoforge/FiveZigNeoForge.java` | `@Mod("the5zigmod")` entrypoint (FML 1 has no `dist`), keybinds, channel creation in the constructor, core init on the tick END phase |
| `loader/neoforge-1.20.2/java/eu/the5zig/mod/platform/PluginMessages.java` | NeoForge payload bridge (`bootstrap()`, `send()`, `MAX_PAYLOAD_SIZE`) |
| `neoforge-1.20.2/src/main/resources/META-INF/mods.toml` | FML 1 metadata, `loaderVersion="[1,)"`, `[[mixins]]`, Forge-era `mandatory=true` dependencies |
| `neoforge-1.20.2/src/main/resources/mixins.the5zigmod.json` | Platform mixin config (`JAVA_17`), the same 14 mixins as the Fabric 1.20.2 build |

## 1.20.2 specifics the other builds did not hit

1. **No payload registrar at all.** NeoForge 20.2 still has the Forge-derived networking API:
   `NetworkRegistry.newEventChannel(id, version, clientPredicates, serverPredicates)` +
   `EventNetworkChannel.addListener(NetworkEvent.ClientCustomPayloadEvent)`. The event's
   `getPayload()` is the raw `FriendlyByteBuf`, and `getSource().enqueueWork(...)` hops to the client
   thread. Channels must be created **during mod construction** because the network registry locks
   when mod loading ends — hence `PluginMessages.bootstrap()` is called from the constructor instead
   of from a registration event.
   `javap` of `neoforge-20.2.93-universal.jar` confirms there is no
   `net.neoforged.neoforge.network.registration` package at all, so the 20.4 namespace check
   (`IPayloadRegistrar` → `RegistrationFailedException(INVALID_NAMESPACE)`) has no counterpart here
   and the 1.20.2 build needs no namespace argument.
2. **`acceptMissingOr("1")`** on both sides keeps a vanilla server / single-player world without the
   5zig plugin working, the same thing `optional()` does on 20.4+.
3. **`mandatory=true`** is required: FML 1.0.16 parses the Forge-era flag and has no `type` field
   (that arrived with FML 2).
4. **`@Mod`** comes from `net.neoforged.fml.common.Mod` shipped in the FancyModLoader
   `language-java` artifact, and only has `value()`.
5. **Sending** is vanilla `ServerboundCustomPayloadPacket(new RawPayload(id, data))`: 1.20.2 still
   writes any id + body, so `Variables#sendCustomPayload`'s raw fallback stays valid.

## Verification (no client run, per task rules)

- `gradle build` green; jar is 18.8 MB.
- Eight nested jars + `META-INF/jarjar/metadata.json` with exact ranges after the rewrite
  (`[1.14.3]`, `[20231013]`, `[3.45.3.0]`, `[4.1.97.Final]`, `[1.1.1]`, `[2.5.1]`, `[2.5.1]`;
  `DiscordIPC` `[master-SNAPSHOT,)`).
- `META-INF/mods.toml` expanded and parsed; `[[mixins]]` present; `[20.2,20.3)` / `[1.20.2,1.20.3)`.
- Manifest carries `Minecraft-Version: 1.20.2`; class files are version 61; zero `net.fabricmc`
  references.
- 36/36 named (name-only — see the payload section) mixin method targets in the 14 platform mixins resolve against NeoGradle's patched
  and recompiled 1.20.2 jar.
- Channel registration replayed in-process against the real `NetworkRegistry` from
  `neoforge-20.2.93-universal.jar`: `PluginMessages.bootstrap()` creates
  `the5zigmod:5zig`, `the5zigmod:5zig_reg`, `the5zigmod:5zig_set` once, and a second call is a no-op
  (no duplicate-channel `IllegalArgumentException`).

## Custom-payload dispatch is NeoForge's, not vanilla's (fixed after the first live run)

The first connection crashed with `InvalidInjectionException: @Inject annotation on
the5zig$handleCustomPayload could not find any targets matching
handleCustomPayload(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V`.

`javap` of the **patched** client (`build/neoForm/neoFormJoined1.20.2-20231019.002635/steps/recompile/outputs.jar`) shows what the compile-time check could not:

| Class | NeoForge signature |
|---|---|
| `ClientPacketListener` | `handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V)` |
| `ClientCommonPacketListenerImpl` | `handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V` — the real entry point; it handles `BrandPayload` itself (sets `serverBrand`) and returns **before** delegating to the subclass |

So the one-argument descriptor does not exist: NeoForge 20.2 (like 20.4) adds the packet as a
**new first parameter**, so the mixin target had to grow the same parameter.

Retargeting the subclass method would only silence the crash — brand never reaches it. The brand
branch now lives in `ClientCommonPacketListenerImpl`, and that is where the mixin hooks
(`MixinClientCommonPacketListener`), guarded with `this instanceof ClientPacketListener` so it
stays play-phase-only exactly like the platform mixin.

The platform `MixinClientPacketListener` is excluded from this project's source set; this build has
its own copy (`loader/neoforge-1.20.2/java/eu/the5zig/mod/mixin/MixinClientPacketListener.java`) with
the five remaining play-connection injections, so the Fabric builds keep their verified target.

**Why the 36/36 check passed:** it matched target *names*. `handleCustomPayload` does exist in the
patched jar — under a different descriptor (and, on 1.20.6, a different class owning the brand
branch). A target is only valid when `name + descriptor` matches; the re-check used here compares
`javap -p -s` descriptors and resolves 18/18 loader-local targets per version.
