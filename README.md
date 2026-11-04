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

## Status & downloads

Every row is a **download link** — click the size to get that jar. Status is per loader.

| Legend | Meaning |
|---|---|
| ✅ | booted in a real headless client: world loaded, HUD rendered, `scripts/check-client-log.sh` clean |
| 🟡 | builds, is packaged correctly, but has never been booted (reason in [Known gaps](#known-gaps)) |
| ⛔ | cannot load, for a reason outside this project (see footnote ¹) |
| ➖ | this loader does not target that version |

**58 downloads are client-verified, 1 is build-only, 2 are blocked upstream** — 60 release assets total.

| # | Minecraft | Java | Fabric | NeoForge | Forge |
|---:|:---|:---:|:---|:---|:---|
| 1 | **1.20.1** | 17 | ✅ [17.9 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1/5zig-renewed-1.20.1-fabric-4.0.0-alpha.1.jar) | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1201/5zig-renewed-1.20.1-neoforge-4.0.0-alpha.1.jar) | ✅ [17.9 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-forge/5zig-renewed-1.20.1-forge-4.0.0-alpha.1.jar) |
| 2 | **1.21.1** | 21 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1211/5zig-renewed-1.21.1-fabric-4.0.0-alpha.1.jar) | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1211/5zig-renewed-1.21.1-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 3 | **1.21.11** | 21 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-12111/5zig-renewed-1.21.11-fabric-4.0.0-alpha.1.jar) | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf12111/5zig-renewed-1.21.11-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 4 | **1.21.4** | 21 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1214/5zig-renewed-1.21.4-fabric-4.0.0-alpha.1.jar) | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1214/5zig-renewed-1.21.4-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 5 | **1.21.8** | 21 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1218/5zig-renewed-1.21.8-fabric-4.0.0-alpha.1.jar) | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1218/5zig-renewed-1.21.8-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 6 | **26.1.2** | 25 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-2612/5zig-renewed-26.1.2-fabric-4.0.0-alpha.1.jar) | ✅ [20.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf2612/5zig-renewed-26.1.2-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 7 | **1.21** | 21 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-121/5zig-renewed-1.21-fabric-4.0.0-alpha.1.jar) | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf121/5zig-renewed-1.21-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 8 | **1.21.10** | 21 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-12110/5zig-renewed-1.21.10-fabric-4.0.0-alpha.1.jar) | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf12110/5zig-renewed-1.21.10-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 9 | **1.21.5** | 21 | ✅ [20.8 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1215/5zig-renewed-1.21.5-fabric-4.0.0-alpha.1.jar) | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1215/5zig-renewed-1.21.5-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 10 | **26.2** | 25 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-262/5zig-renewed-26.2-fabric-4.0.0-alpha.1.jar) | ✅ [20.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf262/5zig-renewed-26.2-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 11 | **1.20.4** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1204/5zig-renewed-1.20.4-fabric-4.0.0-alpha.1.jar) | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1204/5zig-renewed-1.20.4-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 12 | **1.21.7** | 21 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1217/5zig-renewed-1.21.7-fabric-4.0.0-alpha.1.jar) | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1217/5zig-renewed-1.21.7-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 13 | **1.21.6** | 21 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1216/5zig-renewed-1.21.6-fabric-4.0.0-alpha.1.jar) | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/untagged-728c7237e87e7ec79554/5zig-renewed-1.21.6-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 14 | **1.21.9** | 21 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1219/5zig-renewed-1.21.9-fabric-4.0.0-alpha.1.jar) | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1219/5zig-renewed-1.21.9-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 15 | **1.21.3** | 21 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1213/5zig-renewed-1.21.3-fabric-4.0.0-alpha.1.jar) | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1213/5zig-renewed-1.21.3-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 16 | **1.19.2** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1192/5zig-renewed-1.19.2-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 17 | **26.1** | 25 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-261/5zig-renewed-26.1-fabric-4.0.0-alpha.1.jar) | ✅ [20.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf261/5zig-renewed-26.1-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 18 | **1.21.2** | 21 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1212/5zig-renewed-1.21.2-fabric-4.0.0-alpha.1.jar) | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1212/5zig-renewed-1.21.2-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 19 | **1.20.2** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1202/5zig-renewed-1.20.2-fabric-4.0.0-alpha.1.jar) | 🟡 [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1202/5zig-renewed-1.20.2-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 20 | **26.1.1** | 25 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-2611/5zig-renewed-26.1.1-fabric-4.0.0-alpha.1.jar) | ✅ [20.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf2611/5zig-renewed-26.1.1-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 21 | **1.20.6** | 21 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1206/5zig-renewed-1.20.6-fabric-4.0.0-alpha.1.jar) | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1206/5zig-renewed-1.20.6-neoforge-4.0.0-alpha.1.jar) | ➖ |
| 22 | **1.20** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-120/5zig-renewed-1.20-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 23 | **1.20.3** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1203/5zig-renewed-1.20.3-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 24 | **26.3** | 25 | ✅ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-263/5zig-renewed-26.3-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 25 | **1.19.4** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1194/5zig-renewed-1.19.4-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 26 | **1.18.2** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1182/5zig-renewed-1.18.2-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 27 | **1.20.5** | 21 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1205/5zig-renewed-1.20.5-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 28 | **1.19.3** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1193/5zig-renewed-1.19.3-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 29 | **1.19** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-119/5zig-renewed-1.19-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 30 | **1.16.5** | 8 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1165/5zig-renewed-1.16.5-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 31 | **1.19.1** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1191/5zig-renewed-1.19.1-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 32 | **1.18.1** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1181/5zig-renewed-1.18.1-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 33 | **1.17.1** | 16 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1171/5zig-renewed-1.17.1-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 34 | **1.18** | 17 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-118/5zig-renewed-1.18-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 35 | **1.17** | 16 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-117/5zig-renewed-1.17-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 36 | **1.16.4** | 8 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1164/5zig-renewed-1.16.4-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 37 | **1.16.3** | 8 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1163/5zig-renewed-1.16.3-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 38 | **1.16.2** | 8 | ✅ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1162/5zig-renewed-1.16.2-fabric-4.0.0-alpha.1.jar) | ➖ | ➖ |
| 39 | **1.16.1** | 8 | ⛔ | ➖ | ➖ |
| 40 | **1.16** | 8 | ⛔ | ➖ | ➖ |

