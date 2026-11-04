# 26.1 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | **9.8.2** (`~/.tools/gradle/gradle-9.8.2`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | **25** (`/usr/lib/jvm/java-25-openjdk`), target class file 69 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **26.1.0.8-beta** (FancyModLoader 11.0.5, neoform `26.1-1`) |
| Minecraft | **26.1 — unobfuscated**, no mappings anywhere |
| Mixin | `compatibilityLevel: JAVA_25` |

Build:

```bash
cd neoforge-26.1
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  ~/.tools/gradle/gradle-9.8.2/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-26.1/build/libs/5zig-renewed-26.1-neoforge-4.0.0-alpha.1.jar`
(21.4 MB, eleven libraries nested under `META-INF/jarjar/`).

## NeoForge never shipped a stable 26.1 build

NeoForged's maven has **only betas for Minecraft 26.1 and 26.1.1**: `26.1.0.0-beta` …
`26.1.0.8-beta` and `26.1.1.0-beta` … `26.1.1.8-beta`. The 26.1.2 line is the first one with
stable builds (`26.1.2.71` … `26.1.2.126`). `26.1.0.8-beta` is the newest 26.1 artifact, so that is
what this build pins.

MDG accepts the `-beta` suffix and the four-part version: `VersionCapabilitiesInternal`
parses `^(\\d+\\.\\d+\\.\\d+)\\.\\d+(-.*)?$`, strips a trailing `.0` from the Minecraft part and maps
`26.1.0.8-beta` → Minecraft **26.1** (confirmed by NFRT downloading `26.1.json` and running the
`neoform:26.1-1` config). `26.1.1.8-beta` maps to **26.1.1** the same way.

## Source layout

Identical to `neoforge-26.1.2`:

* `build/core-compat` — `core/` copied by `coreCompatTree` with the twelve authlib-7 /
  commons-lang3-3.19 files filtered out.
* `versions/26.1-fabric/compat-src` — the 26.x replacements (read-only reuse of the verified Fabric
  overlay; nothing under `versions/` was modified).
* `platform/26.1/eu`, `loader/neoforge-26.1/java`.
* `eu/the5zig/mod/installer/**` and `eu/the5zig/mod/mixin/MixinCommonMinecraft.java` excluded.

`platform/26.1` is byte-identical to `platform/26.1.2` apart from the two
`UnsupportedOperationException` message strings (`"5zig 26.1:"` vs `"5zig 26.1.2:"`), so the whole
port reduces to glue.

## Glue deltas vs 26.1.2 NeoForge

1. **`MixinGui` hotbar target.** NeoForge 26.1 patches `Gui.java` exactly like 26.1.2 does: the
   patched jar has `extractRenderState(GuiGraphicsExtractor, DeltaTracker)`, `tick(Z)V`,
   `setOverlayMessage(Component;Z)V` and **`extractHotbar`** — `extractHotbarAndDecorations` does
   not exist. `loader/neoforge-26.1/java/eu/the5zig/mod/mixin/MixinGui.java` is the platform class
   with that one target corrected; the build excludes `the5zig/mod/mixin/MixinGui.java` from the
   platform source root (no `eu/` prefix, so only the platform copy is filtered).
2. **No payload bridge.** `platform/26.1` has no `PluginMessages`; `Variables#sendCustomPayload`
   throws by design on 26.x. Nothing is registered on the mod bus for networking.
3. **`loaderVersion="[11,)"`**, `neoforge` range `[26.1,27)`, `minecraft` range `[26.1,26.2)`.
4. **No `additionalRuntimeClasspath`**: absent on 26.x (FML 11 mod-locator rework); `implementation`
   alone is correct and MDG fails the build if the old double declaration is used.
5. **Apache HttpClient + httpcore nested** (MC 26.x no longer ships them); eleven nested jars total.

## Verification performed

* `gradle build` → `BUILD SUCCESSFUL` (2m 21s) against the real patched jar
  `minecraft-patched-26.1.0.8-beta.jar`.
* `scripts/verify-neoforge-26x.py`: 29/29 mixin targets from the 13 configured mixins resolve in the
  patched jar; `MixinGui` resolves `extractHotbar`, `extractRenderState` (HEAD+RETURN), `tick`, and
  `setOverlayMessage`.
* `META-INF/neoforge.mods.toml` TOML-parses, `${version}` → `4.0.0-alpha.1`, `[[mixins]]` present.
* Eleven nested jars (`hsqldb` included: the core's `FileDatabaseConfiguration` resolves
  `org.hsqldb.jdbc.JDBCDriver` by name), each a valid zip; `metadata.json` lists exactly those eleven.
* 1477 classes, all class-file major 69; zero `net/fabricmc` references; `PluginMessages` absent.

## Not verified

* **No client run** (task rule) — the parent verifies launch behaviour.
* NeoForge 26.1 beta builds may still move; the version is pinned but not client-proven.
