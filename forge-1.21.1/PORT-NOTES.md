# 1.21.1 Forge port notes

| Piece | Version |
|---|---|
| Gradle | 9.3.1 (`~/work/tools/gradle-9.3.1`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ForgeGradle 7 `net.minecraftforge.gradle` **7.0.40** (declared `[7.0.29,8.0)`) |
| Jar-in-Jar | `net.minecraftforge.jarjar` **0.2.3** |
| Obfuscation | `net.minecraftforge.renamer` **1.1.8** |
| Forge | **1.21.1-52.1.21** (FancyModLoader 3.x / ModLauncher 11) |
| Minecraft | 1.21.1, Mojang official mappings (reobfuscated to SRG for the shipped jar) |

Build:

```bash
cd forge-1.21.1
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-9.3.1/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m clean build
```

Artifacts in `forge-1.21.1/build/libs/`:

| File | Contents |
|---|---|
| `5zig-renewed-1.21.1-forge-4.0.0-alpha.1.jar` | **release**: SRG-named classes, refmap, 8 nested libraries (18.8 MB) |
| `5zig-renewed-1.21.1-forge-4.0.0-alpha.1-slim-all.jar` | pre-rename jar-in-jar (official names, for debugging) |
| `5zig-renewed-1.21.1-forge-4.0.0-alpha.1-slim.jar` | plain classes+resources, no libraries |

## Which Gradle plugin: FG6 vs FG7

The 1.20.1 build uses ForgeGradle 6, so FG6 was tried first. A minimal FG6 probe
(`id 'net.minecraftforge.gradle' version '[6.0,6.2)'`, `mappings channel: 'official', version:
'1.21.1'`, `minecraft 'net.minecraftforge:forge:1.21.1-52.1.21'`) **does configure and resolve**
on Gradle 8.14.3 / JDK 21 — FG6 is not outright broken for 1.21.1.

FG7 was still chosen, for three concrete reasons:

1. the official `forge-1.21.1-52.1.21-mdk.zip` declares FG7 (`version '[7.0.3,8)'`) with the
   `minecraft.mavenizer(it)` / `minecraft.dependency(...)` DSL;
2. FG7's `renamer.enableMixinRefmaps` generates the official→SRG mixin refmap. FG6 has no such
   wiring: the 1.20.1 jar ships official-named mixin annotations and **no refmap**, which is
   invisible in dev (FG's dev runtime is official-named) but means the mixins cannot resolve on
   the SRG-named production runtime — a 1.21.1 jar built that way loads with no HUD;
3. FG7's `net.minecraftforge.jarjar` is the maintained jar-in-jar line (FG6's `jarJar` extension
   and `reobfJarJar` do not exist here).

FG7 also refuses to run on the Gradle 8.x the other projects use — verbatim:

```
ERROR: ForgeGradle 7 requires Gradle 9.3.0 or later to run. You are currently using Gradle 8.14.3.
```

so the build pins Gradle 9.3.1 (the version in the MDK wrapper).

## Glue written

| File | Role |
|---|---|
| `loader/forge-1.21.1/java/eu/the5zig/mod/forge1211/FiveZigForge1211.java` | `@Mod("the5zigmod")` entrypoint; keybind collection, payload channel bootstrap, core init on the first client tick |
| `loader/forge-1.21.1/java/eu/the5zig/mod/platform/PluginMessages.java` | Forge replacement for the Fabric-API payload bridge in `platform/1.21.1` (same package and public API: `bootstrap()`, `send()`, `MAX_PAYLOAD_SIZE`) |
| `forge-1.21.1/src/main/resources/META-INF/mods.toml` | FML metadata for Forge 52.x |
| `forge-1.21.1/src/main/resources/META-INF/MANIFEST.MF` | `MixinConfigs: mixins.the5zigmod.json` |
| `forge-1.21.1/src/main/resources/mixins.the5zigmod.json` | Platform mixin config (`JAVA_21`, `refmap`), same 13 mixins as `loader/fabric-1.21.1` |

## What 1.21.1 Forge forced to change vs the 1.20.1 build

1. **No `ForgeGui`.** Forge 1.21.1 ships only
   `net.minecraftforge.client.gui.overlay.ForgeLayeredDraw`; the 1.20.1
   `net.minecraftforge.client.gui.overlay.ForgeGui` (which overrode `Gui#render` and needed
   `MixinForgeGui`) is gone. The game renders the vanilla `Gui`, so the platform's own
   `@Mixin(Gui)` on `render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V`
   is the HUD bracket, exactly as on Fabric/NeoForge 1.21.1. **No loader-specific mixin.**
2. **Tick event split.** `TickEvent.ClientTickEvent` is now abstract with `Pre`/`Post`
   subclasses; the 1.20.1 `event.phase == Phase.END` check became a listener on
   `TickEvent.ClientTickEvent.Post`.
3. **Mixin config discovery.** Forge 52's `ModFileParser` has no `[[mixins]]` support (that is
   FML 4 / NeoForge), so the config is declared through the legacy `MixinConfigs` manifest
   attribute, like 1.20.1.
