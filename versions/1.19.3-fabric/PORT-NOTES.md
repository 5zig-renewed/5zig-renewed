# 1.19.3 Fabric port notes

Second **pre-1.20** target, ported from the client-verified `platform/1.19.2` and reconciled against
`mc-src/1.19.3/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`).
Built with Gradle 8.10.2 / JDK 17, `--no-daemon`, Java 17 (class file 61).

| Piece | Version |
|---|---|
| Minecraft | 1.19.3 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.76.1+1.19.3 |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.19.3-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.19.3-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained)

Decompiled reference: `mc-src/1.19.3/out`, decompiled from
`versions/1.19.3-fabric/.gradle/loom-cache/minecraftMaven/…/minecraft-merged-d5df7050f8-…jar`.

## The design carries over unchanged: still no `GuiGraphics`

1.19.3 still threads a `PoseStack` through `Screen#render(PoseStack,int,int,float)`,
`AbstractWidget#render`, `Font#draw(PoseStack,…)` and `GuiComponent` statics; `GuiGraphics` is 1.20.
`platform/1.19.3/eu/the5zig/mod/util/GuiContext.java` is therefore the same facade as the 1.19.2
one, with one import change (`com.mojang.math.Matrix4f` → `org.joml.Matrix4f`, the Mojang math
types were replaced by JOML in 1.19.3).

`ItemRenderer` is still pose-less in 1.19.3 (`renderAndDecorateItem(ItemStack,int,int)`), so item
icons keep drawing at absolute screen coordinates.

## API changes handled (1.19.2 → 1.19.3)

### Chat / signing (the big one)

- 1.19.3 moved signing out of `LocalPlayer`: `LocalPlayer#chatSigned` / `commandSigned` are gone and
  `ClientPacketListener#sendChat(String)` / `sendCommand(String)` sign and send instead.
  `MixinLocalPlayer` was **deleted**; the hook now lives in `MixinClientPacketListener`
  (`sendChat`/`sendCommand`, both `HEAD` + cancellable), restoring the leading `/` for commands so
  `The5zigMod.getListener().onSendChatMessage(...)` sees the 1.15 contract. The mixin config dropped
  `MixinLocalPlayer` (14 client mixins, was 15).
- `ChatComponent#render` gained the mouse coordinates: `render(PoseStack,int,int,int)`.
- `ChatComponent#addMessage(Component, MessageSignature, GuiMessageTag)` is unchanged; chat
  reporting merely adds `PlayerInfo#getChatSession/hasVerifiableChat`, which this port does not read.

### Registry rewrite

- `Registry` became an interface and the static registries moved to
  `net.minecraft.core.registries.BuiltInRegistries`: `Registry.ITEM` → `BuiltInRegistries.ITEM`,
  `Registry.BLOCK` → `BuiltInRegistries.BLOCK` (`WrappedItemStack`, `Variables`).

### Widgets / GUI

- `AbstractWidget.x/y` are **private** now; `getX()/setX()/getY()/setY()` are the accessors. The
  platform `Button`/`Textfield` overrides now delegate to `super` instead of touching the fields.
- `AbstractWidget#updateNarration` is `final` and delegates to the new abstract
  `updateWidgetNarration(NarrationElementOutput)` — `Textfield` implements that instead.
- The protected `Button(int,int,int,int,Component,OnPress,CreateNarration)` constructor gained the
  narration supplier; the platform `Button` passes `DEFAULT_NARRATION`.

### Misc

- `SoundEvent`'s constructor is private; use `SoundEvent.createVariableRangeEvent(ResourceLocation)`
  (`Variables#playSound`).
- `DamageSource.GENERIC` still exists in 1.19.3 (it is removed in 1.19.4), so the armour-enchantment
  path is unchanged.
- `ServerData` still stores the favicon as base64 (`getIconB64`) and still has no online/max
  counters — only the sampled `playerList`, so `getPopulationInfo()` is unchanged from 1.19.2.
- `ClientboundCustomPayloadPacket#getIdentifier/getData` unchanged (typed payloads are 1.20.2).

## Stubs / degraded behaviour

| Method | 1.19.3 behaviour |
|---|---|
| `getFPS()` | Parses the leading field of `Minecraft#fpsString` (no `getFps()` before 1.20). |
| `setSession(name, uuid, token, type)` | Replaces the private `Minecraft#user` field by reflected type; `User` gained a `Type` argument, already passed. |
| `ServerData#getPopulationInfo()` | Sampled player-list size only (no online/max counters in 1.19.3). |
| `getDummyPotionEffects()` | Synthetic HUD preview, same as every other port. |
| `resetServer()` / `updateScaledResolution()` | No cached state to reset — intentional no-ops. |
| Item icons | Pose-less item renderer: correct for absolute-coordinate HUD callers, ignores pose transforms. |

## Verification performed

- `gradle --no-daemon build` green from a clean `build/classes`.
- Jar contains remapped classes, refmap (14 mixin classes / 35 mappings), 8 nested runtime jars
  (DiscordIPC, jacob, json, json-simple, junixsocket-common, junixsocket-native-common,
  sqlite-jdbc, netty-codec-http) and the `5zig-Version` / `Minecraft-Version` / `5zig-Build`
  manifest attributes; `fabric.mod.json` depends on `~1.19.3`.
- `tools/check_mixins.py` resolves every `@Inject` name+descriptor against the 1.19.3 named jar:
  **38/38 present**.
- `tools/LinkCheck.java` (ASM) resolves every class/method/field reference in the compiled mod
  against the 1.19.3 named jar + Fabric API + libraries: **1494 classes, 65 301 references,
  0 unresolved**.
- No `com.mojang.math.*` references remain in the platform sources.

## Known gaps / not verified

- **No client run** (task rule): nothing here is pixel-verified. HUD, second chat, widget themes,
  capes and the mod menu are verified only by API/link compatibility.
- `MixinCommonMinecraft` from `common/` is compiled in but not listed in `mixins.the5zigmod.json`
  (annotation-processor warning only), as in every other port.
- Plugin channels use the 1.19.3 `ResourceLocation`-keyed `ServerboundCustomPayloadPacket`.
