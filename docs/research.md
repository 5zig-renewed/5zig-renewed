# Upstream research — 5zig Reborn

## What it is

[The 5zig Mod](https://5zigreborn.eu/) is an all-in-one client mod (HUD, capes, friends/chat with its
own backend, Discord Rich Presence, a plugin API, a server-side Bukkit plugin API). The site states
support for **Minecraft 1.8.9 – 1.15.2**. Last upstream activity: `The-5zig-Mod` pushed 2022-07-19,
`translations` 2022-10-16 — the project is effectively dead.

## The 14 repos (mirrored byte-for-byte into this org)

| Repo | Lang | Size | License | Role |
|---|---|--:|---|---|
| `The-5zig-Mod` | Java | 9.5 MB | GPL-3.0 | The mod itself; `api`/`i18n`/`version-specific` are submodules |
| `version-support` | Java | 8.1 MB | none | Per-version platform layer (`1.8.9`, `1.12.2`, `1.14.4`, `1.15.1`) + `common` |
| `5zig-fabric` | Java | 216 KB | GPL-3.0 | Runtime Jar **remapper** that let the Forge build load on Fabric |
| `plugin-api` | Java | 244 KB | MIT | Client-side plugin API |
| `translations` | — | 2.8 MB | — | Locale resources (15+ languages) |
| `server-api` | Java | 336 KB | MIT | Bukkit plugin exposing 5zig server-side data |
| `documentation` | Python | 2.1 MB | NOASSERTION | API docs (mkdocs) |
| `DiscordIPC` | Java | 168 KB | Apache-2.0 | Fork of jagrosh/DiscordIPC |
| `Mixin` | Java | 6.9 MB | MIT | Fork of Sponge Mixin |
| `MixinGradle` | Groovy | 168 KB | MIT | Gradle plugin for Mixin |
| `ForgeGradle` | Java | 5.5 MB | LGPL-2.1 | Forge's old Gradle plugin (fork) |
| `multimc-bundler` | — | 100 KB | Apache-2.0 | MultiMC instance generator |
| `5zig-reborn.github.io` | JS | 16 MB | AGPL-3.0 | The website |
| `deployments` | — | 220 MB | — | CI artifact dump (built jars, no source) |

## Why the port is feasible (and why it is not a rewrite)

The core is deliberately Minecraft-free:

```
$ grep -rl "import net.minecraft" The-5zig-Mod/mod/src/main/java | wc -l
1          # out of 889 files
```

Everything goes through `MinecraftFactory.getVars()` → `IVariables` (162 methods) plus ~9 GUI
interfaces. Each version's platform is ~56 classes / ~7.6k LOC (measured: 1.8.9 = 7,911 LOC,
1.12.2 = 7,792, 1.14.4 = 7,546, 1.15.1 = 7,601) and ~24 mixins.

So a version port = re-implement the platform, not the mod. 40 versions × 3 loaders is only
tractable because those 56 classes are 90 % identical between adjacent versions — hence eras.

## Per-era cost drivers (why the era boundaries sit where they do)

- **1.16.x**: last Java 8 era; old `RenderSystem`/`GlStateManager`, `MatrixStack` absent.
- **1.17**: Java 16, the big renderer rewrite (`RenderSystem` shaders mandatory, no `GlStateManager`),
  `MatrixStack`/`PoseStack` introduced.
- **1.19.3/1.19.4**: registry + chat-component churn, `Screen` render signature changes.
- **1.20**: `GuiGraphics` replaces `AbstractGui` drawing → this is where every draw call changes.
- **1.20.2–1.20.6**: pack/config reorganisation, NeoForge forks off Forge, `EditBox` changes.
- **1.21**: `ResourceLocation` constructors deprecated, `Component`/`ChatFormatting` moves, item model
  changes begin.
- **1.21.2–1.21.4**: item model rewrite + render pipeline groundwork.
- **1.21.5–1.21.11**: render pipeline/`RenderType` overhaul, GUI render-state changes in 1.21.6+.
- **26.x**: Java **25**, Mojang's new versioning, further render-state work.

## Loader caveats (from live Maven metadata, see `version-matrix.md`)

- Fabric covers all 40 requested versions.
- NeoForge only exists from 1.20.1 (that one release is published as `net.neoforged:forge:1.20.1-47.1.x`).
- Forge never shipped 1.20.5 or 1.21.2, and no 1.16.0 / 1.17.0.
- NeoForge 1.21.6 / 1.21.7 / 1.21.9 only have **beta** builds.
- Java targets: 1.16.x → 8, 1.17 → 16, 1.18–1.20.4 → 17, 1.20.5–1.21.11 → 21, 26.x → 25.
