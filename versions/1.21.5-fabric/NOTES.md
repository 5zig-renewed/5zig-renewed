# 1.21.5 Fabric port — notes

Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon clean build`
Artifact: `build/libs/5zig-renewed-1.21.5-fabric-4.0.0-alpha.1.jar` (3.6 MB)

## Toolchain

| Piece | Version | Why |
|---|---|---|
| Gradle | 8.14.3 | last 8.x line; Loom 1.13 requires 8.14 |
| Loom | 1.13.4 | newest Loom that still runs on JVM 21 |
| Fabric Loader | 0.17.3 | same loader the 1.21.11 project uses |
| Fabric API | 0.128.2+1.21.5 | first API build for 1.21.5 |
| Mappings | officialMojangMappings | |
| Java | 21 | `options.release = 21` |

Ground truth: decompiled merged jar (`vineflower 1.12.0 -dgs=1`) at
`~/work/mc-src/1.21.5/out`, produced from the Loom-remapped
`minecraft-merged-1.21.5-…-v2.jar`. Every signature below was checked there, not assumed.

## Which base this platform was ported from, and why

The plan was to copy `platform/1.21.11`, but 1.21.11 is the *end* of era E9 and 1.21.5 is its
*start*: three of the four E9 breaks had not landed yet in 1.21.5.

| Area | 1.21.5 | 1.21.11 |
|---|---|---|
| Resource locations | `net.minecraft.resources.ResourceLocation` (still exists) | renamed `Identifier` |
| GUI matrices | `PoseStack` (3-D) | 2-D JOML `Matrix3x2fStack` |
| Input callbacks | `mouseClicked(double,double,int)`, `keyPressed(int,int,int)`, `charTyped(char,int)` | `MouseButtonEvent` / `KeyEvent` / `CharacterEvent` records |
| Widget hook | `AbstractButton#renderWidget` | `#renderContents` |
| Death of global GL state | **already gone** (`RenderSystem.enableBlend`, `depthMask`, `setShaderColor`, `GlStateManager` in `blaze3d.opengl`) | same |
| GUI blit | **already** `blit(Function<ResourceLocation,RenderType>, …)` / `RenderType::guiTextured` | `RenderPipelines.GUI_TEXTURED` |
| authlib | 6 (`GameProfile#getId/#getName`, `User(...,Type)`) | 7 (record `id()/name()`, 5-arg `User`) |

Porting 1.21.11 downwards would have meant reverting ~1,000 lines of API shape. Instead the platform
is `platform/1.21.4` (era E8, same GUI generation) rebased onto 1.21.5, then the E9-start deltas
below were applied — the result differs from the 1.21.4 platform in 11 files only:

* `Variables` — E9 item/scoreboard/effect/chat-event changes (see below).
* `GLUtilHandle` — the whole state API is gone, so blend/depth/colour become no-ops.
* `Button`, `Slider` — `RenderSystem.enableBlend/enableDepthTest` gone; widget tint is ARGB.
* `GuiCompat` — lost the blend toggles; per-tile ARGB tint kept.
* `BoundTexture` — `RenderSystem.setShaderTexture(int, ResourceLocation)` now wants a `GpuTexture`.
* `SimpleTexture` — GPU textures are allocated at upload time (see below).
* `ResourceManager` — cape texture cache + vanilla-typed frame textures.
* `PluginMessages` — 1.21.5 wording only.
* `MixinChatComponent`, `MixinAbstractClientPlayer` — target signatures (`render(GuiGraphics,int,int,int,boolean)`, `getFieldOfViewModifier(boolean,float)`).
* `HudFrame` — no diagnostic probe (see "HUD").

## Verified 1.21.5 API breaks against 1.21.4

* `RenderSystem`: `enableBlend`, `disableBlend`, `enableDepthTest`, `disableDepthTest`, `depthMask`,
  `blendFuncSeparate`, `setShaderColor`, `setShaderTexture(int, ResourceLocation)` are gone;
  `GlStateManager` moved to `com.mojang.blaze3d.opengl` and only keeps `_clear(int)`.
* Textures: `AbstractTexture` owns a `GpuTexture` (no numeric ids, no `getId()`), `NativeImage#upload`
  is gone, `DynamicTexture` needs a label `Supplier<String>`; a `GpuTexture`'s size is fixed at
  creation.
* `MobEffects.DAMAGE_RESISTANCE` → `MobEffects.RESISTANCE`.
* `Inventory#getArmor(int)`, `Inventory#items` and `Inventory#selected` are gone
  (`getItemBySlot`, `getNonEquipmentItems()`, `getSelectedSlot()/setSelectedSlot()`).
* `InteractionResult#shouldSwing()` → `Success#swingSource()` (`CLIENT` only, matching vanilla).
* `ClickEvent` and `HoverEvent` are sealed interfaces of records (`OpenUrl`, `OpenFile`,
  `RunCommand`, `SuggestCommand`, `ShowText`).
