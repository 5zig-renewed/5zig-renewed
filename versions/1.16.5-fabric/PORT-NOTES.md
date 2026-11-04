# 1.16.5 Fabric port notes

Oldest target in the repository: **Java 8**, `PoseStack` (Mojang's own name for what Yarn calls
`MatrixStack`), no `GuiGraphics`, no shader objects, immediate-mode `Tesselator`/`BufferBuilder`
GUI drawing.

| Piece | Version |
|---|---|
| Minecraft | 1.16.5 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Gradle | 8.10.2, JDK 17 (compiling with `--release 8`) |
| Fabric Loader | 0.14.24 |
| Fabric API | 0.42.0+1.16 |
| Mixin compatibility level | `JAVA_8` |
| Output class files | major version **52** (Java 8) |

Build:
```
cd versions/1.16.5-fabric
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build
```
Jar: `build/libs/5zig-renewed-1.16.5-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained).

## Decompiled reference

`mc-src/1.16.5/out` — 3,400 files, 65 MB, produced with

```
java -jar ~/.tools/decomp/vineflower-1.12.0.jar -dgs=1 --only=net/minecraft -Xmx2G \
  <loom named jar> mc-src/1.16.5/out
```

from `versions/1.16.5-fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/…`.

## What actually made this port tractable

The community names for 1.16.5 (`MatrixStack`, `Screen`, `TextFieldWidget`, `GuiIngame`, …) are
**Yarn** names. Mojang's official mappings for 1.16.5 keep the modern internal names:

- `com.mojang.blaze3d.vertex.PoseStack` (not `MatrixStack`),
- `net.minecraft.client.gui.GuiComponent`, `...gui.screens.Screen`, `...gui.components.Button`,
  `...gui.components.EditBox`, `...gui.components.ChatComponent`,
- `net.minecraft.world.effect.MobEffectInstance`, `net.minecraft.core.Registry`, `...core.BlockPos`.

So the 1.19.2 platform carried over with a **smaller** delta than the version number suggests: no
package moves, only method/signature changes. The real work was in the rendering backend and in the
handful of widgets/chat paths that changed between 1.16.5 and 1.19.2.

## Rendering: `GuiContext` re-implemented on 1.16.5 primitives

`platform/1.16.5/eu/the5zig/mod/util/GuiContext.java` keeps the exact 1.19.2/1.20 public surface
(`fill`, `fillGradient`, the five `blit` overloads, `blitRepeating`, `blitNineSliced`, sprite blit,
`drawString`, `drawCenteredString`, `drawWordWrap`, `setColor`, `enableScissor`/`disableScissor`,
`renderComponentTooltip`, `renderComponentHoverEffect`, `pose()`), but every implementation is
1.16.5-native:

| 1.19.2 call | 1.16.5 replacement |
|---|---|
| `RenderSystem.setShader(GameRenderer::getPositionColorShader)` | does not exist; fixed-function state only |
| `buffer.begin(VertexFormat.Mode.QUADS, …)` | `buffer.begin(7, DefaultVertexFormat.POSITION_COLOR)` (raw `GL_QUADS`) |
| `BufferUploader.drawWithShader(buffer.end())` | `buffer.end(); BufferUploader.end(buffer);` |
| `RenderSystem.setShaderColor(...)` | `RenderSystem.color4f(...)` |
| `RenderSystem.setShaderTexture(0, rl)` | `Minecraft#getTextureManager().bind(rl)` |
| `GuiComponent.enableScissor/disableScissor` | absent; rectangle converted to window pixels, `y` flipped, GUI scale applied, then `RenderSystem.enableScissor` |
| `fillGradient` helper | `GuiComponent#fillGradient` is protected; the quad is built by hand with `shadeModel(7425)` for the interpolation |

`blit` maps onto the five `GuiComponent.blit` overloads that 1.16.5 does have, so the nine-slice and
tiling helpers are unchanged from 1.19.2 and still address `textures/gui/widgets.png` in its 256×256
sheet space.

## API changes handled (1.19.2 → 1.16.5)

### Text / components

- `Component.literal/empty/translatable` do not exist → `new TextComponent(...)`,
  `new TranslatableComponent(...)` behind `eu.the5zig.mod.util.Texts`.
- `ChatFormatting#getChar()` does not exist → `ChatFormatting#toString()` already returns `"§c"`.
- `String#repeat` is Java 11 → local `repeat(char,int)` helper in `Textfield`.
- `Font` API is already the modern one (`drawShadow(PoseStack, …)`, `width`, `split`).

### Chat

- `ChatComponent#addMessage(Component)` — one argument (no `MessageSignature`, no `GuiMessageTag`).
- `ChatComponent#scrollChat(double)` (1.19.2 takes an `int`).
- Outgoing chat: `LocalPlayer#chat(String)` (the signed `chatSigned`/`commandSigned` pair is 1.19+,
  and `ClientPacketListener#sendChat` never exists in 1.16.5). Commands arrive with their leading
  `/`, so `onSendChatMessage` receives what it expects.

