# 1.19 Fabric port notes

**Base release of the 1.19 line** and the first target older than the client-verified 1.19.2
platform. Ported from the finished `platform/1.19.1` and reconciled against
`mc-src/1.19/out` (vineflower 1.12.0, `-dgs=1 --only=net/minecraft`,
**4234 sources**). Built with Gradle 8.10.2 / JDK 17, `--no-daemon`, Java 17 (class file 61).

| Piece | Version |
|---|---|
| Minecraft | 1.19 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.58.0+1.19 |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.19-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.19-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained)

Decompiled reference: `mc-src/1.19/out`, decompiled from
`versions/1.19-fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-e0e16841ac/1.19-loom.mappings.1_19.layered+hash.2198-v2/…jar`.

## Delta: 1.19 is pre-chat-signing

Against 1.19.2 the tree has **4234 vs 4284 classes: 50 net fewer** (5 removed, 55 added). The 55
additions are the whole signed-chat/reporting feature set that arrived in 1.19.1
(`client/multiplayer/chat/**` — `ChatListener`, `ChatLog`, `ChatTrustLevel`, `report/**`,
`network/chat/{SignedMessage*,LastSeenMessages*,OutgoingPlayerChatMessage,ChatPreviewCache,…}`,
`ClientboundDeleteChatPacket`, `ClientboundPlayerChatHeaderPacket`, `ServerboundChatAckPacket`, …).
The 5 classes that exist only in 1.19 are its predecessors:
`client/gui/chat/{ChatListener,NarratorChatListener,OverlayChatListener,StandardChatListener}` and
`network/chat/ChatDecoration`.

Only three things in this platform had to change.

### 1. Chat receive — single-argument `addMessage` (the real delta)

1.19 has no `GuiMessageTag` and no `MessageSignature` parameter:

```java
// 1.19
public void addMessage(Component component)          // single sink
public void enqueueMessage(Component component)      // deferred queue (player chat)
// 1.19.1+
public void addMessage(Component, @Nullable MessageSignature, @Nullable GuiMessageTag)
```

Player chat arrives through `enqueueMessage` → `processPendingMessages` → `addMessage`, so hooking
`addMessage(Component)` still sees every displayed line (system chat, player chat, the local echo and
messages the mod adds itself). `MixinChatComponent` now injects
`addMessage(Lnet/minecraft/network/chat/Component;)V` for both the chat filter (`@Inject`, cancellable)
and the time prefix (`@ModifyVariable`, `index = 1`). `Gui#handleSystemChat/handlePlayerChat` still
exist in 1.19 and both fan out to `ChatListener`s that end in that same sink, so they need no hook of
their own.

### 2. Chat send — `LocalPlayer#chat`/`command`, not `chatSigned`/`commandSigned`

1.19.0 signs and sends inside `LocalPlayer` but under the plain names:

```java
public void chat(String)                -> chat(String, @Nullable Component)
public void command(String)             -> command(String, @Nullable Component)
```

`MixinLocalPlayer` hooks the two-argument terminal senders (`chat(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V`,
`command(...)`), and `Variables#sendMessage` calls `player.chat(message, null)`. The 1.19.1 rename to
`chatSigned`/`commandSigned`, and the 1.19.3 move to `ClientPacketListener#sendChat/sendCommand`, are
both later releases; `MixinLocalPlayer` remains the right place for 1.19.

### 3. GUI scissor — `GuiComponent.enableScissor` does not exist yet

`GuiComponent#enableScissor(int,int,int,int)` / `disableScissor()` were **added in 1.19.1**; 1.19.0
only has `RenderSystem.enableScissor(x, y, width, height)` in window space. `GuiContext` now performs
the GUI-scale conversion and the bottom-left y-flip itself (window height − `y2 * scale`), clamped at
zero, and forwards to `RenderSystem`. Everything else in the rendering facade — `PoseStack`,
`GuiComponent.fill/blit`, `Font#drawShadow`, pose-less `ItemRenderer` — is identical to 1.19.2.

### Everything else

