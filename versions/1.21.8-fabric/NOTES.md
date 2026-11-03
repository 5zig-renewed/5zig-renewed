# 1.21.8 Fabric port — notes

Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon build`
Artifact: `build/libs/5zig-renewed-1.21.8-fabric-4.0.0-alpha.1.jar`

## Toolchain

| Piece | Version | Why |
|---|---|---|
| Gradle | 8.14.3 | Loom 1.13 needs 8.14 |
| Loom | 1.13.4 | newest Loom that still runs on JVM 21 |
| Fabric Loader | 0.17.3 | 1.21.8 era |
| Fabric API | 0.136.1+1.21.8 | published build for 1.21.8 |
| Mappings | officialMojangMappings | |
| Java | 21 | |

`mixin { useLegacyMixinAp = true }` is required: Loom 1.13 defaults it off, which leaves the
mixin config without a `refmap` entry and the `@Inject` targets in named form, so they fail to
resolve against the intermediary-remapped game jar at runtime.

## Base: 1.21.11 platform, not 1.21.1

1.21.8 already has the 1.21.6 GUI render-stack rewrite (`Matrix3x2fStack` pose stack,
`RenderPipelines.GUI_TEXTURED`, `blitSprite` widget sprites, `Toast#getWantedVisibility`), so the
1.21.11 platform is the correct base and 1.21.1 would have been a regression. The 1.21.9 renames
were reverted instead.

## API breakages vs 1.21.11 (all verified against decompiled 1.21.8)

| 1.21.11 | 1.21.8 |
|---|---|
| `net.minecraft.resources.Identifier` | `net.minecraft.resources.ResourceLocation` (still `final`, so the wrapper-by-composition in `platform/ResourceLocation.java` stays; `toIdentifier()` → `toResourceLocation()`, plus a `toMinecraft(Object)` unwrapper) |
| `net.minecraft.client.renderer.rendertype.RenderType` | `net.minecraft.client.renderer.RenderType` |
| `net.minecraft.util.Util` | `net.minecraft.Util` |
| `net.minecraft.client.input.{MouseButtonEvent,KeyEvent,CharacterEvent}` | plain `(double,double,int)` / `(int,int,int)` / `(char,int)` callbacks (`Screen`, `ChatScreen`, `EditBox`) |
| `AbstractButton#onClick(MouseButtonEvent,boolean)` / `#onRelease(MouseButtonEvent)` | `onClick(double,double)`, no `onRelease` |
| `AbstractButton#renderContents` + `renderDefaultSprite` | `AbstractButton#renderWidget` draws the sprite and calls `renderString`; 5zig's `Button` overrides `renderString`, `Slider` overrides `renderWidget` |
| `KeyMapping.Category` (registered) | free-form category `String` (`new KeyMapping(name, key, category)`) |
| `Options#graphicsPreset()` → `GraphicsPreset{FANCY,FABULOUS,CUSTOM}` | `Options#graphicsMode()` → `GraphicsStatus{FANCY,FABULOUS}` |
| `Window#handle()` | `Window#getWindow()` (still the GLFW handle) |
| `Minecraft#hasControlDown()/hasShiftDown()` | `Screen#hasControlDown()/hasShiftDown()` (static) |
| `Holder#identifier()` | `Holder#unwrapKey()` + `ResourceKey#location()` |
| `ServerStatusPinger#pingServer(data, ok, fail, EventLoopGroupHolder)` | `pingServer(data, ok, fail)` |
| `ChatComponent#render(GuiGraphics, Font, int, int, int, boolean, boolean)` | `render(GuiGraphics, int ticks, int mouseX, int mouseY, boolean focused)` |
| `Gui#tick()` | `Gui#tick(boolean)` |
| `new ChatScreen(text, false)` | `new ChatScreen(text)` |
| `User(name, uuid, token, clientId, xuid)` | `User(name, uuid, token, xuid, clientId, Type)` (authlib 6 still has the account type) |

