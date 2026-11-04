# 1.19.2 Fabric port notes

First **pre-1.20** target of the repo. Built with `gradle-8.10.2` / JDK 17, `--no-daemon`, Java 17
(class file 61). Loom 1.7.4 accepts 1.19.2 without pinning an older Loom.

| Piece | Version |
|---|---|
| Minecraft | 1.19.2 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.77.0+1.19.2 |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.19.2-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.19.2-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained)

## Decompiled reference

`mc-src/1.19.2/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`),
decompiled from `versions/1.19.2-fabric/.gradle/loom-cache/minecraftMaven/…/minecraft-merged-fa35131a1a-…jar`.

## The one real design decision: no `GuiGraphics`

1.19.2 has no `GuiGraphics`. Everything is drawn from a `PoseStack` threaded through
`Screen#render(PoseStack,int,int,float)`, `Widget#render(PoseStack,…)`, `Font#draw(PoseStack,…)`,
`GuiComponent.blit(PoseStack,…)` and `RenderSystem` calls.

The 1.20 platform sources call `graphics.fill/blit/drawString/…` everywhere. Instead of rewriting
~70 call sites into static helpers, `platform/1.19.2/eu/the5zig/mod/util/GuiContext.java` re-implements
that drawing surface on top of the frame's `PoseStack`:

- `fill`, `fillGradient` (Tesselator `POSITION_COLOR` quad; the 1.20 `RenderType` argument is accepted
  and ignored because 1.19.2 GUI geometry is immediate),
- `blit` in all five 1.20 overloads, `blitRepeating`, `blitNineSliced`, sprite blit,
- `drawString`/`drawCenteredString`/`drawWordWrap` through `Font#draw(Shadow)`,
- `enableScissor`/`disableScissor` (1.19.2 `GuiComponent` helpers are already GUI-scale aware),
- `setColor` → `RenderSystem.setShaderColor`,
- `renderComponentTooltip` / `renderComponentHoverEffect(SHOW_TEXT)` through the current `Screen`.

`RenderContext` now stores a `GuiContext` built from the frame's `PoseStack`; the mixins and
`GuiHandle` publish the stack (not a graphics object). All platform sources keep the 1.20 call shape,
so `platform/1.19.2` is a small delta from `platform/1.20.1` instead of a rewrite.

## API changes handled (1.20.1 → 1.19.2)

### GUI / rendering

- `Screen#render(PoseStack,…)`, `renderBackground(PoseStack)`, `ChatComponent#render(PoseStack,int)`
  (no mouse coords), `ChatComponent#addMessage(Component, MessageSignature, GuiMessageTag)` present.
- `AbstractWidget` has **no** `getX()/getY()/setX()/setY()` (added in 1.20) → provided in `Button`
  and `Textfield` for the `IButton`/`ITextfield` contracts; `isHovered()` likewise.
- Widgets override `renderButton(PoseStack,int,int,float)`; `renderWidget` does not exist.
- `AbstractWidget#render` also draws the button sprite itself; our `Button`/`Slider` keep the 5zig
  look by drawing `blitNineSliced` + `drawLabel` explicitly, like the 1.20.1 code did.
- `NarratableEntry#updateNarration` (not `updateWidgetNarration`).
- `Toast#render(PoseStack, ToastComponent, long)`.
- `RenderType` has no GUI types in 1.19.2 (`guiOverlay()`/`gui()` do not exist).
- Item icons come from `ItemRenderer#renderAndDecorateItem` / `renderGuiItemDecorations`, which draw
  in absolute GUI coordinates through the model-view stack — 1.19.2 items are **not** affected by the
  `PoseStack`, so they must be drawn at final screen coordinates (all current callers do).
- `Gui#renderHotbar(float, PoseStack)`, `Gui#setOverlayMessage(Component, boolean)` — unchanged
  targets, new descriptors.

### Client / world API

- authlib 3: `GameProfile` is a bean (`getId()`, `getName()`), `Minecraft#getUser()` returns
  `net.minecraft.client.User` with `getAccessToken()/getName()/getGameProfile()`; `User.Type` exists.
- `Registry.BLOCK` / `Registry.ITEM` instead of `BuiltInRegistries` (registry rewrite is 1.19.3).
- `Minecraft#getFps()` does not exist → `IVariables#getFPS()` parses the leading field of
  `Minecraft#fpsString` (`"120 fps T: …"`).
- Chat is signed and sent inside `LocalPlayer` (`chatSigned`/`commandSigned`); there is no
  `ClientPacketListener#sendChat` yet (that is 1.19.3). The outgoing-chat mixin lives in the new
  `MixinLocalPlayer` and hooks both entry points, restoring the leading `/` for commands.
