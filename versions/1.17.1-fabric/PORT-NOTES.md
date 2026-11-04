# 1.17.1 Fabric port notes

First **1.17** target of the repo, built from `platform/1.18.2` and reconciled against the
decompiled 1.17.1 sources. 1.17 is the transitional Caves & Cliffs release: the rendering rewrite
is in place (`PoseStack` threaded through every screen, no `Tessellator`/immediate-mode GUI, no
`GuiGraphics` yet) and it is the first Java 16 target, but the 1.18 additions (Holder-based biomes,
`canSeeEffects()`, the 6-argument `User` constructor, `startAttack()Z`) do not exist yet.

Built with `gradle-8.10.2` / JDK 17, `--no-daemon`, **`--release 16`** (class file 60).

| Piece | Version |
|---|---|
| Minecraft | 1.17.1 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.46.1+1.17 (its own constraint is `minecraft: ~1.17.1-rc`, i.e. 1.17.1 and up) |
| Mixin compatibility level | `JAVA_16` |

Build: `cd versions/1.17.1-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.17.1-fabric-4.0.0-alpha.1.jar` (18 819 555 bytes, self-contained,
1690 entries, sha256 `9e5639d01591fabd2ff99b93a9bccddefa217d8d704a68eb8a3b1ddadcbebb5a`).

## Fabric API's mod id

On the 1.17.x line the Fabric API jar still declares **`"id": "fabric"`** (verified in
`fabric-api-0.46.1+1.17.jar/fabric.mod.json`). `loader/fabric-1.17.1/resources/fabric.mod.json`
therefore depends on `fabric`, **not** `fabric-api`; declaring `fabric-api` makes the loader reject
the mod with *"requires any version of fabric-api, which is missing"*.

## Decompiled reference

`mc-src/1.17.1/out` (4142 `.java` files), produced with Loom's
`genSourcesWithVineflower` (vineflower 1.12.0) from
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.17.1-loom.mappings.1_17_1.layered+hash.2198-v2/…`.

## Scope of the delta against `platform/1.18.2`

39 platform classes (30 `platform/`, 9 `util/`) + 15 mixins. **Four files carry code changes** (the
other 35 differ only in the `1.18.2` → `1.17.1` comment sweep). Everything else is identical to the 1.18.2 platform, including
the whole `GuiContext` facade — 1.17 and 1.18.2 share the `PoseStack`-per-frame model, `GuiComponent`
blit/fill helpers and `Font#drawShadow|draw|width`, so the drawing layer carries over untouched.

| Area | 1.18.2 | 1.17.1 | Resolution |
|---|---|---|---|
| Biome lookup | `Level#getBiome` returns `Holder<Biome>`, key via `unwrapKey()` | returns the `Biome` itself; `Holder` does not exist | resolve the key through `ClientPacketListener#registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getResourceKey(biome)` |
| Inventory effect column | `EffectRenderingInventoryScreen#canSeeEffects()` (public) | only `protected boolean doRenderEffects` | widened with an access-widener entry, read directly |
| Offline session | `new User(name, uuid, token, Optional, Optional, Type)` | `new User(name, uuid, token, typeName)`; `Type` is `LEGACY`/`MOJANG` only, no `getName()` | pass `"legacy"`/`"mojang"` explicitly |
| Left-click hook | `Minecraft#startAttack()Z` | `startAttack()V` | `@Inject` + `CallbackInfo` |
| 2nd-chat scroll | `ChatComponent#scrollChat(I)V` | `scrollChat(D)V` | `double` parameter, cast to `int` for `IGui2ndChat#scroll` |
| Widget hover state | `AbstractWidget#isHoveredOrFocused()` | method absent; `isHovered()` already returns `isHovered \|\| focused` | call `isHovered()` |

### Mixin changes (1.17.1 vs 1.18.2)

- `MixinMinecraft` — `startAttack` target descriptor `()V`, callback `CallbackInfo`.
- `MixinChatComponent` — `scrollChat` target descriptor `(D)V`.
- The other 13 mixin classes are unchanged; all 37 injector targets resolve against the 1.17.1
  named jar.

## IVariables coverage

`Variables` implements **all 162 methods** of `eu.the5zig.mod.util.IVariables` (162 `@Override`s,
enforced by `javac`, same count as the 1.18.2 platform). Nothing throws
`UnsupportedOperationException` on a hot path; the only throw is the defensive fallback at the end of
`setSession` (unreachable while `Minecraft#user` exists).

