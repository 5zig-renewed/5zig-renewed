# 1.19.4 Fabric port notes

Third **pre-1.20** target, ported from this repo's `platform/1.19.3` and reconciled against
`mc-src/1.19.4/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`).
Built with Gradle 8.10.2 / JDK 17, `--no-daemon`, Java 17 (class file 61).

| Piece | Version |
|---|---|
| Minecraft | 1.19.4 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.87.2+1.19.4 |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.19.4-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.19.4-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained)

Decompiled reference: `mc-src/1.19.4/out`, decompiled from
`versions/1.19.4-fabric/.gradle/loom-cache/minecraftMaven/…/minecraft-merged-96151393a2-…jar`.

## Rendering: 1.19.4 is the transition release

Still no `GuiGraphics` (that is 1.20), so the `GuiContext` facade keeps its shape — but 1.19.4 moved
several GUI primitives in the 1.20 direction, which is the bulk of this delta:

- **`AbstractWidget#renderButton` → `renderWidget(PoseStack,int,int,float)`**, now abstract.
  `Button`, `Slider` and `Textfield` override `renderWidget`; `AbstractWidget#render` already routes
  to it, so the `Button#draw` call path is unchanged.
- **`AbstractWidget#isHovered()` is public**, so the platform overrides are plain overrides of an
  inherited method (they still read the inherited `isHovered` field).
- **`ItemRenderer` takes the pose**: `renderAndDecorateItem(PoseStack, ItemStack, int, int)` and
  `renderGuiItemDecorations(PoseStack, Font, ItemStack, int, int)`. `WrappedItemStack#render` now
  passes `graphics.pose()`, i.e. item icons follow the HUD transform — a behaviour change from
  1.19.2/1.19.3, where the pose-less renderer forced absolute coordinates. Current HUD callers draw
  outside any non-identity transform, so the output is identical.
- **`RenderSystem#enableTexture()/disableTexture()` were removed.** `GuiContext` no longer toggles
  texturing (it is shader state now); only blending is re-asserted before blits.
- **`Screen#renderDirtBackground(int)` → `renderDirtBackground(PoseStack)`**; `GuiHandle` passes the
  frame's pose.

## Other API changes handled (1.19.3 → 1.19.4)

- **`DamageSource.GENERIC` removed.** Damage sources are per-level in 1.19.4:
  `level.damageSources().generic()` in the armour-enchantment protection path (`Variables`).
- **`MobEffectUtil.formatDuration` returns `Component`** (was `String`) → `.getString()` in
  `wrapPotionEffect`.
- **`ServerData#getIconB64()` removed**, replaced by raw PNG `getIconBytes()`. `getServerIcon()`
  base64-encodes on demand (the 5zig API still hands out base64).
- **`ServerData#players` (`ServerStatus.Players`, a record of `max/online/sample`) arrives**, so
  `getPopulationInfo()` now reports the real `online/max` counters and only falls back to the
  sampled player list when the server sent none (the 1.19.3 behaviour).
- `SoundEvent.createVariableRangeEvent` (introduced for the 1.19.3 port) still applies; the private
  constructor and the `Registry` → `BuiltInRegistries` move are unchanged from 1.19.3.
- Chat is signed in `ClientPacketListener#sendChat/sendCommand` exactly as in 1.19.3, so the
  outgoing-chat hook stays in `MixinClientPacketListener` (14 client mixins, no `MixinLocalPlayer`).

## Stubs / degraded behaviour

| Method | 1.19.4 behaviour |
|---|---|
| `getFPS()` | Parses the leading field of `Minecraft#fpsString` (no `getFps()` before 1.20). |
| `setSession(name, uuid, token, type)` | Replaces the private `Minecraft#user` field by reflected type. |
| `getDummyPotionEffects()` | Synthetic HUD preview, same as every other port. |
| `resetServer()` / `updateScaledResolution()` | No cached state to reset — intentional no-ops. |
| `ServerData#getPopulationInfo()` | Real `online/max` when the ping carried `players`, else sampled list size. |

## Verification performed

- `gradle --no-daemon build` green from a clean `build/classes`.
- Jar contains remapped classes, refmap (14 mixin classes / 35 mappings), 8 nested runtime jars
  (DiscordIPC, jacob, json, json-simple, junixsocket-common, junixsocket-native-common,
  sqlite-jdbc, netty-codec-http) and the `5zig-Version` / `Minecraft-Version` / `5zig-Build`
  manifest attributes; `fabric.mod.json` depends on `~1.19.4`.
- `tools/check_mixins.py` resolves every `@Inject` name+descriptor against the 1.19.4 named jar:
  **38/38 present**.
- `tools/LinkCheck.java` (ASM) resolves every class/method/field reference in the compiled mod
  against the 1.19.4 named jar + Fabric API + libraries: **1494 classes, 65 309 references,
  0 unresolved**.

## Known gaps / not verified

- **No client run** (task rule): HUD, second chat, widget themes, capes and the mod menu are
  verified only by API/link compatibility, not visually.
- `MixinCommonMinecraft` from `common/` is compiled in but not listed in `mixins.the5zigmod.json`
  (annotation-processor warning only), as in every other port.
- Item icons now obey the pose; only relevant if a future caller draws items inside a scaled or
  translated HUD transform.
