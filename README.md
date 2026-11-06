<p align="center">
  <img src="assets/logo.svg" width="112" alt="5zig Renewed">
</p>

<h1 align="center">5zig Renewed</h1>

<p align="center">
  HUD, chat and server utilities for modern Minecraft.<br>
  A maintained continuation of <a href="https://github.com/5zig-reborn/The-5zig-Mod">The 5zig Mod</a>.
</p>

<p align="center">
  <a href="https://github.com/5zig-renewed/5zig-renewed/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/5zig-renewed/5zig-renewed?include_prereleases&label=release"></a>
  <img alt="Minecraft 1.16 - 26.3" src="https://img.shields.io/badge/minecraft-1.16%E2%80%9326.3-62b47a">
  <img alt="Fabric, NeoForge, Forge" src="https://img.shields.io/badge/loaders-Fabric%20%7C%20NeoForge%20%7C%20Forge-c68b4b">
  <a href="LICENSE"><img alt="GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue"></a>
</p>

---

## About

The 5zig Mod was one of the most widely used utility mods of the 1.7-1.15 era: a configurable HUD, a
second chat, server information, Discord presence and a plugin API, all in one package. Upstream stopped
at Minecraft 1.15.2.

5zig Renewed continues it. The mod's own code - modules, HUD layout, configuration, chat handling - is
kept as-is, and the Minecraft-facing layer has been rebuilt for every version since, on Fabric, NeoForge
and Forge. One codebase, forty Minecraft versions, the same config file.

> Independent project. Not affiliated with Mojang, Microsoft, 5zig or 5zig Reborn.

## Features

| Feature | Details | Status |
|:--|:--|:--|
| **HUD** | 120+ widgets: FPS, CPS, ping, coordinates, armour, potions, held item, arrows, biome, facing, entity count, target block, memory, battery, clock, date, countdown, timer | Supported |
| **HUD editor** | Drag, scale, anchor and configure every item; layout is saved per server | Supported |
| **Coordinates** | On-screen position, F3-style overlay, and a clipboard for sharing coordinates in chat | Supported |
| **Second chat** | A separate chat window with its own history | Supported |
| **Chat log** | Persistent local chat history | Partial - the database schema is not created yet |
| **Server info** | Server IP, player count, latency and custom server labels | Supported |
| **Discord presence** | Server, world and game state shown in Discord | Supported |
| **Zoom** | Smooth zoom on a configurable key | Supported |
| **Minigame widgets** | Hypixel BedWars and SkyWars readouts, GommeHD and other server-specific items | Partial - several depend on APIs that no longer exist |
| **Plugin API** | Third-party 5zig add-ons | Supported to 1.20.6; custom payloads are unavailable on 1.21+ |
| **Friends, parties, capes** | Social and cosmetic features from the original mod | Unavailable - the 5zig backend is offline |

## Downloads

Every cell links to a jar. Pick the row for your Minecraft version and the column for your loader.

| | Meaning |
|:--|:--|
| ✓ | client-verified: world loaded, HUD and menus exercised |
| ◐ | builds and is packaged correctly, not boot-tested |
| ✕ | cannot load, for a reason outside this project |
| — | this loader does not target that version |