Fully real (read live from Minecraft state): text/width/split, widget factories, chat (2nd chat,
chat box text, chat field), sounds, server data/pinger, FOV/smooth camera, screen switching, joining
servers, client accessors (user, profile, session, proxy, FPS source, window, scaled resolution),
player position/rotation/chunk, target block/entity ray trace, biome/light/entity count,
food/health/armor/air/resistance, potion effects, items in hand/armor/hotbar + counts, HUD helpers,
plugin payloads, resource/texture handling (dynamic images, capes), scoreboard, battery (OSHI 5.7.5,
shipped by the 1.17.1 client), key dispatch, reflective session replacement, chat components with
click/hover.

Degraded / approximated (documented, not stubbed):

| Method | 1.17.1 behaviour |
|---|---|
| `getFPS()` | Parses the leading field of `Minecraft#fpsString` (`"120 fps T: …"`, verified in the 1.17.1 source); correct value, only updated once per second. |
| `setFOV(float)` / `getFOV()` | Writes/reads the public `Options#fov` (`double`, verified `public double fov = 70.0`). No 30..110 clamp on the programmatic path — the vanilla slider still clamps when the player uses it. |
| `setSession(name, uuid, token, type)` | Replaces the private **final** `Minecraft#user` field by reflection (`setAccessible` + `Field#set`). The only reflection-dependent path. An `msa` type string (written by a newer client) falls back to `MOJANG`, because 1.17.1's `User.Type` has only `LEGACY`/`MOJANG`. |
| `getPopulationInfo()` (`ServerData`) | 1.17.1's `ServerData` has no online/max counters (only `name/ip/motd/ping/players`), so the sampled player-list size is returned, or `""`. |
| `getDummyPotionEffects()` | Intentionally synthetic (HUD preview), as in every other port. |
| `resetServer()` / `updateScaledResolution()` | No cached state exists in 1.17.1 (values are read live from `ClientPacketListener`/`Window`), so they are no-ops by design. |
| Item icon drawing (`WrappedItemStack#render`) | `ItemRenderer#renderAndDecorateItem` draws in absolute GUI coordinates through its own model view and ignores the `PoseStack`; correct for the current absolute-coordinate callers, wrong inside a scaled/translated pose. |
| `GuiContext#enableScissor/disableScissor` | Not provided by `GuiComponent` in 1.17 (added in 1.19.2); the facade multiplies by `Window#getGuiScale()`, flips Y and rounds, so a non-integral scale can be off by one pixel at the edges. |
| `getMinecraftIcon()` | The 1.17.1 client jar ships no `icons/` resources (verified with `unzip -l`), so `/pack.png` is used, falling back to `/images/5zig-icon.png`. |

## Verification performed

- `gradle --no-daemon build` green (JDK 17, Loom 1.7.4, `--release 16`); `validateAccessWidener`
  passes with the `doRenderEffects` entry.
- `tools/check_mixins.py` resolves every `@Inject`/`@ModifyVariable` name+descriptor against the
  1.17.1 named jar: **37/37 present** across 15 mixin classes.
- `tools/LinkCheck.java` (ASM) resolves every class/method/field reference in the compiled mod
  against the 1.17.1 named jar + Fabric API + libraries: **1495 classes, 65 315 references,
  0 unresolved** (would catch `NoSuchMethodError`/`NoSuchFieldError`/`NoClassDefFoundError`).
- Remapped jar: refmap covers all 15 mixin classes / 34 injector descriptors with **0 empty
  targets**; `MixinMinecraft.startAttack` → `class_310;method_1536`, `MixinChatComponent.scrollChat`
  → `class_338;method_1802`.
- Jar contains the remapped classes (Java 16, class-file major 60), the refmap, 8 nested runtime
  jars (DiscordIPC, jacob, json, json-simple, junixsocket-common, junixsocket-native-common,
  sqlite-jdbc, netty-codec-http) and the `5zig-Version` / `Minecraft-Version` / `5zig-Build`
  manifest attributes; `fabric.mod.json` declares `minecraft: ~1.17.1` and depends on `fabric`.
- No reference to 1.18+-only symbols (`net/minecraft/core/Holder`, `GuiGraphics`,
  `BuiltInRegistries`, `ItemRenderer`) in the remapped platform classes.

## Known gaps / not verified

- **No client run** (task rule), so nothing here is pixel-verified. HUD, second chat, widget themes,
  capes and the mod menu are verified only by API/link compatibility.
- Item icons ignore `PoseStack` transforms (see table); fine for today's callers.
- `MixinCommonMinecraft` ("clickMouse") from `common/` is compiled in but **not** listed in
  `mixins.the5zigmod.json`; `MixinMinecraft#startAttack` covers CPS. It produces the only
  annotation-processor warning in the build.
- 1.17.1 has no chat signing (introduced in 1.19), so the secure-chat hooks of later ports are
  simply absent rather than disabled.
