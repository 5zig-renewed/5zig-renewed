# 1.21.11 Fabric port — notes

Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon build`
Artifact: `build/libs/5zig-renewed-1.21.11-fabric-4.0.0-alpha.1.jar`

## Toolchain

| Piece | Version | Why |
|---|---|---|
| Gradle | 8.14.3 | last 8.x line; Loom 1.13 requires 8.14 |
| Loom | 1.13.4 | newest Loom that still runs on JVM 21 (1.14+ needs Gradle 9.2, 1.18+ needs JVM 25) |
| Fabric Loader | 0.17.3 | 1.21.11 era |
| Fabric API | 0.141.6+1.21.11 | |
| Mappings | officialMojangMappings | |
| Java | 21 | |

## Core overlay (`compat-src/`)

`core/` is shared with the verified 1.20.1 build and is left untouched. Minecraft 1.21.11 ships
authlib 7, where `GameProfile` is a record (`id()`/`name()` instead of `getId()`/`getName()`) and the
legacy Yggdrasil client (`UserAuthentication`, `Agent`) is gone, and commons-lang3 3.19 added a
`Strings` class that collides with Guava's in two `*`-imported files. Twelve core files that need
those changes live in `compat-src/`; `coreCompatTree` copies `core/` into `build/core-compat`
without them. Everything else in the core is compiled straight from `core/`.

Behaviour change in `AccountManager`: offline ("legacy") accounts still work; online Mojang account
login now throws `AuthenticationException` because authlib 7 has no client-side login API.

## Platform layer

Ported against decompiled 1.21.11 sources (`~/work/mc-src/1.21.11/out`). The big breaks:

* `ResourceLocation` → `Identifier`, and it is now `final`; `platform.ResourceLocation` became a
  wrapper with `toIdentifier()` instead of a subclass.
* GUI matrices are 2D JOML `Matrix3x2fStack` (`pushMatrix`/`popMatrix`, no z axis); `PoseStack` is
  gone from GUI rendering.
* Every blit needs an explicit `RenderPipeline` (`RenderPipelines.GUI_TEXTURED`); `setColor`,
  `setShaderColor`, `enableBlend`, `depthMask`, `blitRepeating` and `blitNineSliced` were removed.
  Widgets draw from GUI-atlas sprites (`widget/button`, `widget/slider`, `toast/system`, `hud/food_*`).
* Input callbacks take records: `MouseButtonEvent`, `KeyEvent`, `CharacterEvent`.
* `Screen#renderBackground(GuiGraphics,int,int,float)`, `AbstractButton#renderContents`,
  `Toast#update/getWantedVisibility/render(GuiGraphics,Font,long)`, `TextureManager` only accepts
  `Identifier`, texture ids are gone (`DynamicTexture` owns a `GpuTexture`).

## Deliberate degradations

| Area | What happens now |
|---|---|
| Legacy plugin channels (`sendCustomPayload`, inbound `handlePluginMessage`) | 1.21.6+ replaced free-form payloads with registered `CustomPacketPayload` types and the fallback codec writes zero bytes. Outbound messages are dropped with a single warning; inbound ones surface the channel id with an empty buffer. Server-side 5zig features are unavailable. |
| 5zig capes | `AbstractClientPlayer#getCloakTextureLocation` is gone; capes live in the immutable `PlayerSkin`. The resolver still runs, but the texture is not applied. |
| Enchantment damage bonus / protection | `EnchantmentHelper#getDamageBonus`/`#getDamageProtection` need a `ServerLevel` and `MobType`/`DamageSource` pipelines the client cannot evaluate; those terms are omitted from the tooltip and the resistance factor. |
| Panel tint | The 0.125 grey `setColor` before list/menu backgrounds is not expressible per draw call; the background is drawn untinted. |
| Animated capes | `Tickable` was removed; frames advance from the wall clock (100 ms) on access. |
| Chat/click events | `ChatScreen#handleComponentClicked` became private; the second chat handles OpenUrl/RunCommand/SuggestCommand/CopyToClipboard itself. |
| Auto-reconnect overlay | `DisconnectedScreen` no longer overrides `render()`; the countdown is driven from the generic `Screen#render` hook. |

## HUD path (the part that must be real)

`MixinGui` brackets `Gui#render(GuiGraphics, DeltaTracker)`, publishes the `GuiGraphics` through
`RenderContext`, and draws the overlay (`GuiIngame#renderGameOverlay`) plus the hotbar add-ons
(`Gui#renderItemHotbar` TAIL). `Variables` draws text with `GuiGraphics#drawString/drawCenteredString`,
food/saturation icons via `hud/food_*` sprites, potion icons via `Gui#getMobEffectSprite`, and
`GLUtilHandle` maps the core's matrix/state calls onto the 2D pose stack (state calls are no-ops
because state is baked into the pipeline).
