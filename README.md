<p align="center">
  <img src="assets/logo.svg" width="128" alt="5zig Renewed">
</p>

<h1 align="center">5zig Renewed</h1>

<p align="center">
  <a href="https://sladge.net"><img src="https://sladge.net/badge.svg" alt="AI Slop Inside" width="136" height="20"></a>
</p>

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
| **1.20.1 NeoForge** | green, jarJar'd (17.7 MB) | **HUD** |
| **1.21.1 Fabric** | green, self-contained jar (17.8 MB) | **HUD** |
| **1.21.11 Fabric** | green | **HUD** (strict-ARGB colour fix) |
| **26.1.2 Fabric** | green (Java 25, unobfuscated) | **HUD** (`GuiGraphicsExtractor#text` + strict-ARGB fix) |
| **1.21.4 Fabric** | green | **HUD** |
| **1.21.8 Fabric** | green, refmap + `useLegacyMixinAp` | **HUD** |
| **1.21.5 Fabric** | green, refmap, plugin channels restored | **HUD** |
| **1.21 Fabric** | green, self-contained (17.8 MB) | **HUD** |
| **1.21.10 Fabric** | green, refmap, every colour call opaque-guarded | **HUD** |
| **26.2 Fabric** | green; `Hud` split handled by its own mixin | **HUD** |
| **1.20.4 Fabric** | green, self-contained | **HUD** |
| **1.19.2 Fabric** | green, self-contained (first pre-1.20 port) | **HUD** |
| **1.21.7 Fabric** | green, self-contained | **HUD** |
| **1.21.3 Fabric** | green, self-contained | **HUD** |
| **1.21.6 Fabric** | green, self-contained | **HUD** |
| **1.21.9 Fabric** | green, self-contained | **HUD** |
| **1.21.1 NeoForge** | green, ModDevGradle 2.0.148 | **HUD** |
| **26.1 Fabric** | green, self-contained | **HUD** |
| **26.1.1 Fabric** | green, self-contained | **HUD** |
| **26.3 Fabric** | green, self-contained | **HUD** (needs the keybind fix below) |
| **1.20 Fabric** | green, self-contained | **HUD** |
| **1.20.2 Fabric** | green, self-contained | **HUD** |
| **1.18 Fabric** | green, self-contained | **HUD** |
| **1.18.1 Fabric** | green, self-contained | **HUD** |
| **1.16.5 Fabric** | green, self-contained | **HUD** |
| **1.16.4 Fabric** | green, self-contained | **HUD** |
| **1.16.3 Fabric** | green, self-contained | **HUD** |
| **1.16.2 Fabric** | green, self-contained | **HUD** |
| **1.16.1 Fabric** | green, self-contained; no `FormattedCharSequence` (text is `FormattedText`); Fabric API 0.34.7 | build-only — no client run (task rule); 37/37 mixin targets, 0 unresolved refs |
| **1.16 Fabric** | green, self-contained; byte-identical platform to 1.16.1 (version bump only) | build-only — no client run (task rule); 37/37 mixin targets, 0 unresolved refs |
| **1.18.2 Fabric** | green, self-contained | **HUD** |
| **1.17.1 Fabric** | green, self-contained | **HUD** |
| **1.17 Fabric** | green, self-contained | **HUD** |
| **1.19.1 Fabric** | green, self-contained | **HUD** |
| **1.19 Fabric** | green, self-contained | **HUD** |
| **1.20.5 Fabric** | green, self-contained | **HUD** |
| **1.19.4 Fabric** | green, self-contained | **HUD** |
| **1.19.3 Fabric** | green, self-contained | **HUD** |
| **1.20.6 Fabric** | green, self-contained | **HUD** |
| **1.20.3 Fabric** | green, self-contained | **HUD** |
| **1.21.2 Fabric** | green, self-contained | **HUD** |
| **1.21 NeoForge** | green, ModDevGradle | **HUD** |
| **1.21.5 NeoForge** | green, ModDevGradle | **HUD** |
| **1.21.10 NeoForge** | green, ModDevGradle | **HUD** |
| **1.21.4 NeoForge** | green, ModDevGradle | **HUD** |

