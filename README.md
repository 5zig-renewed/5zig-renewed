<p align="center">
  <img src="assets/logo.svg" width="128" alt="5zig Renewed">
</p>

<h1 align="center">5zig Renewed</h1>

The 5zig Mod, renewed for Minecraft 1.16 – 26.3 on **Fabric**, **NeoForge** and **Forge**.

5zig died at 1.15.2 (last upstream commit: July 2022). This is a continuation that keeps the mod's
design — the HUD module system, capes, friends/chat, server ping, plugin API — and rebuilds the
Minecraft-facing layer for every version since.

> Not affiliated with Mojang, Microsoft, 5zig or 5zig Reborn. GPL-3.0, same as upstream.

## How the port works

Upstream is unusually friendly to porting: `eu.the5zig.mod.*` (≈70k LOC) contains **zero
`net.minecraft` imports**. All Minecraft access is funnelled through a platform layer:

| Layer | Upstream | Here |
|---|---|---|
| Core (config, modules, HUD layout, chat/friends protocol, server API) | `The-5zig-Mod/mod` | `core/` — unchanged |
| Platform contract | `version-support/1.15.1/Variables.java` + `gui/elements/*` | `eu.the5zig.mod.util.IVariables` (162 methods) |
| Platform impl | 56 classes + 24 mixins **per Minecraft version** | `platform/<mc>/` |
| Loader glue | Forge `@Mod` + ASM name tables | `loader/{fabric,neoforge,forge}/` |

The 56-class platform layer is ~7.6k LOC per version, so it is grouped into **10 eras** (API
breakpoints, not every patch) and shared across the versions inside an era. That turns 120
version×loader artifacts into 10 platform implementations + 3 loader adapters.

Eras and the per-version matrix: [`docs/version-matrix.md`](docs/version-matrix.md).
Port conventions and verified 1.20.1 signatures: [`docs/port-spec.md`](docs/port-spec.md).

## Status

| Target | Build | Client-verified |
|---|---|---|
| **1.20.1 Fabric** | green, self-contained jar (17.8 MB, 5 libs nested) | **HUD + M menu + translations** |
| **1.20.1 Forge** | green, jarJar'd (17.7 MB) | **HUD** |
| **1.20.1 NeoForge** | green (ForgeGradle 6 + official mappings) | **HUD** |
| **1.21.1 Fabric** | green | **HUD** |
| **1.21.11 Fabric** | green | **HUD** (strict-ARGB colour fix) |
| **26.1.2 Fabric** | green (Java 25, unobfuscated) | **HUD** (`GuiGraphicsExtractor#text` + strict-ARGB fix) |

Everything above was launched for real (Xvfb + llvmpipe, headless) — not inferred from compilation.

### Platform layout

One platform implementation per Minecraft version under `platform/<mc>/eu/the5zig/mod/platform`, with
loader glue in `loader/<loader>-<mc>/`. The 1.20.1 platform is the reference; later versions were
ported from it against decompiled sources (`~/work/mc-src/<mc>/out`, not from memory).

1.21.1 and later need authlib 6/7 and commons-lang 3.19 fixes that the 1.20.1 core cannot take, so
those builds use a version-local `compat-src` overlay plus a filtered `core` copy — `core/` stays
byte-identical for the 1.20.1 builds.

### Known gaps

- 26.x draws through `GuiGraphicsExtractor` (`Gui#extractRenderState`), whose text entry point is
  `text(Font, String|Component|FormattedCharSequence, x, y, color, dropShadow)` — there is no
  `drawString`; colours are strict ARGB, so the same alpha guard is needed there.
- 1.21.11/26.1.2 jars have **no mixin refmap**, so they are dev-only until Loom emits one.
- plugin channels, capes, 2nd chat and server ping are degraded/stubbed in the 1.21+ platforms (typed
  payloads, `getCloakTextureLocation` removal); see each `versions/*/PORT-NOTES.md`.
- translations cover the 1.20.1 builds; 1.21+ ships the same bundle but newer labels were not audited.

## Building 1.20.1 Fabric

```bash
./gradlew build          # needs JDK 17+ (1.20.1 compiles to Java 17)
./gradlew runClient      # dev client
```

Source layout is wired through `sourceSets` in `build.gradle`; a version module only differs by
`minecraft_version`, the loader dependency and the `platform/<era>` source dir.

## Dropped from upstream

These were tied to the 1.15 Forge/launchwrapper world and do not exist in a loader-packaged mod:

- the self-updater and the GUI installer (`Updater`, `installer/`, `Start`)
- the ASM transformer + obfuscation name tables → replaced by mixins
- `5zig-fabric` (a runtime Jar remapper to run the Forge build on Fabric) → native Fabric build now

## License

GPL-3.0. Original work © 5zig, 5zig Reborn contributors. See `LICENSE`.