## Draw-path trap: legacy RGB colours are invisible

1.21.8's `GuiGraphics#drawString` is

```java
if (ARGB.alpha(k) != 0) { this.guiRenderState.submitText(...); }
```

so a legacy `0xRRGGBB` colour (alpha byte 0), which is what the 1.15-era core passes everywhere,
is **skipped silently** — the draw "runs", nothing appears. Every text colour now goes through
`platform/Argb.opaque(int)` (`(color & 0xFF000000) == 0 ? color | 0xFF000000 : color`) at the
`drawString`/`drawCenteredString` call sites: `Variables`, `Textfield`, `Button`, `StringButton`,
`GuiList`, `Overlay`, `WrappedItemStack`, `MixinPlayerTabOverlay`. Explicit translucent colours
are passed through unchanged.

## Hotbar hook

`Gui#renderItemHotbar(GuiGraphics, DeltaTracker)` **does exist in 1.21.8** — it is private (the
1.21.9+ signature is `public`), and `renderHotbarAndDecorations` calls it when the player is not a
spectator. `MixinGui` keeps its `TAIL` injection into it; the refmap resolves it to
`class_329.method_1759(Lnet/minecraft/class_332;Lnet/minecraft/class_9779;)V`, so the hook is real.
There is no `renderHotbar` method in 1.21.8 to hook instead.

## Core overlay (`compat-src/`)

Only two files differ from `core/` now, both because authlib 6 (1.21.8) has no legacy Yggdrasil
client (`UserAuthentication`/`Agent`/`YggdrasilAuthenticationService(Proxy,String)` are gone; this
already happened in authlib 6, not 7):

* `eu/the5zig/mod/account/AccountManager.java` — offline accounts work, online login throws
  `AuthenticationException` ("select the account in the launcher").
* `eu/the5zig/mod/chat/network/packets/PacketEncryption.java` — the one-argument
  `YggdrasilAuthenticationService(proxy)` and `joinServer(profile.getId(), ...)`.

Everything else the 1.21.11 build had to patch (`getId()/getName()` accessors, the
commons-lang3 `Strings` ambiguity) does **not** apply to 1.21.8: authlib 6's `GameProfile` is
still a bean and 1.21.8 ships commons-lang3 3.17.0, which has no `org.apache.commons.lang3.Strings`.
Ten files were therefore dropped from the overlay and compile from `core/` unchanged.

## Inherited degradations (same as 1.21.11)

| Area | What happens |
|---|---|
| Legacy plugin channels | 1.21.6+ replaced free-form payloads with registered `CustomPacketPayload` types; outbound messages are dropped with a warning, inbound ones surface the channel with an empty buffer |
| 5zig capes | `AbstractClientPlayer#getCloakTextureLocation` is gone; the resolver runs, the texture is not applied |
| Enchantment damage bonus / protection | needs a `ServerLevel`, omitted from tooltips and the resistance factor |
| Panel tint | the 0.125 grey `setColor` before list/menu backgrounds is not expressible per draw call |
| Animated capes | `Tickable` is gone; frames advance from the wall clock (100 ms) |
| Chat/click events | `ChatScreen#handleComponentClicked` is private; the second chat resolves OpenUrl/RunCommand itself |
| Auto-reconnect overlay | driven from the generic `Screen#render` hook |

## Status

* `gradle build` green; jar contains 1479 classes, `mixins.the5zigmod.json` with
  `"refmap": "5zig-renewed-1.21.8-fabric-refmap.json"`, and a refmap in which **every** mixin
  target resolved to a real intermediary method (that is the compile-time proof that no
  `@Inject` points at a method 1.21.8 does not have).
* **Not client-verified** — no Minecraft client was launched for this port. The HUD overlay draw
  path (`HudFrame.begin` → `GuiIngame#renderGameOverlay` → `Variables` text/blits) compiles and the
  colour-alpha trap above is fixed, but nothing has been rendered on screen yet.