¹ Minecraft 1.16.0 / 1.16.1: Fabric API for the `1.16` line declares `minecraft: ~1.16.2-alpha.20.28.a`, so
Fabric refuses to load the **API itself** on those two patches. Our jars build and are downloadable, but no
build of this mod can run there. 1.16.2 onward is fine.

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

### Release integrity

Every release asset is checked to have the same byte size as the jar currently built in the tree
(`gh api …/releases` vs the local file). Jars rebuilt after a fix — the HSQLDB bundling swept all 39
Fabric projects — silently leave older assets published unless they are re-uploaded; 36 of 60 releases
had drifted before this check existed.

### Verifying a build

`scripts/check-client-log.sh <client.log>` fails a run on the defect classes that a world + HUD cannot
catch: missing runtime classes, mixins that bind only at runtime, payload-registration errors, core
service threads dying after init, and a loader refusing the mod. Run it after every client verification —
three separate defects in this tree were invisible to "the HUD renders".

### Loader policy

**Forge is supported for Minecraft 1.20.1 only**; every version above 1.20.1 uses **NeoForge** instead
(decision recorded 2026-11-04: ForgeGradle 7's Slime Launcher could not load the mod in a dev run, and
NeoForge's ModDevGradle path is proven across 1.20.1 → 26.1.2). Fabric remains the primary target for
every version, NeoForge is the second loader from 1.20.2 onward.

### Packaging trap: nested mod versions must be semver

Fabric Loader 0.17+ (Minecraft 26.3) parses every nested jar-in-jar mod's `version` with the extended
semver scheme and **refuses to load the whole mod** for a non-semver value:

    Mod com_github_5zig-reborn_discordipc uses the version master-SNAPSHOT
    which isn't compatible with Loader's extended version parsing scheme

Loom copies the Maven version verbatim into the generated nested `fabric.mod.json`, so JitPack
`master-SNAPSHOT`, `json:20231013` and `sqlite-jdbc:3.45.3.0` all poison the artifact. Dev runs and
every pre-26.3 build are unaffected, which is exactly why only a packaged-jar test catches it.
`tools/fix_nested_versions.py` rewrites those to `1.0.0` and is wired into the 26.3 `jar` task.

### Known gaps

- **NeoForge 1.20.2 is build-only.** NeoForge published only betas for that line (`21.2`-era aside: `20.2.0`–`20.2.93`),
  and NeoGradle 7's dev runtime keeps the `jarJar`-nested libraries off the module path FML's
  `TransformingClassLoader` reads, so `runClient` dies with `NoClassDefFoundError:
  com/jagrosh/discordipc/...` before the HUD. The packaged jar nests all 10 libraries and builds clean;
  it has not been exercised from a real install. `minecraftLibrary` — the fix that works for ForgeGradle 6
  on 1.20.1 — does not exist in NeoGradle 7.

- **5zig's own chat/party features are not functional.** The core keeps conversations in an HSQLDB file
  database. The driver was never bundled, so `Class.forName("org.hsqldb.jdbc.JDBCDriver")` threw and the
  whole conversation stack ran against a `null` connection (visible as `NullPointerException … because
  "sql" is null`). `org.hsqldb:hsqldb:2.7.4` is now nested in every jar, which removes that failure — but
  the conversations schema still is not created, so `SELECT * FROM version` and
  `… FROM conversations_groupchat` fail with `object not found`. The HUD, second chat and everything
  else that does not touch the conversation database work. This affects every version and needs a core fix, not a port fix. It also matters less than it
  sounds: 5zig's friends/party chat talks to the 5zig backend, which no longer exists, so those
  features are unreachable for every user regardless. The local chat log is the only part that
  would benefit.

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
