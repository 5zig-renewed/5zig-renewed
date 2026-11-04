# 1.20.3 Fabric port notes

Built with `gradle-8.10.2` / JDK 17, `--no-daemon`, target Java 17 (class file 61).

| Piece | Version |
|---|---|
| Minecraft | 1.20.3 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.91.1+1.20.3 (last build published for 1.20.3) |
| authlib | 6.0.52 (bundled by MC 1.20.3 — the same jar 1.20.4 ships) |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.20.3-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.20.3-fabric-4.0.0-alpha.1.jar`

## Decompiled reference

`mc-src/1.20.3/out` (vineflower 1.12.0, `--folder --only=net/minecraft`, 4849 files),
decompiled from
`versions/1.20.3-fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-57017e2b25/…`.

Two vineflower 1.12.0 quirks worth remembering, both silent:

- The destination directory must **already exist**, and so must every package directory under it;
  `DirectoryResultSaver` does not create parents and otherwise fails with
  `NoSuchFileException: <dest>/net/minecraft/…`. If the destination does not exist at all, the path is
  read as a *source* and ignored (`warn: missing '…', ignored`).
- `--folder` must be passed explicitly. `-dgs=1` is **not** a valid flag in this version: it is parsed
  as a positional argument, so the destination becomes a directory literally named `-dgs=1` and the
  real destination is consumed as an (ignored) source. Generics are decompiled by default
  (`--decompile-generics=true`), so no flag is needed.

## Delta vs 1.20.4 — no code change

`platform/1.20.3` is `platform/1.20.4` verbatim. The only edits are a comment/log-string sweep
(`1.20.4` → `1.20.3`, 16 occurrences in 8 files); nothing in the code needed to move. 1.20.3 is pinned
by API shape, not by version number:

- 1.20.3 already has `MobEffectUtil.formatDuration(MobEffectInstance, float, float)` — the third
  `tickRate` parameter (`net/minecraft/world/effect/MobEffectUtil.java:15` in the decompiled tree,
  confirmed by `javap`: `(Lnet/minecraft/world/effect/MobEffectInstance;FF)V`). 1.20.2 still has the
  2-arg form, so this site is on the **1.20.4 side**.
- 1.20.3 already has `Scoreboard#listPlayerScores(Objective)` returning
  `Collection<PlayerScoreEntry>`, with
  `record PlayerScoreEntry(String owner, int value, @Nullable Component display, @Nullable NumberFormat numberFormatOverride)`
  and accessors `owner()` / `value()`. 1.20.2 has `getPlayerScores(Objective)` returning
  `Collection<Score>` with `getOwner()` / `getScore()`. Also the **1.20.4 side**.
- authlib: 1.20.3 resolves `com.mojang:authlib:6.0.52`, byte-identical to 1.20.4's. `Agent`,
  `UserAuthentication` and `YggdrasilAuthenticationService(Proxy, String)` are all gone, exactly as on
  1.20.4, so the two platform replacements (`…/account/AccountManager.java`,
  `…/chat/network/packets/PacketEncryption.java`) are required unchanged — the core copies are still
  excluded in `build.gradle`, which also roots the platform source dir at `platform/1.20.3/eu` so those
  exclude patterns cannot match the replacements.

Both named split points landed on the 1.20.4 side; the port needed zero behavioural edits.

## API delta scan

All **116** `net.minecraft.*` types imported by `platform/1.20.3` + `loader/fabric/1.20.3` exist in both
the 1.20.3 and the 1.20.4 named jars, and every one has an **identical member set** (name + JVM
descriptor): `javap -p -s` diff over the two jars reports **0 classes with member differences**. Spot
checks: `Minecraft` 376 members, `GuiGraphics` 83, `Scoreboard` 55 — equal on both sides. This is
stricter than the 1.20.2 result, where 36 of the imported classes differed.

## Everything else carries over from 1.20.2/1.20.4

- **Packet layer**: 1.20.2's typed payloads (`CustomPacketPayload`, `network.protocol.common`,
  `DiscardedPayload` for unknown channels), so `PluginMessages.bootstrap()` from the
  `FiveZigFabric1203` entrypoint is still what keeps the 5zig API channels' bytes alive;
  `RawCustomPayload` remains the fallback for unregistered server channels.
- **GUI**: 1.20.3 is the sprite-atlas generation (`textures/gui/sprites/widget/…`), `blitNineSliced` /
  `blitRepeating` are gone (covered by `GuiCompat`), `Screen#renderBackground(GuiGraphics,int,int,float)`.
- `Minecraft#clearClientLevel(Screen)`, `getCurrentServer().isRealm()`, `User` carrying a `UUID`,
  `Minecraft#getDebugOverlay()`, `EditBox#moveCursorToEnd(boolean)`,
  `ServerData(String,String,ServerData.Type)`: all the 1.20.4 shapes already.
- `Font#adjustColor` ORs `0xFF000000` into zero-alpha colours here too, so legacy `0xRRGGBB` values
  from core render without forcing alpha.

## Verification performed

- `gradle --no-daemon clean build` succeeds (56 s); jar is 18 MB, 1692 entries, 1298
  `eu/the5zig/mod` classes.
- Jar carries the remapped classes, `5zig-renewed-1.20.3-fabric-refmap.json` (**14** mixin classes,
  **33** mappings — identical counts to the 1.20.2 and 1.20.4 refmaps), `5zig.accesswidener`, and all
  **eight** nested runtime jars: DiscordIPC, jacob, json, json-simple, junixsocket-common,
  junixsocket-native-common, sqlite-jdbc, netty-codec-http. Loom lists all eight in
  `fabric.mod.json` `jars`; `processIncludeJars` ran and `validateAccessWidener` passed.
- Manifest carries `5zig-Version: 4.0.0-alpha.1`, `Minecraft-Version: 1.20.3`, `5zig-Build: alpha`
  on both `jar` and `remapJar`; `fabric.mod.json` has id `the5zigmod`, entrypoint
  `eu.the5zig.mod.fabric.FiveZigFabric1203`, `minecraft: ~1.20.3` and `java >=17`.
- `tools/check_mixins.py` (retargeted to the 1.20.3 named jar) resolves every `@Inject` target:
  **36/36 present**.
- The single `@Shadow` (`DisconnectedScreen#parent`, `Screen`) exists in 1.20.3, and
  `DisconnectedScreen` has no `render` override (`javap` reports 0 `render` members), so the
  auto-reconnect countdown stays in `MixinScreen#render`.

## Known gaps

- No client launch was performed (task rules); HUD/chat pixels are verified by API compatibility,
  not on screen.
- Plugin-channel delivery depends on Fabric's channel registration; on servers that never announce
  the 5zig channels the client still sends (raw `RawCustomPayload` fallback) but receives nothing.
- `MixinKeyboardHandler` injects `keyPress(JIIII)V`; keybinds are dispatched from the shared
  `FiveZigFabric` tick hook, as on 1.20.2/1.20.4.