* `Scoreboard#listPlayerScores` returns `PlayerScoreEntry` records (`owner()`, `value()`).
* `ChatScreen(String)` only; `Gui#renderItemHotbar` still exists (private) inside
  `renderHotbarAndDecorations` — 1.21.9 is what made it public.
* `Toast` already uses `update(ToastManager,long)` + `getWantedVisibility()` +
  `render(GuiGraphics,Font,long)`.
* `User` has the six-argument constructor and `User.Type` again (authlib 6). `GameProfile` is a class
  with `getId()/getName()`.
* `Options#graphicsMode()` is still `GraphicsStatus` (`GraphicsPreset` is 1.21.9+).
* `ReceivingLevelScreen` still exists (folded into `LevelLoadingScreen` only in 1.21.9).

### Colour trap check (1.21.9+ strict ARGB)

`Font#adjustColor` in 1.21.5 still reads
`return (i & -67108864) == 0 ? ARGB.opaque(i) : i;`, and `Font#drawInternal` applies it to both the
`String` and `FormattedCharSequence` paths. Legacy `0xRRGGBB` colours therefore stay opaque in
1.21.5, so no `forceOpaque` shim is needed here. `fill`/`blit` take ARGB directly and the core
already passes ARGB for rectangles (`0x2200ffff`, …).

## Core overlay (`compat-src/`)

`core/` stays byte-identical. Minecraft 1.21.5 ships authlib 6, which (like 1.21.1) removed
`YggdrasilAuthenticationService`/`UserAuthentication`; only `AccountManager` and `PacketEncryption`
are affected. Both are taken from `platform/1.21.1` and placed in `compat-src/`;
`coreCompatTree` copies `core/` into `build/core-compat` without them. The 12-file authlib-7 /
commons-lang `Strings` overlay of the 1.21.11 project is not needed (commons-lang3 here predates the
`Strings` class).

## Deliberate degradations

| Area | What happens now |
|---|---|
| Enchantment damage bonus / protection | `EnchantmentHelper#getDamageProtection` needs a `ServerLevel`; the client cannot evaluate it, so the tooltip/resistance factor omits those terms (same as 1.20.1/1.21.1). |
| Free-form plugin channels | 1.21.5 decodes custom payloads into registered types before the listener runs. `PluginMessages` registers raw payload types for the three 5zig channels (`the5zigmod:5zig`, `:5zig_reg`, `:5zig_set`) and forwards inbound bytes, so server-side 5zig features keep working; any *other* channel is still discarded by vanilla. |
| 5zig capes | `getCloakTextureLocation` is gone; the cape is spliced into `PlayerSkin` (`MixinAbstractClientPlayer#getSkin`). |
| Animated capes | `Tickable` still exists in 1.21.5, so frames advance from `TextureManager#tick()` as on 1.21.1. |
| Legacy `icons.png` | gone since 1.21.4; the six hunger/saturation offsets the core uses map to `hud/food_*` GUI sprites. |

## HUD path

`MixinGui` brackets `Gui#render(GuiGraphics, DeltaTracker)` (HEAD/RETURN), publishes the
`GuiGraphics` through `RenderContext`, draws `GuiIngame#renderGameOverlay`, and hooks
`Gui#renderItemHotbar` TAIL for the hotbar add-ons. 1.21.5 renders the HUD through
`LayeredDraw`, but `Gui#render` is still the single entry point and `GuiGraphics` is still a
`PoseStack` + `MultiBufferSource` recorder, so the 1.21.1/1.21.4 draw model applies unchanged.
Unlike the 1.21.11 port there is **no diagnostic probe rect / println** in `HudFrame`.

## Verification done

* `gradle --no-daemon clean build` — BUILD SUCCESSFUL.
* `tools/check_mixins.py` — 35/35 `@Inject` targets resolve against the 1.21.5 jar
  (`renderItemHotbar`, `getFieldOfViewModifier(ZF)F`, `ChatComponent#render(GuiGraphics,IIIZ)`, …).
* Jar inspection: mixin annotations are statically remapped to intermediary
  (`method_1753`, `method_1759`, `method_3118`, `method_1805`, `method_52814`), so the build is
  production-remapped and needs no refmap (`Fabric-Loom-Mixin-Remap-Type: static`).
* `fabric.mod.json` in the jar: `minecraft ~1.21.5`, entrypoint `FiveZigFabric1215`
  (registers the payload types before `super.onInitializeClient()`).

## Not done / risks

* **No client run** (task rules). The HUD path is structurally identical to the client-verified
  1.21.4/1.21.1 platforms, but nothing here was seen on screen.
* Capes use a new `SimpleTexture` that allocates its `GpuTexture` at upload time (size is only known
  then); that path is compile-checked, not render-checked.
* 5zig's own libraries (DiscordIPC, jacob, json, sqlite-jdbc, netty-codec-http) are on the compile
  classpath but are **not** nested into the jar (`include`), same packaging as the 1.21.11 project.
  A production jar needs those `include(...)` lines or a launcher-provided copy.
