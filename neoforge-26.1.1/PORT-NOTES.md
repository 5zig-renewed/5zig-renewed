# 26.1.1 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | **9.8.2** (`~/.tools/gradle/gradle-9.8.2`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | **25** (`/usr/lib/jvm/java-25-openjdk`), target class file 69 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **26.1.1.8-beta** (FancyModLoader 11.0.5, neoform `26.1.1-1`) |
| Minecraft | **26.1.1 — unobfuscated**, no mappings anywhere |
| Mixin | `compatibilityLevel: JAVA_25` |

Build:

```bash
cd neoforge-26.1.1
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  ~/.tools/gradle/gradle-9.8.2/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-26.1.1/build/libs/5zig-renewed-26.1.1-neoforge-4.0.0-alpha.1.jar`
(21.4 MB, eleven libraries nested under `META-INF/jarjar/`).

## NeoForge never shipped a stable 26.1.1 build

The 26.1.1 line stops at **26.1.1.8-beta** on NeoForged's maven (14 artifacts, all beta). The first
stable 26.x artifacts are the 26.1.2 ones, so a 26.1.1 NeoForge build has to pin a beta.
MDG maps `26.1.1.8-beta` → Minecraft **26.1.1** and NFRT runs `neoform:26.1.1-1`
(verified: `minecraft-patched-26.1.1.8-beta.jar` is produced).

## Source layout

Identical to `neoforge-26.1.2` / `neoforge-26.1`:

* `build/core-compat` — `core/` with the twelve authlib-7 / commons-lang3-3.19 files filtered out.
* `versions/26.1.1-fabric/compat-src` — the 26.x replacements (read-only reuse of the verified
  Fabric overlay).
* `platform/26.1.1/eu`, `loader/neoforge-26.1.1/java`.
* `eu/the5zig/mod/installer/**` and `eu/the5zig/mod/mixin/MixinCommonMinecraft.java` excluded.

`platform/26.1.1` differs from `platform/26.1.2` only in the two `UnsupportedOperationException`
message strings, so the port is glue-only.

## Glue deltas vs 26.1.2 NeoForge

1. **`MixinGui` hotbar target.** The NeoForge 26.1.1 patched jar has the same `Gui` shape as 26.1 and
   26.1.2: `extractRenderState(GuiGraphicsExtractor, DeltaTracker)`, `tick(Z)V`,
   `setOverlayMessage(Component;Z)V`, and **`extractHotbar`** in place of the vanilla
   `extractHotbarAndDecorations`. `loader/neoforge-26.1.1/.../MixinGui.java` carries that corrected
   target and the platform copy is excluded from the compile.
2. **No payload bridge** (`PluginMessages` absent; `sendCustomPayload` throws by design).
3. **`loaderVersion="[11,)"`**, `neoforge` `[26.1,27)`, `minecraft` `[26.1.1,26.2)`.
4. **No `additionalRuntimeClasspath`** on 26.x (FML 11 mod-locator rework).
5. **HttpClient + httpcore nested**; eleven nested jars total.

## Verification performed

* `gradle build` → `BUILD SUCCESSFUL` (1m 0s) against `minecraft-patched-26.1.1.8-beta.jar`.
* `scripts/verify-neoforge-26x.py`: 29/29 mixin targets resolve; `MixinGui` resolves `extractHotbar`
  plus the three `Gui` hooks.
* TOML parses, `${version}` → `4.0.0-alpha.1`, `[[mixins]]` present, ranges as above.
* Eleven nested jars, valid zips, `metadata.json` matches; 1477 classes at major 69; no `net/fabricmc`
  references; `PluginMessages` absent.

## Not verified

* **No client run** (task rule).
* Beta NeoForge artifact: not client-proven.
