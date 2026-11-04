# 1.20.4 Fabric port notes

Built with `gradle-8.10.2` / JDK 17, `--no-daemon`, target Java 17 (class file 61).

| Piece | Version |
|---|---|
| Minecraft | 1.20.4 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.97.2+1.20.4 |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.20.4-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.20.4-fabric-4.0.0-alpha.1.jar`

## Decompiled reference

`mc-src/1.20.4/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`), decompiled from
`versions/1.20.4-fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-d0b2754079/…`.

## Why 1.20.4 is not just 1.20.1

1.20.4 sits on the other side of the 1.20.2 "network/protocol rewrite" and also ships authlib 6, so the
delta is bigger than the version number suggests. `platform/1.20.4` is a copy of `platform/1.20.1`
with the following changes.

### authlib 6 (same treatment as 1.21.1)

Minecraft 1.20.4 ships **authlib 6.0.52**, which removed `YggdrasilAuthenticationService(Proxy, String)`,
`Agent` and `UserAuthentication`. Two core classes cannot compile unchanged and are replaced by
platform copies (wired in `build.gradle` by excluding the core paths and rooting the platform source
directory at `platform/1.20.4/eu`, exactly like the 1.21.1 project):

- `platform/1.20.4/…/account/AccountManager.java` — identical API; offline accounts and the encrypted
  account list work, e-mail/password login reports `AuthenticationException` (Microsoft-only).
- `platform/1.20.4/…/chat/network/packets/PacketEncryption.java` — same wire format,
  `MinecraftSessionService#joinServer(UUID, String, String)`.

### Typed custom payloads (1.20.2+)

`ClientboundCustomPayloadPacket`/`ServerboundCustomPayloadPacket` moved to `network.protocol.common`
and carry a `CustomPacketPayload` (`id()` + `write`). Unknown incoming payloads are decoded into a
body-less `DiscardedPayload`, so the 5zig API channels have to be declared:

- `platform/1.20.4/…/platform/PluginMessages.java` (new) registers `the5zigmod:5zig`, `…:5zig_reg`,
  `…:5zig_set` as global receivers through Fabric's still-raw channel API
  (`ClientPlayNetworking.registerGlobalReceiver(ResourceLocation, …)`) and hands the bytes to
  `EventListener#handlePluginMessage`.
- `FiveZigFabric1204` (new entrypoint in `loader/fabric-1.20.4/java`) calls `PluginMessages.bootstrap()`
  before the shared `FiveZigFabric` setup.
- `Variables#sendCustomPayload` uses `PluginMessages.send` and falls back to a plain
  `ServerboundCustomPayloadPacket(new RawCustomPayload(id, bytes))` (1.20.4 still accepts payloads for
  unregistered channels) so arbitrary API channels keep working.
- `MixinClientPacketListener#handleCustomPayload` now targets
  `handleCustomPayload(CustomPacketPayload)`; only `BrandPayload` is re-encoded for the legacy
  `minecraft:brand` hook (the 5zig channels are delivered by Fabric and no longer reach this hook).

### GUI / rendering

- `textures/gui/widgets.png` and `textures/gui/toasts.png` are gone (GUI sprite atlas): buttons draw
  `widget/button{,_disabled,_highlighted}`, sliders `widget/slider` + `widget/slider_handle{,_highlighted}`,
  the 5zig toast draws `toast/system`.
- `GuiGraphics#blitNineSliced`/`#blitRepeating` removed → `GuiCompat` (new, copied from the 1.21 port)
  reimplements `blitRepeating` with the plain `blit` overload; list/file-selector backgrounds use
  `Screen.BACKGROUND_LOCATION` as before.
- `Screen#renderBackground(GuiGraphics, int, int, float)`, `mouseScrolled(double, double, double, double)`.

### Client API changes

- `Minecraft#clearClientLevel(Screen)` (was `clearLevel()` + `setScreen`), `getCurrentServer().isRealm()`
  (was `isConnectedToRealms()`).
- `User` carries a `UUID` instead of a `GameProfile`: `getGameProfile()` rebuilds it,
  `setSession` derives the offline UUID (`OfflinePlayer:<name>`) when the stored id is not a UUID.
- `Options#renderDebug` → `Minecraft#getDebugOverlay().showDebugScreen()`.
- `Scoreboard#getDisplayObjective(DisplaySlot)`, `listPlayerScores` → `PlayerScoreEntry#owner()/value()`.
- `MobEffectUtil.formatDuration(instance, float, float)`, `MobEffect#byId/getId` →
  `BuiltInRegistries.MOB_EFFECT.getHolder(int)` / `#getId`.
- `EditBox#moveCursorToEnd(boolean)`.
- `ServerData(String, String, ServerData.Type)`.
- `AbstractClientPlayer#getCloakTextureLocation` → capes are spliced into `getSkin()`'s `PlayerSkin`
  record; `DisconnectedScreen` no longer overrides `render`, so the auto-reconnect countdown is drawn
  from `MixinScreen#render`.

### Colours

1.20.4's `Font#adjustColor` ORs `0xFF000000` into colours whose alpha byte is 0 (`Font.java:148`), so
the core's legacy `0xRRGGBB` values render normally; no `| 0xFF000000` forcing was needed.

## Known gaps

- Plugin-channel delivery depends on Fabric's channel registration; on servers that never announce the
  5zig channels the client still sends (raw fallback) but receives nothing.
- `MixinKeyboardHandler` injects `keyPress(JIIII)V`; keybinds are dispatched from the shared
  `FiveZigFabric` tick hook, as on 1.20.1.
- No client launch was performed (per task rules), so HUD/chat pixels are verified only by API
  compatibility, not on screen.

## Verification performed

- `gradle --no-daemon clean build` succeeds; jar contains the remapped classes, the mixin refmap and
  the five nested runtime jars (DiscordIPC, jacob, json, sqlite-jdbc, netty-codec-http).
- `tools/check_mixins.py` resolves every `@Inject` target (`name + descriptor`) against the 1.20.4 named
  jar: **36/36 present**.
- The single `@Shadow` (`DisconnectedScreen#parent`, `Screen`) matches the 1.20.4 field.
- `fabric.mod.json` entrypoint, mixin list (14 classes), `~1.20.4` dependency and `Minecraft-Version`
  manifest attribute verified inside the built jar.