### Widgets / screens

- `AbstractWidget` has **no** `getX()/getY()/setX()/setY()`, no `isHoveredOrFocused()`, no
  `updateNarration(NarrationElementOutput)` — the platform's `Button`/`Textfield` already provided
  their own accessors; hover state is read/written through the protected `isHovered` field, and the
  narration override is gone (there is no narration API in 1.16.5).
- `Screen` uses `addButton`/`addWidget` and renders its `buttons` list; widgets still override
  `renderButton(PoseStack,int,int,float)`.
- `Screen#renderBackground(PoseStack)` and `(PoseStack,int)` both exist; the mixin cancels the
  one-argument entry point, which covers both.
- `EffectRenderingInventoryScreen#canSeeEffects()` does not exist → the protected `doRenderEffects`
  field is exposed through `5zig.accesswidener`, which Loom remaps to
  `class_485 field_2900 Z` in the shipped jar.

### Client / world

- `Minecraft#startAttack()` is `void` in 1.16.5 (it returns `boolean` from 1.19).
- `Minecraft#fpsString` still begins with `"<n> fps …"` → `getFPS()` parses the leading token.
- `Options#fov` is a plain `double` field, `Options#graphicsMode` a plain `GraphicsStatus` field;
  there is no `OptionInstance` accessor.
- `AbstractClientPlayer#getAbilities()` → public `abilities` field.
- `ItemStack#is(Item)` → `getItem() == Items.BOW`.
- `Entity#getYRot()/getXRot()` → public `yRot`/`xRot`.
- `LocalPlayer#getInventory()` → public `inventory` field (`inventory.items`, `inventory.selected`,
  `inventory.getArmor(slot)`).
- `MultiPlayerGameMode#useItemOn(LocalPlayer, ClientLevel, InteractionHand, BlockHitResult)` and
  `useItem(Player, Level, InteractionHand)` take the level explicitly.
- `ConnectScreen` connects from its constructor; there is no `startConnecting`. `ServerAddress` lives
  in `net.minecraft.client.multiplayer` and has a private constructor.
- `Level#getBiome(BlockPos)` returns a `Biome`, not a `Holder<Biome>`; the registry key comes from
  `level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getKey(biome)`.
- `User` constructor is `(name, uuid, accessToken, typeName)`; `Minecraft#user` is a `final` field,
  so `setSession` keeps the reflective field replacement (works on Java 8 runtimes).
- `TextureManager#getTexture(ResourceLocation)` only — there is no “existing or null” two-argument
  lookup, so an unregistered cape/frame path resolves to the missing texture and the `instanceof`
  check rejects it.
- `ServerData.ping` is a `long`; the favicon is still base64 (`getIconB64`).

### Battery (`SystemPowerStatus`)

Minecraft 1.16.5 ships **oshi-core 1.1**, which has no `PowerSource` API
(`HardwareAbstractionLayer#getPowerSources()` arrives in OSHI 3.x). Rather than nesting a second,
conflicting OSHI into the mod jar, the battery HUD reads the OS directly:

- Linux: `/sys/class/power_supply/BAT*/capacity`, `status`, `time_to_empty_now`,
- macOS: `pmset -g batt`,
- **Windows: not implemented** → returns `null`, the HUD shows its “100 %” placeholder.

## Mixins

15 mixins, all listed in `mixins.the5zigmod.json` (compatibility level `JAVA_8`):

| Mixin | 1.16.5 target | Note |
|---|---|---|
| `MixinAbstractClientPlayer` | `getFieldOfViewModifier()F`, `getCloakTextureLocation()L…;` | static FOV + capes |
| `MixinChatComponent` | `render(PoseStack,I)V`, `addMessage(Component)V`, `clearMessages(Z)V`, `scrollChat(D)V` | second chat, filters, time prefix |
| `MixinChatScreen` | `render(PoseStack,IIF)V`, `keyPressed(III)Z`, `mouseClicked(DDI)Z` | second chat input |
| `MixinClientPacketListener` | `handleCustomPayload`, `handleTabListCustomisation`, `handleContainerSetSlot`, `handleMovePlayer`, `handleChangeDifficulty` | plugin channels, tab list, slot mirror, teleport, server-ready |
| `MixinConnection` | `send(Packet)V`, `channelRead0(ChannelHandlerContext,Packet)V` | packet tap |
| `MixinDeathScreen` | `init()V` | death screen hook |
| `MixinDisconnectedScreen` | `init()V`, `render(PoseStack,IIF)V` | disconnect info |
| `MixinGameRenderer` | `pick(F)V` | combat range |
| `MixinGui` | `render(PoseStack,F)V` ×2, `renderHotbar(F,PoseStack)V`, `tick()V`, `setOverlayMessage(Component,Z)V` | HUD frame + action bar |
| `MixinKeyboardHandler` | `keyPress(JIIII)V` | key queue |
| `MixinLocalPlayer` | `chat(Ljava/lang/String;)V` | outgoing chat filter |
| `MixinMinecraft` | `setScreen(L…Screen;)V`, `startAttack()V`, `startUseItem()V` | textfield flush, CPS |
| `MixinPlayerTabOverlay` | `renderPingIcon(PoseStack,III,PlayerInfo)V` | numeric ping |
| `MixinScreen` | `render(PoseStack,IIF)V` ×2, `renderBackground(PoseStack)V`, `hasControlDown()Z` | context bracket, transparent background, mac Ctrl/Cmd |
| `MixinTitleScreen` | `render(PoseStack,IIF)V` | version string |