**58 of 61 assets are client-verified**; the exceptions are listed under [Compatibility](#compatibility).

| Minecraft | Java | Fabric | NeoForge | Forge |
|:--|:-:|:--|:--|:--|
| **26.3** | 25 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-263/5zig-renewed-26.3-fabric-4.0.0-alpha.1.jar) | — | — |
| **26.2** | 25 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-262/5zig-renewed-26.2-fabric-4.0.0-alpha.1.jar) | ✓ [20.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf262/5zig-renewed-26.2-neoforge-4.0.0-alpha.1.jar) | — |
| **26.1.2** | 25 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-2612/5zig-renewed-26.1.2-fabric-4.0.0-alpha.1.jar) | ✓ [20.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf2612/5zig-renewed-26.1.2-neoforge-4.0.0-alpha.1.jar) | — |
| **26.1.1** | 25 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-2611/5zig-renewed-26.1.1-fabric-4.0.0-alpha.1.jar) | ✓ [20.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf2611/5zig-renewed-26.1.1-neoforge-4.0.0-alpha.1.jar) | — |
| **26.1** | 25 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-261/5zig-renewed-26.1-fabric-4.0.0-alpha.1.jar) | ✓ [20.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf261/5zig-renewed-26.1-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.11** | 21 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-12111/5zig-renewed-1.21.11-fabric-4.0.0-alpha.1.jar) | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf12111/5zig-renewed-1.21.11-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.10** | 21 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-12110/5zig-renewed-1.21.10-fabric-4.0.0-alpha.1.jar) | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf12110/5zig-renewed-1.21.10-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.9** | 21 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1219/5zig-renewed-1.21.9-fabric-4.0.0-alpha.1.jar) | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1219/5zig-renewed-1.21.9-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.8** | 21 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1218/5zig-renewed-1.21.8-fabric-4.0.0-alpha.1.jar) | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1218/5zig-renewed-1.21.8-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.7** | 21 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1217/5zig-renewed-1.21.7-fabric-4.0.0-alpha.1.jar) | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1217/5zig-renewed-1.21.7-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.6** | 21 | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1216/5zig-renewed-1.21.6-fabric-4.0.0-alpha.1.jar) | ✓ [20.7 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1216/5zig-renewed-1.21.6-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.5** | 21 | ✓ [20.8 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1215/5zig-renewed-1.21.5-fabric-4.0.0-alpha.1.jar) | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1215/5zig-renewed-1.21.5-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.4** | 21 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1214/5zig-renewed-1.21.4-fabric-4.0.0-alpha.1.jar) | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1214/5zig-renewed-1.21.4-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.3** | 21 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1213/5zig-renewed-1.21.3-fabric-4.0.0-alpha.1.jar) | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1213/5zig-renewed-1.21.3-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.2** | 21 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1212/5zig-renewed-1.21.2-fabric-4.0.0-alpha.1.jar) | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1212/5zig-renewed-1.21.2-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21.1** | 21 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1211/5zig-renewed-1.21.1-fabric-4.0.0-alpha.1.jar) | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1211/5zig-renewed-1.21.1-neoforge-4.0.0-alpha.1.jar) | — |
| **1.21** | 21 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-121/5zig-renewed-1.21-fabric-4.0.0-alpha.1.jar) | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf121/5zig-renewed-1.21-neoforge-4.0.0-alpha.1.jar) | — |
| **1.20.6** | 21 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1206/5zig-renewed-1.20.6-fabric-4.0.0-alpha.1.jar) | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1206/5zig-renewed-1.20.6-neoforge-4.0.0-alpha.1.jar) | — |
| **1.20.5** | 21 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1205/5zig-renewed-1.20.5-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.20.4** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1204/5zig-renewed-1.20.4-fabric-4.0.0-alpha.1.jar) | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1204/5zig-renewed-1.20.4-neoforge-4.0.0-alpha.1.jar) | — |
| **1.20.3** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1203/5zig-renewed-1.20.3-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.20.2** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1202/5zig-renewed-1.20.2-fabric-4.0.0-alpha.1.jar) | ◐ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1202/5zig-renewed-1.20.2-neoforge-4.0.0-alpha.1.jar) | — |
| **1.20.1** | 17 | ✓ [17.9 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1/5zig-renewed-1.20.1-fabric-4.0.0-alpha.1.jar) | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-nf1201/5zig-renewed-1.20.1-neoforge-4.0.0-alpha.1.jar) | ✓ [17.9 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-forge/5zig-renewed-1.20.1-forge-4.0.0-alpha.1.jar) |
| **1.20** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-120/5zig-renewed-1.20-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.19.4** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1194/5zig-renewed-1.19.4-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.19.3** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1193/5zig-renewed-1.19.3-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.19.2** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1192/5zig-renewed-1.19.2-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.19.1** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1191/5zig-renewed-1.19.1-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.19** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-119/5zig-renewed-1.19-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.18.2** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1182/5zig-renewed-1.18.2-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.18.1** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1181/5zig-renewed-1.18.1-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.18** | 17 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-118/5zig-renewed-1.18-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.17.1** | 16 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1171/5zig-renewed-1.17.1-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.17** | 16 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-117/5zig-renewed-1.17-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.16.5** | 8 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1165/5zig-renewed-1.16.5-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.16.4** | 8 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1164/5zig-renewed-1.16.4-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.16.3** | 8 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1163/5zig-renewed-1.16.3-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.16.2** | 8 | ✓ [19.4 MB](https://github.com/5zig-renewed/5zig-renewed/releases/download/v4.0.0-alpha.1-1162/5zig-renewed-1.16.2-fabric-4.0.0-alpha.1.jar) | — | — |
| **1.16.1** | 8 | — | — | — |
| **1.16** | 8 | — | — | — |

## Installation

**Fabric** - install [Fabric Loader](https://fabricmc.net/use/) and
[Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version, then drop the jar into
`.minecraft/mods`.

**NeoForge** - install NeoForge for your Minecraft version, then drop the jar into `.minecraft/mods`. No
additional API mod is needed.

**Forge** - Minecraft 1.20.1 only. Install Forge 47.x, then drop the jar into `.minecraft/mods`.

Java is bundled with modern launchers; the Java column above shows the version each build targets.

## Compatibility

- **Alpha.** The mod is stable enough to play, but back up worlds before updating.
- **Forge is 1.20.1 only.** From 1.20.2 onward, use NeoForge.
- **Minecraft 1.16.0 and 1.16.1 cannot load.** Every published Fabric API for that line requires 1.16.2
  or newer, so Fabric refuses to load the API itself. 1.16.2 and later work normally.
- **NeoForge 1.20.2 is build-tested only.** The jar is complete but has not been exercised from a real
  installation.
- **Hotkeys are configured in the mod's own menu**, not in the vanilla Controls screen.
- **Friends, parties and capes need the original 5zig backend**, which no longer exists. The rest of the
  mod does not depend on it.
- **Custom plugin channels are unavailable on 1.21+**, where Minecraft replaced them with typed payloads.
  This affects third-party plugins that used them, not the HUD.

## Building

```bash
./gradlew build        # 1.20.1 Fabric
./gradlew runClient    # development client
```

Each Minecraft version has its own project: `versions/<version>-fabric` for Fabric and
`neoforge-<version>` for NeoForge. The root project targets 1.20.1 Fabric. The Gradle and JDK versions each
era needs are listed in [`docs/port-spec.md`](docs/port-spec.md), and the full version matrix is in
[`docs/version-matrix.md`](docs/version-matrix.md).

## Architecture

The mod's core contains no Minecraft imports. Everything it needs from the game goes through a platform
layer, and each loader contributes only the glue it must:

| Layer | Contents | Location |
|:--|:--|:--|
| Core | Modules, HUD layout, configuration, chat, plugin API | `core/` |
| Platform | `IVariables` implementation, GUI wrappers, mixins | `platform/<minecraft>/` |
| Loader | Entrypoints, keybind registration, loader-specific mixins | `loader/` |

That split is what makes a forty-version port tractable: a new Minecraft version needs a platform
implementation and, at most, a small loader adapter.

## Credits

Original mod by **5zig**. This continuation builds on the sources published by the **5zig Reborn** project.
Copyright headers are retained on every file.

## License

GPL-3.0. See [`LICENSE`](LICENSE).
