# 1.20.5 Fabric port notes

Built with `gradle-8.10.2` / JDK 21, `--no-daemon`, target Java 21 (class file 65).

| Piece | Version |
|---|---|
| Minecraft | 1.20.5 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.16.10 |
| Fabric API | 0.97.8+1.20.5 |
| Mixin compatibility level | `JAVA_21` |

Build: `cd versions/1.20.5-fabric && JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.20.5-fabric-4.0.0-alpha.1.jar` (18,812,435 bytes)

## Decompiled reference

`mc-src/1.20.5/out` — 5130 `.java` files, vineflower 1.12.0,
`-dgs=1 --only=net/minecraft`, taken from the **named** loom jar

`versions/1.20.5-fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-31d2365197/…`

not from `~/.gradle/caches/fabric-loom/1.20.5/minecraft-merged.jar`: that cache entry is the
*obfuscated* merged jar, so `--only=net/minecraft` matches only the 14 classes that are already
unobfuscated (`Main`, `MinecraftServer`, the JFR events, …) and the decompile silently produces
almost nothing.

## Where 1.20.5 sits between 1.20.4 and 1.20.6

1.20.5 is the release that introduced the whole 1.20.5/1.20.6 API surface 5zig cares about, so the
port is **platform/1.20.6 copied verbatim** — no API edits were needed:

- item components (`DataComponents.FOOD`, `Item.TooltipContext`, `forEachModifier`, `Holder`-based
  attributes/mob effects) — introduced in 1.20.5, not 1.20.6;
- `Gui#renderHotbarAndDecorations(GuiGraphics, float)`, `ChatComponent#render(…, boolean focused)`,
  `Screen.MENU_BACKGROUND` / `renderMenuBackground`, `StringUtil.filterText`,
  `ClientboundTabListPacket` as a record, `ServerStatusPinger#pingServer(ServerData, Runnable,
  Runnable)`, `ConnectScreen.startConnecting(…, TransferState)` — all 1.20.5 changes;
- the typed-payload-only Fabric networking API (payload types registered through
  `PayloadTypeRegistry`, `RawCustomPayload` deleted, `PluginMessages.RawPayload` carrying the raw
  bytes) — Fabric API 0.97.8 exposes the same typed-only surface as 0.100.8 does on 1.20.6.

Measured delta between the two decompiled trees: **5131 files in 1.20.6 vs 5130 in 1.20.5**, one new
file (`util/datafix/schemas/V3808_2.java`) and 14 changed classes:

```
DetectedVersion, SharedConstants, creative inventory screen, ReportEnvironment, TagKey,
DataFixers, ChestedHorsesInventoryZeroIndexingFix, CustomData, BaseCommandBlock,
BannerBlockEntity, BaseContainerBlockEntity, BeaconBlockEntity, BlockEntity,
EnchantingTableBlockEntity
```

None of them is imported by `platform/1.20.5`, `loader/fabric-1.20.5`, `core`, `common` or `api`:
of the **109 distinct `net.minecraft.*` classes the platform imports, 0 differ** between the two
versions. That is why the copied platform compiles and remaps without a single edit.

Only text changed relative to 1.20.6: three comments and one log message that named 1.20.6, plus
the entrypoint class (`FiveZigFabric1206` → `FiveZigFabric1205`).

## Verification performed

- `gradle --no-daemon clean build` green (6 tasks executed); jar contains remapped classes, the
  mixin refmap, the access widener and the eight nested runtime jars (DiscordIPC, jacob, json,
  json-simple, junixsocket-common, junixsocket-native-common, sqlite-jdbc, netty-codec-http).
- `tools/check_mixins.py` resolves every `@Inject` target (`name + descriptor`) against the 1.20.5
  named jar: **36/36 present**.
- The generated refmap is **identical to the 1.20.6 one** — 14 mixin classes, 33 member mappings,
  same intermediary ids — so every mixin target maps onto the same 1.20.5 member Loom remapped
  against.
- `fabric.mod.json` entrypoint (`FiveZigFabric1205`), mixin list (14 classes), `~1.20.5` /
  `java >=21` dependencies and the `5zig-Version` / `Minecraft-Version` / `5zig-Build` manifest
  attributes verified inside the built jar.
- Vanilla textures the platform blits exist in the 1.20.5 assets (`gui/container/inventory.png`,
  `gui/demo_background.png`, `gui/menu_background.png`, `misc/unknown_server.png`,
  `environment/snow.png`).

## Known gaps

- No client launch was performed (per task rules), so HUD/chat pixels are verified only by API
  compatibility + mixin target resolution, not on screen.
- Custom payloads on channels other than `the5zigmod:5zig`, `…:5zig_reg` and `…:5zig_set` are
  impossible from 1.20.5 on (vanilla's fallback codec drops unknown payload bodies).
- Pre-existing, not 1.20.5-specific: `core/…/chat/entity/Friend.java` and
  `platform/1.20.5/…/GuiHandle#DEFAULT_TEXTURE` still name `textures/gui/resource_packs.png` and
  `textures/gui/widgets.png`, which vanilla removed in 1.20.2. Same behaviour as 1.20.4/1.20.6.
