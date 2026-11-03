# 5zig Renewed — Minecraft 26.1.2 / Fabric

Status: **tree compiles, jar builds** (`build/libs/5zig-renewed-26.1.2-fabric-4.0.0-alpha.1.jar`).
The HUD render path is implemented for real; the remaining gaps are listed at the bottom.

## Toolchain (verified)

| Piece | Value |
|---|---|
| Minecraft | 26.1.2 (unobfuscated jar — **no `mappings` line at all**) |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.155.3+26.1.2 |
| Loom | `net.fabricmc.fabric-loom` **1.19.3** (requires Gradle ≥ 9.8) |
| Gradle | **9.8.2** (`~/.tools/gradle/gradle-9.8.2`; 9.3.1 is too old for Loom 1.19.3) |
| JDK | **25** (`/usr/lib/jvm/java-25-openjdk`); `options.release = 25` |
| Mixin | sponge-mixin 0.17.3+mixin.0.8.7 → supports `JAVA_25` compatibility level |

Build:

```bash
cd versions/26.1.2-fabric
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  ~/.tools/gradle/gradle-9.8.2/bin/gradle --no-daemon build
```

## 26.x API notes (the ones the port had to absorb)

* `GuiGraphics` → **`GuiGraphicsExtractor`**: draws are *recorded* into a `GuiRenderState`, not submitted.
  Same shapes though: `fill`, `fillGradient`, `text(Font, String|Component, x, y, colour, shadow)`,
  `centeredText`, `enableScissor`/`disableScissor`, `guiWidth/guiHeight`, `pose()`.
* `pose()` is a **`Matrix3x2fStack`** (2-D): `pushMatrix/translate(x,y)/scale(x,y)/popMatrix` — no `pushPose`, no z.
* `blit(...)` uses **normalised UVs**: `blit(texture, x0, y0, x1, y1, u0, u1, v0, v1)`.
* `ResourceLocation` → **`Identifier`**, and it is **`final`** (the 5zig wrapper can no longer extend it).
* Screen rendering: `render(...)` → **`extractRenderState(GuiGraphicsExtractor, int, int, float)`**,
  `renderBackground(g)` → `extractBackground(g, mx, my, partial)`, `renderDirtBackground` → `extractMenuBackground`.
* Input events are records: `keyPressed(KeyEvent)`, `charTyped(CharacterEvent)`, `mouseClicked(MouseButtonEvent, boolean)`,
  `mouseScrolled(double,double,double,double)`.
* `KeyMapping(String,int,String)` → `KeyMapping(String, InputConstants.Type.KEYSYM, int, KeyMapping.Category)`;
  categories are registered records. Fabric helper: `...client.keymapping.v1.KeyMappingHelper` (the old
  `keybinding.v1.KeyBindingHelper` package is gone).
* HUD entry point: `Gui#extractRenderState(GuiGraphicsExtractor, DeltaTracker)`; hotbar pass is the private
  `Gui#extractHotbarAndDecorations`.
* Chat: `ChatComponent#addMessage` → `addClientSystemMessage` / `addPlayerMessage`; rendering is
  `extractRenderState(extractor, Font, ticks, mouseX, mouseY, DisplayMode, focused)`.
* Toasts: `Toast#render` → `extractRenderState(extractor, Font, timeSinceLastVisible)` +
  `getWantedVisibility()`, and `Minecraft#getToastManager()`.
* GameProfile (authlib 7) is a **record**: `id()` / `name()`. `Agent`, `UserAuthentication` and the whole
  password/Yggdrasil client are gone → `core/…/account/AccountManager` needs a 26.x version (see overlay).
* `Minecraft#getWindow()` exists; `Window#getWindow()` (GLFW handle) → `Window#handle()`.
* `GlStateManager` moved to `com.mojang.blaze3d.opengl` and GUI drawing has no global shader colour /
  blend / depth state at all.

## Core overlay (core/ untouched)

`core/` is written against authlib 4 (`GameProfile#getId/#getName`, `UserAuthentication`) and commons-lang3
< 3.19. The 26.1.2 build therefore copies the core into `build/core-compat` with the affected files
filtered out (`coreCompatTree` task) and ships 26.x versions of them in `compat-src/`:

`account/AccountManager`, `chat/network/NetworkManager`, `chat/network/packets/PacketEncryption`,
`gui/GuiHypixelStats`, `gui/GuiNameHistory`, `manager/ChatFilterManager`, `manager/DataManager`,
`modules/items/server/ServerPing`, `server/octc/OCCListener`, `util/ChatHighlighting`,
`teamspeak/impl/ServerTabImpl`, `teamspeak/util/ImageManager`.

The changes are mechanical (`getId()→id()`, `getName()→name()`, `joinServer(uuid,…)`, qualify Guava
`Strings`) except `AccountManager`, whose password login cannot exist any more and now throws with a
precise message while the local/offline account list keeps working.

## Real vs. stubbed

**Real (MC API used, draws/acts):** `Variables` (all 162 `IVariables` methods except `sendCustomPayload`),
`HudFrame`, `RenderContext`, `GuiHandle`, `Button`, `Slider`, `StringButton`, `AudioButton`, `IconButton`,
`Textfield`, `PlaceholderTextfield`, `WrappedTextfield`, `ColorSelector`, `FileSelector`, `GuiList`,
`GuiListChat`, `Gui2ndChat`, `GuiChatLine`, `Overlay` (toast), `ResourceManager`, `WrappedItemStack`
(icon/count/durability/tooltip/food), `WrappedNetworkPlayerInfo`, `ServerData`, `ServerPinger`,
`Keybinding`, `Keybinds`, `LegacyText`, `ResourceLocation`, `TextureData`, `CoreState`, `SystemPowerStatus`,
`GLUtilHandle` (matrix/scissor parts).

**Partial / refused with a precise message:**

| Item | State |
|---|---|
| `Variables.sendCustomPayload(channel, bytes)` | `UnsupportedOperationException`: 26.x replaced raw plugin channels with typed `CustomPacketPayload`s |
| `SimpleTexture#checkTextureUploaded` / `#getId` | `UnsupportedOperationException`: dynamic GPU texture upload not ported (acts on capes / plugin images) |
| `SimpleTickingTexture` | frame bookkeeping only; no GPU textures are created |
| `GLUtilHandle` colour / blend / depth / blendFunc | no-ops (26.x pipelines own that state) |
| `WrappedItemStack` "+damage/+armor" overlay | not drawn (attribute modifiers became a data component) |
| `MixinAbstractClientPlayer` (capes) | inert: `getCloakTextureLocation` is gone, skins are `PlayerSkin` records |
| `MixinTitleScreen` version stamp, `MixinDisconnectedScreen` countdown draw | hooks removed: those screens no longer override the render pass |
| `GuiList` / `FileSelector` custom background images | flat fill / texture blit (no `setColor`, no `blitRepeating`) |

## Verification done here

* `gradle clean build` → `BUILD SUCCESSFUL`, 1 642 entries in the jar.
* `check-mixins.py` — every `@Inject` target descriptor exists in the 26.1.2 jar.
* `check-handlers.py` — every injector handler's parameter list matches its target descriptor.
* `fabric.mod.json`: `minecraft >=26.1.2 <26.2`, `java >=25`, `fabricloader >=0.20.1`; mixin config
  `compatibilityLevel: JAVA_25`; all 13 listed mixin classes are present in the jar.
* No client was started from this build (`runClient` deliberately not used).