| **26.1.2 NeoForge** | green, 26.x toolchain | **HUD** |

Everything above was launched for real (Xvfb + llvmpipe, headless) — not inferred from compilation.

### Platform layout

One platform implementation per Minecraft version under `platform/<mc>/eu/the5zig/mod/platform`, with
loader glue in `loader/<loader>-<mc>/`. The 1.20.1 platform is the reference; later versions were
ported from it against decompiled sources (`~/work/mc-src/<mc>/out`, not from memory).

1.21.1 and later need authlib 6/7 and commons-lang 3.19 fixes that the 1.20.1 core cannot take, so
those builds use a version-local `compat-src` overlay plus a filtered `core` copy — `core/` stays
byte-identical for the 1.20.1 builds.

### IVariables parity

Platform completeness is measurable and roughly flat across eras — the early ports are not stub jobs:

| Platform | `@Override`s | `UnsupportedOperationException` |
|---|--:|--:|
| 1.20.1 (reference) | 379 | 2 |
| 1.19.2 | 388 | 2 |
| 1.18.2 | 388 | 2 |
| 1.17.1 | 388 | 2 |
| 1.17 | 388 | 2 |
| 1.16.5 | 387 | 2 |
| 1.16.4 | 387 | 2 |
| 1.16.3 | 387 | 2 |

The single live `UnsupportedOperationException` in each is the `setSession` guard (replacing the client's
`User` field); the other occurrences are the doc comment.

### Loader policy

**Forge is supported for Minecraft 1.20.1 only**; every version above 1.20.1 uses **NeoForge** instead
(decision recorded 2026-11-04: ForgeGradle 7's Slime Launcher could not load the mod in a dev run, and
NeoForge's ModDevGradle path is proven across 1.20.1 → 26.1.2). Fabric remains the primary target for
every version, NeoForge is the second loader from 1.20.2 onward.

### Known gaps

- **NeoForge 1.20.2 / 1.20.4 / 1.20.6 and 1.21.8 / 1.21.11 do not load yet**: each NeoForge line changed
  the `PayloadRegistrar` contract (`registrar("1")` is a *namespace* on 1.20.x, and 1.21.8+ rejects an
  implicitly-clientbound `playBidirectional`). Jars build; registration fixes are in progress.

- **Minecraft 1.16.0 and 1.16.1 cannot run** (confirmed by a real client run, not just metadata: Fabric
  aborts with `Mod 'Fabric API' (fabric) 0.34.7+1.16 requires any version between 1.16.2-alpha.20.28.a
  (inclusive) and 1.17- (exclusive) of 'Minecraft'`): Every published Fabric API for the `1.16` line
  (`0.14.x`–`0.42.0+1.16`) declares `minecraft: ~1.16.2-alpha.20.28.a`, so Fabric refuses to load *the
  API itself* on 1.16.0/1.16.1 — and our mod hard-depends on it. Jars build; those two versions cannot
  run. 1.16.2+ works.

- **26.3 crashes on unbound keybindings.** `KeyMapping.setAll` feeds every key code to
  `InputConstants.isKeyDown`, which indexes a native buffer; a `GLFW_KEY_UNKNOWN` (-1) code throws
  `IndexOutOfBoundsException` during mouse grab. Always give 5zig keybinds a real default code.
- **Packaged-jar testing is not optional.** Dev-classpath runs hide missing nested libraries: `json-simple`,
  `junixsocket` and `httpcore` were each fatal only in the shipped jar, and the last one disabled the whole
  mod (`NoClassDefFoundError: org/apache/http/HttpRequest` during core init). Test with a source-less Loom
  project and `-Dfabric.addMods`.
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
