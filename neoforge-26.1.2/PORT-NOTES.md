# 26.1.2 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | **9.8.2** (`~/.tools/gradle/gradle-9.8.2`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | **25** (`/usr/lib/jvm/java-25-openjdk`), target class file 69 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **26.1.2.126** (FancyModLoader 11.0.15, sponge-mixin 0.17.4+mixin.0.8.7) |
| Minecraft | **26.1.2 — unobfuscated**, no mappings anywhere |
| Mixin | `compatibilityLevel: JAVA_25` |

Build:

```bash
cd neoforge-26.1.2
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  ~/.tools/gradle/gradle-9.8.2/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-26.1.2/build/libs/5zig-renewed-26.1.2-neoforge-4.0.0-alpha.1.jar`
(19.8 MB, ten libraries nested under `META-INF/jarjar/`).

## ModDevGradle handled 26.x — what it needed

MDG **2.0.148 does support NeoForge 26.x**; no workaround was necessary. Concretely:

* `VersionCapabilitiesInternal` has an `UNOBF_NEOFORGE_PATTERN` (`^(\d+\.\d+\.\d+)\.\d+…`), so
  `26.1.2.126` is mapped to Minecraft `26.1.2`. NFRT then runs the `neoform:26.1.2-1` config, which
  has **no mappings steps at all** (download client/server → merge → decompile → patch) and a
  `java_target` of 25. The game jar is already in official names, so nothing is remapped and no
  refmap exists (same situation as the verified 26.1.2 Fabric build).
* `neoForge { enable { disableRecompilation = true } }` still applies: MDG logs
  "Creating Minecraft artifacts without recompilation" and produces
  `build/moddev/artifacts/minecraft-patched-26.1.2.126.jar`.
* **`additionalRuntimeClasspath` no longer exists**: MDG fails the build with *"there is no
  additional classpath anymore for Minecraft 26.1.2"*. FML 11's mod-locator rework
  (`modLocatorRework` capability) makes the mod's own class loader see the game classpath, so the
  1.21.1/1.21.4 double declaration is unnecessary here — `implementation` alone is correct.
* Gradle 9.8.2 + JDK 25 are required (Gradle 8.14 cannot analyse class file 69). MDG 2.0.148 runs
  cleanly on Gradle 9.8.2; its only 9.x noise is a "incompatible with Gradle 10" deprecation notice.

## Source layout

Same repository-root layout as the other NeoForge builds, with the 26.x core overlay:

* `build/core-compat` — `core/` copied by the `coreCompatTree` task with the twelve authlib-7 /
  commons-lang3-3.19 files filtered out.
* `versions/26.1.2-fabric/compat-src` — the 26.x replacements for those files (read-only reuse of
  the verified Fabric overlay; nothing under `versions/` was modified).
* `platform/26.1.2/eu`, `loader/neoforge-26.1.2/java`.
* `eu/the5zig/mod/installer/**` and `eu/the5zig/mod/mixin/MixinCommonMinecraft.java` are excluded
  (the latter's `clickMouse` injection target no longer exists on 26.x; it is not in this build's
  mixin config).

## Glue deltas vs 1.21.1/1.21.4

1. **No payload bridge.** `platform/26.1.2` has no `PluginMessages` at all: `Variables#sendCustomPayload`
   throws `UnsupportedOperationException` because 26.x receivers only accept registered payload
   types. The entrypoint registers nothing on the mod bus for networking, so the `the5zigmod:*`
   channels are simply unavailable — identical to the 26.1.2 Fabric build. `PluginMessages` is
   therefore absent from this jar (not stubbed).
2. **A loader copy of `MixinGui` was required.** NeoForge 26.1.2 rewrites `Gui#extractRenderState`
   into a `GuiLayerManager` and, in `patches/net/minecraft/client/gui/Gui.java.patch`, renames
   `extractHotbarAndDecorations` → `extractHotbar`. The vanilla jar still has the old name; the
   NeoForge-patched jar does not. The platform mixin's `@Inject(... extractHotbarAndDecorations ...)`
   would fail `injectors.defaultRequire = 1` and take the whole `Gui` transform down with it, so the
   build excludes `the5zig/mod/mixin/MixinGui.java` (no `eu/` prefix — the platform source dir is
   rooted at `eu`, so this matches only the platform file) and compiles
   `loader/neoforge-26.1.2/java/eu/the5zig/mod/mixin/MixinGui.java` instead: same class, same
   package, same four other injections, hotbar target corrected to `extractHotbar`. Rendering still
   goes through the platform's `HudFrame`; nothing was reimplemented.
3. **`loaderVersion="[11,)"`** in `META-INF/neoforge.mods.toml` (FML 11.0.15), dependency ranges
   `[26.1,27)` for `neoforge` and `[26.1.2,26.2)` for `minecraft`.
4. **Apache HttpClient is nested.** MC 26.x no longer ships it, but the core uses it for Spotify and
   the URL opener. `httpclient:4.5.14` **and** `httpcore:4.4.16` are on `implementation` and in
   `jarJar`; the 26.1.2 Fabric build adds `httpclient` for the same reason. Ten nested jars total.
5. **Java 25 everywhere**: toolchain, `options.release = 25`, mixin config `JAVA_25`. NeoForge's
   bundled sponge-mixin 0.17.4 has the `JAVA_25` compatibility level.

## Behaviour that is stubbed or degraded

* **Plugin channels are gone.** `sendCustomPayload` throws by design on 26.x; any 5zig server-side
  plugin integration is inert. Same as the 26.1.2 Fabric build.
* **ReplayMod detection is off** (`Transformer.FORGE` makes `ModCompatibility` look for a
  `net.minecraftforge.fml.common.Loader` that does not exist); harmless.
* **No config screen** — the mod's own `M` menu is the settings UI, as on every other target.
* **Key-mapping categories** are created by the platform's `Keybinding#categoryOf` via
  `KeyMapping.Category.register`; the NeoForge event's `registerCategory` is not called, so NeoForge
  cannot group them for its own sorting. Cosmetic only: the bindings register and work.
* **Not client-verified.** Per the task rules no Minecraft client was launched.

## Verification performed

* `gradle build` → `BUILD SUCCESSFUL`; compile against the real NeoForge 26.1.2.126 patched jar and
  the 26.1.2 platform.
* Mixin targets: 32/32 targets from the twelve untouched platform mixins resolve in
  `minecraft-patched-26.1.2.126.jar`, plus 5/5 from the loader's `MixinGui`; the compiled
  `MixinGui.class` in the jar targets `extractHotbar` and no longer mentions
  `extractHotbarAndDecorations`.
* `META-INF/neoforge.mods.toml` TOML-parses, `${version}` expands to `4.0.0-alpha.1`, ranges and
  `[[mixins]]` present; `mixins.the5zigmod.json` is `JAVA_25` with 13 classes, all present in the jar.
* Ten nested jars, each a valid zip; `metadata.json` lists exactly those ten; no duplicate entries.
* All classes are class file 69; zero `net/fabricmc` references; `PluginMessages` absent by design.
