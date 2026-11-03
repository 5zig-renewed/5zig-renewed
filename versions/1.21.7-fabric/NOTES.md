# 1.21.7 Fabric port — notes

Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon build`
Artifact: `build/libs/5zig-renewed-1.21.7-fabric-4.0.0-alpha.1.jar`

## Toolchain

| Piece | Version | Why |
|---|---|---|
| Gradle | 8.14.3 | Loom 1.13 needs 8.14 |
| Loom | 1.13.4 | newest Loom that still runs on JVM 21 |
| Fabric Loader | 0.17.3 | same as the 1.21.8 build |
| Fabric API | 0.129.0+1.21.7 | newest build published for 1.21.7 (`maven-metadata.xml`); no 0.134.x exists for this game version |
| Mappings | officialMojangMappings | |
| Java | 21 | |

`mixin { useLegacyMixinAp = true }` is required: Loom 1.13 defaults it off, which leaves the mixin
config without a `refmap` entry and the `@Inject` targets in named form, so they fail to resolve
against the intermediary-remapped game jar at runtime.

## Base: platform/1.21.8 (era E9, pre-1.21.9 split)

1.21.7 and 1.21.8 are both inside the 1.21.6 GUI render-stack rewrite
(`Matrix3x2f` pose stack, `RenderPipelines.GUI_TEXTURED`, `blitSprite` widget sprites) and both
predate the 1.21.9 `renderHotbarAndDecorations` split. `platform/1.21.7` is therefore a copy of
`platform/1.21.8`; no source change was needed.

## Delta vs 1.21.8 — measured, not assumed

| Check | Result |
|---|---|
| Public/protected API of all 101 `net.minecraft` classes imported by `platform/1.21.7` + `loader/fabric/java` (javap diff against both named jars) | **0 differences** |
| Decompiled bodies of the GUI classes the mod draws through (`Gui`, `GuiGraphics`, `Font`, `ChatComponent`, `AbstractButton`, `Button`, `AbstractSliderButton`) | byte-identical logic; diff is decompiler formatting only (nested types printed qualified) |
| All 1479 compiled classes vs the 1.21.8 jar | 1478/1479 byte-identical (sha256); the one difference is the compat overlay's `AccountManager`, where only the user-facing "not available on Minecraft 1.21.7" string changed |
| Mixin refmap targets (13 mixin classes, 30 targets) | 30/30 resolved to the *same* intermediary descriptors as 1.21.8, e.g. `Gui#renderItemHotbar` → `class_329.method_1759(class_332, class_9779)`, `Gui#render` → `class_329.method_1753`, `ChatComponent#render` → `class_338.method_1805` |
| Bundled libraries | authlib 6.0.58, commons-lang3 3.17.0, guava 33.3.1, DFU 8.0.16 — identical in both versions |

So the port is a no-op at the code level: 1.21.7's client surface, as used here, is the same as
1.21.8's. Everything that made 1.21.8 compile applies unchanged.

## Traps carried over (all preserved verbatim)

* `useLegacyMixinAp = true` (above).
* **Text colour alpha.** `GuiGraphics#drawString` in 1.21.7 is
  `if (ARGB.alpha(k) != 0) { submitText(...) }` — verified in the 1.21.7 decompile, lines 218-223 —
  so the core's legacy `0xRRGGBB` colours would be dropped silently. All 13 call sites still go
  through `platform/Argb.opaque(...)` (`Variables`, `Textfield`, `Button`, `StringButton`, `GuiList`,
  `Overlay`, `WrappedItemStack`, `MixinPlayerTabOverlay`). Explicit translucent colours
  (`Gui2ndChat`'s `16777215 + (alpha << 24)`) are passed through untouched.
* **Hotbar hook.** 1.21.7 has `Gui#renderItemHotbar(GuiGraphics, DeltaTracker)` (private, called from
  `renderHotbarAndDecorations`) and no `renderHotbar`; `MixinGui` still injects at its `TAIL`.
* **Jar-in-jar.** The `include(...)` lines are unchanged, so the six third-party jars
  (DiscordIPC, jacob, json, sqlite-jdbc, httpclient, netty-codec-http) are nested under
  `META-INF/jars/` and listed in `fabric.mod.json`'s `jars`.

## Core overlay (`compat-src/`)

Same two files as 1.21.8, for the same reason (authlib 6 dropped the legacy Yggdrasil client;
`GameProfile` is still a bean and commons-lang3 3.17 has no `Strings`):

* `eu/the5zig/mod/account/AccountManager.java` — offline accounts work, online login throws
  `AuthenticationException`.
* `eu/the5zig/mod/chat/network/packets/PacketEncryption.java` — one-argument
  `YggdrasilAuthenticationService(proxy)`.

## Inherited degradations (same as 1.21.8)

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
`Minecraft#clickMouse`, which does not exist in 1.21.7 (the mixin AP cannot resolve a descriptor and
compiles it as a no-op). That mixin is only listed in `core/resources/mixins.json`, which this
Fabric `fabric.mod.json` does not register, so the CPS left-click counter is inert on this loader —
the same is true of the 1.21.8 build.

## Status

* `gradle build` green (clean 23 s, incremental 17 s; 2 compiler warnings, both pre-existing), 1479 classes,
  `mixins.the5zigmod.json` with `"refmap": "5zig-renewed-1.21.7-fabric-refmap.json"`, refmap fully
  resolved, six nested jars, `fabric.mod.json` `minecraft: ~1.21.7`.
* **Not client-verified** — no Minecraft client was launched. Compile + refmap resolution is the
  whole proof here.