- `ConnectScreen.startConnecting(Screen, Minecraft, ServerAddress, ServerData)` (no quick-play flag).
- `SignEditScreen` (not `AbstractSignEditScreen`), `new SoundEvent(ResourceLocation)` (no
  `createVariableRangeEvent`), `BlockPos` constructor (no `BlockPos.containing`),
  `DamageSource.GENERIC` (no `level.damageSources()`), `MobEffectUtil.formatDuration` returns
  `String`, `MobEffect.byId/getId` still exist.
- `ServerData` carries `name/ip/motd/ping/playerList` and a base64 favicon; there are no
  online/max counters (see gaps).

## IVariables coverage

`Variables` implements **all 162 methods** of `eu.the5zig.mod.util.IVariables` (162 `@Override`s,
`javac` enforces the full contract). No method throws `UnsupportedOperationException` on a hot path;
the only throw is the defensive fallback at the end of `setSession` (unreachable while
`Minecraft#user` exists).

Fully real (Minecraft state read live): text/width/split, widget factories, chat (2nd chat, chat box
text, chat field), sounds, server data/pinger, FOV/smooth camera, screen switching/joining servers,
client accessors (user, profile, session, proxy, FPS source, window, scaled resolution), player
position/rotation/chunk, target block/entity ray trace, biome/light/entity count, food/health/armor/
air/resistance, potion effects, items in hand/armor/hotbar + counts, HUD helpers, plugin payloads,
resource/texture handling (dynamic images, capes), scoreboard, battery (OSHI), key dispatch,
reflective session replacement, chat components with click/hover actions.

Degraded / approximated (documented, not stubbed):

| Method | 1.19.2 behaviour |
|---|---|
| `getFPS()` | Parses `Minecraft#fpsString` instead of a counter method; correct value, string form. |
| `setSession(name, uuid, token, type)` | Replaces `Minecraft#user` by reflected field of type `User`; works, but is the only reflection-dependent path. |
| `ServerData#getPopulationInfo()` | 1.19.2 has no online/max counters — returns the sampled player-list size (or `""`). |
| `getDummyPotionEffects()` | Intentionally synthetic (HUD preview), as in every other port. |
| `resetServer()` / `updateScaledResolution()` | No cached state in 1.19.2 (values are read from `ClientPacketListener`/`Window` live), so they are no-ops by design. |
| Item icon drawing | `WrappedItemStack#render` uses the 1.19.2 item renderer, which ignores the `PoseStack`; correct for the absolute-coordinate callers, wrong if someone draws items inside a scaled/translated pose. |

## Verification performed

- `gradle --no-daemon build` green; jar contains remapped classes, refmap, 8 nested runtime jars
  (DiscordIPC, jacob, json, json-simple, junixsocket-common, junixsocket-native-common, sqlite-jdbc,
  netty-codec-http) and the `5zig-Version` / `Minecraft-Version` / `5zig-Build` manifest attributes.
- `tools/check_mixins.py` resolves every `@Inject` name+descriptor against the 1.19.2 named jar:
  **38/38 present** (15 mixin classes, incl. the new `MixinLocalPlayer`).
- `tools/LinkCheck.java` (ASM) resolves every class/method/field reference in the compiled mod against
  the 1.19.2 named jar + Fabric API + libraries: **1495 classes, 65 300 references, 0 unresolved**
  (would catch `NoSuchMethodError`/`NoSuchFieldError`/`NoClassDefFoundError`).
- The remapped jar contains no reference to 1.20-only symbols (`GuiGraphics`, `BuiltInRegistries`,
  `org.joml`, `AbstractSignEditScreen`, `createVariableRangeEvent`).

## Known gaps / not verified

- **No client run** (task rule), so nothing here is pixel-verified. HUD, second chat, widget themes,
  capes and the mod menu are verified only by API/link compatibility.
- Item icons ignore `PoseStack` transforms in 1.19.2 (see table); fine for today's callers.
- The `MixinCommonMinecraft` ("clickMouse") mixin from `common/` is compiled in but **not** listed in
  `mixins.the5zigmod.json`; `MixinMinecraft#startAttack` covers CPS. The unused class only produces an
  annotation-processor warning.
- `platform/1.19.2` is the E3 anchor (1.18–1.19.2); 1.19/1.19.1/1.18.2 would reuse it with a version
  bump once their jars are checked.
- Plugin channels use the 1.19.2 `ResourceLocation`-keyed `ServerboundCustomPayloadPacket`, which is
  the era-correct form; 1.19.3+ typed payloads are not relevant here.
