# 1.21.1 Fabric port notes

Built with `gradle-8.10.2` / JDK 21, `--no-daemon`, target Java 21 (class file 65).

| Piece | Version |
|---|---|
| Minecraft | 1.21.1 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.16.10 |
| Fabric API | 0.116.17+1.21.1 |
| Mixin compatibility level | `JAVA_21` |

Build: `cd versions/1.21.1-fabric && JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.21.1-fabric-4.0.0-alpha.1.jar`

## Decompiled reference

`mc-src/1.21.1/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`), decompiled from
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2/`.

## Core deltas (NOT applied — the 1.21.1 platform supplies replacements instead)

authlib 6 (shipped with 1.21.1) removed the legacy Yggdrasil user authentication, so two core files cannot
compile unchanged on 1.21.1:

1. `core/eu/the5zig/mod/account/AccountManager.java` — imports `com.mojang.authlib.Agent` /
   `UserAuthentication`, calls `AuthenticationService#createUserAuthentication` and the
   `YggdrasilAuthenticationService(Proxy, String)` constructor. Replaced by
   `platform/1.21.1/eu/the5zig/mod/account/AccountManager.java`: same public API, offline accounts and the
   encrypted account list still work, e-mail/password login throws `AuthenticationException` because
   1.21.1 authentication is Microsoft-only and owned by the launcher.
2. `core/eu/the5zig/mod/chat/network/packets/PacketEncryption.java` — the constructor lost its UUID argument
   and `MinecraftSessionService#joinServer` takes `(UUID, String, String)` instead of `(GameProfile, ...)`.
   Replaced by a version-local copy with the same wire format.

The replacements are wired in `build.gradle` by excluding those two core paths and adding the platform
source directory rooted at `platform/1.21.1/eu` (Gradle exclude patterns are relative to each source
directory, so the two files can coexist with the core originals).

If the core is ever made authlib-6 aware, delete the two platform copies and the three lines in
`sourceSets` that reference them.

## 1.21.1 API changes that required work

- `Gui#render(GuiGraphics, DeltaTracker)` (was `float`), `renderHotbar` → `renderItemHotbar(GuiGraphics, DeltaTracker)`.
- `Screen#renderBackground(GuiGraphics, int, int, float)`, `renderDirtBackground` → `renderMenuBackground`,
  `mouseScrolled(double, double, double, double)`.
- `ResourceLocation` is `final` with private constructors and no longer subclassable → the platform type is
  now a wrapper around a vanilla location (`toMinecraft()` / `ResourceLocation.toMinecraft(IResourceLocation)`).
- `textures/gui/widgets.png`, `textures/gui/toasts.png` and `Screen.BACKGROUND_LOCATION` are gone: buttons and
  sliders draw `widget/button*`, `widget/slider*` sprites, toasts draw `toast/system`, list backgrounds use
  `Screen.MENU_BACKGROUND`. `GuiGraphics#blitNineSliced`/`blitRepeating` were removed (`GuiCompat.blitRepeating`).
- `ChatComponent#render` gained a trailing `boolean`; `AbstractClientPlayer#getCloakTextureLocation` was
  replaced by `PlayerSkin` (cape is spliced into `getSkin()`), `DisconnectedScreen` no longer overrides
  `render` (countdown moved to the `Screen#render` hook).
- Custom payloads are typed: `ClientboundCustomPayloadPacket`/`ServerboundCustomPayloadPacket` moved to
  `network.protocol.common`, unknown channels arrive as body-less `DiscardedPayload`. The 5zig API channels
  are registered through Fabric `PayloadTypeRegistry` (`PluginMessages`) from the 1.21.1 entrypoint.
- Mob effects are `Holder<MobEffect>` based (`Registry#getHolder`, `MobEffectUtil.formatDuration(instance, float, float)`,
  `MobEffectTextureManager#get(Holder)`), `MobEffect#byId`/`getId` live on the registry.
- `ItemStack#getTooltipLines` needs `Item.TooltipContext`, food data is the `FOOD` component
  (`nutrition()`/`saturation()`), attributes come from `forEachModifier`, `AttributeModifier` is a record.
- `EnchantmentHelper#getDamageBonus` removed; `getDamageProtection` now needs a `ServerLevel` (returns 0 on a client).
- `SharedConstants.filterText/isAllowedChatCharacter` → `StringUtil`; `EditBox#moveCursorToEnd(boolean)`.
- `ServerData.Type`, `ConnectScreen.startConnecting(..., TransferState)`, `ServerStatusPinger#pingServer`
  takes two callbacks, `Minecraft#clearClientLevel`, `getCurrentServer().isRealm()`, `User` takes a `UUID`,
  debug HUD state is `getDebugOverlay().showDebugScreen()`, scoreboard uses `DisplaySlot`/`listPlayerScores`.

## Known gaps

- Item tooltip attribute overlay no longer adds the enchantment damage bonus (API removed in 1.21.1).
- Damage-protection HUD value ignores enchantments (server-side API).
- `sendCustomPayload` only works for the three registered 5zig channels; other channels are refused with a
  one-time warning (1.21.1 cannot send unregistered payload types).
- The legacy `textures/gui/widgets.png` fallback in `GuiHandle` no longer exists in the 1.21.1 assets.
- `platform/1.21.1` was forked from the 1.20.1 platform at the start of the port; later 1.20.1 work
  (keybind dispatch: `MixinKeyboardHandler`, `KeyDispatch`, `ModMenuKey`) is not mirrored yet.

## Verification performed

- `gradle --no-daemon clean build` succeeds; jar contains the remapped classes and `…-refmap.json`.
- `tools/check_mixins.py` resolves every `@Inject`/`@ModifyVariable` target (`name + descriptor`) against the
  1.21.1 named jar: 35/35 present. Run it after touching a mixin.
- No client was launched (per task rules).
