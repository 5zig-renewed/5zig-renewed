# 1.21.6 Fabric port — notes

Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon build`
Artifact: `build/libs/5zig-renewed-1.21.6-fabric-4.0.0-alpha.1.jar`

## Toolchain

| Piece | Version | Why |
|---|---|---|
| Gradle | 8.14.3 | Loom 1.13 needs 8.14 |
| Loom | 1.13.4 | newest Loom that still runs on JVM 21 |
| Fabric Loader | 0.17.3 | same as the 1.21.7/1.21.8 builds |
| Fabric API | 0.128.2+1.21.6 | newest build published for 1.21.6 (`maven-metadata.xml`; this line stops at 0.128.2, there is no 0.129.x/0.134.x for 1.21.6) |
| Mappings | officialMojangMappings | |
| Java | 21 | |

`mixin { useLegacyMixinAp = true }` is required: Loom 1.13 defaults it off, which leaves the mixin
config without a `refmap` entry and the `@Inject` targets in named form, so they fail to resolve
against the intermediary-remapped game jar at runtime.

The `jar`/`remapJar` manifest block (`5zig-Version`, `Minecraft-Version`, `5zig-Build`) is carried
over from the 1.21.5/1.21.10 builds — without it `eu.the5zig.mod.Version` falls back to `bDEV`.

## Base: platform/1.21.7 (era E9, pre-1.21.9 split)

1.21.6 is the *start* of the GUI render-state rewrite (`Matrix3x2f` pose stack,
`RenderPipelines.GUI_TEXTURED`, `blitSprite` widget sprites) and predates the 1.21.9
`renderHotbarAndDecorations` split, exactly like 1.21.7/1.21.8. `platform/1.21.6` is therefore a
copy of `platform/1.21.7`; no source change was needed.

## Delta vs 1.21.7 — measured, not assumed

| Check | Result |
|---|---|
| Public/protected API of all 101 `net.minecraft` classes imported by `platform/1.21.6` + `loader/fabric/java` (javap diff against both named jars) | 5 differences, **all additions in 1.21.7 or private internals**: `Util.isAarch64()`, `GuiGraphics.textHighlight(IIII)`, `RenderPipelines.GUI_INVERT` and `Items.MUSIC_DISC_LAVA_CHICKEN` exist only in 1.21.7; `EditBox.renderHighlight` is private in 1.21.6 and was folded into the new public `textHighlight` in 1.21.7. None of them is referenced by this mod. Every class it actually calls is present with an identical signature. |
| `Gui.java` decompiled body | byte-identical (1449 lines, zero diff hunks) — `renderItemHotbar(GuiGraphics, DeltaTracker)` still private, still called from `renderHotbarAndDecorations` |
| `GuiGraphics.java` | 4 hunks: the added `textHighlight` + `ItemStackRenderState` → `TrackingItemStackRenderState` rename inside `renderItem`. `drawString` still guards on `if (ARGB.alpha(k) != 0)` |
| `Font.java` | 4 hunks, all the effect-depth constants (`0.001F` → `0.01F`); no API change |
| `AbstractButton`, `Button`, `AbstractSliderButton`, `ChatComponent` decompiled bodies | byte-identical |
| All 1479 compiled classes vs the 1.21.7 jar | **1479/1479 byte-identical** (sha256) before the version string in the compat overlay was retargeted; afterwards only `AccountManager` differs |
| Mixin refmap targets (13 mixin classes, 30 targets) | 30/30 resolve to the *same* intermediary descriptors as 1.21.7 (`Gui#renderItemHotbar` → `class_329.method_1759(class_332, class_9779)`), and all 30 descriptors exist in the 1.21.6 intermediary jar (javap-verified) |
| Bundled libraries | authlib 6.0.58, commons-lang3 3.17.0, guava 33.3.1-jre, DFU 8.0.16, asset index 26 — identical in both versions |

So the port is a no-op at the code level: 1.21.6's client surface, as used here, is the same as
1.21.7's.

## Traps carried over (all preserved verbatim)

* `useLegacyMixinAp = true` (above).
* **Text colour alpha.** `GuiGraphics#drawString` in 1.21.6 is
  `if (ARGB.alpha(k) != 0) { submitText(...) }` — verified in the 1.21.6 decompile, line 214 — so
  the core's legacy `0xRRGGBB` colours would be dropped silently. All 13 call sites still go
  through `platform/Argb.opaque(...)` (`Variables`, `Textfield`, `Button`, `StringButton`, `GuiList`,
  `Overlay`, `WrappedItemStack`, `MixinPlayerTabOverlay`). Explicit translucent colours
  (`Gui2ndChat`'s `16777215 + (alpha << 24)`) are passed through untouched.
* **Hotbar hook.** 1.21.6 has `Gui#renderItemHotbar(GuiGraphics, DeltaTracker)` (private, called from
  `renderHotbarAndDecorations`) and no `renderHotbar`; `MixinGui` still injects at its `TAIL`.
* **Jar-in-jar.** The `include(...)` lines are unchanged, so the six third-party jars
  (DiscordIPC, jacob, json, sqlite-jdbc, httpclient, netty-codec-http) are nested under
  `META-INF/jars/` and listed in `fabric.mod.json`'s `jars`.

## Core overlay (`compat-src/`)

Same two files as 1.21.7, for the same reason (authlib 6 dropped the legacy Yggdrasil client;
`GameProfile` is still a bean and commons-lang3 3.17 has no `Strings`):

* `eu/the5zig/mod/account/AccountManager.java` — offline accounts work, online login throws
  `AuthenticationException`.
* `eu/the5zig/mod/chat/network/packets/PacketEncryption.java` — one-argument
  `YggdrasilAuthenticationService(proxy)`.

## Inherited degradations (same as 1.21.7)

| Area | What happens |
|---|---|
| Legacy plugin channels | 1.21.6+ replaced free-form payloads with registered `CustomPacketPayload` types; outbound messages are dropped with a warning, inbound ones surface the channel with an empty buffer |
| 5zig capes | `AbstractClientPlayer#getCloakTextureLocation` is gone; the resolver runs, the texture is not applied |
| Enchantment damage bonus / protection | needs a `ServerLevel`, omitted from tooltips and the resistance factor |
| Panel tint | the 0.125 grey `setColor` before list/menu backgrounds is not expressible per draw call |
| Animated capes | `Tickable` is gone; frames advance from the wall clock (100 ms) |
| Chat/click events | `ChatScreen#handleComponentClicked` is private; the second chat resolves OpenUrl/RunCommand itself |
| Auto-reconnect overlay | driven from the generic `Screen#render` hook |

Also pre-existing (not introduced here): `common/.../MixinCommonMinecraft` injects into
`Minecraft#clickMouse`, which does not exist in 1.21.6 (the mixin AP cannot resolve a descriptor and
compiles it as a no-op). That mixin is only listed in `core/resources/mixins.json`, which this
Fabric `fabric.mod.json` does not register, so the CPS left-click counter is inert on this loader —
the same is true of the 1.21.7/1.21.8 builds.

## Status

* `gradle build` green from a deleted `build/` directory, 1479 classes,
  `mixins.the5zigmod.json` with `"refmap": "5zig-renewed-1.21.6-fabric-refmap.json"`, refmap 30/30
  resolved, six nested jars, manifest `5zig-Version: 4.0.0-alpha.1` /
  `Minecraft-Version: 1.21.6`, `fabric.mod.json` `minecraft: ~1.21.6`.
* **Not client-verified** — no Minecraft client was launched. Compile + refmap resolution is the
  whole proof here.
