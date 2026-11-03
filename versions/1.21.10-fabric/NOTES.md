# 1.21.10 Fabric port — notes

Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon build`
Artifact: `build/libs/5zig-renewed-1.21.10-fabric-4.0.0-alpha.1.jar`

## Toolchain

| Piece | Version | Why |
|---|---|---|
| Gradle | 8.14.3 | last 8.x line; Loom 1.13 requires 8.14 |
| Loom | 1.13.4 | newest Loom that still runs on JVM 21 (1.14+ needs Gradle 9.2, 1.18+ needs JVM 25) |
| Fabric Loader | 0.17.3 | same line as the verified 1.21.11 build |
| Fabric API | 0.138.4+1.21.10 | last 1.21.10 build published on the Fabric maven |
| Mappings | officialMojangMappings | |
| Java | 21 | |
| Decompiled sources | `~/work/mc-src/1.21.10/out` | vineflower 1.12.0, `-dgs=1 --only=net/minecraft` |

The project is a copy of `versions/1.21.11-fabric` with the Minecraft/Fabric versions, the archive
name, the platform dir and the two resource dirs pointed at 1.21.10.

## Core overlay (`compat-src/`)

Unchanged from 1.21.11 and still required: 1.21.10 ships the same authlib 7 (`GameProfile` is a
record, no client-side Yggdrasil) and commons-lang3 3.19 (`Strings` collides with Guava's). Twelve
core files live in `compat-src/`; `coreCompatTree` copies `core/` into `build/core-compat` without
them. Behaviour change in `AccountManager`: offline ("legacy") accounts still work, online Mojang
login throws `AuthenticationException`.

## What actually differs from 1.21.11

1.21.10 (2025-10-07) and 1.21.11 (2025-12-09) are on the same side of the 1.21.9 render-state split,
so the HUD path is identical. The delta is renames plus a few small API moves:

| 1.21.11 | 1.21.10 |
|---|---|
| `net.minecraft.resources.Identifier` (`withDefaultNamespace`, `ResourceKey#identifier`) | `net.minecraft.resources.ResourceLocation` (`#withDefaultNamespace`, `ResourceKey#location`) — already `final`, so the composition wrapper stays |
| `net.minecraft.client.renderer.rendertype.RenderType` | `net.minecraft.client.renderer.RenderType` |
| `net.minecraft.util.Util` | `net.minecraft.Util` |
| `net.minecraft.client.GraphicsPreset` + `Options#graphicsPreset()` (…/CUSTOM) | `net.minecraft.client.GraphicsStatus` + `Options#graphicsMode()` (FAST/FANCY/FABULOUS) |
| `ChatComponent#render(GuiGraphics, Font, int, int, int, boolean, boolean)` | `ChatComponent#render(GuiGraphics, int, int, int, boolean)` |
| `ServerStatusPinger#pingServer(ServerData, Runnable, Runnable, EventLoopGroupHolder)` | `#pingServer(ServerData, Runnable, Runnable)` |
| `AbstractButton#renderWidget` is `final`; subclasses implement `renderContents` + `renderDefaultSprite` | `renderWidget(GuiGraphics, int, int, float)` is the overridable hook; `AbstractButton` keeps its sprite table private, so `platform/Button` rebuilds the three `widget/button*` sprites |

`platform/ResourceLocation.java` still wraps the vanilla value by composition (it has been `final`
since 1.21.9); the unwrap method is now `toResourceLocation()` and every reference to the vanilla type
is fully qualified, because the simple name belongs to the wrapper.

## Colour trap (strict ARGB)

Since 1.21.9 the alpha channel of every `GuiGraphics` colour goes straight into the blend state, so a
legacy `0xRRGGBB` value (alpha 0) draws *nothing* and reports no error. The core is full of those
constants. `eu.the5zig.mod.util.RenderColor#opaque(int)` (new in this platform) restores the missing
alpha and is applied at every colour-carrying call:

* `Variables#drawString` (carried over) **and** `Variables#drawCenteredString` — the 1.21.11 build
  missed the centred variant, so its centred text (most of the HUD) was invisible
* `Button`/`StringButton#drawLabel`, `GuiList#drawHeader`, `Textfield` (`TEXT_COLOR` and
  `TEXT_COLOR_DISABLED` are now opaque constants)
* `Overlay` toast titles, `WrappedItemStack` stack-size overlay, `MixinPlayerTabOverlay` ping number
* `GuiHandle#drawRect` / `#drawGradientRect` (the core's `IVariables` rectangles)
* Widget sprite tints go through `ARGB.white(alpha)`, exactly like vanilla `AbstractButton`.

Remaining legacy-alpha colours are only the ones that deliberately carry an alpha byte
(`Gui2ndChat` fade/scroll, `FileSelector` overlay gradient, `ColorSelector` swatches, `Textfield`
chrome).

## Deliberate degradations

| Area | What happens now |
|---|---|
| Legacy plugin channels (`sendCustomPayload`, inbound `handlePluginMessage`) | 1.21.6+ replaced free-form payloads with registered `CustomPacketPayload` types and the fallback codec writes zero bytes. Outbound messages are dropped with a single warning; inbound ones surface the channel id with an empty buffer. Server-side 5zig features are unavailable. |
| 5zig capes | `AbstractClientPlayer#getCloakTextureLocation` is gone; capes live in the immutable `PlayerSkin`. The resolver still runs, but the texture is not applied. |
| Enchantment damage bonus / protection | `EnchantmentHelper#getDamageBonus`/`#getDamageProtection` need a `ServerLevel` and `MobType`/`DamageSource` pipelines the client cannot evaluate; those terms are omitted from the tooltip and the resistance factor. |
| Panel tint | The 0.125 grey `setColor` before list/menu backgrounds is not expressible per draw call; the background is drawn untinted. |
| Animated capes | Texture animation is no longer driven through `Tickable`; frames advance from the wall clock (100 ms) on access. |
| Chat/click events | `ChatScreen#handleComponentClicked` became private; the second chat handles OpenUrl/RunCommand/SuggestCommand/CopyToClipboard itself. |
| Auto-reconnect overlay | `DisconnectedScreen` no longer overrides `render()`; the countdown is driven from the generic `Screen#render` hook. |

## HUD path (the part that must be real)

`MixinGui` brackets `Gui#render(GuiGraphics, DeltaTracker)`, publishes the `GuiGraphics` through
`RenderContext`, and draws the overlay (`GuiIngame#renderGameOverlay`) plus the hotbar add-ons
(`Gui#renderItemHotbar` TAIL — private in 1.21.10, which Mixin can still target). `Variables` draws
text with `GuiGraphics#drawString/drawCenteredString`, food/saturation icons via `hud/food_*` sprites,
potion icons via `Gui#getMobEffectSprite`, and `GLUtilHandle` maps the core's matrix/state calls onto
the 2D `Matrix3x2fStack` (state calls are no-ops because state is baked into the pipeline).

## Verification done here

* `gradle build` green; artifact + refmap (`5zig-renewed-1.21.10-fabric-refmap.json`) generated,
  every mixin target resolved to intermediary (no `Cannot find target method` warnings), classes in
  the jar remapped to `net/minecraft/class_*`.
* `validateAccessWidener` passes.
* No client run (per task rules).