The remaining 1.19-vs-1.19.2 differences are invisible to this platform: `ChatScreen#renderChatPreview(PoseStack)`
exists here (chat preview was removed in 1.19.2) but is not used; `ServerData` still stores the favicon
as base64 (`getIconB64`) and has no online/max counters; `ClientPacketListener` has the same five hook
targets (`handleCustomPayload`, `handleTabListCustomisation`, `handleContainerSetSlot`,
`handleMovePlayer`, `handleChangeDifficulty`); `SharedConstants.filterText(String)`, `Minecraft#fpsString`,
`DamageSource.GENERIC`, `SoundEvent` construction and the `Registry` statics are unchanged from 1.19.2.
Comments that named 1.19.2 as "this version" were retargeted to 1.19.

## Stubs / degraded behaviour

| Method | 1.19 behaviour |
|---|---|
| `getFPS()` | Parses the leading field of `Minecraft#fpsString` (no `getFps()` before 1.20). |
| `setSession(name, uuid, token, type)` | Replaces the private `Minecraft#user` field by reflected type; the 1.19 `User` constructor already takes the `Type`. |
| `getDummyPotionEffects()` | Synthetic HUD preview, same as every other port. |
| `resetServer()` / `updateScaledResolution()` | No cached state to reset — intentional no-ops. |
| `ServerData#getPopulationInfo()` | Sampled player-list size only (no online/max counters before 1.19.4). |
| Item icons | Pose-less `ItemRenderer#renderAndDecorateItem(ItemStack,int,int)` — absolute HUD coordinates, pose transforms ignored. |
| Chat preview | Not rendered; 1.19's preview UI is client-vanilla and the mod does not hook it. |

## Verification performed

- `gradle --no-daemon build` green from a clean `rm -rf build`.
- Jar contains remapped classes (major version 61), refmap (15 mixin classes / 35 mappings),
  8 nested runtime jars (DiscordIPC, jacob, json, json-simple, junixsocket-common,
  junixsocket-native-common, sqlite-jdbc, netty-codec-http) and the `5zig-Version` /
  `Minecraft-Version: 1.19` / `5zig-Build` manifest attributes; `fabric.mod.json` depends on `~1.19`
  and lists all 8 nested jars.
- `tools/check_mixins.py` resolves every `@Inject`/`@ModifyVariable` name+descriptor against the 1.19
  named jar: **38/38 present** (including the rewritten `addMessage(Component)V` and
  `chat(String,Component)V` / `command(String,Component)V` targets).
- `tools/LinkCheck.java` (ASM) resolves every class/method/field reference in the compiled mod
  against the 1.19 named jar + Fabric API + libraries: **1495 classes, 65 309 references,
  0 unresolved**.

## Errors encountered (and fixes)

Three compile errors on the first build of the copied tree, plus two silent mixin-target errors that
the compiler cannot see:

| Error | Fix |
|---|---|
| `cannot find symbol: GuiMessageTag` / `MessageSignature` in `MixinChatComponent` | descriptor changed to `addMessage(Lnet/minecraft/network/chat/Component;)V` (both `@Inject` and `@ModifyVariable`). |
| `cannot find symbol: method enableScissor(int,int,int,int)` on `GuiComponent` | `GuiContext` converts GUI coordinates to window space and calls `RenderSystem.enableScissor/disableScissor` directly. |
| `cannot find symbol: method chatSigned(String,<null>)` in `Variables#sendMessage` | `player.chat(message, null)` (1.19's pre-signing name). |
| silent: `MixinLocalPlayer` used `chatSigned`/`commandSigned` (annotation strings, not compile-checked) | retargeted to `chat`/`command`; `tools/check_mixins.py` now proves all 38 targets resolve. |
| silent: `MixinChatComponent`'s two injections still named the 3-arg `addMessage` | same descriptor fix as above; verified via the refmap (`method_1812` for 1.19 vs `method_44811` for 1.19.1). |

## Known gaps / not verified

- **No client run** (task rule): HUD, second chat, widget themes, capes and the mod menu are verified
  only by API/link compatibility, not visually.
- `MixinCommonMinecraft` from `common/` is compiled in but not listed in `mixins.the5zigmod.json`
  (annotation-processor warning only), as in every other port.
- 1.19.0's chat has no signing, so the "chat not secure" tag path that 1.19.1+ exposes never applies;
  the platform does not surface chat trust state on any version.