Verified with `tools/check_mixins.py` (descriptors resolved against the 1.16.5 named jar):
**37/37 injection targets present**. The remapped jar's refmap lists all 15 classes and 34
named→intermediary entries.

## IVariables coverage

`Variables` implements **all 162 methods** of `eu.the5zig.mod.util.IVariables`; `javac` enforces the
full contract (162 `@Override`s). Every method reads live 1.16.5 state — there is no stubbed HUD
data. The only `throw` is the defensive “no user field” fallback at the end of `setSession`, which is
unreachable while `Minecraft#user` exists.

Fully real (read live from the client): text measuring/drawing, widget factories, chat box + second
chat, sounds, server data/pinger, FOV/smooth camera, screen switching/joining servers, session and
profile accessors, proxy, FPS source, window/scaled resolution, player position/rotation/chunk,
target block/entity ray trace, biome/light/entity count, food/health/armor/air/resistance, potion
effects, items in hand/armor/hotbar + counts, HUD helpers, plugin payloads, resource/texture
handling (dynamic images, capes), scoreboard, battery, key dispatch, session replacement, chat
components with click/hover actions.

Degraded / approximated (all documented in code):

| Method | 1.16.5 behaviour |
|---|---|
| `getFPS()` | Parses the leading field of `Minecraft#fpsString` (`"120 fps T: …"`); correct value, string form. |
| `setSession(name, uuid, token, type)` | Reflective replacement of the `final Minecraft#user` field; works, and is the only reflection-dependent path. |
| `getBatteryStatus()` | Linux sysfs / macOS `pmset`; **`null` on Windows** (oshi 1.1 has no battery API). |
| `enableRepeatEvents(boolean)` | No-op: 1.16.5 has no global GLFW repeat toggle. |
| `resetServer()` | No-op: server data is read live from `Minecraft#getCurrentServer()`, nothing is cached. |
| `updateScaledResolution()` | No-op: `Window` always carries the current GUI scale, there is no cached `ScaledResolution`. |
| Item icon drawing | 1.16.5's `ItemRenderer` draws in absolute GUI coordinates and ignores the `PoseStack`; correct for the current callers (all draw at final coordinates), wrong if someone draws items inside a translated/scaled pose. |

So: **162/162 implemented, 156 exact, 6 documented approximations/no-ops** (the Windows battery
branch shares the same method as the working Linux/macOS one).

## Verification performed

- `gradle --no-daemon build` green (Loom 1.7.4, Gradle 8.10.2, JDK 17 → `--release 8`).
- Jar: 18.8 MB, class major version **52**, `5zig-Version` / `Minecraft-Version: 1.16.5` /
  `5zig-Build` manifest attributes, 8 nested runtime jars (DiscordIPC, jacob, json, json-simple,
  junixsocket-common, junixsocket-native-common, sqlite-jdbc, netty-codec-http).
- Access widener remapped to intermediary in the shipped jar (`class_485 field_2900 Z`).
- `tools/check_mixins.py`: 37/37 mixin targets resolve.
- `tools/LinkCheck.java` (ASM): every class/method/field reference in the compiled mod resolved
  against the 1.16.5 named jar + Fabric API + libraries — **1505 classes, 70,794 references,
  0 unresolved** (would catch `NoSuchMethodError` / `NoSuchFieldError` / `NoClassDefFoundError`).
- The remapped jar contains no 1.17+ symbols (`GuiGraphics`, `setShader`, `BuiltInRegistries`).

## Known gaps / not verified

- **No client run** (task rule), so nothing is pixel-verified. HUD, second chat, widget themes,
  capes and the mod menu are verified only by compile + link + mixin resolution.
- The 5zig overlay is drawn at the head of `Gui#render`, i.e. underneath the vanilla hotbar/chat —
  same ordering as the 1.19.2 port.
- Item icons ignore `PoseStack` transforms (see table above).
- `common/eu/the5zig/mod/mixin/MixinCommonMinecraft` (the old `clickMouse` hook) is still compiled
  but is **not** listed in the mixin config; it only produces an annotation-processor warning.
- Windows battery reporting is absent (documented above); Linux and macOS are implemented.