4. **Refmap is mandatory in production.** Forge's production runtime is SRG-named
   (`m_280421_`), while the platform mixins are written against official names. FG6 shipped no
   refmap (1.20.1 dev runs are official-named, so the gap is invisible there);
   `renamer.enableMixinRefmaps` + `org.spongepowered:mixin:0.8.7:processor` now emit
   `mixins.the5zigmod.refmap.json`. Verified independently against Forge's own
   `official-1.21.1-srg` mapping: `Gui#render -> m_280421_`,
   `Gui#renderItemHotbar -> m_324469_`, matching the refmap.
5. **Reobfuscation is a separate step.** `renamer.classes(tasks.named('jarJar'))` creates
   `renameJarJar`, which is the release artifact; `assemble` depends on it. `jar` is kept as
   `-slim`.
6. **Jar-in-Jar metadata.** `jarJar.register()`, dependencies in the `jarJar` configuration, and
   per-dependency metadata `version` (what Gradle resolves) + `range` (what lands in
   `META-INF/jarjar/metadata.json`). `junixsocket-core` is never passed to jarJar.
7. **mods.toml.** `loaderVersion="[52,)"`, `[[dependencies]]` on `forge` `[52,)` and
   `minecraft` `[1.21.1,1.21.2)` with `mandatory=true` / `side="CLIENT"` (Forge still uses
   `mandatory`; NeoForge's `type=` is not valid here).
8. **Networking.** `platform/1.21.1`'s `PluginMessages` is Fabric-API code, so the build excludes
   it and compiles a Forge implementation: one `SimpleChannel` per 5zig channel
   (`ChannelBuilder.named(...).optional().simpleChannel()`, opened from the mod constructor
   because Forge locks `NetworkRegistry` when construction ends), raw-bytes message,
   `consumerMainThread`, `PacketDistributor.SERVER.noArg()` to send and
   `Channel#isRemotePresent` as the `canSend` equivalent.
9. **Toolchains.** FG7's launcher tools (`slime-launcher`, `renamer`) declare a Java 8
   toolchain. No JDK 8 is installed, so `settings.gradle` adds the same
   `org.gradle.toolchains.foojay-resolver-convention` the MDK uses (Gradle downloads Temurin 8
   once); the renamer task itself is pointed at Java 21 since it only reads/writes jars.

## Verification done (no client launched)

- `clean build` green; release jar 18.8 MB.
- Release jar is SRG-named (`eu/the5zig/mod/platform/Variables.class` references `m_*`).
- Java 21 class files (major 65); one `META-INF/MANIFEST.MF` carrying `MixinConfigs`,
  `5zig-Version`, `Minecraft-Version`.
- `mixins.the5zigmod.refmap.json` present in the jar and populated for all 13 mixins.
- All 35 `@Inject`/`@Redirect`/`@Modify*` targets across the 13 registered mixins resolve against
  the patched Forge 1.21.1 client jar (`35/35`, same count as the NeoForge 1.21.1 build).
- 8 nested jars intact, `META-INF/jarjar/metadata.json` parses (exact ranges, DiscordIPC
  `[1.0,)`); no `junixsocket-core`, no duplicate zip entries.
- No `net/fabricmc` references left; class list identical to the verified Fabric 1.21.1 jar
  (1482 classes, only the loader glue differs).
- `runClient --dry-run` configures; the client run carries
  `--quickPlaySingleplayer "New World" --mixin.config mixins.the5zigmod.json` plus the
  SRG→official refmap remap system property.

## Behaviour that is stubbed or degraded

- **Plugin channels** cover only the three registered 5zig channels
  (`the5zigmod:5zig`, `…:5zig_reg`, `…:5zig_set`), declared `optional()` so vanilla servers are
  unaffected. `send()` returns `false` (once, with a warning) for any other channel — 1.21.1
  cannot send unregistered payload types. Because they are `SimpleChannel`s, the wire payload is
  Forge's `ForgePayload` envelope (channel name + 2-byte message index + body), so only a Forge
  peer can decode them; the Fabric/NeoForge builds use plain typed payloads instead.
- **No config screen.** The mod's own `M` menu is the settings UI, as on every other target.
- **Not client-verified.** Per the task rules no Minecraft client was launched; everything above
  is static verification against the real Forge 52.1.21 artifacts.
