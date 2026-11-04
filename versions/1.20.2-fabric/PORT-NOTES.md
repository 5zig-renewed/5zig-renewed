# 1.20.2 Fabric port notes

Built with `gradle-8.10.2` / JDK 17, `--no-daemon`, target Java 17 (class file 61).

| Piece | Version |
|---|---|
| Minecraft | 1.20.2 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.91.6+1.20.2 |
| authlib | 5.0.47 (bundled by MC 1.20.2) |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.20.2-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.20.2-fabric-4.0.0-alpha.1.jar`

## Decompiled reference

`mc-src/1.20.2/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`),
decompiled from
`versions/1.20.2-fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-57017e2b25/…`.

## Delta vs 1.20.4

`platform/1.20.2` is `platform/1.20.4` verbatim apart from **two source changes** and a comment
sweep. That is not laziness: 1.20.2 is the release that introduced the configuration/play split and
the typed custom payloads, and 1.20.3/1.20.4 only added content and message-signature plumbing on
top, so the 1.20.4 platform's network/GUI code is already written against the 1.20.2 API generation.

Compile-level deltas found against the decompiled 1.20.2 sources (4 errors, 2 sites):

- `Variables#wrapPotionEffect`: `MobEffectUtil.formatDuration(MobEffectInstance, float)` — 1.20.2 has
  no `tickRate` parameter (1.20.4 added the third `float` for the tick-rate manager). Dropped `20.0F`;
  the hard-coded 20 tps factor is unchanged.
- `Variables#getScoreboard`: 1.20.2 `Scoreboard#getPlayerScores(Objective)` returns
  `Collection<Score>` with `Score#getOwner()/getScore()`; 1.20.4 renamed it to
  `listPlayerScores(Objective)` returning `Collection<PlayerScoreEntry>` with `owner()/value()`.
  The 1.20.2 platform uses the old pair.

Everything else that 1.20.4 does, 1.20.2 already has: `Minecraft#clearClientLevel(Screen)`,
`getCurrentServer().isRealm()`, `User` carrying a `UUID`, `Minecraft#getDebugOverlay()`,
`Screen#renderBackground(GuiGraphics,int,int,float)`, `EditBox#moveCursorToEnd(boolean)`,
`ServerData(String,String,ServerData.Type)`.

## authlib decision — the 1.20.4 compat files STAY

The task allowed dropping the two authlib-6 replacements if 1.20.2 still shipped authlib 4/5. It does
ship authlib **5.0.47**, but 5.0.47 already carries the api removals authlib 6 is known for:

- `com.mojang.authlib.Agent` and `UserAuthentication` are **absent** from `authlib-5.0.47.jar`.
- `YggdrasilAuthenticationService` only has `(Proxy)` and `(Proxy, Environment)` — the
  `(Proxy, String)` constructor is gone.
- `MinecraftSessionService#joinServer` takes `(UUID, String, String)`.

Verified by compiling the core copies: with the excludes removed, `core/…/AccountManager.java` fails
on `cannot find symbol: class Agent / UserAuthentication` and `String cannot be converted to
Environment`, and `core/…/PacketEncryption.java` fails on the same constructor plus
`GameProfile cannot be converted to UUID`. So `platform/1.20.2/…/account/AccountManager.java` and
`…/chat/network/packets/PacketEncryption.java` are required, wired exactly like 1.20.4 (core paths
excluded, platform source dir rooted at `platform/1.20.2/eu` so the exclude patterns do not match
the replacements). Consequence for users is unchanged from 1.20.4: offline accounts and the encrypted
account list work, e-mail/password login reports `AuthenticationException` (Microsoft-only).

## Packet layer (the reason this port needed care)

1.20.2's rewrite is byte-for-byte the 1.20.4 shape:

- `ClientboundCustomPayloadPacket`/`ServerboundCustomPayloadPacket` live in
  `net.minecraft.network.protocol.common` and carry a `CustomPacketPayload` (`id()` + `write`).
  `ServerboundCustomPayloadPacket` is identical between 1.20.2 and 1.20.4 (zero-line diff).
- Unknown inbound channels are decoded into a body-less `DiscardedPayload` in
  `ClientboundCustomPayloadPacket#readPayload` (`KNOWN_TYPES` is a static vanilla map).
- Fabric API 3.1.5 (`fabric-networking-api-v1` in 0.91.6+1.20.2) still provides the raw channel API
  and its `CustomPayloadS2CPacketMixin` injects at the `readUnknownPayload` call site, returning
  `PayloadHelper.readCustom(id, buf, max, NetworkingImpl.FACTORY_RETAIN)`. Registering a channel
  therefore installs a codec and the bytes survive decoding — this is what
  `PluginMessages.bootstrap()` relies on, and why it must run during `onInitializeClient`
  (`FiveZigFabric1202`).

No changes were needed to `PluginMessages`, `RawCustomPayload`,
`MixinClientPacketListener#handleCustomPayload` (targets `handleCustomPayload(CustomPacketPayload)`,
present in 1.20.2), `MixinConnection`, or the `minecraft:brand` re-encode.

## GUI / rendering

1.20.2 is already the sprite-atlas generation: `textures/gui/widgets.png` and `toasts.png` are
absent from the 1.20.2 assets, `textures/gui/sprites/widget/{button,button_disabled,
button_highlighted,slider,slider_handle,slider_handle_highlighted}.png` and
`textures/gui/sprites/toast/system.png` are present. The 1.20.4 `Button`/`Slider`/`Overlay` sprite
paths and the `GuiCompat#blitRepeating` helper (replacing the removed `blitNineSliced`/
`blitRepeating`) are therefore correct unchanged.

`Font#adjustColor` is identical in 1.20.2 and 1.20.4 (`(i & -67108864) == 0 ? i | 0xFF000000 : i`),
so the core's legacy `0xRRGGBB` colours render without forcing alpha.

## Verification performed

- `gradle --no-daemon clean build` succeeds; jar contains remapped classes, the mixin refmap
  (`5zig-renewed-1.20.2-fabric-refmap.json`), `5zig.accesswidener` and all **eight** nested runtime
  jars: DiscordIPC, jacob, json, json-simple, junixsocket-common, junixsocket-native-common,
  sqlite-jdbc, netty-codec-http.
- Manifest carries `5zig-Version`, `Minecraft-Version: 1.20.2`, `5zig-Build: alpha` on both `jar`
  and `remapJar`; `fabric.mod.json` has `the5zigmod`, entrypoint `FiveZigFabric1202`, `~1.20.2`
  dependency and the `jars` list.
- `tools/check_mixins.py` (retargeted to the 1.20.2 named jar) resolves every `@Inject` target:
  **36/36 present**.
- The single `@Shadow` (`DisconnectedScreen#parent`, `Screen`) exists in 1.20.2, and
  `DisconnectedScreen` has no `render` override there either, so the auto-reconnect countdown stays
  in `MixinScreen#render`.
- API delta scan over all **109** `net.minecraft.*` types imported by `platform/1.20.2` between the
  1.20.2 and 1.20.4 named jars: no class imported by the platform is missing from 1.20.2; the 36
  classes with member differences differ only in members the platform does not call (plus the two
  sites above).

## Known gaps

- No client launch was performed (task rules), so HUD/chat pixels are verified by API compatibility,
  not on screen.
- Plugin-channel delivery depends on Fabric channel registration; on servers that never announce the
  5zig channels the client still sends (raw `RawCustomPayload` fallback) but receives nothing.
- `MixinKeyboardHandler` still injects `keyPress(JIIII)V`; keybinds are dispatched from the shared
  `FiveZigFabric` tick hook, as on 1.20.1/1.20.4.
